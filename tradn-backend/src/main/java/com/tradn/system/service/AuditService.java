package com.tradn.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tradn.common.api.RequestIds;
import com.tradn.security.SecurityUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void login(
            Long userId, String username, boolean success, String reason, String sessionDigest) {
        // 登录审计保留必要快照和会话摘要，不记录密码或原始令牌。
        HttpServletRequest request = request();
        jdbc.update(
                "INSERT INTO sys_login_audit(id,user_id,username_snapshot,success,reason_code,ip_address,user_agent,session_digest,occurred_at,request_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                IdWorker.getId(),
                userId,
                safe(username, 64),
                success ? 1 : 0,
                safe(reason, 50),
                ip(request),
                request == null ? null : safe(request.getHeader("User-Agent"), 500),
                safe(sessionDigest, 64),
                LocalDateTime.now(),
                RequestIds.current());
    }

    public void access(String method, String endpoint, int status, long durationMs) {
        jdbc.update(
                "INSERT INTO sys_access_audit(id,user_id,http_method,endpoint,response_status,duration_ms,ip_address,result,occurred_at,request_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                IdWorker.getId(),
                SecurityUtils.userIdOrZero(),
                safe(method, 10),
                safe(endpoint, 300),
                status,
                durationMs,
                ip(request()),
                status < 400 ? "SUCCESS" : "FAILURE",
                LocalDateTime.now(),
                RequestIds.current());
    }

    public void operation(
            String operation, String businessType, String businessId, String summary) {
        // summary 只允许传入脱敏后的业务摘要，调用方不得传入请求体或 Markdown 正文。
        HttpServletRequest request = request();
        jdbc.update(
                "INSERT INTO sys_access_audit(id,user_id,http_method,endpoint,operation_type,business_type,business_id,parameter_summary,response_status,duration_ms,ip_address,result,occurred_at,request_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                IdWorker.getId(),
                SecurityUtils.userIdOrZero(),
                request == null ? "SYSTEM" : request.getMethod(),
                request == null ? "system" : safe(request.getRequestURI(), 300),
                safe(operation, 50),
                safe(businessType, 50),
                safe(businessId, 100),
                safe(summary, 2000),
                200,
                0,
                ip(request),
                "SUCCESS",
                LocalDateTime.now(),
                RequestIds.current());
    }

    public void recordException(Exception ex) {
        try {
            StringWriter writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));
            String stack = safe(writer.toString(), 12000);
            // 用异常类型与截断消息生成指纹，同类异常聚合计数，避免日志表被重复堆满。
            String fingerprint =
                    DigestUtils.sha256Hex(
                            (ex.getClass().getName() + ":" + safe(ex.getMessage(), 500))
                                    .getBytes(StandardCharsets.UTF_8));
            HttpServletRequest request = request();
            jdbc.update(
                    "INSERT INTO sys_exception_log(id,fingerprint,exception_type,message_summary,stack_summary,endpoint,user_id,first_occurred_at,last_occurred_at,request_id) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE last_occurred_at=VALUES(last_occurred_at),occurrence_count=occurrence_count+1,request_id=VALUES(request_id)",
                    IdWorker.getId(),
                    fingerprint,
                    safe(ex.getClass().getName(), 300),
                    safe(ex.getMessage(), 2000),
                    stack,
                    request == null ? null : safe(request.getRequestURI(), 300),
                    SecurityUtils.userIdOrZero(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    RequestIds.current());
        } catch (Exception ignored) {
            // 审计是旁路能力，写审计失败不能覆盖原始业务异常或改变接口结果。
        }
    }

    private HttpServletRequest request() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes))
            return null;
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
    }

    private String ip(HttpServletRequest request) {
        if (request == null) return null;
        String value = request.getHeader("X-Forwarded-For");
        return safe(value == null ? request.getRemoteAddr() : value.split(",")[0].trim(), 64);
    }

    private String safe(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
