-- 系统设置三级分组。分组节点只负责导航组织，不单独定义权限码；叶子菜单继续使用原权限码鉴权。
INSERT INTO sys_menu (
  id, parent_id, menu_type, menu_name, route_path, component_path,
  permission_code, icon, sort_no, visible, built_in
) VALUES
(220, 200, 'DIRECTORY', '人员管理', NULL, NULL, NULL, 'team', 10, 1, 1),
(230, 200, 'DIRECTORY', '基础管理', NULL, NULL, NULL, 'database', 20, 1, 1),
(240, 200, 'DIRECTORY', '审计管理', NULL, NULL, NULL, 'audit', 30, 1, 1),
(250, 200, 'BUTTON', '维护角色', NULL, NULL, 'system:role:update', NULL, 40, 0, 1);

-- 将现有系统菜单移动到人员、基础和审计分组下，保持叶子路由及权限码不变。
UPDATE sys_menu SET parent_id = 220, sort_no = 1 WHERE id = 205;
UPDATE sys_menu SET parent_id = 220, sort_no = 2 WHERE id = 206;
UPDATE sys_menu SET parent_id = 220, sort_no = 3 WHERE id = 207;

UPDATE sys_menu SET parent_id = 230, sort_no = 1 WHERE id = 201;
UPDATE sys_menu SET parent_id = 230, sort_no = 2 WHERE id = 202;
UPDATE sys_menu SET parent_id = 230, sort_no = 3 WHERE id = 203;
UPDATE sys_menu SET parent_id = 230, sort_no = 4 WHERE id = 204;
UPDATE sys_menu SET parent_id = 230, sort_no = 5 WHERE id = 210;

UPDATE sys_menu SET parent_id = 240, sort_no = 1 WHERE id = 208;
UPDATE sys_menu SET parent_id = 240, sort_no = 2 WHERE id = 209;

-- 管理员获得新增分组节点和角色维护权限，其他内置角色不开放系统管理功能。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (220, 230, 240, 250);

-- 内置数据字典类型。业务表保存 item_value，页面统一展示 item_label。
INSERT INTO sys_dict_type (
  id, type_code, type_name, description, status, built_in
) VALUES
(10000, 'COMMON_STATUS', '通用启停状态', '账号、角色、字典等通用启停状态', 'ENABLED', 1),
(10010, 'LOGIN_SUCCESS', '登录是否成功', '登录审计 success 字段显示文本', 'ENABLED', 1),
(10020, 'LOGIN_REASON', '登录结果原因', '登录审计 reason_code 字段显示文本', 'ENABLED', 1),
(10030, 'TRADE_STATUS', '开仓记录状态', '开仓记录状态机的页面显示文本', 'ENABLED', 1),
(10040, 'NOTE_TYPE', '笔记类型', '笔记来源和用途分类', 'ENABLED', 1),
(10050, 'ACCESS_RESULT', '访问结果', '访问审计 result 字段显示文本', 'ENABLED', 1),
(10060, 'EXCEPTION_STATUS', '异常处理状态', '异常日志 handle_status 字段显示文本', 'ENABLED', 1),
(10070, 'MENU_TYPE', '菜单节点类型', '菜单树节点类型显示文本', 'ENABLED', 1);

-- 内置字典项。display_style 使用 Ant Design Vue 的标签色名称。
INSERT INTO sys_dict_item (
  id, type_id, item_code, item_value, item_label, sort_no,
  status, display_style, built_in
) VALUES
(10101, 10000, 'ENABLED', 'ENABLED', '启用', 1, 'ENABLED', 'green', 1),
(10102, 10000, 'DISABLED', 'DISABLED', '禁用', 2, 'ENABLED', 'red', 1),

(10201, 10010, 'SUCCESS', '1', '成功', 1, 'ENABLED', 'green', 1),
(10202, 10010, 'FAILURE', '0', '失败', 2, 'ENABLED', 'red', 1),

(10301, 10020, 'SUCCESS', 'SUCCESS', '成功', 1, 'ENABLED', 'green', 1),
(10302, 10020, 'BAD_CREDENTIALS', 'BAD_CREDENTIALS', '账号/密码错误', 2, 'ENABLED', 'red', 1),
(10303, 10020, 'RATE_LIMITED', 'RATE_LIMITED', '登录尝试过多', 3, 'ENABLED', 'orange', 1),
(10304, 10020, 'ACCOUNT_DISABLED', 'ACCOUNT_DISABLED', '账号已禁用', 4, 'ENABLED', 'orange', 1),

(10401, 10030, 'DRAFT', 'DRAFT', '草稿', 1, 'ENABLED', 'default', 1),
(10402, 10030, 'PLANNED', 'PLANNED', '待开仓', 2, 'ENABLED', 'blue', 1),
(10403, 10030, 'OPEN', 'OPEN', '持仓中', 3, 'ENABLED', 'orange', 1),
(10404, 10030, 'CLOSED', 'CLOSED', '已平仓', 4, 'ENABLED', 'green', 1),

(10501, 10040, 'NORMAL', 'NORMAL', '普通笔记', 1, 'ENABLED', 'default', 1),
(10502, 10040, 'GOLD_DAILY_SUMMARY', 'GOLD_DAILY_SUMMARY', '黄金每日汇总', 2, 'ENABLED', 'gold', 1),
(10503, 10040, 'TRADE_REVIEW', 'TRADE_REVIEW', '交易复盘', 3, 'ENABLED', 'blue', 1),

(10601, 10050, 'SUCCESS', 'SUCCESS', '成功', 1, 'ENABLED', 'green', 1),
(10602, 10050, 'FAILURE', 'FAILURE', '失败', 2, 'ENABLED', 'red', 1),

(10701, 10060, 'UNREAD', 'UNREAD', '未处理', 1, 'ENABLED', 'red', 1),
(10702, 10060, 'HANDLED', 'HANDLED', '已处理', 2, 'ENABLED', 'green', 1),

(10801, 10070, 'DIRECTORY', 'DIRECTORY', '目录', 1, 'ENABLED', 'blue', 1),
(10802, 10070, 'MENU', 'MENU', '菜单', 2, 'ENABLED', 'green', 1),
(10803, 10070, 'BUTTON', 'BUTTON', '按钮', 3, 'ENABLED', 'orange', 1);
