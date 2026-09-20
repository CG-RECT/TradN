package com.tradn.note.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.tradn.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note")
/** 用户笔记实体，既承载手工笔记，也承载时间线自动汇总笔记。 */
public class Note extends BaseEntity {
    /** 笔记所属账号 ID，用于数据隔离。 */
    private Long userId;

    /** 笔记标题。 */
    private String title;

    /** 笔记类型，如交易心得或黄金时间线汇总。 */
    private String noteType;

    /** 笔记对应的业务日期。 */
    private LocalDate businessDate;

    /** 用于列表和时间线缩略展示的摘要。 */
    private String summary;

    /** 系统根据来源数据生成的 Markdown 内容。 */
    private String generatedContent;

    /** 用户手工编辑的 Markdown 内容。 */
    private String manualContent;

    /** 自动汇总同步状态，用于标识是否与来源数据保持同步。 */
    private String syncStatus;

    /** 笔记来源类型，如 MANUAL 或 TIMELINE。 */
    private String sourceType;

    /** 来源业务记录 ID，手工笔记可为空。 */
    private Long sourceId;

    /** 置顶标识：0 表示不置顶，1 表示置顶。 */
    private Integer pinned;

    /** 有效数据唯一键，用于逻辑删除场景下约束来源唯一性。 */
    private String activeUniqueKey;

    /** 当前笔记绑定的标签，仅用于接口返回，不映射到 note 表。 */
    @TableField(exist = false)
    private List<NoteTag> tags;
}
