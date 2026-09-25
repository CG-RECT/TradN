package com.tradn.timeline.controller;

import com.tradn.common.api.ApiResponse;
import com.tradn.timeline.model.DailyTimeline;
import com.tradn.timeline.model.TimelineCommand;
import com.tradn.timeline.model.TimelineEntryCommand;
import com.tradn.timeline.model.TimelineEntryOrderCommand;
import com.tradn.timeline.service.TimelineService;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/timelines")
@RequiredArgsConstructor
/** 提供按日浏览和维护黄金时间线的接口。 */
public class TimelineController {
    private final TimelineService service;

    /** 查询日期范围内的时间线卡片数据。 */
    @GetMapping
    @PreAuthorize("hasAuthority('timeline:daily:view')")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.list(from, to));
    }

    /** 查询某一天的时间线详情、图片和关联笔记。 */
    @GetMapping("/{date}")
    @PreAuthorize("hasAuthority('timeline:daily:view')")
    public ApiResponse<Map<String, Object>> get(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.get(date));
    }

    /** 新增或更新某一天的时间线，并同步对应汇总笔记。 */
    @PutMapping("/{date}")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<DailyTimeline> save(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody TimelineCommand c) {
        return ApiResponse.ok(service.save(date, c));
    }

    /** 为某一天新增一张文字或已有笔记备注卡片。 */
    @PostMapping("/{date}/entries")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Map<String, Object>> addEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody TimelineEntryCommand command) {
        return ApiResponse.ok(service.addEntry(date, command));
    }

    /** 保存同一天全部备注卡片的人工排列顺序。 */
    @PutMapping("/{date}/entries/order")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Void> reorderEntries(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody TimelineEntryOrderCommand command) {
        service.reorderEntries(date, command);
        return ApiResponse.ok();
    }

    /** 修改已有文字备注卡片。图片和笔记卡片通过原业务模块维护。 */
    @PutMapping("/{date}/entries/{entryId}")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Map<String, Object>> updateEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable long entryId,
            @RequestBody TimelineEntryCommand command) {
        return ApiResponse.ok(service.updateTextEntry(date, entryId, command));
    }

    /** 上传并新增一张图片备注卡片。 */
    @PostMapping("/{date}/entries/image")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Map<String, Object>> addImageEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.addImageEntry(date, file));
    }

    /** 删除一张时间线备注卡片，不删除关联的普通笔记或文件对象。 */
    @DeleteMapping("/{date}/entries/{entryId}")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Void> deleteEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable long entryId) {
        service.deleteEntry(date, entryId);
        return ApiResponse.ok();
    }

    /** 逻辑删除某一天的时间线及其业务关联。 */
    @DeleteMapping("/{date}")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Void> delete(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(date);
        return ApiResponse.ok();
    }
}
