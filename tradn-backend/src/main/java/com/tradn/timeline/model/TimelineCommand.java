package com.tradn.timeline.model;

import java.util.List;
import lombok.Data;

@Data
/** 保存某日黄金时间线时使用的请求模型。 */
public class TimelineCommand {
    /** 当日用户输入的 Markdown 笔记内容。 */
    private String dailyContent;

    /** 需要关联到当日时间线的已有笔记 ID 列表。 */
    private List<Long> noteIds;

    /** 客户端读取到的乐观锁版本号。 */
    private Integer version;
}
