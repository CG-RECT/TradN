package com.tradn.system.model;

import lombok.Data;

@Data
/** 新增或修改字典项的请求参数。 */
public class DictionaryItemCommand {
    /** 所属字典类型主键。 */
    private Long typeId;

    /** 字典项唯一编码。 */
    private String itemCode;

    /** 业务表实际保存的值。 */
    private String itemValue;

    /** 页面展示文本。 */
    private String itemLabel;

    /** 显示顺序。 */
    private Integer sortNo;

    /** 字典项状态：ENABLED 或 DISABLED。 */
    private String status;

    /** 前端标签颜色等展示样式。 */
    private String displayStyle;
}
