package com.tradn.security;

import com.tradn.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser)) {
            throw new BizException(401, "登录状态已失效");
        }
        return ((SecurityUser) auth.getPrincipal()).getUserId();
    }

    public static Long userIdOrZero() {
        try {
            return userId();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static SecurityUser current() {
        userId();
        return (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
