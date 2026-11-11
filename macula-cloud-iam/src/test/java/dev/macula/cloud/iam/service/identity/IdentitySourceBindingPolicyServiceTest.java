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
package dev.macula.cloud.iam.service.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.system.enums.IdentityBindingRule;
import dev.macula.cloud.system.enums.IdentitySourceType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证所有来源统一遵循开关、匹配规则和已有绑定，企业成员无默认旁路。
 *
 * @author Rain
 * @since 6.1.0
 */
class IdentitySourceBindingPolicyServiceTest {
    private final RegistrationIdentityBindingService bindings = mock(RegistrationIdentityBindingService.class);
    private final IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final IdentitySourceBindingPolicyService policy = new IdentitySourceBindingPolicyService(bindings, configs,
            users, dev.macula.cloud.iam.authentication.thirdparty.BindingTestTransactions.directory());
    private final VerifiedExternalIdentity identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "alice")
            .identity();

    /**
     * 关闭开关不影响已有有效绑定，用户名变化也无需重新匹配。
     */
    @Test
    void existingBindingWinsWithoutAutomaticMatching() {
        var principal = User.withUsername("renamed").password("unused").authorities("USER").build();
        when(bindings.findBound("app", 1L, UserTypeEnum.EMPLOYEE, identity)).thenReturn(Optional.of(principal));
        assertSame(principal, policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, identity).orElseThrow());
        verifyNoInteractions(configs, users);
    }

    /**
     * 企微同名本地账号在开关关闭时不能触发任何匹配查询。
     */
    @Test
    void enterpriseDisabledDoesNotQueryLocalUsername() {
        source(false);
        assertTrue(policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, identity).isEmpty());
        verifyNoInteractions(users);
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(),
                org.mockito.ArgumentMatchers.isNull());
    }

    /**
     * 明确开启且唯一候选才请求原子建立绑定。
     */
    @Test
    void uniqueVerifiedAttributeCreatesBinding() {
        source(true);
        SysUser user = new SysUser();
        user.setId(7L);
        when(users.findIdentityBindingCandidates(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(user));
        var principal = User.withUsername("alice").password("unused").authorities("USER").build();
        when(bindings.bind("app", 1L, UserTypeEnum.EMPLOYEE, identity, 7L, true, null)).thenReturn(principal);
        assertSame(principal, policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, identity).orElseThrow());
    }

    /**
     * 零候选和多候选转手动证明，不取第一条。
     */
    @Test
    void absentOrAmbiguousCandidatesRequireManualBinding() {
        source(true);
        when(users.findIdentityBindingCandidates(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(),
                List.of(new SysUser(), new SysUser()));
        assertTrue(policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, identity).isEmpty());
        assertTrue(policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, identity).isEmpty());
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(),
                org.mockito.ArgumentMatchers.isNull());
    }

    /**
     * 缺少本次已核验属性时不尝试展示属性或其他字段。
     */
    @Test
    void missingAttributeRequiresManualBinding() {
        source(true);
        var missing = new VerifiedExternalIdentity(1L, "WECOM", "WECOM_SDK", 0L,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow("WECOM_SDK"),
                "WECOM_APP_CODE", "MEMBER_ID", "id", Map.of());
        assertTrue(policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, missing).isEmpty());
        verifyNoInteractions(users);
    }

    /**
     * 禁用、越权或冲突属于拒绝，不转手动页面绕过。
     */
    @Test
    void invalidCandidateDenialPropagates() {
        source(true);
        SysUser user = new SysUser();
        user.setId(7L);
        when(users.findIdentityBindingCandidates(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(user));
        when(bindings.bind(any(), any(), any(), any(), any(), anyBoolean(),
                org.mockito.ArgumentMatchers.isNull())).thenThrow(new BadCredentialsException("disabled"));
        assertThrows(BadCredentialsException.class, () -> policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, identity));
    }

    /**
     * 构造明确来源策略，无厂商默认开启。
     */
    private void source(boolean enabled) {
        when(configs.requireEnabledById(1L, "WECOM")).thenReturn(new IdentityProviderConfigService.ProviderConfig(
                1L, IdentitySourceType.WECOM_SDK, enabled, enabled ? IdentityBindingRule.MEMBER_ID_TO_USERNAME : null,
                0L, "WECOM", "corp", "secret", Map.of(), Map.of(), true,
                dev.macula.cloud.system.enums.IdentitySourceFlow.WECOM_SDK_V1,
                dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY, null, null));
    }
}
