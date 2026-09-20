package com.tradn.trade.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tradn.common.exception.BizException;
import org.junit.jupiter.api.Test;

class TradeStatusPolicyTest {
    @Test
    void plannedTradeCanOpen() {
        assertDoesNotThrow(() -> TradeStatusPolicy.requireCanOpen("PLANNED"));
    }

    @Test
    void draftTradeCannotOpen() {
        assertThrows(BizException.class, () -> TradeStatusPolicy.requireCanOpen("DRAFT"));
    }

    @Test
    void openTradeCanClose() {
        assertDoesNotThrow(() -> TradeStatusPolicy.requireCanClose("OPEN"));
    }

    @Test
    void plannedTradeCanRecordPostOpenReview() {
        assertDoesNotThrow(() -> TradeStatusPolicy.requireCanReview("PLANNED"));
    }

    @Test
    void draftTradeCannotRecordPostOpenReview() {
        assertThrows(BizException.class, () -> TradeStatusPolicy.requireCanReview("DRAFT"));
    }
}
