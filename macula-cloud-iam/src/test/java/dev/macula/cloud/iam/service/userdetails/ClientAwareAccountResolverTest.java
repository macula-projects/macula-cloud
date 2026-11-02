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
package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.pojo.entity.SysUserIdentityBinding;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Client 用户类型边界内的唯一标识与外部绑定解析。
 *
 * @author Rain
 * @since 6.1.0
 */
class ClientAwareAccountResolverTest {
    private final SysUserService users = mock(SysUserService.class);
    private final SysUserIdentityBindingMapper bindings = mock(SysUserIdentityBindingMapper.class);
    private final ClientAwareAccountResolver resolver = new ClientAwareAccountResolver(users, bindings,
        new UserIdentifierNormalizer());

    @Test
    void deduplicatesTheSameUserAcrossIdentifierFields() {
        SysUser account = user(7L, "sales@example.com");
        when(users.findUniqueByUsername("sales@example.com")).thenReturn(account);
        when(users.findUniqueByEmail("sales@example.com")).thenReturn(account);

        assertThat(resolver.resolveIdentifier(UserTypeEnum.EMPLOYEE, " sales@example.com ").getId())
            .isEqualTo(7L);
    }

    @Test
    void rejectsIdentifierThatMatchesDifferentUsers() {
        when(users.findUniqueByUsername("13800000000")).thenReturn(user(7L, "13800000000"));
        when(users.findUniqueByMobile("13800000000")).thenReturn(user(8L, "alice"));

        assertThatThrownBy(() -> resolver.resolveIdentifier(UserTypeEnum.EMPLOYEE, "13800000000"))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void resolvesOnlyTheExactProviderSubjectBinding() {
        SysUserIdentityBinding binding = new SysUserIdentityBinding();
        binding.setProviderType("WECOM");
        binding.setProviderSubject("member-id");
        binding.setUsername("alice");
        when(bindings.selectList(any())).thenReturn(List.of(binding));
        when(users.findUniqueByUsername("alice")).thenReturn(user(9L, "alice"));

        assertThat(resolver.findByExternalIdentity(UserTypeEnum.EMPLOYEE, "WECOM", "member-id"))
            .get().extracting(SysUser::getUsername).isEqualTo("alice");
        verify(users).findUniqueByUsername("alice");
    }

    @Test
    void rejectsBindingThatReferencesMissingLocalAccount() {
        SysUserIdentityBinding binding = new SysUserIdentityBinding();
        binding.setProviderType("WECOM");
        binding.setProviderSubject("orphan-member-id");
        binding.setUsername("deleted-user");
        when(bindings.selectList(any())).thenReturn(List.of(binding));

        assertThatThrownBy(() -> resolver.findByExternalIdentity(UserTypeEnum.EMPLOYEE, "WECOM",
            "orphan-member-id")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsUnsupportedUserTypeWithoutGlobalFallback() {
        assertThatThrownBy(() -> resolver.findByMobile(UserTypeEnum.MEMBER, "13800000000"))
            .isInstanceOf(BadCredentialsException.class);
    }

    private SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
