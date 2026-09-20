package com.tradn.trade.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tradn.common.api.ApiResponse;
import com.tradn.trade.model.TradeCommand;
import com.tradn.trade.model.TradeRecord;
import com.tradn.trade.service.TradeService;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/trades")
@RequiredArgsConstructor
/** 提供黄金交易记录及开仓提示问卷的完整操作接口。 */
public class TradeController {
    private final TradeService service;

    /** 分页查询当前账号的交易记录。 */
    @GetMapping
    @PreAuthorize("hasAuthority('trade:record:list')")
    public ApiResponse<Page<TradeRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(page, size, status));
    }

    /** 查询新建页面使用的问卷模板，本接口不会创建交易记录。 */
    @GetMapping("/questionnaire-template")
    @PreAuthorize("hasAuthority('trade:record:list')")
    public ApiResponse<Map<String, Object>> questionnaireTemplate() {
        return ApiResponse.ok(service.questionnaireTemplate());
    }

    /** 保存完整开仓计划并创建交易记录。 */
    @PostMapping
    @PreAuthorize("hasAuthority('trade:record:create')")
    public ApiResponse<TradeRecord> create(@RequestBody TradeCommand c) {
        return ApiResponse.ok(service.create(c));
    }

    /** 查询交易主记录及问卷答案详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('trade:record:list')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** 保存开仓前问卷、方向、止盈和止损计划。 */
    @PutMapping("/{id}/pre-open-questionnaire")
    @PreAuthorize("hasAuthority('trade:record:update')")
    public ApiResponse<Void> plan(@PathVariable long id, @RequestBody TradeCommand c) {
        service.savePlan(id, c);
        return ApiResponse.ok();
    }

    /** 补充实际开仓信息并将交易推进到已开仓状态。 */
    @PutMapping("/{id}/position")
    @PreAuthorize("hasAuthority('trade:record:update')")
    public ApiResponse<Void> open(@PathVariable long id, @RequestBody TradeCommand c) {
        service.open(id, c);
        return ApiResponse.ok();
    }

    /** 保存开仓后的走势和开仓逻辑复盘答案。 */
    @PutMapping("/{id}/post-open-review")
    @PreAuthorize("hasAuthority('trade:record:update')")
    public ApiResponse<Void> review(@PathVariable long id, @RequestBody TradeCommand c) {
        service.review(id, c);
        return ApiResponse.ok();
    }

    /** 补充平仓信息并完成交易记录。 */
    @PutMapping("/{id}/close")
    @PreAuthorize("hasAuthority('trade:record:update')")
    public ApiResponse<Void> close(@PathVariable long id, @RequestBody TradeCommand c) {
        service.close(id, c);
        return ApiResponse.ok();
    }

    /** 按日期范围统计已平仓交易的总盈亏。 */
    @GetMapping("/statistics/profit-loss")
    @PreAuthorize("hasAuthority('trade:statistics:view')")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.statistics(from, to));
    }
}
