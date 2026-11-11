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
package dev.macula.cloud.iam.authentication.thirdparty.directory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ProviderIntegrationProperties;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.IdentityDirectoryPolicy;
import dev.macula.cloud.system.enums.IdentitySourceFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 目录权限、资料故障和企业准入互不混淆；已有绑定同样需要原来源版本。
 *
 * @author Rain
 * @since 6.1.0
 */
class ProviderDirectoryPolicyServiceTest {
    private final IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
    private final IdentityProviderConfigService.ProviderConfig source = mock(
            IdentityProviderConfigService.ProviderConfig.class);
    private final ProviderDirectoryAdapter adapter = mock(ProviderDirectoryAdapter.class);
    private final ProviderMemberSnapshotStore snapshots = mock(ProviderMemberSnapshotStore.class);
    private final ProviderDirectoryRefreshService refresh = mock(ProviderDirectoryRefreshService.class);
    private final VerifiedExternalIdentity identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "member")
            .identity();
    private ProviderDirectoryPolicyService policy;

    /**
     * 每次使用明确企业与原版本，不依赖用户请求中的corpId。
     */
    @BeforeEach
    void setup() {
        when(configs.requireEnabledById(identity.registrationId(), "WECOM")).thenReturn(source);
        when(source.forCapability(identity.loginCapability())).thenReturn(source);
        when(source.version()).thenReturn(identity.sourceVersion());
        when(source.flow()).thenReturn(IdentitySourceFlow.WECOM_SDK_V1);
        when(source.organizationId()).thenReturn("fixture-corp");
        when(source.providerType()).thenReturn("WECOM");
        when(adapter.providerType()).thenReturn("WECOM");
        policy = new ProviderDirectoryPolicyService(configs, List.of(adapter), snapshots, refresh,
                new ProviderIntegrationProperties());
    }

    /**
     * 默认身份模式不增加目录权限或后台任务。
     */
    @Test
    void identityOnlyDoesNotQueryDirectory() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.IDENTITY_ONLY);
        policy.check(identity);
        verifyNoInteractions(adapter, snapshots, refresh);
    }

    /**
     * OPTIONAL仅容忍已确认的资料权限/网络故障，明确非成员和停用仍拒绝。
     */
    @Test
    void optionalDoesNotRelaxMembership() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.OPTIONAL);
        for (var status : List.of(ProviderDirectoryResult.Status.NOT_MEMBER, ProviderDirectoryResult.Status.DISABLED)) {
            when(adapter.lookup(source, identity)).thenReturn(result(status, false));
            assertThrows(AuthenticationServiceException.class, () -> policy.check(identity));
        }
        when(adapter.lookup(source, identity)).thenReturn(ProviderDirectoryResult.unavailable(false));
        assertThrows(AuthenticationServiceException.class, () -> policy.check(identity));
        when(adapter.lookup(source, identity)).thenReturn(
                result(ProviderDirectoryResult.Status.PERMISSION_DENIED, true));
        policy.check(identity);
        when(adapter.lookup(source, identity)).thenReturn(ProviderDirectoryResult.unavailable(true));
        policy.check(identity);
    }

    /**
     * REQUIRED无有效当前成员证明一律拒绝，不能用缺少权限冒充非成员。
     */
    @Test
    void requiredRejectsMissingPermissionAndWrongOrganization() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.REQUIRED);
        when(adapter.lookup(source, identity)).thenReturn(
                result(ProviderDirectoryResult.Status.PERMISSION_DENIED, true));
        assertThrows(AuthenticationServiceException.class, () -> policy.check(identity));
        when(adapter.lookup(source, identity)).thenReturn(new ProviderDirectoryResult(
                ProviderDirectoryResult.Status.MEMBER, "other-corp", Map.of(), Instant.now(), false));
        assertThrows(AuthenticationServiceException.class, () -> policy.check(identity));
        when(adapter.lookup(source, identity)).thenReturn(result(ProviderDirectoryResult.Status.MEMBER, false));
        assertTrue(policy.check(identity).expiresAt().isAfter(Instant.now()));
    }

    /**
     * 持有事务时不能调用外部目录，即使已有binding ticket也不能绕过。
     */
    @Test
    void rejectsNetworkInsideTransactionAndOldSourceVersion() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.REQUIRED);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(AuthenticationServiceException.class, () -> policy.check(identity));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(adapter, never()).lookup(any(), any());
        when(source.version()).thenReturn(identity.sourceVersion() + 1);
        assertThrows(AuthenticationServiceException.class, () -> policy.check(identity));
    }

    /**
     * 未绑定身份不入队，只有账号解析/事务提交成功后才显式调bound。
     */
    @Test
    void backgroundEnqueuesOnlyKnownBoundMember() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.BACKGROUND);
        policy.check(identity);
        verifyNoInteractions(refresh);
        policy.bound(identity);
        verify(refresh).enqueueBound(identity);
    }

    /**
     * 实时已验证资料可补充自动绑定凭据；缓存写失败不能推翻本次成员证明。
     */
    @Test
    void liveMemberProfileEnrichesOnlyTheVerifiedIdentity() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.REQUIRED);
        when(adapter.lookup(source, identity)).thenReturn(new ProviderDirectoryResult(
                ProviderDirectoryResult.Status.MEMBER, "fixture-corp", Map.of("employee_no", "staff-42"), Instant.now(),
                false));
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture outage")).when(snapshots).save(any(), any());
        var admission = policy.check(identity);
        var enriched = policy.enrich(identity, admission);
        org.junit.jupiter.api.Assertions.assertEquals("staff-42", enriched.attributes().get("employee_no"));
        org.junit.jupiter.api.Assertions.assertEquals(identity.identityValue(), enriched.identityValue());
        org.junit.jupiter.api.Assertions.assertEquals(identity.registrationId(), enriched.registrationId());
        org.junit.jupiter.api.Assertions.assertEquals(identity.sourceVersion(), enriched.sourceVersion());
        var other = IdentitySourceTestFixtures.sdkIdentity("WECOM", "other-member").identity();
        assertThrows(AuthenticationServiceException.class, () -> policy.enrich(other, admission));
    }

    /**
     * 后台旧资料只用于准入拒绝，不能变成实时自动绑定凭据。
     */
    @Test
    void backgroundSnapshotDoesNotSupplyAutoBindingCredentials() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.BACKGROUND);
        when(snapshots.find(identity)).thenReturn(new ProviderDirectoryResult(
                ProviderDirectoryResult.Status.MEMBER, "fixture-corp", Map.of("employee_no", "old-staff"),
                Instant.now(), false));
        var admission = policy.check(identity);
        assertTrue(admission.profile().isEmpty());
        org.junit.jupiter.api.Assertions.assertSame(identity, policy.enrich(identity, admission));
        verifyNoInteractions(adapter);
    }

    /**
     * 仅用于测试的明确目录响应，不模拟真实平台认证。
     */
    private ProviderDirectoryResult result(ProviderDirectoryResult.Status status, boolean tolerable) {
        return new ProviderDirectoryResult(status, "fixture-corp", Map.of(), Instant.now(), tolerable);
    }

    /**
     * 用户明确仅成员ID时，AUTO使用固定企业的基础授权证明，不强加敏感资料/通讯录权限。
     */
    @Test
    void wecomAutoUsesVerifiedMemberWithoutRequiringDirectoryPermissions() {
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.AUTO);
        when(source.require("corpId")).thenReturn("fixture-corp");
        assertEquals("member", policy.check(identity).profile().get("member_id"));
        verifyNoInteractions(adapter, snapshots, refresh);
    }

}
