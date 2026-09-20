package com.tradn.system.model;

import lombok.Data;

@Data
/** 新增、修改或移动菜单节点的请求参数。 */
public class MenuCommand {
    /** 父节点主键，0 表示根节点。 */
    private Long parentId;

    /** 节点类型：DIRECTORY、MENU 或 BUTTON。 */
    private String menuType;

    /** 菜单或按钮名称。 */
    private String menuName;

    /** 页面路由地址。 */
    private String routePath;

    /** 前端组件标识。 */
    private String componentPath;

    /** 前后端共用的权限码。 */
    private String permissionCode;

    /** 菜单图标标识。 */
    private String icon;

    /** 同级排序号。 */
    private Integer sortNo;

    /** 是否在导航中显示：0 否，1 是。 */
    private Integer visible;
}
