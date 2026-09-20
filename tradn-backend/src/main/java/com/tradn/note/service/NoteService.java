package com.tradn.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tradn.common.exception.BizException;
import com.tradn.note.mapper.NoteMapper;
import com.tradn.note.mapper.NoteTagMapper;
import com.tradn.note.mapper.NoteTagRelationMapper;
import com.tradn.note.model.Note;
import com.tradn.note.model.NoteCommand;
import com.tradn.note.model.NoteTag;
import com.tradn.note.model.NoteTagCommand;
import com.tradn.note.model.NoteTagRelation;
import com.tradn.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteMapper mapper;
    private final NoteTagMapper tagMapper;
    private final NoteTagRelationMapper relationMapper;

    public NoteService(
            NoteMapper mapper,
            NoteTagMapper tagMapper,
            NoteTagRelationMapper relationMapper) {
        this.mapper = mapper;
        this.tagMapper = tagMapper;
        this.relationMapper = relationMapper;
    }

    public Page<Note> list(int page, int size, String keyword, String type, Long tagId) {
        LambdaQueryWrapper<Note> q =
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, SecurityUtils.userId())
                        .eq(type != null && !type.isEmpty(), Note::getNoteType, type)
                        .and(
                                keyword != null && !keyword.isEmpty(),
                                w ->
                                        w.like(Note::getTitle, keyword)
                                                .or()
                                                .like(Note::getSummary, keyword))
                        .orderByDesc(Note::getPinned)
                        .orderByDesc(Note::getUpdatedAt);
        if (tagId != null) {
            List<Long> noteIds = mapper.selectIdsByTag(SecurityUtils.userId(), tagId);
            if (noteIds.isEmpty()) {
                q.eq(Note::getId, -1L);
            } else {
                q.in(Note::getId, noteIds);
            }
        }
        Page<Note> result = mapper.selectPage(new Page<Note>(page, Math.min(size, 100)), q);
        fillTags(result.getRecords());
        return result;
    }

    public Note get(long id) {
        Note n =
                mapper.selectOne(
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getId, id)
                                .eq(Note::getUserId, SecurityUtils.userId()));
        if (n == null) throw new BizException(404, "笔记不存在");
        fillTags(Collections.singletonList(n));
        return n;
    }

    @Transactional
    public Note create(NoteCommand c) {
        if (c.getTitle() == null || c.getTitle().trim().isEmpty()) throw new BizException("标题不能为空");
        Note n = new Note();
        n.setUserId(SecurityUtils.userId());
        n.setTitle(c.getTitle());
        n.setNoteType(c.getNoteType() == null ? "NORMAL" : c.getNoteType());
        n.setBusinessDate(c.getBusinessDate());
        n.setSummary(c.getSummary());
        n.setManualContent(c.getManualContent());
        n.setGeneratedContent("");
        n.setSyncStatus("NONE");
        n.setPinned(c.getPinned() == null ? 0 : c.getPinned());
        mapper.insert(n);
        replaceTags(n.getId(), c.getTagIds());
        return n;
    }

    // 自动同步笔记的类型和来源由时间线维护，但人工补充区允许正常编辑。
    @Transactional
    public void update(long id, NoteCommand c) {
        Note n = get(id);
        if ("AUTO".equals(n.getSyncStatus())
                && c.getNoteType() != null
                && !n.getNoteType().equals(c.getNoteType()))
            throw new BizException("自动同步笔记不能直接修改类型");
        n.setTitle(c.getTitle());
        n.setBusinessDate(c.getBusinessDate());
        n.setSummary(c.getSummary());
        n.setManualContent(c.getManualContent());
        n.setPinned(c.getPinned());
        n.setVersion(c.getVersion());
        if (mapper.updateById(n) == 0) throw new BizException(409, "笔记已被其他页面修改");
        replaceTags(id, c.getTagIds());
    }

    // 逻辑删除前释放活动唯一键，允许用户以后重新生成同日期的自动笔记。
    @Transactional
    public void delete(long id) {
        Note n = get(id);
        n.setActiveUniqueKey(null);
        mapper.updateById(n);
        mapper.deleteById(id);
    }

    // 解除同步时把当前自动内容合并进人工区，确保已有复盘内容不会因断开来源而丢失。
    @Transactional
    public void detach(long id) {
        Note n = get(id);
        if (!"AUTO".equals(n.getSyncStatus())) return;
        n.setManualContent(
                (n.getGeneratedContent() == null ? "" : n.getGeneratedContent())
                        + "\n\n"
                        + (n.getManualContent() == null ? "" : n.getManualContent()));
        n.setGeneratedContent("");
        n.setSyncStatus("DETACHED");
        n.setNoteType("NORMAL");
        n.setActiveUniqueKey(null);
        mapper.updateById(n);
    }

    public byte[] exportMarkdown(long id) {
        Note n = get(id);
        String content =
                "# "
                        + n.getTitle()
                        + "\n\n"
                        + (n.getGeneratedContent() == null ? "" : n.getGeneratedContent())
                        + "\n\n"
                        + (n.getManualContent() == null ? "" : n.getManualContent());
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** 查询当前账号的标签，供列表筛选和笔记编辑使用。 */
    public List<NoteTag> listTags() {
        return tagMapper.selectList(
                new LambdaQueryWrapper<NoteTag>()
                        .eq(NoteTag::getUserId, SecurityUtils.userId())
                        .eq(NoteTag::getDeleted, 0)
                        .orderByAsc(NoteTag::getTagName));
    }

    /** 创建当前账号的自定义标签，同名标签直接复用已有记录。 */
    @Transactional
    public NoteTag createTag(NoteTagCommand command) {
        String tagName = command.getTagName().trim();
        NoteTag existing =
                tagMapper.selectOne(
                        new LambdaQueryWrapper<NoteTag>()
                                .eq(NoteTag::getUserId, SecurityUtils.userId())
                                .eq(NoteTag::getTagName, tagName)
                                .eq(NoteTag::getDeleted, 0));
        if (existing != null) return existing;
        NoteTag tag = new NoteTag();
        tag.setId(IdWorker.getId());
        tag.setUserId(SecurityUtils.userId());
        tag.setTagName(tagName);
        tag.setDeleted(0);
        tagMapper.insert(tag);
        return tag;
    }

    /** 删除标签前先解除关系，避免留下无法维护的关系记录。 */
    @Transactional
    public void deleteTag(long id) {
        NoteTag tag =
                tagMapper.selectOne(
                        new LambdaQueryWrapper<NoteTag>()
                                .eq(NoteTag::getId, id)
                                .eq(NoteTag::getUserId, SecurityUtils.userId())
                                .eq(NoteTag::getDeleted, 0));
        if (tag == null) throw new BizException(404, "标签不存在");
        relationMapper.delete(
                new LambdaQueryWrapper<NoteTagRelation>().eq(NoteTagRelation::getTagId, id));
        tag.setDeleted(1);
        tagMapper.updateById(tag);
    }

    /** 替换一篇笔记的全部标签关系，并校验标签归属当前账号。 */
    private void replaceTags(long noteId, List<Long> tagIds) {
        relationMapper.delete(
                new LambdaQueryWrapper<NoteTagRelation>().eq(NoteTagRelation::getNoteId, noteId));
        if (tagIds == null || tagIds.isEmpty()) return;
        List<NoteTag> tags =
                tagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>()
                                .eq(NoteTag::getUserId, SecurityUtils.userId())
                                .eq(NoteTag::getDeleted, 0)
                                .in(NoteTag::getId, tagIds));
        if (tags.size() != tagIds.stream().distinct().count()) {
            throw new BizException("存在无效的笔记标签");
        }
        for (NoteTag tag : tags) {
            NoteTagRelation relation = new NoteTagRelation();
            relation.setNoteId(noteId);
            relation.setTagId(tag.getId());
            relationMapper.insert(relation);
        }
    }

    /** 将标签关系组装到列表/详情返回对象中。 */
    private void fillTags(List<Note> notes) {
        for (Note note : notes) {
            List<Long> tagIds =
                    relationMapper.selectList(
                                    new LambdaQueryWrapper<NoteTagRelation>()
                                            .eq(NoteTagRelation::getNoteId, note.getId()))
                            .stream()
                            .map(NoteTagRelation::getTagId)
                            .collect(java.util.stream.Collectors.toList());
            if (tagIds.isEmpty()) {
                note.setTags(Collections.emptyList());
                continue;
            }
            note.setTags(
                    tagMapper.selectList(
                            new LambdaQueryWrapper<NoteTag>()
                                    .eq(NoteTag::getUserId, SecurityUtils.userId())
                                    .eq(NoteTag::getDeleted, 0)
                                    .in(NoteTag::getId, tagIds)));
        }
    }
}
