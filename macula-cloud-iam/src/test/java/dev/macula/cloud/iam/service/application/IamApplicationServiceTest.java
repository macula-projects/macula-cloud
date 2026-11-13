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
package dev.macula.cloud.iam.service.application;

import dev.macula.cloud.iam.service.application.impl.IamApplicationServiceImpl;

import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.iam.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysIamApplicationMapper;
import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.pojo.entity.SysIamApplication;
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.system.enums.HostedLoginMethod;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolDefinition;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 中立应用快照的登录/协议边界、凭据隐藏和锁定当前读顺序。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationServiceTest {
    private static final Long ID = 51L;
    private static final String HASH = "synthetic-not-a-real-credential";
    private final SysIamApplicationMapper applications = mock(SysIamApplicationMapper.class);
    private final SysIamApplicationProtocolConfigMapper protocols = mock(SysIamApplicationProtocolConfigMapper.class);
    private final SysIamApplicationIdentityProviderMapper sources = mock(SysIamApplicationIdentityProviderMapper.class);
    private final IamApplicationServiceImpl service = new IamApplicationServiceImpl(applications, protocols, sources,
        new DateTimeConverter("UTC"), OAuth2ProtocolDefinition.registry());
    private final JsonMapper json = JsonMapper.builder().build();

    /** 无应用时不读取协议、来源，也不能从请求猜测默认应用。 */
    @Test
    void missingApplicationDoesNotResolveOtherData() {
        assertThat(service.findById(null)).isNull();
        verifyNoInteractions(applications, protocols, sources);
        assertThat(service.findById(ID)).isNull();
        verify(applications).selectById(ID);
        verifyNoInteractions(protocols, sources);
    }

    /** 未确认历史草稿可以读取，但不存在任何可认证协议。 */
    @Test
    void unselectedDraftKeepsNeutralMetadataWithoutProtocol() {
        var app = application();
        app.setApplicationType(null);
        when(applications.selectById(ID)).thenReturn(app);
        var snapshot = service.findById(ID);
        assertThat(snapshot.applicationCode()).isEqualTo("original-client-id");
        assertThat(snapshot.codeIssuedAt().toString()).isEqualTo("2024-01-02T03:04:05Z");
        assertThat(snapshot.loginEnabled()).isFalse();
        assertThat(snapshot.protocol()).isNull();
        assertThat(snapshot.login().type()).isNull();
        assertThat(snapshot.login().hostedMethods()).isNull();
    }

    /** 历史槽保留凭据存在性，不能把旧快照当成执行设置或在视图中泄露哈希。 */
    @Test
    void legacySlotNeverExecutesAndSecretDoesNotEnterSnapshot() {
        when(applications.selectById(ID)).thenReturn(application());
        var row = protocol();
        row.setProtocol(null);
        row.setSchemaVersion(0);
        row.setEnabled(false);
        row.setConfiguration("{\"authorization_grant_types\":\"password\"}");
        when(protocols.selectByApplicationId(ID)).thenReturn(row);
        var snapshot = service.findById(ID);
        assertThat(snapshot.hasCredential()).isTrue();
        assertThat(snapshot.loginEnabled()).isFalse();
        assertThat(snapshot.protocol().assigned()).isFalse();
        assertThat(snapshot.protocol().settings()).isNull();
        assertThat(snapshot.toString()).doesNotContain(HASH, "password");
        assertThat(json.writeValueAsString(snapshot)).doesNotContain(HASH, "credentialHash", "password");
        assertThat(row.toString()).doesNotContain(HASH);
        assertThat(json.writeValueAsString(row)).doesNotContain(HASH, "credentialHash");
    }

    /** 资源管理登记采用OAuth内部代码也不能被公共登录端口接受。 */
    @Test
    void resourceManagementRegistrationCannotLogin() {
        var app = application();
        app.setPurpose("RESOURCE_SERVER");
        app.setConfigurationStatus("ENABLED");
        app.setApplicationType("SERVICE");
        when(applications.selectById(ID)).thenReturn(app);
        var row = protocol();
        var settings = new OAuth2ApplicationProtocolSettings();
        var oauth = new OAuth2ProtocolSettings();
        oauth.setClientAuthenticationMethods("client_secret_basic");
        oauth.setAuthorizationGrantTypes("");
        settings.setOauth2(oauth);
        row.setProtocol("OAUTH2_1");
        row.setConfiguration(json.writeValueAsString(settings));
        when(protocols.selectByApplicationId(ID)).thenReturn(row);
        var snapshot = service.findById(ID);
        assertThat(snapshot.protocol().enabled()).isTrue();
        assertThat(snapshot.loginEnabled()).isFalse();
        assertThat(snapshot.login().sources()).isEmpty();
    }

    /** null继承与显式关闭保持不同；合法EMAIL/SMS集合稳定且不可修改。 */
    @Test
    void hostedMethodsPreserveInheritanceAndExplicitDisable() {
        var app = application();
        when(applications.selectById(ID)).thenReturn(app);
        app.setHostedLoginMethods("");
        assertThat(service.findById(ID).login().hostedMethods()).isEmpty();
        app.setHostedLoginMethods("SMS,EMAIL");
        var methods = service.findById(ID).login().hostedMethods();
        assertThat(methods).isEqualTo(Set.of(HostedLoginMethod.SMS, HostedLoginMethod.EMAIL));
        assertThrows(UnsupportedOperationException.class, methods::clear);
        app.setHostedLoginMethods("EMAIL,EMAIL");
        assertThrows(IllegalStateException.class, () -> service.findById(ID));
    }

    /** 不合法持久化载荷只返回有界配置错误，不能携带JSON或秘密原因链。 */
    @Test
    void rejectsInvalidProtocolAndMissingActiveCredentialWithoutLeakingCause() {
        var app = application();
        app.setConfigurationStatus("ENABLED");
        when(applications.selectById(ID)).thenReturn(app);
        var row = protocol();
        when(protocols.selectByApplicationId(ID)).thenReturn(row);
        assertThat(service.findById(ID).loginEnabled()).isTrue();
        row.setCredentialHash(null);
        assertThrows(IllegalStateException.class, () -> service.findById(ID));
        row.setCredentialHash(HASH);
        row.setConfiguration("{\"unexpectedSecret\":\"" + HASH + "\"}");
        var error = assertThrows(IllegalStateException.class, () -> service.findById(ID));
        assertThat(error.getMessage()).isEqualTo("应用配置无效");
        assertThat(error.getCause()).isNull();
        row.setProtocol("CAS");
        assertThrows(IllegalStateException.class, () -> service.findById(ID));
    }

    /** 重复/跨模式来源不能因协议载荷合法而越过公共登录约束。 */
    @Test
    void rejectsConflictingSourcesAndModeEvenForDraft() {
        var app = application();
        when(applications.selectById(ID)).thenReturn(app);
        var first = new SysIamApplicationIdentityProvider();
        first.setApplicationId(ID);
        first.setLoginCapability("GITHUB_OAUTH");
        first.setIdentityProviderId(11L);
        when(sources.selectByApplicationId(ID)).thenReturn(List.of(first, first));
        assertThrows(IllegalStateException.class, () -> service.findById(ID));
        when(sources.selectByApplicationId(ID)).thenReturn(List.of(first));
        assertThat(service.findById(ID).login().sources()).hasSize(1);
        app.setWebLoginMode("IN_APP");
        assertThrows(IllegalStateException.class, () -> service.findById(ID));
    }

    /** 锁内只使用当前读；主应用、协议、来源顺序固定，不混入普通快照查询。 */
    @Test
    void lockedReadRequiresCallerTransactionAndUsesCurrentReadOnly() {
        assertThrows(IllegalStateException.class, () -> service.lock(ID));
        verifyNoInteractions(applications, protocols, sources);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalStateException.class, () -> service.lock(ID));
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        when(applications.selectByIdForUpdate(ID)).thenReturn(application());
        assertThat(service.lock(ID)).isNotNull();
        var ordered = inOrder(applications, protocols, sources);
        ordered.verify(applications).selectByIdForUpdate(ID);
        ordered.verify(protocols).selectByApplicationIdForUpdate(ID);
        ordered.verify(sources).selectByApplicationIdForUpdate(ID);
        verifyNoMoreInteractions(applications, protocols, sources);
    }

    /** 创建中立应用合成数据，不通过旧OAuth实体传递登录策略。 */
    private SysIamApplication application() {
        var app = new SysIamApplication();
        app.setId(ID);
        app.setApplicationCode("original-client-id");
        app.setApplicationCodeIssuedAt(LocalDateTime.parse("2024-01-02T03:04:05"));
        app.setApplicationName("synthetic-application");
        app.setPurpose("APPLICATION");
        app.setApplicationType("WEB_SERVER");
        app.setConfigurationStatus("DRAFT");
        app.setResourcePermissions("{}");
        app.setTenantId(1L);
        app.setUserType("employee");
        return app;
    }

    /** 创建非秘密v2策略；合成哈希只存在受保护的持久化列。 */
    private SysIamApplicationProtocolConfig protocol() {
        var row = new SysIamApplicationProtocolConfig();
        row.setApplicationId(ID);
        row.setProtocolIdentifier("original-client-id");
        row.setProtocol("OIDC");
        row.setSchemaVersion(2);
        row.setEnabled(true);
        row.setCredentialHash(HASH);
        var oauth = new OAuth2ProtocolSettings();
        oauth.setClientAuthenticationMethods("client_secret_basic");
        oauth.setAuthorizationGrantTypes("authorization_code");
        oauth.setRedirectUris("https://synthetic.example/callback");
        oauth.setScopes("openid");
        oauth.setRequireProofKey(true);
        oauth.setRequireAuthorizationConsent(false);
        var settings = new OAuth2ApplicationProtocolSettings();
        settings.setOauth2(oauth);
        row.setConfiguration(json.writeValueAsString(settings));
        return row;
    }

    /** 清理仅用于单元层验证事务前置条件的线程标记。 */
    @AfterEach
    void clearTransactionMarkers() {
        TransactionSynchronizationManager.clear();
    }
}
