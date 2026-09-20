package com.tradn.security;

import com.tradn.common.exception.BizException;
import com.tradn.system.mapper.SystemUserMapper;
import com.tradn.system.service.AuditService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final StringRedisTemplate redis;
    private final SystemUserMapper userMapper;
    private final AuditService auditService;

    public Map<String, Object> login(String username, String password) {
        // 登录失败计数放在 Redis 并设置短 TTL，达到阈值后临时限流，不锁死数据库账号。
        String limitKey = "tradn:prod:login-limit:" + username.toLowerCase();
        String attempts = redis.opsForValue().get(limitKey);
        if (attempts != null && Integer.parseInt(attempts) >= 8) {
            auditService.login(null, username, false, "RATE_LIMITED", null);
            throw new BizException(429, "登录尝试过多，请稍后再试");
        }
        try {
            SecurityUser user = (SecurityUser) userDetailsService.loadUserByUsername(username);
            if (!user.isEnabled() || !passwordEncoder.matches(password, user.getPassword()))
                throw new BizException(401, "用户名或密码错误");
            String token = tokenService.issue(user.getUserId());
            userMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                                    com.tradn.system.model.SystemUser>()
                            .eq(com.tradn.system.model.SystemUser::getId, user.getUserId())
                            .set(
                                    com.tradn.system.model.SystemUser::getLastLoginAt,
                                    LocalDateTime.now()));
            redis.delete(limitKey);
            // 审计只记录不可逆的短摘要用于关联会话，绝不落库原始 JWT。
            String digest =
                    DigestUtils.sha256Hex(token.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
            auditService.login(user.getUserId(), username, true, "SUCCESS", digest);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("token", token);
            result.put("expiresIn", 28800);
            result.put("profile", profile(user));
            return result;
        } catch (BizException ex) {
            increment(limitKey);
            auditService.login(null, username, false, "BAD_CREDENTIALS", null);
            throw ex;
        } catch (Exception ex) {
            increment(limitKey);
            auditService.login(null, username, false, "BAD_CREDENTIALS", null);
            throw new BizException(401, "用户名或密码错误");
        }
    }

    public void logout() {
        Object credentials =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getCredentials();
        tokenService.revoke(String.valueOf(credentials), SecurityUtils.userId());
    }

    public Map<String, Object> profile(SecurityUser user) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", user.getUserId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("permissions", user.getPermissions());
        return result;
    }

    private void increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) redis.expire(key, 15, TimeUnit.MINUTES);
    }
}
