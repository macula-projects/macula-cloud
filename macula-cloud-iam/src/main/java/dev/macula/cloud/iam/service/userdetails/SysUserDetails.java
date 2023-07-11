/*
 * Copyright (c) 2023 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.macula.cloud.iam.service.userdetails;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dev.macula.boot.enums.StatusEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 系统用户认证信息
 *
 * @author <a href="mailto:xianrui0365@163.com">haoxr</a>
 * @since 2021/9/27
 */
@Data
@JsonDeserialize
public class SysUserDetails implements UserDetails {

    /** 扩展字段：部门ID */
    private Long deptId;

    /** 用户角色数据权限集合 */
    private int dataScope;

    /** 默认字段 */
    private String username;
    private String nickname;
    private String password;
    private Boolean enabled;
    private String mobile;
    private String openId;
    private String unionId;
    private Collection<SimpleGrantedAuthority> authorities;

    private boolean accountNonExpired = true;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;

    public SysUserDetails() {
    }

    /**
     * 系统管理用户
     */
    public SysUserDetails(UserAuthInfo user) {
        this.setUsername(user.getUsername());
        this.setNickname(user.getNickname());
        this.setDeptId(user.getDeptId());
        this.setDataScope(user.getDataScope());
        this.setPassword(user.getPassword());
        this.setEnabled(StatusEnum.ENABLE.getValue().equals(user.getStatus()));
        if (CollectionUtil.isNotEmpty(user.getRoles())) {
            authorities = user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
