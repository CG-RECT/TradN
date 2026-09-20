package com.tradn.timeline.model;

import lombok.Data;

/** 保存一张黄金时间线备注卡片时使用的请求模型。 */
@Data
public class TimelineEntryCommand {
    /** 备注类型：TEXT文字、IMAGE图片、NOTE笔记。 */
    private String entryType;

    /** 文字备注内容，只有 TEXT 类型使用。 */
    private String content;

    /** 关联已有笔记 ID，只有 NOTE 类型使用。 */
    private Long noteId;

    /** 关联已上传文件 ID，只有 IMAGE 类型使用。 */
    private Long fileId;
}
