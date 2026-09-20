package com.tradn.trade.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
/** 创建或推进交易记录状态时使用的请求模型。 */
public class TradeCommand {
    /** 做单方向：LONG 表示做多，SHORT 表示做空。 */
    private String direction;

    /** 开仓前计划的止盈价格。 */
    private BigDecimal plannedTakeProfitPrice;

    /** 开仓前计划的止损价格。 */
    private BigDecimal plannedStopLossPrice;

    /** 实际开仓价格。 */
    private BigDecimal openPrice;

    /** 实际平仓价格。 */
    private BigDecimal closePrice;

    /** 实际开仓手数。 */
    private BigDecimal lotSize;

    /** 实际开仓时间。 */
    private LocalDateTime openTime;

    /** 实际平仓时间。 */
    private LocalDateTime closeTime;

    /** 本次交易最终盈亏金额。 */
    private BigDecimal profitLoss;

    /** 用户对交易计划或结果的补充备注。 */
    private String remark;

    /** 客户端读取到的乐观锁版本号。 */
    private Integer version;

    /** 问卷题目 ID 与答案内容的映射。 */
    private Map<Long, Object> answers;
}
