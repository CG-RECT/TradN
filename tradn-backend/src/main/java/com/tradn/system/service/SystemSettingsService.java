package com.tradn.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tradn.common.api.PageResult;
import com.tradn.common.exception.BizException;
import com.tradn.security.JwtTokenService;
import com.tradn.security.SecurityUtils;
import com.tradn.system.model.DictionaryItemCommand;
import com.tradn.system.model.DictionaryTypeCommand;
import com.tradn.system.model.CacheNamespaceCommand;
import com.tradn.system.model.MenuCommand;
import com.tradn.system.model.RoleCommand;
import com.tradn.system.model.SystemParameterCommand;
import com.tradn.system.model.UserCommand;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** 系统设置领域服务，统一处理账号、角色、菜单、字典、缓存和审计数据。 */
public class SystemSettingsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final StringRedisTemplate redis;
    private final AuditService audit;

    public SystemSettingsService(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            JwtTokenService tokens,
            StringRedisTemplate redis,
            AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.tokens = tokens;
        this.redis = redis;
        this.audit = audit;
    }

    /** 按用户名、昵称和状态查询账号。 */
    public List<Map<String, Object>> users(String username, String nickname, String status) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT id,username,nickname,status,last_login_at,created_at,updated_at "
                                + "FROM sys_user WHERE deleted=0");
        List<Object> args = new ArrayList<Object>();
        appendLike(sql, args, "username", username);
        appendLike(sql, args, "nickname", nickname);
        appendEqual(sql, args, "status", status);
        sql.append(" ORDER BY created_at DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 查询账号当前关联的有效角色。 */
    public List<Map<String, Object>> userRoles(long userId) {
        return jdbc.queryForList(
                "SELECT r.id,r.role_code,r.role_name,r.status,r.built_in "
                        + "FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id "
                        + "WHERE ur.user_id=? AND r.deleted=0 ORDER BY r.role_code",
                userId);
    }

    /** 创建账号并建立角色关系。 */
    @Transactional
    public void createUser(UserCommand command) {
        String username = trim(command.getUsername());
        String password = trim(command.getPassword());
        String nickname = trim(command.getNickname());
        if (username.isEmpty() || password.length() < 10) {
            throw new BizException("用户名不能为空且密码至少10位");
        }
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_user(id,username,password_hash,nickname,status) VALUES(?,?,?,?,?)",
                    id,
                    username,
                    encoder.encode(password),
                    nickname.isEmpty() ? username : nickname,
                    enabledStatus(command.getStatus()));
        } catch (DuplicateKeyException ex) {
            throw new BizException("用户名已存在");
        }
        assignRoles(id, command.getRoleIds());
        audit.operation("USER_CREATE", "SYS_USER", String.valueOf(id), "username=" + username);
    }

    /** 修改账号昵称、状态和角色，变更后撤销旧会话使权限立即生效。 */
    @Transactional
    public void updateUser(long id, UserCommand command) {
        int changed =
                jdbc.update(
                        "UPDATE sys_user SET nickname=?,status=?,updated_at=NOW(3),updated_by=? "
                                + "WHERE id=? AND deleted=0",
                        trim(command.getNickname()),
                        enabledStatus(command.getStatus()),
                        SecurityUtils.userId(),
                        id);
        if (changed == 0) {
            throw new BizException(404, "账号不存在");
        }
        if (command.getRoleIds() != null) {
            assignRoles(id, command.getRoleIds());
        }
        tokens.revokeAll(id);
        audit.operation("USER_UPDATE", "SYS_USER", String.valueOf(id), null);
    }

    /** 单独启用或禁用账号。 */
    public void updateUserStatus(long id, String status) {
        int changed =
                jdbc.update(
                        "UPDATE sys_user SET status=?,updated_at=NOW(3),updated_by=? "
                                + "WHERE id=? AND deleted=0",
                        enabledStatus(status),
                        SecurityUtils.userId(),
                        id);
        if (changed == 0) {
            throw new BizException(404, "账号不存在");
        }
        tokens.revokeAll(id);
        audit.operation("USER_STATUS", "SYS_USER", String.valueOf(id), "status=" + status);
    }

    /** 管理员重置账号密码。 */
    public void resetPassword(long id, String password) {
        if (password == null || password.length() < 10) {
            throw new BizException("密码至少10位");
        }
        jdbc.update(
                "UPDATE sys_user SET password_hash=?,updated_at=NOW(3),updated_by=? WHERE id=?",
                encoder.encode(password),
                SecurityUtils.userId(),
                id);
        tokens.revokeAll(id);
        audit.operation("PASSWORD_RESET", "SYS_USER", String.valueOf(id), null);
    }

    /** 强制账号全部登录会话失效。 */
    public void forceLogout(long id) {
        tokens.revokeAll(id);
        audit.operation("FORCE_LOGOUT", "SYS_USER", String.valueOf(id), null);
    }

    private void assignRoles(long userId, Collection<Long> roleIds) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            jdbc.update(
                    "INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
        }
    }

    /** 按编码、名称和状态查询角色。 */
    public List<Map<String, Object>> roles(String roleCode, String roleName, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_role WHERE deleted=0");
        List<Object> args = new ArrayList<Object>();
        appendLike(sql, args, "role_code", roleCode);
        appendLike(sql, args, "role_name", roleName);
        appendEqual(sql, args, "status", status);
        sql.append(" ORDER BY built_in DESC,role_code");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 创建自定义角色。 */
    public long createRole(RoleCommand command) {
        validateRole(command);
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_role(id,role_code,role_name,status,built_in) VALUES(?,?,?,?,0)",
                    id,
                    trim(command.getRoleCode()).toUpperCase(),
                    trim(command.getRoleName()),
                    enabledStatus(command.getStatus()));
        } catch (DuplicateKeyException ex) {
            throw new BizException("角色编码已存在");
        }
        audit.operation("ROLE_CREATE", "SYS_ROLE", String.valueOf(id), null);
        return id;
    }

    /** 修改非内置角色的基础信息。 */
    public void updateRole(long id, RoleCommand command) {
        requireNotBuiltIn("sys_role", id, "内置角色不允许修改");
        validateRole(command);
        try {
            jdbc.update(
                    "UPDATE sys_role SET role_code=?,role_name=?,status=?,updated_at=NOW(3),updated_by=? "
                            + "WHERE id=? AND deleted=0",
                    trim(command.getRoleCode()).toUpperCase(),
                    trim(command.getRoleName()),
                    enabledStatus(command.getStatus()),
                    SecurityUtils.userId(),
                    id);
        } catch (DuplicateKeyException ex) {
            throw new BizException("角色编码已存在");
        }
        audit.operation("ROLE_UPDATE", "SYS_ROLE", String.valueOf(id), null);
    }

    /** 删除未被账号使用的非内置角色。 */
    @Transactional
    public void deleteRole(long id) {
        requireNotBuiltIn("sys_role", id, "内置角色不允许删除");
        Integer used =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user_role WHERE role_id=?", Integer.class, id);
        if (used != null && used > 0) {
            throw new BizException("角色已关联账号，不能删除");
        }
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=?", id);
        jdbc.update(
                "UPDATE sys_role SET deleted=1,updated_at=NOW(3),updated_by=? WHERE id=?",
                SecurityUtils.userId(),
                id);
        audit.operation("ROLE_DELETE", "SYS_ROLE", String.valueOf(id), null);
    }

    /** 查询角色已授权的可见菜单主键；按钮权限由服务端自动维护，不暴露给页面。 */
    public List<Long> roleMenuIds(long roleId) {
        return jdbc.queryForList(
                "SELECT rm.menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id "
                        + "WHERE rm.role_id=? AND m.deleted=0 AND m.menu_type<>'BUTTON' ORDER BY rm.menu_id",
                Long.class,
                roleId);
    }

    /** 覆盖角色授权，并自动授予所选业务菜单下的隐藏按钮权限。 */
    @Transactional
    public void grantRole(long roleId, List<Long> menuIds) {
        String roleCode = roleCode(roleId);
        if ("ADMIN".equals(roleCode)) {
            throw new BizException("系统管理员默认拥有全部权限，不允许修改授权");
        }
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=?", roleId);
        List<Long> safeIds = menuIds == null ? Collections.<Long>emptyList() : menuIds;
        for (Long menuId : safeIds) {
            Integer valid =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sys_menu WHERE id=? AND deleted=0 AND menu_type<>'BUTTON'",
                            Integer.class,
                            menuId);
            if (valid != null && valid == 1) {
                jdbc.update(
                        "INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES(?,?)",
                        roleId,
                        menuId);
                jdbc.update(
                        "INSERT IGNORE INTO sys_role_menu(role_id,menu_id) "
                                + "SELECT ?,id FROM sys_menu WHERE parent_id=? AND deleted=0 "
                                + "AND menu_type='BUTTON'",
                        roleId,
                        menuId);
                if (menuId == 110L) {
                    // 笔记正文图片与时间线共用文件上传权限。
                    jdbc.update(
                            "INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES(?,300)",
                            roleId);
                }
            }
        }
        List<Long> users =
                jdbc.queryForList(
                        "SELECT user_id FROM sys_user_role WHERE role_id=?", Long.class, roleId);
        for (Long userId : users) {
            tokens.revokeAll(userId);
        }
        audit.operation(
                "ROLE_GRANT",
                "SYS_ROLE",
                String.valueOf(roleId),
                "menuCount=" + safeIds.size());
    }

    /** 查询目录和页面菜单；按钮仅作为后端权限资源存在，不参与菜单管理。 */
    public List<Map<String, Object>> menus(String keyword, String menuType) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT * FROM sys_menu WHERE deleted=0 AND menu_type<>'BUTTON'");
        List<Object> args = new ArrayList<Object>();
        if (!trim(keyword).isEmpty()) {
            sql.append(" AND (menu_name LIKE ? OR permission_code LIKE ? OR route_path LIKE ?)");
            String value = "%" + trim(keyword) + "%";
            args.add(value);
            args.add(value);
            args.add(value);
        }
        appendEqual(sql, args, "menu_type", menuType);
        sql.append(" ORDER BY parent_id,sort_no,id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 新增同级或子级菜单节点。 */
    public long createMenu(MenuCommand command) {
        validateMenu(command);
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_menu(id,parent_id,menu_type,menu_name,route_path,component_path,"
                            + "permission_code,icon,sort_no,visible,built_in) VALUES(?,?,?,?,?,?,?,?,?,?,0)",
                    id,
                    value(command.getParentId(), 0L),
                    trim(command.getMenuType()).toUpperCase(),
                    trim(command.getMenuName()),
                    blankToNull(command.getRoutePath()),
                    blankToNull(command.getComponentPath()),
                    blankToNull(command.getPermissionCode()),
                    blankToNull(command.getIcon()),
                    value(command.getSortNo(), 0),
                    value(command.getVisible(), 1));
        } catch (DuplicateKeyException ex) {
            throw new BizException("权限码已存在");
        }
        jdbc.update(
                "INSERT IGNORE INTO sys_role_menu(role_id,menu_id) "
                        + "SELECT id,? FROM sys_role WHERE role_code='ADMIN' AND deleted=0",
                id);
        audit.operation("MENU_CREATE", "SYS_MENU", String.valueOf(id), null);
        return id;
    }

    /** 修改菜单节点详细信息。 */
    public void updateMenu(long id, MenuCommand command) {
        validateMenu(command);
        if (id == value(command.getParentId(), 0L)) {
            throw new BizException("菜单不能以自己作为父节点");
        }
        try {
            jdbc.update(
                    "UPDATE sys_menu SET parent_id=?,menu_type=?,menu_name=?,route_path=?,component_path=?,"
                            + "permission_code=?,icon=?,sort_no=?,visible=?,updated_at=NOW(3),updated_by=? "
                            + "WHERE id=? AND deleted=0",
                    value(command.getParentId(), 0L),
                    trim(command.getMenuType()).toUpperCase(),
                    trim(command.getMenuName()),
                    blankToNull(command.getRoutePath()),
                    blankToNull(command.getComponentPath()),
                    blankToNull(command.getPermissionCode()),
                    blankToNull(command.getIcon()),
                    value(command.getSortNo(), 0),
                    value(command.getVisible(), 1),
                    SecurityUtils.userId(),
                    id);
        } catch (DuplicateKeyException ex) {
            throw new BizException("权限码已存在");
        }
        audit.operation("MENU_UPDATE", "SYS_MENU", String.valueOf(id), null);
    }

    /** 拖动菜单后只更新父节点和同级排序号。 */
    public void moveMenu(long id, Long parentId, Integer sortNo) {
        if (id == value(parentId, 0L)) {
            throw new BizException("菜单不能移动到自身下面");
        }
        jdbc.update(
                "UPDATE sys_menu SET parent_id=?,sort_no=?,updated_at=NOW(3),updated_by=? WHERE id=?",
                value(parentId, 0L),
                value(sortNo, 0),
                SecurityUtils.userId(),
                id);
        audit.operation("MENU_MOVE", "SYS_MENU", String.valueOf(id), null);
    }

    /** 删除没有子节点的自定义菜单。 */
    @Transactional
    public void deleteMenu(long id) {
        requireNotBuiltIn("sys_menu", id, "内置菜单不允许删除，可以修改为隐藏");
        Integer children =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE parent_id=? AND deleted=0",
                        Integer.class,
                        id);
        if (children != null && children > 0) {
            throw new BizException("请先删除子节点");
        }
        jdbc.update("DELETE FROM sys_role_menu WHERE menu_id=?", id);
        jdbc.update(
                "UPDATE sys_menu SET deleted=1,updated_at=NOW(3),updated_by=? WHERE id=?",
                SecurityUtils.userId(),
                id);
        audit.operation("MENU_DELETE", "SYS_MENU", String.valueOf(id), null);
    }

    /** 查询字典类型及其字典项。 */
    public List<Map<String, Object>> dictionaries(
            String typeCode, String typeName, String itemLabel, String status) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT t.id type_id,t.type_code,t.type_name,t.description type_description,"
                                + "t.status type_status,t.built_in type_built_in,i.id,i.item_code,"
                                + "i.item_value,i.item_label,i.sort_no,i.status,i.display_style,i.built_in "
                                + "FROM sys_dict_type t LEFT JOIN sys_dict_item i "
                                + "ON i.type_id=t.id AND i.deleted=0 WHERE t.deleted=0");
        List<Object> args = new ArrayList<Object>();
        appendLike(sql, args, "t.type_code", typeCode);
        appendLike(sql, args, "t.type_name", typeName);
        appendLike(sql, args, "i.item_label", itemLabel);
        if (!trim(status).isEmpty()) {
            sql.append(" AND (i.status=? OR (i.id IS NULL AND t.status=?))");
            args.add(status);
            args.add(status);
        }
        sql.append(" ORDER BY t.type_code,i.sort_no,i.id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 查询字典类型列表，供字典项编辑器选择。 */
    public List<Map<String, Object>> dictionaryTypes() {
        return jdbc.queryForList(
                "SELECT * FROM sys_dict_type WHERE deleted=0 ORDER BY type_code");
    }

    /** 查询业务页面可使用的启用字典项，不返回字典管理元数据。 */
    public List<Map<String, Object>> dictionaryOptions(String typeCode) {
        return jdbc.queryForList(
                "SELECT i.item_code,i.item_value,i.item_label,i.display_style,i.sort_no "
                        + "FROM sys_dict_type t JOIN sys_dict_item i ON i.type_id=t.id "
                        + "WHERE t.type_code=? AND t.status='ENABLED' AND t.deleted=0 "
                        + "AND i.status='ENABLED' AND i.deleted=0 ORDER BY i.sort_no,i.id",
                typeCode);
    }

    /** 新增字典类型。 */
    public long createDictionaryType(DictionaryTypeCommand command) {
        validateDictionaryType(command);
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_dict_type(id,type_code,type_name,description,status,built_in) "
                            + "VALUES(?,?,?,?,?,0)",
                    id,
                    trim(command.getTypeCode()).toUpperCase(),
                    trim(command.getTypeName()),
                    blankToNull(command.getDescription()),
                    enabledStatus(command.getStatus()));
        } catch (DuplicateKeyException ex) {
            throw new BizException("字典类型编码已存在");
        }
        clearNamespace("dictionary", false);
        audit.operation("DICT_TYPE_CREATE", "SYS_DICT_TYPE", String.valueOf(id), null);
        return id;
    }

    /** 修改字典类型。 */
    public void updateDictionaryType(long id, DictionaryTypeCommand command) {
        validateDictionaryType(command);
        try {
            jdbc.update(
                    "UPDATE sys_dict_type SET type_code=?,type_name=?,description=?,status=?,"
                            + "updated_at=NOW(3),updated_by=? WHERE id=? AND deleted=0",
                    trim(command.getTypeCode()).toUpperCase(),
                    trim(command.getTypeName()),
                    blankToNull(command.getDescription()),
                    enabledStatus(command.getStatus()),
                    SecurityUtils.userId(),
                    id);
        } catch (DuplicateKeyException ex) {
            throw new BizException("字典类型编码已存在");
        }
        clearNamespace("dictionary", false);
        audit.operation("DICT_TYPE_UPDATE", "SYS_DICT_TYPE", String.valueOf(id), null);
    }

    /** 删除没有字典项的自定义字典类型。 */
    public void deleteDictionaryType(long id) {
        requireNotBuiltIn("sys_dict_type", id, "内置字典类型不允许删除");
        Integer children =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_dict_item WHERE type_id=? AND deleted=0",
                        Integer.class,
                        id);
        if (children != null && children > 0) {
            throw new BizException("请先删除该类型下的字典项");
        }
        jdbc.update("UPDATE sys_dict_type SET deleted=1 WHERE id=?", id);
        clearNamespace("dictionary", false);
        audit.operation("DICT_TYPE_DELETE", "SYS_DICT_TYPE", String.valueOf(id), null);
    }

    /** 新增字典项。 */
    public long createDictionaryItem(DictionaryItemCommand command) {
        validateDictionaryItem(command);
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_dict_item(id,type_id,item_code,item_value,item_label,sort_no,status,"
                            + "display_style,built_in) VALUES(?,?,?,?,?,?,?,?,0)",
                    id,
                    command.getTypeId(),
                    trim(command.getItemCode()).toUpperCase(),
                    trim(command.getItemValue()),
                    trim(command.getItemLabel()),
                    value(command.getSortNo(), 0),
                    enabledStatus(command.getStatus()),
                    blankToNull(command.getDisplayStyle()));
        } catch (DuplicateKeyException ex) {
            throw new BizException("同一类型下字典项编码不能重复");
        }
        clearNamespace("dictionary", false);
        audit.operation("DICT_ITEM_CREATE", "SYS_DICT_ITEM", String.valueOf(id), null);
        return id;
    }

    /** 修改字典项。 */
    public void updateDictionaryItem(long id, DictionaryItemCommand command) {
        validateDictionaryItem(command);
        try {
            jdbc.update(
                    "UPDATE sys_dict_item SET type_id=?,item_code=?,item_value=?,item_label=?,sort_no=?,"
                            + "status=?,display_style=?,updated_at=NOW(3),updated_by=? WHERE id=? AND deleted=0",
                    command.getTypeId(),
                    trim(command.getItemCode()).toUpperCase(),
                    trim(command.getItemValue()),
                    trim(command.getItemLabel()),
                    value(command.getSortNo(), 0),
                    enabledStatus(command.getStatus()),
                    blankToNull(command.getDisplayStyle()),
                    SecurityUtils.userId(),
                    id);
        } catch (DuplicateKeyException ex) {
            throw new BizException("同一类型下字典项编码不能重复");
        }
        clearNamespace("dictionary", false);
        audit.operation("DICT_ITEM_UPDATE", "SYS_DICT_ITEM", String.valueOf(id), null);
    }

    /** 删除自定义字典项；内置业务枚举只允许停用，避免历史数据失去解释。 */
    public void deleteDictionaryItem(long id) {
        requireNotBuiltIn("sys_dict_item", id, "内置字典项不允许删除，可以修改为停用");
        jdbc.update("UPDATE sys_dict_item SET deleted=1 WHERE id=?", id);
        clearNamespace("dictionary", false);
        audit.operation("DICT_ITEM_DELETE", "SYS_DICT_ITEM", String.valueOf(id), null);
    }

    /** 查询可维护的系统参数，敏感参数永不回显当前值。 */
    public List<Map<String, Object>> parameters(String key, String name, String type) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_parameter WHERE deleted=0");
        List<Object> args = new ArrayList<Object>();
        appendLike(sql, args, "param_key", key);
        appendLike(sql, args, "param_name", name);
        appendEqual(sql, args, "param_type", type);
        sql.append(" ORDER BY param_key");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            if (number(row.get("is_sensitive"), 0) == 1) {
                row.put("param_value", null);
            }
        }
        return rows;
    }

    /** 新增系统参数。 */
    public long createParameter(SystemParameterCommand command) {
        validateParameter(command);
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_parameter(id,param_key,param_name,param_type,param_value,default_value,"
                            + "is_sensitive,dynamic_effect,description,created_by,updated_by) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    id,
                    trim(command.getParamKey()),
                    trim(command.getParamName()),
                    trim(command.getParamType()).toUpperCase(),
                    command.getParamValue(),
                    command.getDefaultValue(),
                    value(command.getIsSensitive(), 0),
                    value(command.getDynamicEffect(), 1),
                    blankToNull(command.getDescription()),
                    SecurityUtils.userId(),
                    SecurityUtils.userId());
        } catch (DuplicateKeyException ex) {
            throw new BizException("参数编码已存在");
        }
        clearNamespace("parameter", false);
        audit.operation("PARAMETER_CREATE", "SYS_PARAMETER", String.valueOf(id), null);
        return id;
    }

    /** 修改系统参数的完整配置。 */
    public void updateParameter(long id, SystemParameterCommand command) {
        if (value(command.getIsSensitive(), 0) == 1
                && trim(command.getParamValue()).isEmpty()) {
            List<String> values =
                    jdbc.queryForList(
                            "SELECT param_value FROM sys_parameter WHERE id=? AND deleted=0",
                            String.class,
                            id);
            if (values.isEmpty()) {
                throw new BizException(404, "系统参数不存在");
            }
            command.setParamValue(values.get(0));
        }
        validateParameter(command);
        try {
            int changed =
                    jdbc.update(
                            "UPDATE sys_parameter SET param_key=?,param_name=?,param_type=?,param_value=?,"
                                    + "default_value=?,is_sensitive=?,dynamic_effect=?,description=?,"
                                    + "updated_at=NOW(3),updated_by=? WHERE id=? AND deleted=0",
                            trim(command.getParamKey()),
                            trim(command.getParamName()),
                            trim(command.getParamType()).toUpperCase(),
                            command.getParamValue(),
                            command.getDefaultValue(),
                            value(command.getIsSensitive(), 0),
                            value(command.getDynamicEffect(), 1),
                            blankToNull(command.getDescription()),
                            SecurityUtils.userId(),
                            id);
            if (changed == 0) {
                throw new BizException(404, "系统参数不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException("参数编码已存在");
        }
        clearNamespace("parameter", false);
        audit.operation("PARAMETER_UPDATE", "SYS_PARAMETER", String.valueOf(id), null);
    }

    /** 逻辑删除系统参数并释放其唯一编码。 */
    public void deleteParameter(long id) {
        int changed =
                jdbc.update(
                        "UPDATE sys_parameter SET param_key=CONCAT('DELETED_',id),deleted=1,"
                                + "updated_at=NOW(3),updated_by=? WHERE id=? AND deleted=0",
                        SecurityUtils.userId(),
                        id);
        if (changed == 0) {
            throw new BizException(404, "系统参数不存在");
        }
        clearNamespace("parameter", false);
        audit.operation("PARAMETER_DELETE", "SYS_PARAMETER", String.valueOf(id), null);
    }

    /** 校验类型后更新系统参数。 */
    public void updateParameter(String key, String value) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT * FROM sys_parameter WHERE param_key=? AND deleted=0", key);
        validateValue(String.valueOf(row.get("param_type")), value);
        jdbc.update(
                "UPDATE sys_parameter SET param_value=?,updated_at=NOW(3),updated_by=? WHERE param_key=?",
                value,
                SecurityUtils.userId(),
                key);
        clearNamespace("parameter", false);
        audit.operation(
                "PARAMETER_UPDATE",
                "SYS_PARAMETER",
                key,
                number(row.get("is_sensitive"), 0) == 1
                        ? "secret changed"
                        : "value changed");
    }

    /** 查询已登记的缓存命名空间及当前键数量。 */
    public List<Map<String, Object>> caches() {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT * FROM sys_cache_namespace WHERE deleted=0 ORDER BY built_in DESC,id");
        for (Map<String, Object> row : rows) {
            String namespace = String.valueOf(row.get("namespace"));
            row.put("keyCount", countKeys(namespace));
            row.put("pattern", "tradn:prod:" + namespace + ":*");
        }
        return rows;
    }

    /** 新增可管理的 Redis 缓存命名空间。 */
    public long createCache(CacheNamespaceCommand command) {
        validateCache(command);
        long id = IdWorker.getId();
        try {
            jdbc.update(
                    "INSERT INTO sys_cache_namespace(id,namespace,display_name,description,status,built_in,created_by,updated_by) "
                            + "VALUES(?,?,?,?,?,0,?,?)",
                    id,
                    trim(command.getNamespace()),
                    trim(command.getDisplayName()),
                    blankToNull(command.getDescription()),
                    enabledStatus(command.getStatus()),
                    SecurityUtils.userId(),
                    SecurityUtils.userId());
        } catch (DuplicateKeyException ex) {
            throw new BizException("缓存命名空间已存在");
        }
        audit.operation("CACHE_CREATE", "SYS_CACHE_NAMESPACE", String.valueOf(id), null);
        return id;
    }

    /** 修改缓存命名空间配置。 */
    public void updateCache(long id, CacheNamespaceCommand command) {
        validateCache(command);
        try {
            int changed =
                    jdbc.update(
                            "UPDATE sys_cache_namespace SET namespace=?,display_name=?,description=?,status=?,"
                                    + "updated_at=NOW(3),updated_by=? WHERE id=? AND deleted=0",
                            trim(command.getNamespace()),
                            trim(command.getDisplayName()),
                            blankToNull(command.getDescription()),
                            enabledStatus(command.getStatus()),
                            SecurityUtils.userId(),
                            id);
            if (changed == 0) {
                throw new BizException(404, "缓存命名空间不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException("缓存命名空间已存在");
        }
        audit.operation("CACHE_UPDATE", "SYS_CACHE_NAMESPACE", String.valueOf(id), null);
    }

    /** 删除自定义缓存命名空间；系统内置命名空间只允许停用。 */
    public void deleteCache(long id) {
        requireNotBuiltIn("sys_cache_namespace", id, "内置缓存命名空间不允许删除，可以修改为停用");
        jdbc.update(
                "UPDATE sys_cache_namespace SET namespace=CONCAT('DELETED_',id),deleted=1,"
                        + "updated_at=NOW(3),updated_by=? WHERE id=?",
                SecurityUtils.userId(),
                id);
        audit.operation("CACHE_DELETE", "SYS_CACHE_NAMESPACE", String.valueOf(id), null);
    }

    /** 使用 SCAN + UNLINK 清理白名单命名空间，避免阻塞 Redis 主线程。 */
    public long clearNamespace(String namespace, boolean record) {
        Integer registered =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_cache_namespace WHERE namespace=? AND status='ENABLED' AND deleted=0",
                        Integer.class,
                        namespace);
        if (registered == null || registered == 0) {
            throw new BizException("未注册的缓存命名空间");
        }
        final String pattern = "tradn:prod:" + namespace + ":*";
        Long count =
                redis.execute(
                        (RedisCallback<Long>)
                                connection -> {
                                    long total = 0;
                                    Cursor<byte[]> cursor =
                                            connection.scan(
                                                    ScanOptions.scanOptions()
                                                            .match(pattern)
                                                            .count(500)
                                                            .build());
                                    try {
                                        List<byte[]> batch = new ArrayList<byte[]>();
                                        while (cursor.hasNext()) {
                                            batch.add(cursor.next());
                                            if (batch.size() >= 200) {
                                                connection.unlink(batch.toArray(new byte[0][]));
                                                total += batch.size();
                                                batch.clear();
                                            }
                                        }
                                        if (!batch.isEmpty()) {
                                            connection.unlink(batch.toArray(new byte[0][]));
                                            total += batch.size();
                                        }
                                    } finally {
                                        cursor.close();
                                    }
                                    return total;
                                });
        if (record) {
            audit.operation("CACHE_CLEAR", "REDIS", namespace, "count=" + count);
        }
        return count == null ? 0 : count;
    }

    /** 分页查询登录审计。 */
    public PageResult<Map<String, Object>> loginAudits(
            int page,
            int size,
            String username,
            Integer success,
            String reasonCode,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        Query query = new Query(" FROM sys_login_audit WHERE 1=1");
        appendLike(query.sql, query.args, "username_snapshot", username);
        if (success != null) {
            appendEqual(query.sql, query.args, "success", success);
        }
        appendEqual(query.sql, query.args, "reason_code", reasonCode);
        appendRange(query, "occurred_at", startTime, endTime);
        return pageQuery(
                "SELECT *",
                query,
                " ORDER BY occurred_at DESC",
                page,
                size);
    }

    /** 分页查询访问审计。 */
    public PageResult<Map<String, Object>> accessAudits(
            int page,
            int size,
            String username,
            String endpoint,
            String result,
            Integer responseStatus,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        Query query =
                new Query(
                        " FROM sys_access_audit a LEFT JOIN sys_user u ON u.id=a.user_id AND u.deleted=0 WHERE 1=1");
        appendLike(query.sql, query.args, "u.username", username);
        appendLike(query.sql, query.args, "a.endpoint", endpoint);
        appendEqual(query.sql, query.args, "a.result", result);
        if (responseStatus != null) {
            appendEqual(query.sql, query.args, "a.response_status", responseStatus);
        }
        appendRange(query, "a.occurred_at", startTime, endTime);
        return pageQuery(
                "SELECT a.*,COALESCE(u.username,'-') username",
                query,
                " ORDER BY a.occurred_at DESC",
                page,
                size);
    }

    /** 分页查询聚合异常日志。 */
    public PageResult<Map<String, Object>> exceptions(
            int page,
            int size,
            String exceptionType,
            String endpoint,
            String handleStatus,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        Query query = new Query(" FROM sys_exception_log WHERE 1=1");
        appendLike(query.sql, query.args, "exception_type", exceptionType);
        appendLike(query.sql, query.args, "endpoint", endpoint);
        appendEqual(query.sql, query.args, "handle_status", handleStatus);
        appendRange(query, "last_occurred_at", startTime, endTime);
        return pageQuery(
                "SELECT *",
                query,
                " ORDER BY last_occurred_at DESC",
                page,
                size);
    }

    /** 更新异常处理状态和处理备注。 */
    public void handleException(long id, String status, String remark) {
        jdbc.update(
                "UPDATE sys_exception_log SET handle_status=?,handle_remark=? WHERE id=?",
                status,
                remark,
                id);
        audit.operation("EXCEPTION_HANDLE", "SYS_EXCEPTION_LOG", String.valueOf(id), null);
    }

    private PageResult<Map<String, Object>> pageQuery(
            String select, Query query, String orderBy, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 100));
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*)" + query.sql,
                        query.args.toArray(),
                        Long.class);
        List<Object> pageArgs = new ArrayList<Object>(query.args);
        pageArgs.add(safeSize);
        pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records =
                jdbc.queryForList(
                        select + query.sql + orderBy + " LIMIT ? OFFSET ?", pageArgs.toArray());
        return new PageResult<Map<String, Object>>(
                records, total == null ? 0 : total, safePage, safeSize);
    }

    private long countKeys(String namespace) {
        final String pattern = "tradn:prod:" + namespace + ":*";
        Long count =
                redis.execute(
                        (RedisCallback<Long>)
                                connection -> {
                                    long total = 0;
                                    Cursor<byte[]> cursor =
                                            connection.scan(
                                                    ScanOptions.scanOptions()
                                                            .match(pattern)
                                                            .count(500)
                                                            .build());
                                    try {
                                        while (cursor.hasNext() && total < 100000) {
                                            cursor.next();
                                            total++;
                                        }
                                    } finally {
                                        cursor.close();
                                    }
                                    return total;
                                });
        return count == null ? 0 : count;
    }

    private void validateRole(RoleCommand command) {
        if (trim(command.getRoleCode()).isEmpty() || trim(command.getRoleName()).isEmpty()) {
            throw new BizException("角色编码和名称不能为空");
        }
    }

    private void validateMenu(MenuCommand command) {
        String type = trim(command.getMenuType()).toUpperCase();
        if (trim(command.getMenuName()).isEmpty()
                || !("DIRECTORY".equals(type) || "MENU".equals(type))) {
            throw new BizException("菜单名称或节点类型不正确");
        }
    }

    private void validateParameter(SystemParameterCommand command) {
        String type = trim(command.getParamType()).toUpperCase();
        if (trim(command.getParamKey()).isEmpty()
                || trim(command.getParamName()).isEmpty()
                || !("STRING".equals(type)
                        || "INTEGER".equals(type)
                        || "BOOLEAN".equals(type))) {
            throw new BizException("参数编码、名称或类型不正确");
        }
        validateValue(type, command.getParamValue());
    }

    private void validateCache(CacheNamespaceCommand command) {
        if (trim(command.getNamespace()).isEmpty()
                || !trim(command.getNamespace()).matches("[A-Za-z0-9_-]+")
                || trim(command.getDisplayName()).isEmpty()) {
            throw new BizException("缓存命名空间或显示名称不正确");
        }
    }

    private String roleCode(long roleId) {
        List<String> rows =
                jdbc.queryForList(
                        "SELECT role_code FROM sys_role WHERE id=? AND deleted=0",
                        String.class,
                        roleId);
        if (rows.isEmpty()) {
            throw new BizException(404, "角色不存在");
        }
        return rows.get(0);
    }

    private void validateDictionaryType(DictionaryTypeCommand command) {
        if (trim(command.getTypeCode()).isEmpty() || trim(command.getTypeName()).isEmpty()) {
            throw new BizException("字典类型编码和名称不能为空");
        }
    }

    private void validateDictionaryItem(DictionaryItemCommand command) {
        if (command.getTypeId() == null
                || trim(command.getItemCode()).isEmpty()
                || trim(command.getItemValue()).isEmpty()
                || trim(command.getItemLabel()).isEmpty()) {
            throw new BizException("字典类型、项编码、业务值和显示文本不能为空");
        }
    }

    private void validateValue(String type, String value) {
        try {
            if ("INTEGER".equals(type)) {
                Integer.parseInt(value);
            } else if ("BOOLEAN".equals(type)
                    && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw new BizException("参数值类型不正确");
        }
    }

    private void requireNotBuiltIn(String table, long id, String message) {
        Integer builtIn =
                jdbc.queryForObject(
                        "SELECT built_in FROM " + table + " WHERE id=? AND deleted=0",
                        Integer.class,
                        id);
        if (builtIn == null) {
            throw new BizException(404, "记录不存在");
        }
        if (builtIn == 1) {
            throw new BizException(message);
        }
    }

    private String enabledStatus(String status) {
        String value = trim(status).toUpperCase();
        if (value.isEmpty()) {
            return "ENABLED";
        }
        if (!("ENABLED".equals(value) || "DISABLED".equals(value))) {
            throw new BizException("状态值不正确");
        }
        return value;
    }

    private void appendRange(
            Query query, String column, LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null) {
            query.sql.append(" AND ").append(column).append(">=?");
            query.args.add(startTime);
        }
        if (endTime != null) {
            query.sql.append(" AND ").append(column).append("<=?");
            query.args.add(endTime);
        }
    }

    private void appendLike(
            StringBuilder sql, List<Object> args, String column, String keyword) {
        if (!trim(keyword).isEmpty()) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            args.add("%" + trim(keyword) + "%");
        }
    }

    private void appendEqual(
            StringBuilder sql, List<Object> args, String column, Object value) {
        if (value != null && !trim(value).isEmpty()) {
            sql.append(" AND ").append(column).append("=?");
            args.add(value);
        }
    }

    private String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        String result = trim(value);
        return result.isEmpty() ? null : result;
    }

    private int number(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <T> T value(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /** 可复用的安全查询片段，仅由代码内固定列名组成。 */
    private static class Query {
        /** FROM/WHERE 查询片段。 */
        private final StringBuilder sql;

        /** 对应占位符参数。 */
        private final List<Object> args = new ArrayList<Object>();

        private Query(String sql) {
            this.sql = new StringBuilder(sql);
        }
    }
}
