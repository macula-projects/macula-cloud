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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.macula.cloud.system.config.AdminClientProvisioningProperties;
import dev.macula.cloud.system.config.AdminClientProvisioningRunner;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import dev.macula.cloud.system.service.IamApplicationService;
import dev.macula.cloud.system.vo.IamApplicationVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.nullable;

/**
 * 初始化边界单元测试；事务和真实保存行为由隔离数据库 IT 验证。
 * @author Rain
 * @since 6.1.0
 */
class AdminClientProvisioningServiceTest {
    private final SysIamApplicationMapper mapper = mock(SysIamApplicationMapper.class);
    private final IamApplicationService clients = mock(IamApplicationService.class);
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
    private SysIamApplication gateway(AdminClientProvisioningProperties properties) {
        var gateway = new SysIamApplication();
        gateway.setId(7L);
        gateway.setApplicationCode(properties.getGatewayClientId());
        gateway.setApplicationName("Gateway");
        var secretConfigured = new AtomicBoolean(true);
        var methods = new AtomicReference<String>("client_secret_basic,client_secret_post");
        gateway.setTenantId(1L);
        gateway.setUserType("employee");
        when(clients.get(7L)).thenAnswer(call -> {
            var view = new IamApplicationVO();
            var settings = new OAuth2ProtocolSettings();
            settings.setClientAuthenticationMethods(methods.get());
            view.setProtocolConfigurations(List.of(new ApplicationProtocolConfiguration("OAUTH2_1", 1, true, settings)));
            view.setConfigurationVersion("fixture-version");
            view.setSecretConfigured(secretConfigured.get());
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
        var forms = ArgumentCaptor.forClass(IamApplicationForm.class);
        verify(clients, times(2)).save(nullable(Long.class), forms.capture());
        var machine = forms.getAllValues().get(0);
        var admin = forms.getAllValues().get(1);
        assertEquals("SERVICE", machine.getApplicationType());
        assertEquals("client_credentials", machine.getAuthorizationGrantTypes());
        assertNull(machine.getClientSecret());
        verify(clients, never()).resetSecret(any(), any(), any());
        assertEquals(properties.getAdminClientId(), admin.getClientId());
        assertEquals("BROWSER", admin.getWebLoginMode());
        assertNull(machine.getWebLoginMode());
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
                case "secret" -> when(clients.get(7L)).thenReturn(new IamApplicationVO());
                case "tenant" -> gateway.setTenantId(2L);
                case "type" -> gateway.setApplicationType("SPA");
                case "method" -> {
                    var view = clients.get(7L);
                    ((OAuth2ProtocolSettings) view.getProtocolConfigurations().get(0).settings()).setClientAuthenticationMethods("none");
                    when(clients.get(7L)).thenReturn(view);
                }
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
        var admin = new SysIamApplication();
        admin.setId(8L);
        var gateway = gateway(properties);
        when(mapper.selectOne(any())).thenReturn(gateway, admin);
        when(clients.get(8L)).thenReturn(new IamApplicationVO());
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
