package com.tradn.timeline.controller;

import com.tradn.common.api.ApiResponse;
import com.tradn.timeline.model.DailyTimeline;
import com.tradn.timeline.model.TimelineCommand;
import com.tradn.timeline.service.TimelineService;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    /** 逻辑删除某一天的时间线及其业务关联。 */
    @DeleteMapping("/{date}")
    @PreAuthorize("hasAuthority('timeline:daily:update')")
    public ApiResponse<Void> delete(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(date);
        return ApiResponse.ok();
    }
}
