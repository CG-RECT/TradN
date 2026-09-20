package com.tradn.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradn.system.model.SystemUser;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SystemUserMapper extends BaseMapper<SystemUser> {
    // 权限由“用户-有效角色-菜单/按钮”实时聚合，角色停用后无需修改用户即可立即失效。
    @Select(
            "SELECT DISTINCT m.permission_code FROM sys_menu m "
                    + "JOIN sys_role_menu rm ON rm.menu_id=m.id JOIN sys_user_role ur ON ur.role_id=rm.role_id "
                    + "JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status='ENABLED' "
                    + "WHERE ur.user_id=#{userId} AND m.deleted=0 AND m.permission_code IS NOT NULL")
    List<String> selectPermissions(@Param("userId") Long userId);

    // 登录菜单只返回可见目录和页面；BUTTON 权限保留在 permissions 中供按钮级判断。
    @Select(
            "SELECT DISTINCT m.id,m.parent_id,m.menu_type,m.menu_name,m.route_path,m.component_path,m.permission_code,m.icon,m.sort_no "
                    + "FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.id JOIN sys_user_role ur ON ur.role_id=rm.role_id "
                    + "JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status='ENABLED' "
                    + "WHERE ur.user_id=#{userId} AND m.deleted=0 AND m.visible=1 AND m.menu_type IN ('DIRECTORY','MENU') "
                    + "ORDER BY m.parent_id,m.sort_no,m.id")
    List<Map<String, Object>> selectMenus(@Param("userId") Long userId);
}
