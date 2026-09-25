package com.tradn.timeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tradn.common.exception.BizException;
import com.tradn.file.service.FileService;
import com.tradn.note.mapper.NoteMapper;
import com.tradn.note.model.Note;
import com.tradn.security.SecurityUtils;
import com.tradn.timeline.mapper.DailyTimelineMapper;
import com.tradn.timeline.model.DailyTimeline;
import com.tradn.timeline.model.TimelineCommand;
import com.tradn.timeline.model.TimelineEntryCommand;
import com.tradn.timeline.model.TimelineEntryOrderCommand;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TimelineService {
    private final DailyTimelineMapper mapper;
    private final NoteMapper noteMapper;
    private final JdbcTemplate jdbc;
    private final FileService files;

    public TimelineService(
            DailyTimelineMapper mapper,
            NoteMapper noteMapper,
            JdbcTemplate jdbc,
            FileService files) {
        this.mapper = mapper;
        this.noteMapper = noteMapper;
        this.jdbc = jdbc;
        this.files = files;
    }
    // 限制日期窗口，防止时间线一次加载全部历史图片和签名 URL。
    public List<Map<String, Object>> list(LocalDate from, LocalDate to) {
        if (ChronoUnit.DAYS.between(from, to) > 366) throw new BizException("时间线查询范围不能超过366天");
        List<DailyTimeline> rows =
                mapper.selectList(
                        new LambdaQueryWrapper<DailyTimeline>()
                                .eq(DailyTimeline::getUserId, SecurityUtils.userId())
                                .between(DailyTimeline::getTimelineDate, from, to)
                                .orderByAsc(DailyTimeline::getTimelineDate));
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (DailyTimeline row : rows) result.add(view(row));
        return result;
    }

    public Map<String, Object> get(LocalDate date) {
        DailyTimeline row = find(date);
        if (row == null) throw new BizException(404, "当日时间线不存在");
        return view(row);
    }
    // 时间线、普通笔记关系和自动汇总笔记必须在同一事务内保持一致。
    @Transactional
    public DailyTimeline save(LocalDate date, TimelineCommand c) {
        DailyTimeline row = find(date);
        Set<Long> previousNoteIds =
                row == null ? Collections.<Long>emptySet() : currentNoteIds(row.getId());
        if (row == null) {
            row = new DailyTimeline();
            row.setUserId(SecurityUtils.userId());
            row.setTimelineDate(date);
            row.setActiveUniqueKey(SecurityUtils.userId() + "#" + date);
            row.setDailyContent(c.getDailyContent());
            mapper.insert(row);
        } else {
            row.setDailyContent(c.getDailyContent());
            row.setVersion(c.getVersion());
            if (mapper.updateById(row) == 0) throw new BizException(409, "时间线已被其他页面修改");
        }
        replaceNotes(row.getId(), c.getNoteIds());
        // 只有新增关联笔记时才生成或更新黄金每日复盘，单独修改文字/图片不再制造笔记。
        if (hasNewLinkedNote(previousNoteIds, c.getNoteIds())) syncSummary(row);
        return row;
    }

    /** 新增一张独立备注卡片；同一天允许反复新增，旧卡片不会被覆盖。 */
    @Transactional
    public Map<String, Object> addEntry(LocalDate date, TimelineEntryCommand c) {
        DailyTimeline row = ensureRow(date);
        String type = normalizeEntryType(c.getEntryType());
        validateEntry(row, type, c);
        if ("NOTE".equals(type)) {
            addNoteRelation(row.getId(), c.getNoteId());
        }
        if ("IMAGE".equals(type)) {
            validateImageRelation(row.getId(), c.getFileId());
        }
        long entryId = IdWorker.getId();
        jdbc.update(
                "INSERT INTO timeline_entry(id,timeline_id,user_id,entry_type,content,note_id,file_id,sort_no,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                entryId,
                row.getId(),
                SecurityUtils.userId(),
                type,
                "TEXT".equals(type) ? c.getContent().trim() : null,
                "NOTE".equals(type) ? c.getNoteId() : null,
                "IMAGE".equals(type) ? c.getFileId() : null,
                nextSortNo(row.getId()),
                SecurityUtils.userId(),
                SecurityUtils.userId());
        if ("NOTE".equals(type)) syncSummary(row);
        return entryView(entryId);
    }

    /** 修改文字备注卡片，保留卡片 ID 和日期位置。 */
    @Transactional
    public Map<String, Object> updateTextEntry(
            LocalDate date, long entryId, TimelineEntryCommand c) {
        DailyTimeline row = find(date);
        if (row == null) throw new BizException(404, "当日时间线不存在");
        String content = c.getContent() == null ? "" : c.getContent().trim();
        if (content.isEmpty()) throw new BizException("文字备注不能为空");
        int changed =
                jdbc.update(
                        "UPDATE timeline_entry SET content=?,updated_by=?,version=version+1 WHERE id=? AND timeline_id=? AND user_id=? AND entry_type='TEXT' AND deleted=0",
                        content,
                        SecurityUtils.userId(),
                        entryId,
                        row.getId(),
                        SecurityUtils.userId());
        if (changed == 0) throw new BizException(404, "文字备注不存在或类型不匹配");
        return entryView(entryId);
    }

    /**
     * 保存同一天备注的人工顺序。请求必须包含当天全部有效卡片，避免过期页面把新卡片排除在外。
     */
    @Transactional
    public void reorderEntries(LocalDate date, TimelineEntryOrderCommand command) {
        DailyTimeline row = find(date);
        if (row == null) throw new BizException(404, "当日时间线不存在");
        List<Long> currentIds =
                jdbc.queryForList(
                        "SELECT id FROM timeline_entry WHERE timeline_id=? AND user_id=? AND deleted=0 ORDER BY sort_no,created_at",
                        Long.class,
                        row.getId(),
                        SecurityUtils.userId());
        List<Long> requestedIds = command == null ? null : command.getEntryIds();
        if (!isCompleteEntryOrder(currentIds, requestedIds)) {
            throw new BizException(409, "备注数据已变化，请刷新后重新排序");
        }
        for (int index = 0; index < requestedIds.size(); index++) {
            int changed =
                    jdbc.update(
                            "UPDATE timeline_entry SET sort_no=?,updated_by=?,version=version+1 WHERE id=? AND timeline_id=? AND user_id=? AND deleted=0",
                            index,
                            SecurityUtils.userId(),
                            requestedIds.get(index),
                            row.getId(),
                            SecurityUtils.userId());
            if (changed != 1) {
                throw new BizException(409, "备注数据已变化，请刷新后重新排序");
            }
        }
    }

    /** 校验排序请求没有遗漏、重复或混入其他日期的卡片。 */
    static boolean isCompleteEntryOrder(List<Long> currentIds, List<Long> requestedIds) {
        if (currentIds == null
                || requestedIds == null
                || currentIds.size() != requestedIds.size()) {
            return false;
        }
        return new HashSet<Long>(currentIds).size() == currentIds.size()
                && new HashSet<Long>(requestedIds).size() == requestedIds.size()
                && new HashSet<Long>(currentIds).equals(new HashSet<Long>(requestedIds));
    }

    /** 上传图片并创建图片备注卡片，图片本体仍由 MinIO 文件服务管理。 */
    @Transactional
    public Map<String, Object> addImageEntry(LocalDate date, MultipartFile file) {
        DailyTimeline row = ensureRow(date);
        com.tradn.file.model.FileObject object =
                files.upload(file, "TIMELINE", row.getId(), "CHART");
        TimelineEntryCommand command = new TimelineEntryCommand();
        command.setEntryType("IMAGE");
        command.setFileId(object.getId());
        return addEntry(date, command);
    }

    /** 删除备注卡片，同时解除文件或普通笔记关系，但不删除用户原始数据。 */
    @Transactional
    public void deleteEntry(LocalDate date, long entryId) {
        DailyTimeline row = find(date);
        if (row == null) return;
        Map<String, Object> entry =
                jdbc.queryForMap(
                        "SELECT entry_type,note_id,file_id FROM timeline_entry WHERE id=? AND timeline_id=? AND user_id=? AND deleted=0",
                        entryId,
                        row.getId(),
                        SecurityUtils.userId());
        jdbc.update(
                "UPDATE timeline_entry SET deleted=1,updated_by=?,version=version+1 WHERE id=?",
                SecurityUtils.userId(),
                entryId);
        if ("NOTE".equals(entry.get("entry_type"))) {
            jdbc.update(
                    "DELETE FROM timeline_note_relation WHERE timeline_id=? AND note_id=?",
                    row.getId(),
                    entry.get("note_id"));
            if (row.getSummaryNoteId() != null) syncSummary(row);
        } else if ("IMAGE".equals(entry.get("entry_type"))) {
            jdbc.update(
                    "DELETE FROM business_file_relation WHERE business_type='TIMELINE' AND business_id=? AND file_id=?",
                    row.getId(),
                    entry.get("file_id"));
        }
    }

    @Transactional
    public void delete(LocalDate date) {
        DailyTimeline row = find(date);
        if (row == null) return;
        jdbc.update("UPDATE timeline_entry SET deleted=1,updated_by=? WHERE timeline_id=?", SecurityUtils.userId(), row.getId());
        row.setActiveUniqueKey(null);
        mapper.updateById(row);
        mapper.deleteById(row.getId());
        if (row.getSummaryNoteId() != null) {
            Note note = noteMapper.selectById(row.getSummaryNoteId());
            if (note != null) {
                note.setActiveUniqueKey(null);
                noteMapper.updateById(note);
                noteMapper.deleteById(note.getId());
            }
        }
    }

    private DailyTimeline find(LocalDate date) {
        return mapper.selectOne(
                new LambdaQueryWrapper<DailyTimeline>()
                        .eq(DailyTimeline::getUserId, SecurityUtils.userId())
                        .eq(DailyTimeline::getTimelineDate, date));
    }

    /** 首次添加卡片时创建日期骨架，避免用户必须先保存一个空的每日记录。 */
    private DailyTimeline ensureRow(LocalDate date) {
        DailyTimeline row = find(date);
        if (row != null) return row;
        row = new DailyTimeline();
        row.setUserId(SecurityUtils.userId());
        row.setTimelineDate(date);
        row.setActiveUniqueKey(SecurityUtils.userId() + "#" + date);
        row.setDailyContent(null);
        mapper.insert(row);
        return row;
    }

    private String normalizeEntryType(String entryType) {
        String type = entryType == null ? "" : entryType.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("TEXT", "IMAGE", "NOTE").contains(type))
            throw new BizException("不支持的时间线备注类型");
        return type;
    }

    private void validateEntry(DailyTimeline row, String type, TimelineEntryCommand c) {
        if ("TEXT".equals(type) && (c.getContent() == null || c.getContent().trim().isEmpty()))
            throw new BizException("文字备注不能为空");
        if ("NOTE".equals(type) && c.getNoteId() == null) throw new BizException("请选择关联笔记");
        if ("IMAGE".equals(type) && c.getFileId() == null) throw new BizException("请选择图片");
    }

    private void addNoteRelation(long timelineId, long noteId) {
        Note note =
                noteMapper.selectOne(
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getId, noteId)
                                .eq(Note::getUserId, SecurityUtils.userId()));
        if (note == null) throw new BizException("关联笔记不存在");
        if ("GOLD_DAILY_SUMMARY".equals(note.getNoteType()))
            throw new BizException("每日汇总笔记不能再次关联到时间线");
        jdbc.update(
                "INSERT IGNORE INTO timeline_note_relation(timeline_id,note_id) VALUES(?,?)",
                timelineId,
                noteId);
    }

    private void validateImageRelation(long timelineId, long fileId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(1) FROM business_file_relation r JOIN file_object f ON f.id=r.file_id WHERE r.business_type='TIMELINE' AND r.business_id=? AND r.file_id=? AND f.user_id=? AND f.deleted=0",
                        Integer.class,
                        timelineId,
                        fileId,
                        SecurityUtils.userId());
        if (count == null || count == 0) throw new BizException("图片不存在或不属于当前日期");
    }

    private int nextSortNo(long timelineId) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(sort_no),-1) FROM timeline_entry WHERE timeline_id=?", Integer.class, timelineId);
        return (max == null ? -1 : max) + 1;
    }

    private Map<String, Object> entryView(long entryId) {
        return jdbc.queryForMap("SELECT id,entry_type,content,note_id,file_id,sort_no,created_at FROM timeline_entry WHERE id=?", entryId);
    }
    // 先按当前用户校验每篇笔记，再整体替换关系；禁止把自动汇总笔记反向关联造成循环。
    private void replaceNotes(long timelineId, List<Long> noteIds) {
        jdbc.update("DELETE FROM timeline_note_relation WHERE timeline_id=?", timelineId);
        if (noteIds == null) return;
        for (Long noteId : noteIds) {
            Note note =
                    noteMapper.selectOne(
                            new LambdaQueryWrapper<Note>()
                                    .eq(Note::getId, noteId)
                                    .eq(Note::getUserId, SecurityUtils.userId()));
            if (note == null) throw new BizException("关联笔记不存在：" + noteId);
            if ("GOLD_DAILY_SUMMARY".equals(note.getNoteType()))
                throw new BizException("每日汇总笔记不能再次关联到时间线");
            jdbc.update(
                    "INSERT INTO timeline_note_relation(timeline_id,note_id) VALUES(?,?)",
                    timelineId,
                    noteId);
        }
    }

    /** 查询保存前已关联的笔记，用于判断本次是否真的新增了笔记。 */
    private Set<Long> currentNoteIds(long timelineId) {
        List<Long> ids =
                jdbc.query(
                        "SELECT note_id FROM timeline_note_relation WHERE timeline_id=?",
                        (resultSet, rowNum) -> resultSet.getLong("note_id"),
                        timelineId);
        return new HashSet<Long>(ids);
    }

    /** 只有新增关系才触发每日复盘笔记同步；重复保存或仅改文字不会触发。 */
    static boolean hasNewLinkedNote(Set<Long> previousNoteIds, List<Long> currentNoteIds) {
        if (currentNoteIds == null || currentNoteIds.isEmpty()) return false;
        for (Long noteId : currentNoteIds) {
            if (noteId != null && !previousNoteIds.contains(noteId)) return true;
        }
        return false;
    }
    // 只重建 generatedContent，manualContent 始终留给用户编辑；解除同步后不再覆盖自动区。
    // tradn-file:// 保存稳定文件 ID，避免把 15 分钟有效的 MinIO 签名地址永久写入 Markdown。
    private void syncSummary(DailyTimeline row) {
        Note note =
                row.getSummaryNoteId() == null
                        ? null
                        : noteMapper.selectById(row.getSummaryNoteId());
        if (note == null) {
            note = new Note();
            note.setUserId(row.getUserId());
            note.setTitle("黄金每日复盘 - " + row.getTimelineDate());
            note.setNoteType("GOLD_DAILY_SUMMARY");
            note.setBusinessDate(row.getTimelineDate());
            note.setSummary("黄金时间线自动汇总");
            note.setManualContent("");
            note.setSyncStatus("AUTO");
            note.setSourceType("TIMELINE");
            note.setSourceId(row.getId());
            note.setPinned(0);
            note.setActiveUniqueKey(
                    row.getUserId() + "#GOLD_DAILY_SUMMARY#" + row.getTimelineDate());
            noteMapper.insert(note);
            row.setSummaryNoteId(note.getId());
            mapper.updateById(row);
        }
        if (!"AUTO".equals(note.getSyncStatus())) return;
        StringBuilder md =
                new StringBuilder("## 当日时间线\n\n")
                        .append(row.getDailyContent() == null ? "" : row.getDailyContent())
                        .append("\n\n## 图片资料\n\n");
        List<Map<String, Object>> texts =
                jdbc.queryForList(
                        "SELECT content FROM timeline_entry WHERE timeline_id=? AND entry_type='TEXT' AND deleted=0 ORDER BY sort_no",
                        row.getId());
        for (Map<String, Object> text : texts)
            md.append(text.get("content") == null ? "" : text.get("content")).append("\n\n");
        List<Map<String, Object>> fs =
                jdbc.queryForList(
                        "SELECT f.id,f.original_name FROM file_object f JOIN business_file_relation r ON r.file_id=f.id WHERE r.business_type='TIMELINE' AND r.business_id=? AND f.deleted=0 ORDER BY r.sort_no",
                        row.getId());
        for (Map<String, Object> f : fs)
            md.append("- ![")
                    .append(f.get("original_name"))
                    .append("](tradn-file://")
                    .append(f.get("id"))
                    .append(")\n");
        md.append("\n## 关联笔记\n\n");
        List<Map<String, Object>> ns =
                jdbc.queryForList(
                        "SELECT n.title,n.summary FROM note n JOIN timeline_note_relation r ON r.note_id=n.id WHERE r.timeline_id=? AND n.deleted=0",
                        row.getId());
        for (Map<String, Object> n : ns)
            md.append("- **")
                    .append(n.get("title"))
                    .append("**：")
                    .append(n.get("summary") == null ? "" : n.get("summary"))
                    .append("\n");
        note.setGeneratedContent(md.toString());
        noteMapper.updateById(note);
    }

    private Map<String, Object> view(DailyTimeline row) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("timeline", row);
        List<Map<String, Object>> fileRows = files.list("TIMELINE", row.getId());
        List<Map<String, Object>> noteRows =
                jdbc.queryForList(
                        "SELECT n.id,n.title,n.summary,n.note_type FROM note n JOIN timeline_note_relation r ON r.note_id=n.id WHERE r.timeline_id=? AND n.deleted=0",
                        row.getId());
        out.put("files", fileRows);
        out.put("notes", noteRows);
        out.put("entries", entryViews(row, fileRows, noteRows));
        return out;
    }

    /** 将独立备注卡片和旧版本字段统一转换为前端时间线展示模型。 */
    private List<Map<String, Object>> entryViews(
            DailyTimeline row, List<Map<String, Object>> fileRows, List<Map<String, Object>> noteRows) {
        List<Map<String, Object>> result =
                jdbc.queryForList(
                        "SELECT id,entry_type,content,note_id,file_id,sort_no,created_at FROM timeline_entry WHERE timeline_id=? AND deleted=0 ORDER BY sort_no,created_at",
                        row.getId());
        Set<Long> entryFileIds = new HashSet<Long>();
        Set<Long> entryNoteIds = new HashSet<Long>();
        for (Map<String, Object> item : result) {
            item.put("entryType", item.remove("entry_type"));
            Number fileId = (Number) item.get("file_id");
            Number noteId = (Number) item.get("note_id");
            if (fileId != null) entryFileIds.add(fileId.longValue());
            if (noteId != null) entryNoteIds.add(noteId.longValue());
            enrichEntry(item, fileRows, noteRows);
        }
        if (row.getDailyContent() != null && !row.getDailyContent().trim().isEmpty()) {
            Map<String, Object> legacy = new LinkedHashMap<String, Object>();
            legacy.put("id", "legacy-text-" + row.getId());
            legacy.put("entryType", "TEXT");
            legacy.put("content", row.getDailyContent());
            legacy.put("legacy", true);
            result.add(0, legacy);
        }
        for (Map<String, Object> file : fileRows) {
            long fileId = ((Number) file.get("id")).longValue();
            if (!entryFileIds.contains(fileId)) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", "legacy-file-" + fileId);
                item.put("entryType", "IMAGE");
                item.put("fileId", fileId);
                item.put("file", file);
                item.put("legacy", true);
                result.add(item);
            }
        }
        for (Map<String, Object> note : noteRows) {
            long noteId = ((Number) note.get("id")).longValue();
            if (!entryNoteIds.contains(noteId)) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", "legacy-note-" + noteId);
                item.put("entryType", "NOTE");
                item.put("noteId", noteId);
                item.put("note", note);
                item.put("legacy", true);
                result.add(item);
            }
        }
        return result;
    }

    private void enrichEntry(
            Map<String, Object> item, List<Map<String, Object>> fileRows, List<Map<String, Object>> noteRows) {
        Number fileId = (Number) item.get("file_id");
        Number noteId = (Number) item.get("note_id");
        if (fileId != null) {
            item.put("fileId", fileId.longValue());
            for (Map<String, Object> file : fileRows)
                if (((Number) file.get("id")).longValue() == fileId.longValue()) item.put("file", file);
        }
        if (noteId != null) {
            item.put("noteId", noteId.longValue());
            for (Map<String, Object> note : noteRows)
                if (((Number) note.get("id")).longValue() == noteId.longValue()) item.put("note", note);
        }
        item.remove("file_id");
        item.remove("note_id");
    }
}
