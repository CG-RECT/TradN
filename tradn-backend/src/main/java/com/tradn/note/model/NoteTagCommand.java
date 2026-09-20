package com.tradn.note.model;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/** 创建用户笔记标签时使用的请求模型。 */
@Data
public class NoteTagCommand {
    /** 标签名称。 */
    @NotBlank(message = "标签名称不能为空")
    private String tagName;
}
