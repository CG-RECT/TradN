package com.tradn.system.controller;

import com.tradn.common.api.ApiResponse;
import com.tradn.common.api.PageResult;
import com.tradn.system.model.DictionaryItemCommand;
import com.tradn.system.model.DictionaryTypeCommand;
import com.tradn.system.model.CacheNamespaceCommand;
import com.tradn.system.model.MenuCommand;
import com.tradn.system.model.RoleCommand;
import com.tradn.system.model.SystemJobCommand;
import com.tradn.system.model.SystemParameterCommand;
import com.tradn.system.model.UserCommand;
import com.tradn.system.service.SchedulerService;
import com.tradn.system.service.SystemSettingsService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统设置入口，提供账号、角色、菜单、字典、调度、缓存和审计管理接口。 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SystemSettingsController {
    private final SystemSettingsService settings;
    private final SchedulerService scheduler;

    /** 按条件查询系统账号列表。 */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<List<Map<String, Object>>> users(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(settings.users(username, nickname, status));
    }

    /** 查询账号当前关联的角色。 */
    @GetMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<List<Map<String, Object>>> userRoles(@PathVariable long id) {
        return ApiResponse.ok(settings.userRoles(id));
    }

    /** 创建系统账号并分配角色。 */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> createUser(@RequestBody UserCommand command) {
        settings.createUser(command);
        return ApiResponse.ok();
    }

    /** 修改系统账号昵称、状态和角色。 */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> updateUser(
            @PathVariable long id, @RequestBody UserCommand command) {
        settings.updateUser(id, command);
        return ApiResponse.ok();
    }

    /** 启用或禁用账号。 */
    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable long id, @RequestBody StatusBody body) {
        settings.updateUserStatus(id, body.status);
        return ApiResponse.ok();
    }

    /** 管理员重置指定账号的登录密码。 */
    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> resetPassword(
            @PathVariable long id, @RequestBody PasswordBody body) {
        settings.resetPassword(id, body.password);
        return ApiResponse.ok();
    }

    /** 强制指定账号的全部登录会话失效。 */
    @PostMapping("/users/{id}/force-logout")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> forceLogout(@PathVariable long id) {
        settings.forceLogout(id);
        return ApiResponse.ok();
    }

    /** 按条件查询角色列表。 */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<List<Map<String, Object>>> roles(
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(settings.roles(roleCode, roleName, status));
    }

    /** 创建自定义角色。 */
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:update')")
    public ApiResponse<Long> createRole(@RequestBody RoleCommand command) {
        return ApiResponse.ok(settings.createRole(command));
    }

    /** 修改非内置角色。 */
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public ApiResponse<Void> updateRole(
            @PathVariable long id, @RequestBody RoleCommand command) {
        settings.updateRole(id, command);
        return ApiResponse.ok();
    }

    /** 删除未被账号使用的非内置角色。 */
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public ApiResponse<Void> deleteRole(@PathVariable long id) {
        settings.deleteRole(id);
        return ApiResponse.ok();
    }

    /** 查询角色已授权的菜单和按钮主键。 */
    @GetMapping("/roles/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<List<Long>> roleMenus(@PathVariable long id) {
        return ApiResponse.ok(settings.roleMenuIds(id));
    }

    /** 覆盖保存角色拥有的菜单和按钮权限。 */
    @PutMapping("/roles/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:grant')")
    public ApiResponse<Void> grant(@PathVariable long id, @RequestBody IdsBody body) {
        settings.grantRole(id, body.ids);
        return ApiResponse.ok();
    }

    /** 查询完整菜单和权限资源列表。 */
    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public ApiResponse<List<Map<String, Object>>> menus(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String menuType) {
        return ApiResponse.ok(settings.menus(keyword, menuType));
    }

    /** 新增菜单、目录或按钮节点。 */
    @PostMapping("/menus")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public ApiResponse<Long> createMenu(@RequestBody MenuCommand command) {
        return ApiResponse.ok(settings.createMenu(command));
    }

    /** 修改菜单节点详细信息。 */
    @PutMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public ApiResponse<Void> updateMenu(
            @PathVariable long id, @RequestBody MenuCommand command) {
        settings.updateMenu(id, command);
        return ApiResponse.ok();
    }

    /** 拖动后更新菜单父节点和同级顺序。 */
    @PutMapping("/menus/{id}/move")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public ApiResponse<Void> moveMenu(
            @PathVariable long id, @RequestBody MenuCommand command) {
        settings.moveMenu(id, command.getParentId(), command.getSortNo());
        return ApiResponse.ok();
    }

    /** 删除没有子节点的自定义菜单。 */
    @DeleteMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public ApiResponse<Void> deleteMenu(@PathVariable long id) {
        settings.deleteMenu(id);
        return ApiResponse.ok();
    }

    /** 按条件查询数据字典类型和字典项。 */
    @GetMapping("/dictionaries")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ApiResponse<List<Map<String, Object>>> dictionaries(
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String typeName,
            @RequestParam(required = false) String itemLabel,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(settings.dictionaries(typeCode, typeName, itemLabel, status));
    }

    /** 查询字典类型下拉选项。 */
    @GetMapping("/dictionary-types")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ApiResponse<List<Map<String, Object>>> dictionaryTypes() {
        return ApiResponse.ok(settings.dictionaryTypes());
    }

    /** 查询业务页面用于解析状态和类型的启用字典项。 */
    @GetMapping("/dictionary-options/{typeCode}")
    public ApiResponse<List<Map<String, Object>>> dictionaryOptions(
            @PathVariable String typeCode) {
        return ApiResponse.ok(settings.dictionaryOptions(typeCode));
    }

    /** 新增字典类型。 */
    @PostMapping("/dictionary-types")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public ApiResponse<Long> createDictionaryType(
            @RequestBody DictionaryTypeCommand command) {
        return ApiResponse.ok(settings.createDictionaryType(command));
    }

    /** 修改字典类型。 */
    @PutMapping("/dictionary-types/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public ApiResponse<Void> updateDictionaryType(
            @PathVariable long id, @RequestBody DictionaryTypeCommand command) {
        settings.updateDictionaryType(id, command);
        return ApiResponse.ok();
    }

    /** 删除没有字典项的自定义字典类型。 */
    @DeleteMapping("/dictionary-types/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public ApiResponse<Void> deleteDictionaryType(@PathVariable long id) {
        settings.deleteDictionaryType(id);
        return ApiResponse.ok();
    }

    /** 新增字典项。 */
    @PostMapping("/dictionary-items")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public ApiResponse<Long> createDictionaryItem(
            @RequestBody DictionaryItemCommand command) {
        return ApiResponse.ok(settings.createDictionaryItem(command));
    }

    /** 修改字典项。 */
    @PutMapping("/dictionary-items/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public ApiResponse<Void> updateDictionaryItem(
            @PathVariable long id, @RequestBody DictionaryItemCommand command) {
        settings.updateDictionaryItem(id, command);
        return ApiResponse.ok();
    }

    /** 删除自定义字典项。 */
    @DeleteMapping("/dictionary-items/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public ApiResponse<Void> deleteDictionaryItem(@PathVariable long id) {
        settings.deleteDictionaryItem(id);
        return ApiResponse.ok();
    }

    /** 按条件查询可维护的系统参数。 */
    @GetMapping("/system-parameters")
    @PreAuthorize("hasAuthority('system:parameter:list')")
    public ApiResponse<List<Map<String, Object>>> parameters(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(settings.parameters(key, name, type));
    }

    /** 新增系统参数。 */
    @PostMapping("/system-parameters")
    @PreAuthorize("hasAuthority('system:parameter:update')")
    public ApiResponse<Long> createParameter(@RequestBody SystemParameterCommand command) {
        return ApiResponse.ok(settings.createParameter(command));
    }

    /** 修改指定系统参数的完整配置。 */
    @PutMapping("/system-parameters/{id}")
    @PreAuthorize("hasAuthority('system:parameter:update')")
    public ApiResponse<Void> updateParameter(
            @PathVariable long id, @RequestBody SystemParameterCommand command) {
        settings.updateParameter(id, command);
        return ApiResponse.ok();
    }

    /** 删除指定系统参数。 */
    @DeleteMapping("/system-parameters/{id}")
    @PreAuthorize("hasAuthority('system:parameter:update')")
    public ApiResponse<Void> deleteParameter(@PathVariable long id) {
        settings.deleteParameter(id);
        return ApiResponse.ok();
    }

    /** 查询白名单调度任务及其当前状态。 */
    @GetMapping("/system-jobs")
    @PreAuthorize("hasAuthority('system:scheduler:list')")
    public ApiResponse<List<Map<String, Object>>> jobs() {
        return ApiResponse.ok(scheduler.list());
    }

    /** 新增服务端已注册的系统调度任务。 */
    @PostMapping("/system-jobs")
    @PreAuthorize("hasAuthority('system:scheduler:update')")
    public ApiResponse<Long> createJob(@RequestBody SystemJobCommand command) {
        return ApiResponse.ok(scheduler.create(command));
    }

    /** 修改系统调度任务的配置。 */
    @PutMapping("/system-jobs/{id}")
    @PreAuthorize("hasAuthority('system:scheduler:update')")
    public ApiResponse<Void> updateJob(
            @PathVariable long id, @RequestBody SystemJobCommand command) {
        scheduler.update(id, command);
        return ApiResponse.ok();
    }

    /** 删除系统调度任务。 */
    @DeleteMapping("/system-jobs/{id}")
    @PreAuthorize("hasAuthority('system:scheduler:update')")
    public ApiResponse<Void> deleteJob(@PathVariable long id) {
        scheduler.delete(id);
        return ApiResponse.ok();
    }

    /** 查询指定调度任务的执行日志。 */
    @GetMapping("/system-jobs/{id}/logs")
    @PreAuthorize("hasAuthority('system:scheduler:list')")
    public ApiResponse<List<Map<String, Object>>> jobLogs(@PathVariable long id) {
        return ApiResponse.ok(scheduler.logs(id));
    }

    /** 修改调度任务的 Cron 表达式和时区。 */
    @PutMapping("/system-jobs/{id}/schedule")
    @PreAuthorize("hasAuthority('system:scheduler:update')")
    public ApiResponse<Void> schedule(@PathVariable long id, @RequestBody ScheduleBody body) {
        scheduler.update(id, body.cronExpression, body.timezone);
        return ApiResponse.ok();
    }

    /** 暂停指定调度任务。 */
    @PostMapping("/system-jobs/{id}/pause")
    @PreAuthorize("hasAuthority('system:scheduler:update')")
    public ApiResponse<Void> pause(@PathVariable long id) {
        scheduler.pause(id);
        return ApiResponse.ok();
    }

    /** 恢复指定调度任务。 */
    @PostMapping("/system-jobs/{id}/resume")
    @PreAuthorize("hasAuthority('system:scheduler:update')")
    public ApiResponse<Void> resume(@PathVariable long id) {
        scheduler.resume(id);
        return ApiResponse.ok();
    }

    /** 立即触发一次指定调度任务。 */
    @PostMapping("/system-jobs/{id}/trigger")
    @PreAuthorize("hasAuthority('system:scheduler:trigger')")
    public ApiResponse<Void> trigger(@PathVariable long id) {
        scheduler.trigger(id);
        return ApiResponse.ok();
    }

    /** 查询系统允许管理的 Redis 缓存命名空间。 */
    @GetMapping("/caches")
    @PreAuthorize("hasAuthority('system:cache:view')")
    public ApiResponse<List<Map<String, Object>>> caches() {
        return ApiResponse.ok(settings.caches());
    }

    /** 新增 Redis 缓存命名空间。 */
    @PostMapping("/caches")
    @PreAuthorize("hasAuthority('system:cache:update')")
    public ApiResponse<Long> createCache(@RequestBody CacheNamespaceCommand command) {
        return ApiResponse.ok(settings.createCache(command));
    }

    /** 修改 Redis 缓存命名空间。 */
    @PutMapping("/caches/{id}")
    @PreAuthorize("hasAuthority('system:cache:update')")
    public ApiResponse<Void> updateCache(
            @PathVariable long id, @RequestBody CacheNamespaceCommand command) {
        settings.updateCache(id, command);
        return ApiResponse.ok();
    }

    /** 删除自定义 Redis 缓存命名空间。 */
    @DeleteMapping("/caches/{id}")
    @PreAuthorize("hasAuthority('system:cache:update')")
    public ApiResponse<Void> deleteCache(@PathVariable long id) {
        settings.deleteCache(id);
        return ApiResponse.ok();
    }

    /** 清理指定白名单命名空间下的 Redis 缓存。 */
    @PostMapping("/caches/{namespace}/clear")
    @PreAuthorize("hasAuthority('system:cache:clear')")
    public ApiResponse<Long> clear(@PathVariable String namespace) {
        return ApiResponse.ok(settings.clearNamespace(namespace, true));
    }

    /** 分页查询登录审计记录。 */
    @GetMapping("/audits/login")
    @PreAuthorize("hasAuthority('system:audit:login:view')")
    public ApiResponse<PageResult<Map<String, Object>>> loginAudits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime) {
        return ApiResponse.ok(
                settings.loginAudits(
                        page, size, username, success, reasonCode, startTime, endTime));
    }

    /** 分页查询接口访问审计记录。 */
    @GetMapping("/audits/access")
    @PreAuthorize("hasAuthority('system:audit:access:view')")
    public ApiResponse<PageResult<Map<String, Object>>> accessAudits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Integer responseStatus,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime) {
        return ApiResponse.ok(
                settings.accessAudits(
                        page,
                        size,
                        username,
                        endpoint,
                        result,
                        responseStatus,
                        startTime,
                        endTime));
    }

    /** 分页查询系统异常日志。 */
    @GetMapping("/exception-logs")
    @PreAuthorize("hasAuthority('system:exception:view')")
    public ApiResponse<PageResult<Map<String, Object>>> exceptions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String exceptionType,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startTime,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endTime) {
        return ApiResponse.ok(
                settings.exceptions(
                        page, size, exceptionType, endpoint, handleStatus, startTime, endTime));
    }

    /** 更新异常日志的处理状态和备注。 */
    @PutMapping("/exception-logs/{id}/status")
    @PreAuthorize("hasAuthority('system:exception:view')")
    public ApiResponse<Void> handle(
            @PathVariable long id, @RequestBody HandleBody body) {
        settings.handleException(id, body.status, body.remark);
        return ApiResponse.ok();
    }

    @Data
    /** 管理员重置密码请求。 */
    public static class PasswordBody {
        /** 待设置的新密码。 */
        private String password;
    }

    @Data
    /** 批量提交主键列表的通用请求。 */
    public static class IdsBody {
        /** 菜单或其他资源主键列表。 */
        private List<Long> ids;
    }

    @Data
    /** 修改单一配置值的请求。 */
    public static class ValueBody {
        /** 待保存的配置值。 */
        private String value;
    }

    @Data
    /** 修改状态字段的请求。 */
    public static class StatusBody {
        /** 新状态编码。 */
        private String status;
    }

    @Data
    /** 修改调度表达式和执行时区的请求。 */
    public static class ScheduleBody {
        /** Quartz Cron 表达式。 */
        private String cronExpression;

        /** 调度时区，默认使用中国标准时间。 */
        private String timezone = "Asia/Shanghai";
    }

    @Data
    /** 标记异常处理结果的请求。 */
    public static class HandleBody {
        /** 异常处理状态。 */
        private String status;

        /** 处理人填写的说明。 */
        private String remark;
    }
}
