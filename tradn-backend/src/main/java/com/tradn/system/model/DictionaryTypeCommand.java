package com.tradn.system.model;

import lombok.Data;

@Data
/** 新增或修改字典类型的请求参数。 */
public class DictionaryTypeCommand {
    /** 字典类型唯一编码。 */
    private String typeCode;

    /** 字典类型名称。 */
    private String typeName;

    /** 字典用途说明。 */
    private String description;

    /** 字典状态：ENABLED 或 DISABLED。 */
    private String status;
}
