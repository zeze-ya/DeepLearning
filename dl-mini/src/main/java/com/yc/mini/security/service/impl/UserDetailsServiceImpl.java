package com.yc.mini.security.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yc.common.global.error.Error;
import com.yc.common.global.error.ErrorException;
import com.yc.core.mini.entity.MiniUser;
import com.yc.core.mini.mapper.MiniUserMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 功能描述:SpringSecurity定义的核心接口，用于根据用户名获取用户信息
 * [登录时校验用户信息,认证成功后将认证信息存入SecurityContextHolder上下文,后续操作会跳过过滤器，无需鉴权]
 *
 * @Author: xieyc && 紫色年华
 * @Date: 2020-03-20
 * @Version: 1.0.0
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserDetailsServiceImpl implements UserDetailsService {

    private final MiniUserMapper miniUserMapper;

    @Autowired
    public UserDetailsServiceImpl(MiniUserMapper miniUserMapper) {
        this.miniUserMapper = miniUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginName) {
        if (StringUtils.isEmpty(loginName)) {
            throw new ErrorException(Error.LoginNameIsNull);
        }
        MiniUser miniUser = miniUserMapper.selectOne(Wrappers.<MiniUser>lambdaQuery()
                .eq(MiniUser::getLoginName, loginName)
        );
        if (ObjectUtil.isNull(miniUser)) {
            throw new ErrorException(Error.UserNotFound);
        } else {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority(miniUser.getMiniUserId()));
            return new User(miniUser.getLoginName(), miniUser.getLoginPwd(), authorities);
        }
    }
}
