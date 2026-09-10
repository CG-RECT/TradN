# TradN 业务与数据库真相文档

本文件是代码仓库内的业务与数据模型基线。业务、接口、状态、权限码或表结构变化时必须与代码和 Flyway 同步修改。

## 1. 技术边界

- 前后端分离；后端 Java 8 + Spring Boot 2.7.18 + MyBatis-Plus；前端 Vue 3 + TypeScript + Vite + Ant Design Vue。
- MySQL 是业务事实源；Redis 只保存登录会话、限流和缓存；MinIO 保存原图和缩略图。
- 用户业务表均包含 `user_id`，Service 必须校验当前登录用户所有权。
- 后端雪花主键为 64 位整数，JSON 统一序列化为字符串，避免超过 JavaScript 安全整数上限后发生精度丢失。

## 2. 认证与权限

- `sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu` 构成 RBAC。
- JWT 包含用户 ID 与会话 ID；Redis 中会话不存在时令牌立即失效。
- 未登录、令牌过期/损坏、Redis 会话被撤销或账号禁用时，安全过滤链返回 HTTP 401 和统一中文响应。已认证但权限不足仍返回 403，不能按登录失效处理。
- 前端收到会话失效响应后清理本地令牌，仅跳转一次登录页并展示提示；登录成功后返回原站内页面。登录接口的账号密码错误留在登录页，403 不清理登录态。
- 首次进入工作台先加载个人信息和菜单，成功后再加载业务页面，避免过期登录导致空菜单和多条接口错误。
- 前端控制菜单显示，后端 `@PreAuthorize` 是最终权限边界。`BUTTON` 记录只作为接口权限资源存在，不在菜单管理或角色授权树中展示。
- 登录菜单取“账号关联的启用角色”与“角色已授权菜单”的交集；账号状态、账号角色或角色授权变化后撤销旧会话，重新登录后按新权限生成菜单。
- `ADMIN`、`TRADER` 为内置角色，禁止修改角色元数据和删除。`ADMIN` 默认拥有全部菜单及接口权限且授权不可修改；`TRADER` 允许修改菜单授权。
- 保存角色菜单授权时，服务端自动补齐所选页面菜单下的隐藏按钮权限，避免页面与接口鉴权资源耦合。
- 初始管理员仅在 `ADMIN_INITIAL_PASSWORD` 至少10位时创建，不存在代码默认密码。

## 3. 开仓记录与问卷

状态：`PLANNED → OPEN → CLOSED`。旧数据中的 `DRAFT` 仍可编辑兼容；新建页面在浏览器中暂存，只有点击保存并通过必填校验后才创建 `PLANNED` 记录。只有 `PLANNED` 可标记开仓，只有 `OPEN` 可平仓。

- `trade_record`：方向、计划止盈止损、开平仓价格/时间、手数、实际盈亏。
- `questionnaire_template`、`questionnaire_question`：版本化问卷模板。
- `trade_questionnaire_answer`：逐题答案；模板变化不增加业务表列。
- 保存开仓前13题后，即使仍是 `PLANNED`，再次编辑也显示并允许填写开仓后2题。
- 点击取消、返回或返回列表不会创建数据库记录；查看模式只读，编辑模式才显示保存和状态推进操作。
- 盈亏按 `close_time` 日期和 `CLOSED` 状态统计；未平仓不计入已实现盈亏。

## 4. 笔记

- `note` 保存 Markdown 原文，类型为 `NORMAL`、`GOLD_DAILY_SUMMARY`、`TRADE_REVIEW`。
- 自动汇总笔记分 `generated_content`（系统写）和 `manual_content`（用户写）。
- `sync_status=AUTO` 时跟随时间线；解除后为 `DETACHED`，自动内容合并到人工内容。
- `note_tag`、`note_tag_relation` 预留标签能力。
- 新建笔记在浏览器中暂存，点击保存才写入数据库；取消或返回不产生占位笔记。查看与编辑入口分离。

## 5. 黄金时间线

- `gold_daily_timeline`：同一用户、同一日期最多一条有效记录。
- `timeline_note_relation`：关联普通笔记，禁止把每日汇总笔记再次关联，避免循环。
- `business_file_relation` 以 `business_type=TIMELINE` 关联图片。
- 保存时间线时同步创建/更新标题为 `黄金每日复盘 - yyyy-MM-dd` 的笔记。
- 前端首次加载今天前后各10天，共21天；视口默认显示7个日期单元，今天位于正中间。
- 所有日期始终排列为一条横向时间线，支持鼠标拖动、滚轮横移和单元格缩放；到达左右边缘或点击边缘箭头时，每次增量加载10天并合并已有数据。
- 每个日期内部的图片缩略图和笔记摘要优先填满当前行，空间不足时最多换到第5行；超出部分显示数量提示并在日期编辑弹窗中查看。

## 6. 文件

- `file_object` 保存 MinIO Bucket、原图键、缩略图键、MIME、大小、SHA-256和宽高。
- Bucket 私有，客户端只获得15分钟预签名 URL。
- 仅允许 JPG、PNG、WebP、GIF；服务端生成对象名，不信任原始文件名。

## 7. 系统设置

- 导航分组：`系统设置 → 人员管理（账号/角色/菜单）`、`基础管理（字典/参数/调度/缓存/异常）`、`审计管理（登录/访问）`。
- 系统设置各列表均提供与业务字段对应的查询条件和重置操作；登录审计、访问审计、异常日志由服务端分页，并支持时间范围查询。
- 账号管理可查看关联角色并启用/禁用账号；角色管理支持自定义角色增删改查和菜单树授权；菜单管理只维护目录与页面菜单，以左侧树选择、右侧详情/内联编辑的方式支持同级/子级新增和拖动排序。
- 数据字典：`sys_dict_type`、`sys_dict_item`。
- 内置字典包含 `COMMON_STATUS`、`LOGIN_SUCCESS`、`LOGIN_REASON`、`TRADE_STATUS`、`NOTE_TYPE`、`ACCESS_RESULT`、`EXCEPTION_STATUS`、`MENU_TYPE`。业务表保存 `item_value`，页面显示 `item_label`。
- 内置字典类型/字典项禁止删除，可以修改显示文本、样式或停用；自定义字典支持增删改查。
- 系统参数：`sys_parameter`，支持增删改查；`is_sensitive=1` 的敏感参数不得回显或写入审计摘要。
- 系统调度：`sys_job`、`sys_job_log`、`QRTZ_*`，支持增删改查；仅注册任务编码可执行。
- 缓存管理：`sys_cache_namespace` 保存可维护命名空间，支持自定义配置增删改查；清理采用 SCAN + UNLINK，禁止任意 Redis 命令，内置命名空间不可删除但可停用。
- 登录审计：`sys_login_audit`；访问审计：`sys_access_audit`，列表通过关联 `sys_user` 展示用户名；异常：`sys_exception_log`。
- 缓存清理、调度操作、强制下线、密码重置必须写访问审计。

## 8. 数据迁移

- `V1__system_tables.sql`：权限、字典、参数、调度和审计。
- `V2__business_tables.sql`：交易、问卷、笔记、时间线和文件。
- `V3__seed_data.sql`：角色、菜单权限、问卷与安全的默认参数，不包含密码。
- `V4__quartz_tables.sql`：Quartz MySQL JobStore。
- `V5__system_management_enhancements.sql`：系统设置菜单分组、角色维护权限以及业务状态数据字典。
- `V6__permission_and_cache_management.sql`：隐藏按钮权限父子关系、管理员全权限、缓存维护权限及缓存命名空间配置表。

已经执行过的迁移禁止修改；后续变化必须新增迁移文件。
