package com.tradn.system.model;

import lombok.Data;

@Data
/** 新增或修改角色的请求参数。 */
public class RoleCommand {
    /** 角色唯一编码。 */
    private String roleCode;

    /** 角色显示名称。 */
    private String roleName;

    /** 角色状态：ENABLED 或 DISABLED。 */
    private String status;
}
