package com.tradn.note.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 笔记和标签之间的多对多关系实体。 */
@Data
@TableName("note_tag_relation")
public class NoteTagRelation {
    /** 关系主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 笔记 ID。 */
    private Long noteId;

    /** 标签 ID。 */
    private Long tagId;
}
