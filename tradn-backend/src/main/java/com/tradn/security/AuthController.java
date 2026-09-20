package com.tradn.security;

import com.tradn.common.api.ApiResponse;
import com.tradn.system.mapper.SystemUserMapper;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
/** 提供登录、退出以及当前账号信息查询接口。 */
public class AuthController {
    private final AuthService authService;
    private final SystemUserMapper userMapper;

    /** 校验账号密码并签发登录令牌。 */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username, request.password));
    }

    /** 注销当前会话并使已签发令牌失效。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    /** 查询当前登录账号的基础资料和权限信息。 */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        return ApiResponse.ok(authService.profile(SecurityUtils.current()));
    }

    /** 查询当前账号有权访问的前端菜单树。 */
    @GetMapping("/menus")
    public ApiResponse<List<Map<String, Object>>> menus() {
        return ApiResponse.ok(userMapper.selectMenus(SecurityUtils.userId()));
    }

    @Data
    /** 登录接口请求参数。 */
    public static class LoginRequest {
        /** 登录用户名。 */
        @NotBlank(message = "请输入用户名")
        private String username;

        /** 登录密码，仅用于本次认证，不会持久化明文。 */
        @NotBlank(message = "请输入密码")
        private String password;
    }
}
