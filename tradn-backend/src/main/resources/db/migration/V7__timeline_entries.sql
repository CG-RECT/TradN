-- 黄金时间线独立备注卡片：同一天可以保存多条文字、图片或笔记备注。
-- 备注卡片与日期记录分离，页面可以按卡片宽度和日期锚点重新排版。
CREATE TABLE timeline_entry (
  id BIGINT NOT NULL COMMENT '时间线备注卡片主键',
  timeline_id BIGINT NOT NULL COMMENT '所属每日时间线ID',
  user_id BIGINT NOT NULL COMMENT '所属账号ID，用于数据隔离',
  entry_type VARCHAR(20) NOT NULL COMMENT '备注类型：TEXT文字、IMAGE图片、NOTE笔记',
  content TEXT NULL COMMENT '文字备注内容，图片和笔记类型为空',
  note_id BIGINT NULL COMMENT '关联笔记ID，备注类型为NOTE时使用',
  file_id BIGINT NULL COMMENT '关联文件ID，备注类型为IMAGE时使用',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同一天内的展示顺序',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人账号ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人账号ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_timeline_entry_day (timeline_id, sort_no, deleted),
  KEY idx_timeline_entry_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黄金时间线独立备注卡片';

-- 已有每日文字内容保留在原字段中，由后端以兼容卡片形式展示；新内容写入 timeline_entry。
