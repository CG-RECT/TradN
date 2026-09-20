package com.tradn.trade.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tradn.common.model.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_record")
/** 一次黄金交易从计划、开仓复盘到平仓的主记录。 */
public class TradeRecord extends BaseEntity {
    /** 交易记录所属账号 ID，用于数据隔离。 */
    private Long userId;

    /** 系统生成的交易记录编号。 */
    private String recordNo;

    /** 交易状态，如 DRAFT、PLANNED、OPENED、REVIEWED 或 CLOSED。 */
    private String status;

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

    /** 盈亏金额币种，当前默认使用 CNY。 */
    private String profitLossCurrency;

    /** 用户对本次交易的补充备注。 */
    private String remark;

    /** 创建交易时采用的问卷模板 ID。 */
    private Long questionnaireTemplateId;
}
