package com.tradn.security;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtTokenService.Session session = tokenService.parseActive(header.substring(7));
                if (session != null) {
                    // 权限从数据库重新加载，Redis 中的会话只证明令牌仍有效，不作为权限事实来源。
                    SecurityUser user = userDetailsService.loadById(session.userId);
                    // 已禁用账号即使还有未过期令牌，也必须重新通过登录认证。
                    if (user.isEnabled()) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        user, session.sessionId, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {
                // 无效或已撤销令牌按未认证处理，具体 401 由 Spring Security 统一返回。
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
