package com.tradn.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tradn.common.api.RequestIds;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceTaskService {
    private final JdbcTemplate jdbc;

    public MaintenanceTaskService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void execute(String jobCode, String triggerType) {
        // 每次执行先创建 RUNNING 日志，成功或失败都在同一条记录上收口，便于定位中断任务。
        Map<String, Object> job =
                jdbc.queryForMap("SELECT id FROM sys_job WHERE job_code=? AND deleted=0", jobCode);
        long logId = IdWorker.getId();
        long start = System.currentTimeMillis();
        LocalDateTime started = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO sys_job_log(id,job_id,trigger_type,started_at,status,request_id) VALUES(?,?,?,?,?,?)",
                logId,
                job.get("id"),
                triggerType == null ? "SCHEDULED" : triggerType,
                started,
                "RUNNING",
                RequestIds.current());
        try {
            if ("AUDIT_LOG_CLEANUP".equals(jobCode)) cleanupAudits();
            else if ("ORPHAN_FILE_CLEANUP".equals(jobCode)) cleanupOrphanMetadata();
            else if ("TIMELINE_SUMMARY_REPAIR".equals(jobCode)) repairTimelineLinks();
            else throw new IllegalArgumentException("Unregistered job code: " + jobCode);
            jdbc.update(
                    "UPDATE sys_job_log SET finished_at=?,duration_ms=?,status='SUCCESS' WHERE id=?",
                    LocalDateTime.now(),
                    System.currentTimeMillis() - start,
                    logId);
            jdbc.update(
                    "UPDATE sys_job SET last_run_at=? WHERE id=?",
                    LocalDateTime.now(),
                    job.get("id"));
        } catch (Exception ex) {
            String message =
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            jdbc.update(
                    "UPDATE sys_job_log SET finished_at=?,duration_ms=?,status='FAILED',error_summary=? WHERE id=?",
                    LocalDateTime.now(),
                    System.currentTimeMillis() - start,
                    message.substring(0, Math.min(1900, message.length())),
                    logId);
            throw ex;
        }
    }

    private void cleanupAudits() {
        int loginDays = parameter("audit.login.retention-days", 180);
        int accessDays = parameter("audit.access.retention-days", 90);
        int exceptionDays = parameter("audit.exception.retention-days", 90);
        LocalDateTime now = LocalDateTime.now();
        // 每次最多删除 1000 条，降低大表清理对线上事务和锁等待的影响。
        jdbc.update(
                "DELETE FROM sys_login_audit WHERE occurred_at < ? LIMIT 1000",
                now.minusDays(loginDays));
        jdbc.update(
                "DELETE FROM sys_access_audit WHERE occurred_at < ? LIMIT 1000",
                now.minusDays(accessDays));
        jdbc.update(
                "DELETE FROM sys_exception_log WHERE last_occurred_at < ? LIMIT 1000",
                now.minusDays(exceptionDays));
    }

    private void cleanupOrphanMetadata() {
        // 只标记超过 7 天且没有任何业务关系的文件元数据，给上传失败和补偿流程留出时间。
        jdbc.update(
                "UPDATE file_object f LEFT JOIN business_file_relation r ON r.file_id=f.id SET f.deleted=1 "
                        + "WHERE r.id IS NULL AND f.created_at < DATE_SUB(NOW(), INTERVAL 7 DAY) AND f.deleted=0 LIMIT 500");
    }

    private void repairTimelineLinks() {
        jdbc.update(
                "UPDATE gold_daily_timeline t JOIN note n ON n.source_type='TIMELINE' AND n.source_id=t.id AND n.deleted=0 "
                        + "SET t.summary_note_id=n.id WHERE t.deleted=0 AND (t.summary_note_id IS NULL OR t.summary_note_id<>n.id)");
    }

    private int parameter(String key, int fallback) {
        try {
            return Integer.parseInt(
                    jdbc.queryForObject(
                            "SELECT param_value FROM sys_parameter WHERE param_key=? AND deleted=0",
                            String.class,
                            key));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
