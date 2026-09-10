package com.tradn.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradn.common.exception.GlobalExceptionHandler;
import com.tradn.security.CustomUserDetailsService;
import com.tradn.security.JwtAuthenticationFilter;
import com.tradn.security.JwtTokenService;
import com.tradn.security.SecurityUser;
import com.tradn.system.service.AuditService;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实安全过滤链验证 401/403 边界，无需启动数据库和 Redis。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SecurityAuthenticationTest.TestConfiguration.class)
@WebAppConfiguration
class SecurityAuthenticationTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService tokens;

    @Autowired
    private CustomUserDetailsService users;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(tokens, users);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void missingTokenReturns401InsteadOf403() throws Exception {
        mvc.perform(get("/v1/auth/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(401))
                .andExpect(jsonPath("message").value("登录已失效，请重新登录"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        when(tokens.parseActive("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));
        mvc.perform(get("/v1/auth/profile").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedRedisSessionReturns401() throws Exception {
        when(tokens.parseActive("revoked")).thenReturn(null);
        mvc.perform(get("/v1/auth/profile").header("Authorization", "Bearer revoked"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        when(tokens.parseActive("invalid")).thenThrow(new IllegalArgumentException("invalid"));
        mvc.perform(get("/v1/auth/profile").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotReuseSession() throws Exception {
        authenticate(false, false);
        mvc.perform(get("/v1/auth/profile").header("Authorization", "Bearer active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutPermissionStillGets403() throws Exception {
        authenticate(true, false);
        mvc.perform(get("/v1/protected").header("Authorization", "Bearer active"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(403));
    }

    @Test
    void authorizedUserCanAccessProtectedEndpoint() throws Exception {
        authenticate(true, true);
        mvc.perform(get("/v1/protected").header("Authorization", "Bearer active"))
                .andExpect(status().isOk());
    }

    @Test
    void loginRemainsAccessibleWithExpiredToken() throws Exception {
        when(tokens.parseActive("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));
        mvc.perform(post("/v1/auth/login").header("Authorization", "Bearer expired"))
                .andExpect(status().isOk());
    }

    private void authenticate(boolean enabled, boolean permitted) {
        when(tokens.parseActive("active")).thenReturn(new JwtTokenService.Session(1L, "session"));
        when(users.loadById(1L)).thenReturn(new SecurityUser(
                1L, "tester", "unused", "测试账号", enabled,
                permitted ? Collections.singletonList("test:read") : Collections.emptyList()));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, TestController.class,
            GlobalExceptionHandler.class})
    static class TestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtTokenService tokens() {
            return mock(JwtTokenService.class);
        }

        @Bean
        CustomUserDetailsService users() {
            return mock(CustomUserDetailsService.class);
        }

        @Bean
        AuditService audit() {
            return mock(AuditService.class);
        }
    }

    /** 只提供测试端点，验证真实过滤器及方法权限的协作行为。 */
    @RestController
    static class TestController {
        @GetMapping("/v1/auth/profile")
        public String profile() {
            return "ok";
        }

        @GetMapping("/v1/protected")
        @PreAuthorize("hasAuthority('test:read')")
        public String protectedResource() {
            return "ok";
        }

        @PostMapping("/v1/auth/login")
        public String login() {
            return "ok";
        }
    }
}
