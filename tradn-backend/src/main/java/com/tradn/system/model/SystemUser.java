package com.tradn.system.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tradn.common.model.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
/** 系统登录账号实体。 */
public class SystemUser extends BaseEntity {
    /** 唯一登录用户名。 */
    private String username;

    /** BCrypt 加密后的密码摘要，禁止保存或返回明文密码。 */
    private String passwordHash;

    /** 页面展示昵称。 */
    private String nickname;

    /** 账号状态，如 ENABLED 或 DISABLED。 */
    private String status;

    /** 最近一次成功登录时间。 */
    private LocalDateTime lastLoginAt;
}
