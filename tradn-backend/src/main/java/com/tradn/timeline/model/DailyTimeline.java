package com.tradn.timeline.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tradn.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gold_daily_timeline")
/** 按自然日记录的黄金时间线实体。 */
public class DailyTimeline extends BaseEntity {
    /** 时间线所属账号 ID，用于数据隔离。 */
    private Long userId;

    /** 黄金时间线日期，最小单位为一天。 */
    private LocalDate timelineDate;

    /** 当日用户输入的 Markdown 笔记内容。 */
    private String dailyContent;

    /** 当日自动汇总到笔记模块后生成的笔记 ID。 */
    private Long summaryNoteId;

    /** 有效记录唯一键，用于保证每个账号每天只有一条时间线。 */
    private String activeUniqueKey;
}
