package com.tradn.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tradn.common.exception.BizException;
import com.tradn.note.mapper.NoteMapper;
import com.tradn.note.model.Note;
import com.tradn.note.model.NoteCommand;
import com.tradn.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteMapper mapper;

    public NoteService(NoteMapper mapper) {
        this.mapper = mapper;
    }

    public Page<Note> list(int page, int size, String keyword, String type) {
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
        return mapper.selectPage(new Page<Note>(page, Math.min(size, 100)), q);
    }

    public Note get(long id) {
        Note n =
                mapper.selectOne(
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getId, id)
                                .eq(Note::getUserId, SecurityUtils.userId()));
        if (n == null) throw new BizException(404, "笔记不存在");
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
}
