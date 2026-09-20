package com.tradn.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tradn.common.exception.BizException;
import com.tradn.security.SecurityUtils;
import com.tradn.system.model.SystemJobCommand;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.springframework.dao.DuplicateKeyException;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerService {
    /** 任务编码必须在代码中注册，数据库配置不得执行任意类名或 SQL。 */
    private static final Set<String> REGISTERED_JOB_CODES =
            new HashSet<String>(
                    Arrays.asList(
                            "AUDIT_LOG_CLEANUP",
                            "ORPHAN_FILE_CLEANUP",
                            "TIMELINE_SUMMARY_REPAIR"));

    private final Scheduler scheduler;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public SchedulerService(Scheduler scheduler, JdbcTemplate jdbc, AuditService audit) {
        this.scheduler = scheduler;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        // 应用重启后只恢复数据库中明确启用的任务，单个任务异常不阻断整个应用启动。
        List<Map<String, Object>> jobs =
                jdbc.queryForList("SELECT * FROM sys_job WHERE deleted=0 AND status='ENABLED'");
        for (Map<String, Object> job : jobs)
            try {
                schedule(job);
            } catch (Exception ignored) {
            }
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT * FROM sys_job WHERE deleted=0 ORDER BY id");
    }

    public List<Map<String, Object>> logs(long id) {
        return jdbc.queryForList(
                "SELECT * FROM sys_job_log WHERE job_id=? ORDER BY started_at DESC LIMIT 100", id);
    }

    /** 新增已注册的系统调度任务。 */
    @Transactional
    public long create(SystemJobCommand command) {
        validate(command);
        long id = IdWorker.getId();
        String status = status(command.getStatus());
        try {
            jdbc.update(
                    "INSERT INTO sys_job(id,job_code,job_name,cron_expression,timezone,status,description,created_by,updated_by) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)",
                    id,
                    command.getJobCode().trim().toUpperCase(),
                    command.getJobName().trim(),
                    command.getCronExpression().trim(),
                    timezone(command.getTimezone()),
                    status,
                    blank(command.getDescription()),
                    SecurityUtils.userId(),
                    SecurityUtils.userId());
        } catch (DuplicateKeyException ex) {
            throw new BizException("任务编码已存在");
        }
        if ("ENABLED".equals(status)) {
            schedule(job(id));
        }
        audit.operation("SCHEDULER_CREATE", "SYS_JOB", String.valueOf(id), null);
        return id;
    }

    /** 修改调度任务的完整配置，并同步刷新 Quartz 任务。 */
    @Transactional
    public void update(long id, SystemJobCommand command) {
        job(id);
        validate(command);
        String status = status(command.getStatus());
        try {
            jdbc.update(
                    "UPDATE sys_job SET job_code=?,job_name=?,cron_expression=?,timezone=?,status=?,description=?,"
                            + "updated_at=NOW(3),updated_by=? WHERE id=? AND deleted=0",
                    command.getJobCode().trim().toUpperCase(),
                    command.getJobName().trim(),
                    command.getCronExpression().trim(),
                    timezone(command.getTimezone()),
                    status,
                    blank(command.getDescription()),
                    SecurityUtils.userId(),
                    id);
        } catch (DuplicateKeyException ex) {
            throw new BizException("任务编码已存在");
        }
        if ("ENABLED".equals(status)) {
            schedule(job(id));
        } else {
            deleteQuartzJob(id);
        }
        audit.operation("SCHEDULER_UPDATE", "SYS_JOB", String.valueOf(id), null);
    }

    /** 删除调度任务及其 Quartz 计划，历史执行日志继续保留。 */
    @Transactional
    public void delete(long id) {
        job(id);
        deleteQuartzJob(id);
        jdbc.update(
                "UPDATE sys_job SET job_code=CONCAT('DELETED_',id),deleted=1,updated_at=NOW(3),updated_by=? WHERE id=?",
                SecurityUtils.userId(),
                id);
        audit.operation("SCHEDULER_DELETE", "SYS_JOB", String.valueOf(id), null);
    }

    @Transactional
    public void update(long id, String cron, String timezone) {
        if (!CronExpression.isValidExpression(cron)) throw new BizException("Cron 表达式无效");
        jdbc.update(
                "UPDATE sys_job SET cron_expression=?,timezone=?,updated_at=NOW(3) WHERE id=? AND deleted=0",
                cron,
                timezone,
                id);
        Map<String, Object> job = job(id);
        if ("ENABLED".equals(job.get("status"))) schedule(job);
        audit.operation("SCHEDULER_UPDATE", "SYS_JOB", String.valueOf(id), "cron updated");
    }

    public void resume(long id) {
        jdbc.update("UPDATE sys_job SET status='ENABLED' WHERE id=?", id);
        schedule(job(id));
        audit.operation("SCHEDULER_RESUME", "SYS_JOB", String.valueOf(id), null);
    }

    public void pause(long id) {
        jdbc.update("UPDATE sys_job SET status='PAUSED' WHERE id=?", id);
        deleteQuartzJob(id);
        audit.operation("SCHEDULER_PAUSE", "SYS_JOB", String.valueOf(id), null);
    }

    public void trigger(long id) {
        Map<String, Object> row = job(id);
        JobDataMap data = new JobDataMap();
        data.put("jobCode", row.get("job_code"));
        data.put("triggerType", "MANUAL");
        try {
            scheduler.scheduleJob(
                    JobBuilder.newJob(ManagedQuartzJob.class)
                            .withIdentity("manual-" + System.nanoTime(), "tradn-manual")
                            .usingJobData(data)
                            .build(),
                    TriggerBuilder.newTrigger().startNow().build());
        } catch (Exception ex) {
            throw new BizException("任务触发失败：" + ex.getMessage());
        }
        audit.operation("SCHEDULER_TRIGGER", "SYS_JOB", String.valueOf(id), null);
    }

    private Map<String, Object> job(long id) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM sys_job WHERE id=? AND deleted=0", id);
        if (rows.isEmpty()) throw new BizException("任务不存在");
        return rows.get(0);
    }

    private JobKey key(long id) {
        return new JobKey("job-" + id, "tradn");
    }

    private void schedule(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        String code = String.valueOf(row.get("job_code"));
        // 数据库只能选择参数，真正可执行的任务必须在代码白名单内，禁止动态执行 SQL 或类名。
        if (!REGISTERED_JOB_CODES.contains(code)) throw new BizException("任务未注册");
        JobDataMap data = new JobDataMap();
        data.put("jobCode", code);
        data.put("triggerType", "SCHEDULED");
        JobDetail detail =
                JobBuilder.newJob(ManagedQuartzJob.class)
                        .withIdentity(key(id))
                        .usingJobData(data)
                        .storeDurably()
                        .build();
        // 错过触发时间时不补跑，避免服务恢复后集中执行维护任务造成负载尖峰。
        CronScheduleBuilder schedule =
                CronScheduleBuilder.cronSchedule(String.valueOf(row.get("cron_expression")))
                        .inTimeZone(TimeZone.getTimeZone(String.valueOf(row.get("timezone"))))
                        .withMisfireHandlingInstructionDoNothing();
        CronTrigger trigger =
                TriggerBuilder.newTrigger()
                        .withIdentity("trigger-" + id, "tradn")
                        .forJob(detail)
                        .withSchedule(schedule)
                        .build();
        try {
            scheduler.scheduleJob(detail, java.util.Collections.singleton(trigger), true);
        } catch (Exception ex) {
            throw new BizException("调度配置失败：" + ex.getMessage());
        }
    }

    private void validate(SystemJobCommand command) {
        String code = command.getJobCode() == null ? "" : command.getJobCode().trim().toUpperCase();
        if (!REGISTERED_JOB_CODES.contains(code)) {
            throw new BizException("任务编码未在服务端注册");
        }
        if (command.getJobName() == null || command.getJobName().trim().isEmpty()) {
            throw new BizException("任务名称不能为空");
        }
        if (command.getCronExpression() == null
                || !CronExpression.isValidExpression(command.getCronExpression().trim())) {
            throw new BizException("Cron 表达式无效");
        }
        status(command.getStatus());
    }

    private String status(String status) {
        String value = status == null ? "PAUSED" : status.trim().toUpperCase();
        if (!("ENABLED".equals(value) || "PAUSED".equals(value))) {
            throw new BizException("任务状态不正确");
        }
        return value;
    }

    private String timezone(String timezone) {
        return timezone == null || timezone.trim().isEmpty() ? "Asia/Shanghai" : timezone.trim();
    }

    private String blank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void deleteQuartzJob(long id) {
        try {
            scheduler.deleteJob(key(id));
        } catch (Exception ex) {
            throw new BizException("删除调度计划失败：" + ex.getMessage());
        }
    }
}
