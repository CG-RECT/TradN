-- 内置角色：ADMIN 拥有系统管理权限，TRADER 只拥有个人交易业务权限。
INSERT INTO sys_role (id, role_code, role_name, status, built_in) VALUES
(1, 'ADMIN', '系统管理员', 'ENABLED', 1),
(2, 'TRADER', '交易用户', 'ENABLED', 1);

-- 菜单、按钮与权限码基线；permission_code 必须和 @PreAuthorize、前端路由保持一致。
INSERT INTO sys_menu (id,parent_id,menu_type,menu_name,route_path,component_path,permission_code,icon,sort_no,visible,built_in) VALUES
(100,0,'MENU','开仓记录','/trades','trade/index','trade:record:list','swap',10,1,1),
(101,100,'BUTTON','新增开仓记录',NULL,NULL,'trade:record:create',NULL,1,0,1),
(102,100,'BUTTON','修改开仓记录',NULL,NULL,'trade:record:update',NULL,2,0,1),
(110,0,'MENU','笔记','/notes','note/index','note:note:list','book',20,1,1),
(111,110,'BUTTON','新增笔记',NULL,NULL,'note:note:create',NULL,1,0,1),
(112,110,'BUTTON','修改笔记',NULL,NULL,'note:note:update',NULL,2,0,1),
(120,0,'MENU','黄金时间线','/timeline','timeline/index','timeline:daily:view','calendar',30,1,1),
(121,120,'BUTTON','修改时间线',NULL,NULL,'timeline:daily:update',NULL,1,0,1),
(130,0,'MENU','盈亏统计','/statistics','statistics/index','trade:statistics:view','line-chart',40,1,1),
(200,0,'DIRECTORY','系统设置','/system',NULL,'system:settings:view','setting',90,1,1),
(201,200,'MENU','数据字典','/system/dictionaries','system/dictionaries','system:dict:list',NULL,1,1,1),
(202,200,'MENU','系统参数','/system/parameters','system/parameters','system:parameter:list',NULL,2,1,1),
(203,200,'MENU','系统调度','/system/jobs','system/jobs','system:scheduler:list',NULL,3,1,1),
(204,200,'MENU','缓存管理','/system/caches','system/caches','system:cache:view',NULL,4,1,1),
(205,200,'MENU','账号管理','/system/users','system/users','system:user:list',NULL,5,1,1),
(206,200,'MENU','角色管理','/system/roles','system/roles','system:role:list',NULL,6,1,1),
(207,200,'MENU','菜单管理','/system/menus','system/menus','system:menu:list',NULL,7,1,1),
(208,200,'MENU','登录审计','/system/audits/login','system/audit-login','system:audit:login:view',NULL,8,1,1),
(209,200,'MENU','访问审计','/system/audits/access','system/audit-access','system:audit:access:view',NULL,9,1,1),
(210,200,'MENU','异常日志','/system/exceptions','system/exceptions','system:exception:view',NULL,10,1,1),
(211,200,'BUTTON','修改数据字典',NULL,NULL,'system:dict:update',NULL,20,0,1),
(212,200,'BUTTON','修改系统参数',NULL,NULL,'system:parameter:update',NULL,21,0,1),
(213,200,'BUTTON','管理调度任务',NULL,NULL,'system:scheduler:update',NULL,22,0,1),
(214,200,'BUTTON','手动执行任务',NULL,NULL,'system:scheduler:trigger',NULL,23,0,1),
(215,200,'BUTTON','清理缓存',NULL,NULL,'system:cache:clear',NULL,24,0,1),
(216,200,'BUTTON','管理账号',NULL,NULL,'system:user:update',NULL,25,0,1),
(217,200,'BUTTON','角色授权',NULL,NULL,'system:role:grant',NULL,26,0,1),
(218,200,'BUTTON','管理菜单',NULL,NULL,'system:menu:update',NULL,27,0,1),
(219,200,'BUTTON','导出审计',NULL,NULL,'system:audit:export',NULL,28,0,1),
(300,0,'BUTTON','上传文件',NULL,NULL,'file:object:upload',NULL,1,0,1);

-- 管理员授予全部权限，交易用户只授予业务菜单和文件上传权限。
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE id < 200 OR id = 300;

-- 开仓提示问卷模板版本 1；历史交易绑定版本号，后续题目变化应新增模板版本。
INSERT INTO questionnaire_template (id,template_code,template_version,template_name,status)
VALUES (1,'OPEN_POSITION_PROMPT',1,'开仓提示问卷','ENABLED');

-- BEFORE_OPEN 为开仓前 13 题，AFTER_OPEN 为保存计划后可补充的复盘 2 题。
INSERT INTO questionnaire_question (id,template_id,phase,question_no,title,question_type,required_flag,sort_no) VALUES
(1001,1,'BEFORE_OPEN','1','是随手单吗？','BOOLEAN',1,1),
(1002,1,'BEFORE_OPEN','2','止盈价格、止损价格分别是多少？','TEXT',1,2),
(1003,1,'BEFORE_OPEN','3','开多/空的依据逻辑是什么？','TEXTAREA',1,3),
(1004,1,'BEFORE_OPEN','4','止盈/止损价格逻辑分别是什么？','TEXTAREA',1,4),
(1005,1,'BEFORE_OPEN','5','价格走势预测？','TEXTAREA',1,5),
(1006,1,'BEFORE_OPEN','6','你的逻辑是否和某位或某些UP主逻辑一致？（指尖金汇-黄金）','TEXTAREA',0,6),
(1007,1,'BEFORE_OPEN','7','各方做单方向是什么？尤其是机构方向，大趋势向上还是向下？','TEXTAREA',1,7),
(1008,1,'BEFORE_OPEN','8','基本面、消息面、资金面、技术面、情绪面分别是什么？','TEXTAREA',1,8),
(1009,1,'BEFORE_OPEN','9','最近的波动你都能解释出来吗？','TEXTAREA',0,9),
(1010,1,'BEFORE_OPEN','10','各大机构有没有出货压力？','TEXTAREA',0,10),
(1011,1,'BEFORE_OPEN','11','最近是否会有重大消息影响？','TEXTAREA',0,11),
(1012,1,'BEFORE_OPEN','12','价格决定权目前在哪里？属于消息还是资金？','TEXTAREA',0,12),
(1013,1,'BEFORE_OPEN','13','关键交易时间点，是否已做到交易、分析优先？','BOOLEAN',0,13),
(1101,1,'AFTER_OPEN','1','价格走势是否按照自己预想的逻辑进行走动？','TEXTAREA',0,101),
(1102,1,'AFTER_OPEN','2','开仓逻辑是否发生变化？是否需要进行平仓？','TEXTAREA',0,102);

-- 系统参数初始值；所有限制仍需由后端执行，前端展示不能作为约束边界。
INSERT INTO sys_parameter (id,param_key,param_name,param_type,param_value,default_value,dynamic_effect,description) VALUES
(1,'file.timeline.max-count-per-day','时间线每日最大图片数','INTEGER','20','20',1,'限制单日上传图片数量'),
(2,'recycle.retention-days','回收站保留天数','INTEGER','30','30',1,'逻辑删除数据保留时间'),
(3,'audit.login.retention-days','登录审计保留天数','INTEGER','180','180',1,'登录审计自动清理周期'),
(4,'audit.access.retention-days','访问审计保留天数','INTEGER','90','90',1,'访问审计自动清理周期'),
(5,'audit.exception.retention-days','异常日志保留天数','INTEGER','90','90',1,'异常日志自动清理周期'),
(6,'timeline.card.max-images','时间线卡片最大图片数','INTEGER','4','4',1,'卡片默认展示图片数'),
(7,'timeline.card.max-note-summaries','时间线卡片最大摘要数','INTEGER','2','2',1,'卡片默认展示笔记摘要数');

-- 调度任务默认暂停，管理员确认保留策略后再启用；job_code 必须存在于代码白名单。
INSERT INTO sys_job (id,job_code,job_name,cron_expression,status,description) VALUES
(1,'AUDIT_LOG_CLEANUP','审计日志清理','0 30 2 * * ?','PAUSED','按保留策略分批清理审计与异常日志'),
(2,'ORPHAN_FILE_CLEANUP','孤立文件清理','0 0 3 * * ?','PAUSED','清理超过保留期的孤立临时文件'),
(3,'TIMELINE_SUMMARY_REPAIR','时间线汇总修复','0 30 3 * * ?','PAUSED','修复时间线与自动汇总笔记一致性');
