package com.tradn.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tradn.common.api.ApiResponse;
import com.tradn.note.model.Note;
import com.tradn.note.model.NoteCommand;
import com.tradn.note.service.NoteService;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notes")
@RequiredArgsConstructor
/** 提供 Markdown 笔记的查询、编辑、同步控制和导出接口。 */
public class NoteController {
    private final NoteService service;

    /** 按关键字和笔记类型分页查询当前账号的笔记。 */
    @GetMapping
    @PreAuthorize("hasAuthority('note:note:list')")
    public ApiResponse<Page<Note>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(service.list(page, size, keyword, type));
    }

    /** 创建一篇用户手工笔记。 */
    @PostMapping
    @PreAuthorize("hasAuthority('note:note:create')")
    public ApiResponse<Note> create(@RequestBody NoteCommand c) {
        return ApiResponse.ok(service.create(c));
    }

    /** 查询一篇笔记的完整内容。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('note:note:list')")
    public ApiResponse<Note> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** 更新笔记标题、摘要、正文和置顶状态。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('note:note:update')")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody NoteCommand c) {
        service.update(id, c);
        return ApiResponse.ok();
    }

    /** 逻辑删除指定笔记。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('note:note:update')")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /** 将自动汇总笔记转为独立手工笔记，后续不再随来源同步。 */
    @PostMapping("/{id}/detach-sync")
    @PreAuthorize("hasAuthority('note:note:update')")
    public ApiResponse<Void> detach(@PathVariable long id) {
        service.detach(id);
        return ApiResponse.ok();
    }

    /** 将笔记内容导出为 UTF-8 编码的 Markdown 文件。 */
    @GetMapping("/{id}/export-markdown")
    @PreAuthorize("hasAuthority('note:note:list')")
    public ResponseEntity<byte[]> export(@PathVariable long id) throws Exception {
        Note n = service.get(id);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''"
                                + URLEncoder.encode(n.getTitle() + ".md", "UTF-8"))
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(service.exportMarkdown(id));
    }
}
