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

import dev.macula.cloud.system.config.AdminClientProvisioningProperties;
import dev.macula.cloud.system.config.AdminClientProvisioningRunner;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 初始化边界单元测试；事务和真实保存行为由隔离数据库 IT 验证。
 * @author Rain
 * @since 6.1.0
 */
class AdminClientProvisioningServiceTest {
    private final SysOAuth2ClientConfigMapper mapper = mock(SysOAuth2ClientConfigMapper.class);
    private final OAuth2ClientConfigService clients = mock(OAuth2ClientConfigService.class);
    private final AdminClientProvisioningService service = new AdminClientProvisioningService(mapper, clients);

    /** 生成显式身份范围，不使用任何真实凭据。 */
    private AdminClientProvisioningProperties properties() {
        var properties = new AdminClientProvisioningProperties();
        properties.setAdminClientId(UUID.randomUUID().toString());
        properties.setGatewayClientId(UUID.randomUUID().toString());
        properties.setAdminOrigin("http://127.0.0.1:5900");
        properties.setTenantId(1L);
        properties.setUserType("employee");
        return properties;
    }

    /** 合成 Gateway，仅表示已持有机密身份。 */
    private SysOAuth2ClientConfig gateway(AdminClientProvisioningProperties properties) {
        var gateway = new SysOAuth2ClientConfig();
        gateway.setId(7L);
        gateway.setClientId(properties.getGatewayClientId());
        gateway.setClientName("Gateway");
        gateway.setClientSecret("synthetic-encoded-secret");
        gateway.setClientAuthenticationMethods("client_secret_basic,client_secret_post");
        gateway.setTenantId(1L);
        gateway.setUserType("employee");
        when(clients.get(7L)).thenAnswer(call -> {
            var view = new OAuth2ClientVO();
            var settings = new dev.macula.cloud.system.protocol.OAuth2ProtocolSettings();
            settings.setClientAuthenticationMethods(gateway.getClientAuthenticationMethods());
            view.setProtocolConfigurations(java.util.List.of(new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration("OAUTH2_1", 1, true, settings)));
            view.setConfigurationVersion("fixture-version");
            return view;
        });
        return gateway;
    }

    /** 两个独立身份，Admin 无密钥，Gateway 不接收新密钥且范围不变。 */
    @Test
    void configuresTwoSeparateIdentitiesWithoutRotatingGatewaySecret() {
        var properties = properties();
        var gateway = gateway(properties);
        when(mapper.selectOne(any())).thenReturn(gateway, null);
        service.provision(properties);
        var forms = ArgumentCaptor.forClass(OAuth2ClientForm.class);
        verify(clients, times(2)).save(nullable(Long.class), forms.capture());
        var machine = forms.getAllValues().get(0);
        var admin = forms.getAllValues().get(1);
        assertEquals("SERVICE", machine.getApplicationType());
        assertEquals("client_credentials", machine.getAuthorizationGrantTypes());
        assertNull(machine.getClientSecret());
        assertEquals("synthetic-encoded-secret", gateway.getClientSecret());
        assertEquals(properties.getAdminClientId(), admin.getClientId());
        assertEquals("SPA", admin.getApplicationType());
        assertEquals("none", admin.getClientAuthenticationMethods());
        assertEquals(Set.of("OIDC"), admin.getEnabledProtocols());
        assertEquals("authorization_code", admin.getAuthorizationGrantTypes());
        assertEquals("openid,profile", admin.getScopes());
        assertEquals("reference", admin.getTokenFormat());
        assertTrue(admin.getRequireProofKey());
        assertFalse(admin.getRequireAuthorizationConsent());
        assertNull(admin.getHostedLoginMethods());
        assertNull(admin.getClientSecret());
    }

    /** ID 碰撞在访问数据前拒绝。 */
    @Test
    void rejectsIdentityCollisionBeforeReadingOrWritingClients() {
        var properties = properties();
        properties.setAdminClientId(properties.getGatewayClientId());
        assertThrows(IllegalArgumentException.class, () -> service.provision(properties));
        verifyNoInteractions(mapper, clients);
    }

    /** 缺失机密身份、范围漂移和类型冲突都不能转成公共 Client。 */
    @Test
    void rejectsGatewayScopeTypeOrSecretMismatch() {
        for (String failure : Set.of("secret", "tenant", "type", "method")) {
            var properties = properties();
            var gateway = gateway(properties);
            switch (failure) {
                case "secret" -> gateway.setClientSecret(null);
                case "tenant" -> gateway.setTenantId(2L);
                case "type" -> gateway.setApplicationType("SPA");
                case "method" -> gateway.setClientAuthenticationMethods("none");
                default -> throw new AssertionError();
            }
            when(mapper.selectOne(any())).thenReturn(gateway);
            assertThrows(IllegalStateException.class, () -> service.provision(properties));
        }
        verify(clients, never()).save(any(), any());
    }

    /** 已有 Admin 配置不一致时，在写入任一身份前停止。 */
    @Test
    void rejectsExistingAdminDriftWithoutOverwriting() {
        var properties = properties();
        var admin = new SysOAuth2ClientConfig();
        admin.setId(8L);
        var gateway = gateway(properties);
        when(mapper.selectOne(any())).thenReturn(gateway, admin);
        when(clients.get(8L)).thenReturn(new OAuth2ClientVO());
        assertThrows(IllegalStateException.class, () -> service.provision(properties));
        verify(clients, never()).save(any(), any());
    }

    /** 正常启动缺少开关不会创建任务。 */
    @Test
    void provisioningRunnerRequiresExplicitEnablement() {
        var condition = AdminClientProvisioningRunner.class.getAnnotation(ConditionalOnProperty.class);
        assertFalse(condition.matchIfMissing());
        assertEquals("true", condition.havingValue());
    }
}
