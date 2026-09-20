package com.tradn.timeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradn.common.exception.BizException;
import com.tradn.file.service.FileService;
import com.tradn.note.mapper.NoteMapper;
import com.tradn.note.model.Note;
import com.tradn.security.SecurityUtils;
import com.tradn.timeline.mapper.DailyTimelineMapper;
import com.tradn.timeline.model.DailyTimeline;
import com.tradn.timeline.model.TimelineCommand;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        syncSummary(row);
        return row;
    }

    @Transactional
    public void delete(LocalDate date) {
        DailyTimeline row = find(date);
        if (row == null) return;
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
        out.put("files", files.list("TIMELINE", row.getId()));
        out.put(
                "notes",
                jdbc.queryForList(
                        "SELECT n.id,n.title,n.summary,n.note_type FROM note n JOIN timeline_note_relation r ON r.note_id=n.id WHERE r.timeline_id=? AND n.deleted=0",
                        row.getId()));
        return out;
    }
}
