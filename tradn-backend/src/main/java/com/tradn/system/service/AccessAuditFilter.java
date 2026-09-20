package com.tradn.system.service;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class AccessAuditFilter extends OncePerRequestFilter {
    private final AuditService auditService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();
            if (!path.contains("/actuator/") && !path.contains("/v1/audits/")) {
                try {
                    auditService.access(
                            request.getMethod(),
                            path,
                            response.getStatus(),
                            System.currentTimeMillis() - start);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
