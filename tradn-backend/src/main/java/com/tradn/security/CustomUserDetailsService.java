package com.tradn.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradn.system.mapper.SystemUserMapper;
import com.tradn.system.model.SystemUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final SystemUserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SystemUser user =
                userMapper.selectOne(
                        new LambdaQueryWrapper<SystemUser>().eq(SystemUser::getUsername, username));
        if (user == null) throw new UsernameNotFoundException("用户不存在");
        List<String> permissions = userMapper.selectPermissions(user.getId());
        return new SecurityUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getNickname(),
                "ENABLED".equals(user.getStatus()),
                permissions);
    }

    public SecurityUser loadById(Long userId) {
        SystemUser user = userMapper.selectById(userId);
        if (user == null) throw new UsernameNotFoundException("用户不存在");
        return new SecurityUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getNickname(),
                "ENABLED".equals(user.getStatus()),
                userMapper.selectPermissions(user.getId()));
    }
}
