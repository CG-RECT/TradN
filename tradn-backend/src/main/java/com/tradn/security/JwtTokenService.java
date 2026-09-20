package com.tradn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final String SESSION_PREFIX = "tradn:prod:session:";
    private static final String USER_SESSION_PREFIX = "tradn:prod:user-session:";
    private final StringRedisTemplate redis;
    private final SecretKey key;
    private final long ttlSeconds;

    public JwtTokenService(
            StringRedisTemplate redis,
            @Value("${tradn.jwt.secret}") String secret,
            @Value("${tradn.jwt.ttl-seconds}") long ttlSeconds) {
        this.redis = redis;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(Long userId) {
        // JWT 负责签名身份，Redis 会话键负责即时撤销；两者同时有效才视为登录态。
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue()
                .set(
                        SESSION_PREFIX + sessionId,
                        String.valueOf(userId),
                        ttlSeconds,
                        TimeUnit.SECONDS);
        redis.opsForSet().add(USER_SESSION_PREFIX + userId, sessionId);
        redis.expire(USER_SESSION_PREFIX + userId, ttlSeconds, TimeUnit.SECONDS);
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(sessionId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    public Session parseActive(String token) {
        Claims claims =
                Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        String userId = redis.opsForValue().get(SESSION_PREFIX + claims.getId());
        if (userId == null || !userId.equals(claims.getSubject())) return null;
        return new Session(Long.valueOf(userId), claims.getId());
    }

    public void revoke(String sessionId, Long userId) {
        redis.delete(SESSION_PREFIX + sessionId);
        redis.opsForSet().remove(USER_SESSION_PREFIX + userId, sessionId);
    }

    public void revokeAll(Long userId) {
        // 用户状态、角色或密码变化时删除其全部会话，实现强制下线和权限即时收敛。
        Set<String> sessions = redis.opsForSet().members(USER_SESSION_PREFIX + userId);
        if (sessions != null)
            for (String session : sessions) redis.delete(SESSION_PREFIX + session);
        redis.delete(USER_SESSION_PREFIX + userId);
    }

    public static class Session {
        public final Long userId;
        public final String sessionId;

        public Session(Long userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
        }
    }
}
