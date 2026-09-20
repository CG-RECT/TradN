-- 交易主记录：状态按 DRAFT -> PLANNED -> OPEN -> CLOSED 流转，所有查询必须按 user_id 隔离。
CREATE TABLE trade_record (
  id BIGINT PRIMARY KEY COMMENT '交易记录主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  record_no VARCHAR(64) NOT NULL COMMENT '用户内唯一交易记录编号',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '交易状态：DRAFT/PLANNED/OPEN/CLOSED',
  direction VARCHAR(10) NULL COMMENT '交易方向：LONG/SHORT',
  planned_take_profit_price DECIMAL(18,4) NULL COMMENT '计划止盈价格',
  planned_stop_loss_price DECIMAL(18,4) NULL COMMENT '计划止损价格',
  open_price DECIMAL(18,4) NULL COMMENT '实际开仓价格',
  close_price DECIMAL(18,4) NULL COMMENT '实际平仓价格',
  lot_size DECIMAL(12,4) NULL COMMENT '开仓手数',
  open_time DATETIME(3) NULL COMMENT '实际开仓时间',
  close_time DATETIME(3) NULL COMMENT '实际平仓时间',
  profit_loss DECIMAL(18,2) NULL COMMENT '实际盈亏金额',
  profit_loss_currency VARCHAR(10) NOT NULL DEFAULT 'CNY' COMMENT '盈亏币种，默认人民币',
  remark VARCHAR(2000) NULL COMMENT '交易备注',
  questionnaire_template_id BIGINT NULL COMMENT '绑定的问卷模板ID',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_trade_record_no (user_id, record_no),
  KEY idx_trade_user_status (user_id, status),
  KEY idx_trade_user_close (user_id, close_time),
  KEY idx_trade_user_open (user_id, open_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黄金交易开平仓记录';

-- 问卷模板：模板按 code + version 唯一，交易记录绑定具体版本以保证历史问题不漂移。
CREATE TABLE questionnaire_template (
  id BIGINT PRIMARY KEY COMMENT '问卷模板主键',
  template_code VARCHAR(100) NOT NULL COMMENT '问卷模板编码',
  template_version INT NOT NULL COMMENT '模板版本号',
  template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '模板状态：ENABLED/DISABLED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  UNIQUE KEY uk_questionnaire_template (template_code, template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易问卷模板';

-- 问卷题目：phase 区分开仓前与开仓后，sort_no 决定稳定展示顺序。
CREATE TABLE questionnaire_question (
  id BIGINT PRIMARY KEY COMMENT '问卷题目主键',
  template_id BIGINT NOT NULL COMMENT '所属问卷模板ID',
  phase VARCHAR(20) NOT NULL COMMENT '问卷阶段：BEFORE_OPEN/AFTER_OPEN',
  question_no VARCHAR(20) NOT NULL COMMENT '阶段内题目编号',
  parent_id BIGINT NULL COMMENT '父题目ID，用于条件子题',
  title VARCHAR(1000) NOT NULL COMMENT '题目文本',
  question_type VARCHAR(30) NOT NULL COMMENT '题型：BOOLEAN/NUMBER/TEXT/TEXTAREA/OPTION',
  required_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：0否，1是',
  option_json TEXT NULL COMMENT '选项型题目的JSON配置',
  sort_no INT NOT NULL COMMENT '跨阶段稳定排序号',
  KEY idx_question_template_phase (template_id, phase, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易问卷题目';

-- 交易问卷答案：一笔交易对同一题只有一个答案，按题型写入对应的类型列。
CREATE TABLE trade_questionnaire_answer (
  id BIGINT PRIMARY KEY COMMENT '问卷答案主键',
  trade_id BIGINT NOT NULL COMMENT '交易记录ID',
  question_id BIGINT NOT NULL COMMENT '问卷题目ID',
  boolean_answer TINYINT NULL COMMENT '布尔型答案：0否，1是',
  number_answer DECIMAL(18,4) NULL COMMENT '数值型答案',
  text_answer TEXT NULL COMMENT '文本型答案',
  option_answer VARCHAR(500) NULL COMMENT '选项型答案编码',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次作答时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  UNIQUE KEY uk_trade_question_answer (trade_id, question_id),
  KEY idx_answer_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易问卷答案';

-- Markdown 笔记：自动生成区与人工编辑区分列保存，避免时间线同步覆盖用户内容。
-- active_unique_key 仅对仍处于有效状态的自动笔记赋值，以兼容逻辑删除后的再次创建。
CREATE TABLE note (
  id BIGINT PRIMARY KEY COMMENT '笔记主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  title VARCHAR(300) NOT NULL COMMENT '笔记标题',
  note_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT '笔记类型：NORMAL/GOLD_DAILY_SUMMARY/TRADE_REVIEW',
  business_date DATE NULL COMMENT '笔记对应业务日期',
  summary VARCHAR(1000) NULL COMMENT '笔记摘要',
  generated_content LONGTEXT NULL COMMENT '系统自动生成的Markdown内容',
  manual_content LONGTEXT NULL COMMENT '用户人工编辑的Markdown内容',
  sync_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '自动同步状态：NONE/AUTO/DETACHED',
  source_type VARCHAR(30) NULL COMMENT '自动内容来源类型',
  source_id BIGINT NULL COMMENT '自动内容来源业务ID',
  pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0否，1是',
  active_unique_key VARCHAR(300) NULL COMMENT '有效自动笔记唯一占位键',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_note_active_key (active_unique_key),
  KEY idx_note_user_type_date (user_id, note_type, business_date),
  FULLTEXT KEY ft_note_title_summary (title, summary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Markdown交易笔记';

-- 用户自定义笔记标签；逻辑删除字段参与唯一键，允许删除后重新创建同名标签。
CREATE TABLE note_tag (
  id BIGINT PRIMARY KEY COMMENT '标签主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  tag_name VARCHAR(100) NOT NULL COMMENT '标签名称',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  UNIQUE KEY uk_note_tag (user_id, tag_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户笔记标签';

-- 笔记与标签多对多关系。
CREATE TABLE note_tag_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系主键',
  note_id BIGINT NOT NULL COMMENT '笔记ID',
  tag_id BIGINT NOT NULL COMMENT '标签ID',
  UNIQUE KEY uk_note_tag_relation (note_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记与标签关系';

-- 黄金每日时间线：每个用户每天最多一条有效记录，并关联一篇自动汇总笔记。
CREATE TABLE gold_daily_timeline (
  id BIGINT PRIMARY KEY COMMENT '每日时间线主键',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  timeline_date DATE NOT NULL COMMENT '黄金时间线日期',
  daily_content TEXT NULL COMMENT '当日文字记录',
  summary_note_id BIGINT NULL COMMENT '自动生成的每日汇总笔记ID',
  active_unique_key VARCHAR(200) NULL COMMENT '用户与日期的有效记录唯一占位键',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_timeline_active_key (active_unique_key),
  KEY idx_timeline_user_date (user_id, timeline_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黄金每日时间线';

-- 时间线关联的普通笔记；自动生成的每日汇总笔记不写入此关系，避免循环引用。
CREATE TABLE timeline_note_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系主键',
  timeline_id BIGINT NOT NULL COMMENT '每日时间线ID',
  note_id BIGINT NOT NULL COMMENT '关联普通笔记ID',
  UNIQUE KEY uk_timeline_note (timeline_id, note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间线与普通笔记关系';

-- MinIO 文件元数据：对象本体保存在 MinIO，数据库只保存所有权、对象键和图片属性。
CREATE TABLE file_object (
  id BIGINT PRIMARY KEY COMMENT '文件元数据主键',
  user_id BIGINT NOT NULL COMMENT '文件所属用户ID',
  bucket_name VARCHAR(100) NOT NULL COMMENT 'MinIO桶名称',
  object_key VARCHAR(500) NOT NULL COMMENT '原始文件对象键',
  thumbnail_object_key VARCHAR(500) NULL COMMENT '缩略图对象键',
  original_name VARCHAR(300) NOT NULL COMMENT '上传时的原始文件名',
  mime_type VARCHAR(100) NOT NULL COMMENT '文件MIME类型',
  file_size BIGINT NOT NULL COMMENT '文件字节数',
  sha256 VARCHAR(64) NULL COMMENT '文件内容SHA-256摘要',
  image_width INT NULL COMMENT '原图宽度像素',
  image_height INT NULL COMMENT '原图高度像素',
  upload_status VARCHAR(20) NOT NULL DEFAULT 'READY' COMMENT '上传状态：READY/FAILED',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_file_object_key (bucket_name, object_key),
  KEY idx_file_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MinIO文件对象元数据';

-- 通用业务文件关系：同一文件可按用途关联业务对象，business_type 必须使用代码登记值。
CREATE TABLE business_file_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系主键',
  file_id BIGINT NOT NULL COMMENT '文件元数据ID',
  business_type VARCHAR(30) NOT NULL COMMENT '关联业务类型：TIMELINE/NOTE等',
  business_id BIGINT NOT NULL COMMENT '关联业务对象ID',
  usage_type VARCHAR(30) NOT NULL COMMENT '文件用途：CHART/EMBEDDED_IMAGE/ATTACHMENT',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '业务对象内展示顺序',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关系创建时间',
  UNIQUE KEY uk_business_file (file_id, business_type, business_id, usage_type),
  KEY idx_business_file_target (business_type, business_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务对象与文件关系';
