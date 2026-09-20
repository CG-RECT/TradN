package com.tradn.file.controller;

import com.tradn.common.api.ApiResponse;
import com.tradn.file.model.FileObject;
import com.tradn.file.service.FileService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
/** 提供业务附件上传、查询、访问和解除关联接口。 */
public class FileController {
    private final FileService service;

    /** 上传文件到 MinIO，并与指定业务记录建立关系。 */
    @PostMapping
    @PreAuthorize("hasAuthority('file:object:upload')")
    public ApiResponse<FileObject> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String businessType,
            @RequestParam long businessId,
            @RequestParam(defaultValue = "ATTACHMENT") String usageType) {
        return ApiResponse.ok(service.upload(file, businessType, businessId, usageType));
    }

    /** 查询指定业务记录关联的全部文件。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam String businessType, @RequestParam long businessId) {
        return ApiResponse.ok(service.list(businessType, businessId));
    }

    /** 获取原图或缩略图的临时访问地址。 */
    @GetMapping("/{id}/access-url")
    public ApiResponse<Map<String, String>> url(
            @PathVariable long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
        return ApiResponse.ok(Collections.singletonMap("url", service.url(id, thumbnail)));
    }

    /** 解除文件与业务记录的关系，不直接删除仍被引用的对象。 */
    @DeleteMapping("/{id}/relation")
    @PreAuthorize("hasAuthority('file:object:upload')")
    public ApiResponse<Void> remove(
            @PathVariable long id,
            @RequestParam String businessType,
            @RequestParam long businessId) {
        service.removeRelation(id, businessType, businessId);
        return ApiResponse.ok();
    }
}
