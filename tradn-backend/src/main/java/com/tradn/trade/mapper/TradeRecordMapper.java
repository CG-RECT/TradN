package com.tradn.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradn.trade.model.TradeRecord;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TradeRecordMapper extends BaseMapper<TradeRecord> {
    // 只统计当前用户已平仓记录；结束时间使用小于 to 的半开区间，避免跨日边界重复计算。
    @Select(
            "SELECT COALESCE(SUM(profit_loss),0) total_profit_loss,"
                    + "SUM(CASE WHEN profit_loss>0 THEN 1 ELSE 0 END) win_count,"
                    + "SUM(CASE WHEN profit_loss<0 THEN 1 ELSE 0 END) loss_count,"
                    + "SUM(CASE WHEN profit_loss=0 THEN 1 ELSE 0 END) flat_count,COUNT(*) total_count "
                    + "FROM trade_record WHERE user_id=#{userId} AND status='CLOSED' AND deleted=0 AND close_time>=#{from} AND close_time<#{to}")
    Map<String, Object> statistics(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
