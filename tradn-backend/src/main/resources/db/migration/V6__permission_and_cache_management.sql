-- TradN 权限关系与缓存命名空间管理增强。
-- 按钮权限继续作为后端接口鉴权资源保留，但不再出现在菜单维护和角色授权界面。

-- 将按钮权限挂到实际业务菜单，角色选择菜单时由服务端自动补齐其按钮权限。
UPDATE sys_menu SET parent_id = 201 WHERE id = 211 AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 202 WHERE id = 212 AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 203 WHERE id IN (213, 214) AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 204 WHERE id = 215 AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 205 WHERE id = 216 AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 206 WHERE id IN (217, 250) AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 207 WHERE id = 218 AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 240 WHERE id = 219 AND menu_type = 'BUTTON';
UPDATE sys_menu SET parent_id = 120 WHERE id = 300 AND menu_type = 'BUTTON';

-- 缓存配置维护权限。
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_name, permission_code, sort_no, visible, built_in
) VALUES (
    251, 204, 'BUTTON', '维护缓存配置', 'system:cache:update', 30, 0, 1
) ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    sort_no = VALUES(sort_no),
    visible = VALUES(visible),
    built_in = VALUES(built_in);

-- 系统管理员始终拥有全部资源权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE deleted = 0;

CREATE TABLE sys_cache_namespace (
    id BIGINT NOT NULL COMMENT '主键ID',
    namespace VARCHAR(64) NOT NULL COMMENT 'Redis缓存命名空间，仅允许字母、数字、中划线和下划线',
    display_name VARCHAR(100) NOT NULL COMMENT '缓存命名空间显示名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '缓存用途说明',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED启用、DISABLED停用',
    built_in TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统内置：1是、0否',
    created_by BIGINT DEFAULT NULL COMMENT '创建人账号ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后更新人账号ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：1已删除、0未删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_cache_namespace (namespace),
    KEY idx_sys_cache_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Redis缓存命名空间配置表';

INSERT INTO sys_cache_namespace (
    id, namespace, display_name, description, status, built_in
) VALUES
    (1, 'session', '登录会话', 'JWT会话与令牌状态缓存', 'ENABLED', 1),
    (2, 'user-session', '账号会话', '按账号维护的会话索引缓存', 'ENABLED', 1),
    (3, 'permission', '权限数据', '账号角色与菜单权限缓存', 'ENABLED', 1),
    (4, 'dictionary', '数据字典', '数据字典选项缓存', 'ENABLED', 1),
    (5, 'parameter', '系统参数', '系统参数读取缓存', 'ENABLED', 1),
    (6, 'login-limit', '登录限制', '登录失败次数与限流缓存', 'ENABLED', 1);
