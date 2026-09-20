-- 系统账号：保存登录主体及乐观锁字段，用户名在未逻辑删除前全局唯一。
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY COMMENT '用户主键',
  username VARCHAR(64) NOT NULL COMMENT '登录用户名',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  nickname VARCHAR(100) NOT NULL COMMENT '用户显示昵称',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED/DISABLED',
  last_login_at DATETIME(3) NULL COMMENT '最近一次成功登录时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户账号';

-- 系统角色：built_in 标识内置角色，内置数据只能受控修改，不能随意删除。
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY COMMENT '角色主键',
  role_code VARCHAR(64) NOT NULL COMMENT '角色唯一编码',
  role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态：ENABLED/DISABLED',
  built_in TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置角色：0否，1是',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色';

-- 菜单与权限资源：DIRECTORY/MENU/BUTTON 共用一棵树，permission_code 作为前后端权限契约。
CREATE TABLE sys_menu (
  id BIGINT PRIMARY KEY COMMENT '菜单资源主键',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级菜单ID，0表示根节点',
  menu_type VARCHAR(20) NOT NULL COMMENT '资源类型：DIRECTORY/MENU/BUTTON',
  menu_name VARCHAR(100) NOT NULL COMMENT '菜单或按钮名称',
  route_path VARCHAR(200) NULL COMMENT '前端路由路径',
  component_path VARCHAR(200) NULL COMMENT '前端组件标识',
  permission_code VARCHAR(128) NULL COMMENT '权限码：模块:资源:动作',
  icon VARCHAR(64) NULL COMMENT '前端图标标识',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同级显示顺序',
  visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示：0隐藏，1显示',
  built_in TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置资源：0否，1是',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_sys_menu_permission (permission_code),
  KEY idx_sys_menu_parent (parent_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单与权限资源';

-- 用户与角色多对多关系；关系表使用物理删除，授权变更时整体替换。
CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与角色关系';

-- 角色与菜单/按钮权限多对多关系。
CREATE TABLE sys_role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系主键',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单或按钮资源ID',
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  KEY idx_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色与菜单权限关系';

-- 数据字典类型：定义一组可配置枚举的元数据。
CREATE TABLE sys_dict_type (
  id BIGINT PRIMARY KEY COMMENT '字典类型主键',
  type_code VARCHAR(100) NOT NULL COMMENT '字典类型唯一编码',
  type_name VARCHAR(100) NOT NULL COMMENT '字典类型名称',
  description VARCHAR(500) NULL COMMENT '字典用途说明',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '字典状态：ENABLED/DISABLED',
  built_in TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置字典：0否，1是',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_dict_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典类型';

-- 数据字典项：同一字典类型内 item_code 唯一，并按 sort_no 展示。
CREATE TABLE sys_dict_item (
  id BIGINT PRIMARY KEY COMMENT '字典项主键',
  type_id BIGINT NOT NULL COMMENT '所属字典类型ID',
  item_code VARCHAR(100) NOT NULL COMMENT '字典项唯一编码',
  item_value VARCHAR(200) NOT NULL COMMENT '字典项业务值',
  item_label VARCHAR(200) NOT NULL COMMENT '字典项显示文本',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '字典项状态：ENABLED/DISABLED',
  display_style VARCHAR(100) NULL COMMENT '前端展示样式标识',
  built_in TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置字典项：0否，1是',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_dict_item (type_id, item_code),
  KEY idx_dict_item_type_sort (type_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典项';

-- 系统参数：is_sensitive 参数查询时必须脱敏，dynamic_effect 表示是否无需重启即可生效。
CREATE TABLE sys_parameter (
  id BIGINT PRIMARY KEY COMMENT '系统参数主键',
  param_key VARCHAR(150) NOT NULL COMMENT '参数唯一键',
  param_name VARCHAR(150) NOT NULL COMMENT '参数名称',
  param_type VARCHAR(30) NOT NULL COMMENT '参数类型：STRING/INTEGER/BOOLEAN等',
  param_value TEXT NULL COMMENT '当前参数值',
  default_value TEXT NULL COMMENT '默认参数值',
  validation_rule VARCHAR(500) NULL COMMENT '参数校验规则',
  is_sensitive TINYINT NOT NULL DEFAULT 0 COMMENT '是否敏感参数：0否，1是',
  dynamic_effect TINYINT NOT NULL DEFAULT 1 COMMENT '是否动态生效：0需重启，1立即生效',
  description VARCHAR(500) NULL COMMENT '参数用途说明',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_sys_parameter_key (param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统运行参数';

-- 调度任务配置：job_code 只能对应后端代码注册的白名单任务，不能保存任意执行内容。
CREATE TABLE sys_job (
  id BIGINT PRIMARY KEY COMMENT '调度任务主键',
  job_code VARCHAR(100) NOT NULL COMMENT '代码白名单中的任务编码',
  job_name VARCHAR(150) NOT NULL COMMENT '任务名称',
  cron_expression VARCHAR(100) NOT NULL COMMENT 'Quartz Cron表达式',
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'Cron计算时区',
  status VARCHAR(20) NOT NULL DEFAULT 'PAUSED' COMMENT '任务状态：ENABLED/PAUSED',
  allow_concurrent TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许并发执行：0否，1是',
  last_run_at DATETIME(3) NULL COMMENT '最近一次执行时间',
  next_run_at DATETIME(3) NULL COMMENT '下一次计划执行时间',
  description VARCHAR(500) NULL COMMENT '任务用途说明',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID，0表示系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID，0表示系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_sys_job_code (job_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统调度任务配置';

-- 调度执行日志：记录每次计划/手工触发的结果和耗时，便于运维追踪。
CREATE TABLE sys_job_log (
  id BIGINT PRIMARY KEY COMMENT '任务执行日志主键',
  job_id BIGINT NOT NULL COMMENT '调度任务ID',
  trigger_type VARCHAR(20) NOT NULL COMMENT '触发类型：SCHEDULED/MANUAL',
  started_at DATETIME(3) NOT NULL COMMENT '开始执行时间',
  finished_at DATETIME(3) NULL COMMENT '结束执行时间',
  duration_ms BIGINT NULL COMMENT '执行耗时毫秒数',
  status VARCHAR(20) NOT NULL COMMENT '执行状态：RUNNING/SUCCESS/FAILED',
  error_summary VARCHAR(2000) NULL COMMENT '失败原因摘要',
  request_id VARCHAR(64) NULL COMMENT '请求链路ID',
  KEY idx_job_log_job_time (job_id, started_at),
  KEY idx_job_log_status_time (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度任务执行日志';

-- 登录审计：只保存令牌摘要，不保存原始密码或完整令牌。
CREATE TABLE sys_login_audit (
  id BIGINT PRIMARY KEY COMMENT '登录审计主键',
  user_id BIGINT NULL COMMENT '登录成功时的用户ID',
  username_snapshot VARCHAR(64) NOT NULL COMMENT '登录时提交的用户名快照',
  success TINYINT NOT NULL COMMENT '是否登录成功：0否，1是',
  reason_code VARCHAR(50) NULL COMMENT '登录结果原因编码',
  ip_address VARCHAR(64) NULL COMMENT '客户端IP地址',
  user_agent VARCHAR(500) NULL COMMENT '客户端User-Agent',
  session_digest VARCHAR(64) NULL COMMENT '会话令牌不可逆摘要',
  occurred_at DATETIME(3) NOT NULL COMMENT '登录发生时间',
  request_id VARCHAR(64) NULL COMMENT '请求链路ID',
  KEY idx_login_audit_time (occurred_at),
  KEY idx_login_audit_user_time (user_id, occurred_at),
  KEY idx_login_audit_result_time (success, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号登录审计';

-- 访问与业务操作审计：parameter_summary 只能保存脱敏摘要，不得保存 Markdown 正文或密钥。
CREATE TABLE sys_access_audit (
  id BIGINT PRIMARY KEY COMMENT '访问审计主键',
  user_id BIGINT NULL COMMENT '操作用户ID，系统操作可为空',
  http_method VARCHAR(10) NOT NULL COMMENT 'HTTP请求方法或SYSTEM',
  endpoint VARCHAR(300) NOT NULL COMMENT '请求接口路径',
  operation_type VARCHAR(50) NULL COMMENT '业务操作类型',
  business_type VARCHAR(50) NULL COMMENT '业务对象类型',
  business_id VARCHAR(100) NULL COMMENT '业务对象标识',
  parameter_summary VARCHAR(2000) NULL COMMENT '脱敏后的操作参数摘要',
  response_status INT NOT NULL COMMENT 'HTTP响应状态码',
  duration_ms BIGINT NOT NULL COMMENT '请求耗时毫秒数',
  ip_address VARCHAR(64) NULL COMMENT '客户端IP地址',
  result VARCHAR(20) NOT NULL COMMENT '操作结果：SUCCESS/FAILURE',
  occurred_at DATETIME(3) NOT NULL COMMENT '操作发生时间',
  request_id VARCHAR(64) NULL COMMENT '请求链路ID',
  KEY idx_access_audit_time (occurred_at),
  KEY idx_access_audit_user_time (user_id, occurred_at),
  KEY idx_access_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口访问与业务操作审计';

-- 聚合异常日志：fingerprint 对同类异常去重，occurrence_count 累计出现次数。
CREATE TABLE sys_exception_log (
  id BIGINT PRIMARY KEY COMMENT '异常日志主键',
  fingerprint VARCHAR(64) NOT NULL COMMENT '异常类型与消息生成的聚合指纹',
  exception_type VARCHAR(300) NOT NULL COMMENT '异常类全限定名',
  message_summary VARCHAR(2000) NULL COMMENT '异常消息摘要',
  stack_summary TEXT NULL COMMENT '截断后的异常堆栈',
  endpoint VARCHAR(300) NULL COMMENT '异常发生接口',
  user_id BIGINT NULL COMMENT '异常发生时的用户ID',
  first_occurred_at DATETIME(3) NOT NULL COMMENT '首次发生时间',
  last_occurred_at DATETIME(3) NOT NULL COMMENT '最近发生时间',
  occurrence_count INT NOT NULL DEFAULT 1 COMMENT '累计发生次数',
  handle_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD' COMMENT '处理状态：UNREAD/HANDLED',
  handle_remark VARCHAR(1000) NULL COMMENT '处理备注',
  request_id VARCHAR(64) NULL COMMENT '最近一次请求链路ID',
  UNIQUE KEY uk_exception_fingerprint (fingerprint),
  KEY idx_exception_last_time (last_occurred_at),
  KEY idx_exception_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合异常日志';
