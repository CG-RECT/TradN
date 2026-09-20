package com.tradn.note.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户自定义笔记标签实体。 */
@Data
@TableName("note_tag")
public class NoteTag {
    /** 标签主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID，用于隔离不同账号的标签。 */
    private Long userId;

    /** 标签显示名称。 */
    private String tagName;

    /** 标签创建时间。 */
    private LocalDateTime createdAt;

    /** 逻辑删除标记。 */
    private Integer deleted;
}
