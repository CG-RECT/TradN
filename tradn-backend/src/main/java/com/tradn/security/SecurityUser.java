package com.tradn.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
/** Spring Security 使用的当前登录账号快照。 */
public class SecurityUser implements UserDetails {
    /** 系统账号 ID。 */
    private final Long userId;

    /** 登录用户名。 */
    private final String username;

    /** BCrypt 密码摘要，仅供登录校验使用。 */
    private final String password;

    /** 页面展示昵称。 */
    private final String nickname;

    /** 账号是否允许登录。 */
    private final boolean enabled;

    /** 账号拥有的权限标识集合。 */
    private final List<String> permissions;

    public SecurityUser(
            Long userId,
            String username,
            String password,
            String nickname,
            boolean enabled,
            List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.enabled = enabled;
        this.permissions = permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
