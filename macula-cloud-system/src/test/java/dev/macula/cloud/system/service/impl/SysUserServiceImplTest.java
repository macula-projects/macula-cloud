/*
 * Copyright (c) 2026 Macula
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
package dev.macula.cloud.system.service.impl;

import dev.macula.cloud.system.converter.UserConverter;
import dev.macula.cloud.system.form.UserForm;
import dev.macula.cloud.system.listener.UserImportListener;
import dev.macula.cloud.system.mapper.SysUserMapper;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.SysRoleService;
import dev.macula.cloud.system.service.SysUserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证用户写入口统一规范化并拒绝非空登录标识重复。
 *
 * @author Rain
 * @since 6.1.0
 */
class SysUserServiceImplTest {
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SysUserRoleService userRoleService = mock(SysUserRoleService.class);
    private final UserImportListener userImportListener = mock(UserImportListener.class);
    private final UserConverter userConverter = mock(UserConverter.class);
    private final SysMenuService menuService = mock(SysMenuService.class);
    private final SysRoleService roleService = mock(SysRoleService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final SysUserMapper mapper = mock(SysUserMapper.class);
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysUserServiceImpl(passwordEncoder, userRoleService, userImportListener, userConverter,
            menuService, roleService, redis, new UserIdentifierNormalizer());
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void normalizesAllIdentifiersBeforeInsert() {
        UserForm form = form(" salesperson ", " 138-0000-0000 ", " USER@Example.COM ");
        when(mapper.selectCount(any())).thenReturn(0L);
        when(userConverter.form2Entity(form)).thenReturn(new SysUser());
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(mapper.insert(any(SysUser.class))).thenReturn(1);

        assertThat(service.saveUser(form)).isTrue();

        ArgumentCaptor<SysUser> inserted = ArgumentCaptor.forClass(SysUser.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getUsername()).isEqualTo("salesperson");
        assertThat(inserted.getValue().getMobile()).isEqualTo("13800000000");
        assertThat(inserted.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(inserted.getValue().getPassword()).isEqualTo("encoded");
    }

    @Test
    void turnsBlankOptionalIdentifiersIntoNull() {
        UserForm form = form("salesperson", "  ", "");
        when(mapper.selectCount(any())).thenReturn(0L);
        when(userConverter.form2Entity(form)).thenReturn(new SysUser());
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(mapper.insert(any(SysUser.class))).thenReturn(1);

        assertThat(service.saveUser(form)).isTrue();

        ArgumentCaptor<SysUser> inserted = ArgumentCaptor.forClass(SysUser.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getMobile()).isNull();
        assertThat(inserted.getValue().getEmail()).isNull();
    }

    @Test
    void rejectsDuplicateMobileBeforeInsert() {
        UserForm form = form("salesperson", "13800000000", null);
        when(mapper.selectCount(any())).thenReturn(0L, 1L);

        assertThatThrownBy(() -> service.saveUser(form))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("手机号码已存在");
        verify(mapper, never()).insert(any(SysUser.class));
    }

    private UserForm form(String username, String mobile, String email) {
        UserForm form = new UserForm();
        form.setUsername(username);
        form.setNickname("Salesperson");
        form.setMobile(mobile);
        form.setEmail(email);
        form.setPassword("password");
        form.setRoleIds(List.of());
        return form;
    }
}
