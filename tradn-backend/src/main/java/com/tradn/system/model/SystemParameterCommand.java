package com.tradn.system.model;

import lombok.Data;

/** 系统参数新增或修改请求。 */
@Data
public class SystemParameterCommand {
    /** 参数唯一编码。 */
    private String paramKey;

    /** 参数显示名称。 */
    private String paramName;

    /** 参数值类型，例如 STRING、NUMBER 或 BOOLEAN。 */
    private String paramType;

    /** 当前参数值。 */
    private String paramValue;

    /** 参数默认值。 */
    private String defaultValue;

    /** 是否敏感参数：1是、0否。 */
    private Integer isSensitive;

    /** 是否支持动态生效：1是、0否。 */
    private Integer dynamicEffect;

    /** 参数用途说明。 */
    private String description;
}
