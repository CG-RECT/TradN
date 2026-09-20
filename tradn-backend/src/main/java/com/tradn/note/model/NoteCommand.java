package com.tradn.note.model;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
/** 新增或修改笔记时使用的请求模型。 */
public class NoteCommand {
    /** 笔记标题。 */
    private String title;

    /** 笔记类型。 */
    private String noteType;

    /** 笔记对应的业务日期。 */
    private LocalDate businessDate;

    /** 列表展示摘要。 */
    private String summary;

    /** 用户手工编辑的 Markdown 内容。 */
    private String manualContent;

    /** 置顶标识：0 表示不置顶，1 表示置顶。 */
    private Integer pinned;

    /** 客户端读取到的乐观锁版本号。 */
    private Integer version;

    /** 笔记绑定的标签 ID 列表。 */
    private List<Long> tagIds;
}
