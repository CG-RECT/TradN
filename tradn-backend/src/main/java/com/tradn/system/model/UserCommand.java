package com.tradn.system.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
/** 新增或修改系统账号的请求参数。 */
public class UserCommand {
    /** 登录用户名。 */
    private String username;

    /** 页面展示昵称。 */
    private String nickname;

    /** 新建账号时的初始密码。 */
    private String password;

    /** 账号状态：ENABLED 或 DISABLED。 */
    private String status;

    /** 账号关联的角色主键列表。 */
    private List<Long> roleIds = new ArrayList<Long>();
}
