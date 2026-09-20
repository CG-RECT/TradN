package com.tradn.trade.service;

import com.tradn.common.exception.BizException;

public final class TradeStatusPolicy {
    private TradeStatusPolicy() {}

    public static void requireCanOpen(String status) {
        if (!"PLANNED".equals(status)) throw new BizException("只有完成计划的记录可以开仓");
    }

    public static void requireCanClose(String status) {
        if (!"OPEN".equals(status)) throw new BizException("只有持仓中的记录可以平仓");
    }

    public static void requireCanReview(String status) {
        if ("DRAFT".equals(status)) throw new BizException("请先保存开仓前问卷");
    }
}
