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

import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.macula.cloud.system.config.PlatformOAuthClientProperties;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import dev.macula.cloud.system.service.IamApplicationService;
import dev.macula.cloud.system.vo.IamApplicationVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 受信平台认领只编排中立聚合保存，不接触Secret编码或重置入口。
 * @author Rain
 * @since 6.1.0
 */
class PlatformOAuthClientProvisioningServiceTest {
    /** 已认领且配置一致的启动不调用保存，不增版本或撤销已有会话。 */
    @Test
    void alreadyClaimedIdentityIsIdempotent() {
        var fixture = new Fixture(true);
        fixture.service.initialize(fixture.properties);
        verify(fixture.applications, never()).save(any(), any());
        verify(fixture.mapper, never()).updateById(any(SysIamApplication.class));
        verify(fixture.applications, never()).resetSecret(any(), any(), any());
    }

    /** 新认领保留公开ID，仅明确清除Gateway业务授予；没有Secret值进入编排表单。 */
    @Test
    void claimRetainsIdentityAndDelegatesSecretPreservation() {
        var fixture = new Fixture(false);
        fixture.service.initialize(fixture.properties);
        var form = ArgumentCaptor.forClass(IamApplicationForm.class);
        verify(fixture.applications).save(ArgumentMatchers.eq(7L), form.capture());
        assertThat(form.getValue().getClientId()).isEqualTo("fixture-gateway");
        assertThat(form.getValue().getClientSecret()).isNull();
        assertThat(form.getValue().getPurpose()).isEqualTo("RESOURCE_SERVER");
        assertThat(form.getValue().getSettings().getAuthorizationGrantTypes()).isEmpty();
        assertThat(form.getValue().getProtocol()).isNull();
        assertThat(form.getValue().getConfigurationVersion()).isEqualTo("updated-version");
        verify(fixture.applications, never()).resetSecret(any(), any(), any());
    }

    /** 不把端内Admin或重复声明ID自动修正成其他身份。 */
    @Test
    void mismatchedModeAndIdentityRejectBeforeWriting() {
        var fixture = new Fixture(true);
        fixture.admin.setWebLoginMode("IN_APP");
        assertThrows(IllegalStateException.class, () -> fixture.service.initialize(fixture.properties));
        verify(fixture.applications, never()).save(any(), any());
        var collision = new Fixture(true);
        collision.properties.setAdminClientId(collision.properties.getGatewayClientId());
        assertThrows(IllegalStateException.class, () -> collision.service.initialize(collision.properties));
        verifyNoInteractions(collision.mapper);
    }

    /**
     * 只模拟公开应用视图与受管标识，不提供任何真实凭据。
     * @since 6.1.0
     */
    private static final class Fixture {
        final SysIamApplicationMapper mapper = mock(SysIamApplicationMapper.class);
        final IamApplicationService applications = mock(IamApplicationService.class);
        final IamApplicationProtocolService protocols = mock(IamApplicationProtocolService.class);
        final IamApplicationWireAdapter wire = mock(IamApplicationWireAdapter.class);
        final PlatformOAuthClientProperties properties = new PlatformOAuthClientProperties();
        final SysIamApplication admin = application(8L, "fixture-admin", "SPA");
        final SysIamApplication gateway = application(7L, "fixture-gateway", "SERVICE");
        final PlatformOAuthClientProvisioningService service = new PlatformOAuthClientProvisioningService(mapper, applications, protocols, wire);

        /** 声明已存在的两身份，原哈希不在公共视图或表单中。 */
        Fixture(boolean claimed) {
            properties.setEnabled(true);
            properties.setAdminClientId(admin.getApplicationCode());
            properties.setGatewayClientId(gateway.getApplicationCode());
            gateway.setPurpose(claimed ? "RESOURCE_SERVER" : "APPLICATION");
            if (claimed) {
                admin.setManagedKey("platform-admin");
                gateway.setManagedKey("platform-gateway");
            }
            admin.setResourcePermissions("{\"profile\":\"urn:macula:iam:profile\",\"system:access\":\"urn:macula:system\"}");
            var adminView = view(admin, "none", "authorization_code");
            adminView.setProtocol("OIDC");
            adminView.getSettings().setScopes("openid,profile,system:access");
            var gatewayView = view(gateway, "client_secret_basic", "");
            gatewayView.setSecretConfigured(true);
            when(mapper.selectOne(any())).thenReturn(gateway, admin);
            when(mapper.updateById(any(SysIamApplication.class))).thenReturn(1);
            when(applications.get(8L)).thenReturn(adminView);
            when(applications.get(7L)).thenReturn(gatewayView);
            when(protocols.state(anyLong(), anyBoolean())).thenReturn(new IamApplicationProtocolService.Stored(null, List.of()));
            when(protocols.version(any(), any())).thenReturn("updated-version");
            var gatewayForm = new IamApplicationForm();
            gatewayForm.setClientId(gateway.getApplicationCode());
            gatewayForm.setClientName("fixture");
            when(wire.form(gatewayView)).thenReturn(gatewayForm);
            when(wire.form(adminView)).thenReturn(new IamApplicationForm());
        }

        /** 仅构造中立主表字段。 */
        private SysIamApplication application(Long id, String code, String type) {
            var app = new SysIamApplication();
            app.setId(id);
            app.setApplicationCode(code);
            app.setApplicationType(type);
            app.setPurpose("APPLICATION");
            app.setTenantId(1L);
            app.setUserType("employee");
            app.setResourcePermissions("{}");
            return app;
        }

        /** 构造不包含任何Secret的协议视图。 */
        private IamApplicationVO view(SysIamApplication app, String auth, String grant) {
            var result = new IamApplicationVO();
            result.setClientId(app.getApplicationCode());
            result.setExternalCodeTypes(Set.of());
            result.setExternalProviderBindings(Map.of());
            var settings = new OAuth2ProtocolSettings();
            settings.setClientAuthenticationMethods(auth);
            settings.setAuthorizationGrantTypes(grant);
            result.setSettings(settings);
            return result;
        }
    }
}
