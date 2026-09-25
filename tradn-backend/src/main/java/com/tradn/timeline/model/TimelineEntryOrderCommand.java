package com.tradn.timeline.model;

import java.util.List;
import lombok.Data;

/** 调整同一天时间线备注卡片顺序时使用的请求模型。 */
@Data
public class TimelineEntryOrderCommand {
    /** 当天全部有效备注卡片 ID，数组顺序就是最终展示顺序。 */
    private List<Long> entryIds;
}
