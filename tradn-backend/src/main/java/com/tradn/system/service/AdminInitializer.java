package com.tradn.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradn.system.mapper.SystemUserMapper;
import com.tradn.system.model.SystemUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final SystemUserMapper mapper;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${tradn.admin.username}")
    private String username;

    @Value("${tradn.admin.initial-password}")
    private String initialPassword;

    public AdminInitializer(SystemUserMapper mapper, PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mapper.selectCount(
                        new LambdaQueryWrapper<SystemUser>().eq(SystemUser::getUsername, username))
                > 0) return;
        if (initialPassword == null || initialPassword.length() < 10) {
            log.warn(
                    "Admin account was not initialized: ADMIN_INITIAL_PASSWORD must contain at least 10 characters");
            return;
        }
        SystemUser user = new SystemUser();
        user.setUsername(username);
        user.setNickname("管理员");
        user.setPasswordHash(encoder.encode(initialPassword));
        user.setStatus("ENABLED");
        mapper.insert(user);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)", user.getId());
        log.info("Initial administrator account created: {}", username);
    }
}
