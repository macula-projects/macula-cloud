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

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IAM 客户端租户、公共客户端和提交后缓存失效约束测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class OAuth2ClientConfigServiceImplTest {
    private final SysOAuth2ClientConfigMapper mapper = mock(SysOAuth2ClientConfigMapper.class);
    private final SysOAuth2ClientExternalProviderMapper externalProviderMapper =
        mock(SysOAuth2ClientExternalProviderMapper.class);
    private final SysIdentityProviderMapper identityProviderMapper = mock(SysIdentityProviderMapper.class);
    private final SysTenantInfoMapper tenantMapper = mock(SysTenantInfoMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final Cache originCache = mock(Cache.class);
    private final dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper protocolMapper = mock(dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper.class);
    private final ApplicationProtocolConfigurationService protocols = new ApplicationProtocolConfigurationService(protocolMapper, externalProviderMapper);

    /** 仅测试显式迁移旧素材，生产save仅接收新模型。 */
    private dev.macula.cloud.system.vo.OAuth2ClientVO saveFixture(Long id, OAuth2ClientForm form) {
        ProtocolConfigurationTestFixtures.configure(form);
        if (id != null) {
            var existing = mapper.selectById(id);
            if (existing != null) {
                form.setConfigurationVersion(protocols.version(existing));
                org.mockito.Mockito.doReturn(existing).when(mapper).selectOne(any());
            }
        }
        return service.save(id, form);
    }

    private final OAuth2ClientConfigServiceImpl service =
        new OAuth2ClientConfigServiceImpl(mapper, externalProviderMapper, identityProviderMapper,
            tenantMapper, encoder, cacheManager, catalog(), protocols);

    @Test
    void externalCodeCatalogExposesOnlyNonSensitiveProviderCapabilities() {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setId(7L);
        provider.setName("微信测试应用");
        provider.setProviderType("WECHAT");
        provider.setConfigJson("{\"mobileAppId\":\"wx-app\",\"miniAppId\":\"wx-mini\"}");
        provider.setSecretConfigJson(
            "{\"mobileAppSecret\":\"mobile-secret\",\"miniAppSecret\":\"mini-secret\"}");
        when(identityProviderMapper.selectList(any())).thenReturn(List.of(provider));

        var registration = service.externalCodeCatalog().getProviderRegistrations().get(0);

        assertEquals(Set.of("DEFAULT", "WECHAT_MOBILE_APP", "WECHAT_MINIAPP"),
            registration.getCapabilities());
    }

    @Test
    void publicClientRequiresTenantAndPkce() {
        OAuth2ClientForm form = valid();
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        form.setRequireProofKey(false);
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        verifyNoInteractions(encoder);
    }

    /** 提交后立即清理真实事务感知包装，不能在afterCommit中再次排队clear。 */
    @Test
    void confidentialSecretIsEncodedAndCacheEvictedAfterCommit() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("test-secret-123456");
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode("test-secret-123456")).thenReturn("{bcrypt}digest");
        stubCaches();
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(
            new org.springframework.cache.transaction.TransactionAwareCacheDecorator(cache));
        when(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)).thenReturn(
            new org.springframework.cache.transaction.TransactionAwareCacheDecorator(originCache));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(saveFixture(null, form).isSecretConfigured());
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                "{bcrypt}digest".equals(entity.getClientSecret()) && entity.getTenantId() == 1L));
            verify(cache, never()).invalidate();
            verify(originCache, never()).invalidate();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(cache).invalidate();
            verify(originCache).invalidate();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cacheClearFailureIsSurfacedAfterCommittedWrite() {
        OAuth2ClientForm form = valid();
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        stubCaches();
        doThrow(new IllegalStateException("fixture cache unavailable")).when(cache).invalidate();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(null, form));
            TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

            assertThrows(IllegalStateException.class, synchronization::afterCommit);
            verify(mapper).insert(any(SysOAuth2ClientConfig.class));
            verify(originCache).invalidate();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void originCacheClearFailureIsSurfacedAfterCommittedWrite() {
        OAuth2ClientForm form = valid();
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        stubCaches();
        doThrow(new IllegalStateException("fixture origin cache unavailable")).when(originCache).invalidate();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(null, form));
            TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

            assertThrows(IllegalStateException.class, synchronization::afterCommit);
            verify(mapper).insert(any(SysOAuth2ClientConfig.class));
            verify(cache).invalidate();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void missingCacheRegionFailsBeforeRegisteringCommitCallback() {
        OAuth2ClientForm form = valid();
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        when(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)).thenReturn(null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class, () -> saveFixture(null, form));
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            verify(cache, never()).invalidate();
            verify(originCache, never()).invalidate();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void machineClientDoesNotNeedRedirectOrOidcScope() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("test-secret-123456");
        form.setProtocolMode("OAUTH2_1");
        form.setEnabledProtocols(java.util.Set.of("OAUTH2_1"));
        form.setDefaultProtocol("OAUTH2_1");
        form.setConfigurationStatus("ENABLED");
        form.setApplicationType("SERVICE");
        form.setAuthorizationGrantTypes("client_credentials");
        form.setRedirectUris(null);
        form.setScopes("message.read");
        form.setRequireProofKey(false);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(encoder.encode("test-secret-123456")).thenReturn("{bcrypt}digest");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(null, form));
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) -> "".equals(entity.getRedirectUris())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 公共和机密Client都不能重新写入已退役短信/邮箱grant。 */
    @Test
    void rejectsRetiredVerificationGrantsForPublicAndConfidentialClients() {
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        stubCaches();
        OAuth2ClientForm form = valid();
        form.setAuthorizationGrantTypes("sms");
        form.setRedirectUris(null);
        form.setProtocolMode("OAUTH2_1");
        form.setEnabledProtocols(java.util.Set.of("OAUTH2_1"));
        form.setDefaultProtocol("OAUTH2_1");
        form.setConfigurationStatus("ENABLED");
        form.setScopes("message.read");
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        verify(protocolMapper, never()).insert(any(dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig.class));

        OAuth2ClientForm confidentialSms = valid();
        confidentialSms.setClientAuthenticationMethods("client_secret_post");
        confidentialSms.setClientSecret("test-secret-123456");
        confidentialSms.setAuthorizationGrantTypes("sms");
        confidentialSms.setRedirectUris(null);
        confidentialSms.setProtocolMode("OAUTH2_1");
        confidentialSms.setEnabledProtocols(java.util.Set.of("OAUTH2_1"));
        confidentialSms.setDefaultProtocol("OAUTH2_1");
        confidentialSms.setConfigurationStatus("ENABLED");
        confidentialSms.setScopes("message.read");
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, confidentialSms));

        OAuth2ClientForm authorizationCode = valid();
        authorizationCode.setAllowedOrigins(Set.of("https://app.example.test"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(null, authorizationCode));
            verify(protocolMapper).insert(argThat((dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig row) ->
                row.getConfiguration().contains("https://app.example.test")));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void invalidOldGrantCombinationCannotBePreservedByEditingOrigins() {
        when(mapper.selectById(2L)).thenReturn(legacyVerificationClient());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        for (Set<String> origins : List.of(Set.of("https://admin.example.test"), Set.of("https://other.example.test"), Set.<String>of())) {
            OAuth2ClientForm form = legacyVerificationForm();
            form.setAllowedOrigins(origins);
            assertThrows(ResponseStatusException.class, () -> saveFixture(2L, form));
        }
        verify(mapper, never()).update(any(SysOAuth2ClientConfig.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void legacyVerificationMaintenanceRejectsProtocolChangesAndNewInvalidTargets() {
        SysOAuth2ClientConfig existing = legacyVerificationClient();
        when(mapper.selectById(2L)).thenReturn(existing);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        OAuth2ClientForm changedAuthentication = legacyVerificationForm();
        changedAuthentication.setClientAuthenticationMethods("client_secret_post");
        assertThrows(ResponseStatusException.class, () -> saveFixture(2L, changedAuthentication));

        OAuth2ClientForm changedGrants = legacyVerificationForm();
        changedGrants.setAuthorizationGrantTypes(
            "authorization_code,client_credentials,refresh_token,password,sms,email");
        assertThrows(ResponseStatusException.class, () -> saveFixture(2L, changedGrants));

        OAuth2ClientForm duplicateAuthentication = legacyVerificationForm();
        duplicateAuthentication.setClientAuthenticationMethods(
            "client_secret_basic,client_secret_post,client_secret_basic");
        assertThrows(ResponseStatusException.class, () -> saveFixture(2L, duplicateAuthentication));

        OAuth2ClientForm emptyGrantToken = legacyVerificationForm();
        emptyGrantToken.setAuthorizationGrantTypes(
            "authorization_code,client_credentials,,refresh_token,password,sms");
        assertThrows(ResponseStatusException.class, () -> saveFixture(2L, emptyGrantToken));

        SysOAuth2ClientConfig legalConfidential = legacyVerificationClient();
        legalConfidential.setAuthorizationGrantTypes("client_credentials");
        when(mapper.selectById(3L)).thenReturn(legalConfidential);
        OAuth2ClientForm addsSms = legacyVerificationForm();
        addsSms.setClientId(legalConfidential.getClientId());
        assertThrows(ResponseStatusException.class, () -> saveFixture(3L, addsSms));

        verify(mapper, never()).update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any());
        verify(mapper, never()).updateById(any(SysOAuth2ClientConfig.class));
    }

    @Test
    void legacyVerificationClientCanBeExplicitlyRemediatedToPublicPkce() {
        SysOAuth2ClientConfig existing = legacyVerificationClient();
        OAuth2ClientForm form = valid();
        when(mapper.selectById(2L)).thenReturn(existing);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(externalProviderMapper.selectList(any())).thenReturn(List.of());
        when(mapper.update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(1);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(2L, form));
            verify(mapper).update(argThat((SysOAuth2ClientConfig entity) -> entity.getClientSecret() == null),
                org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any());
            verify(protocolMapper).insert(argThat((dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig row) ->
                row.getConfiguration().contains("\"clientAuthenticationMethods\":\"none\"") && row.getConfiguration().contains("\"requireProofKey\":true")));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void legacyVerificationClientCanRemoveVerificationGrant() {
        SysOAuth2ClientConfig existing = legacyVerificationClient();
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setAuthorizationGrantTypes("authorization_code,refresh_token");
        when(mapper.selectById(2L)).thenReturn(existing);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(externalProviderMapper.selectList(any())).thenReturn(List.of());
        when(mapper.update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(1);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(2L, form));
            verify(mapper).update(argThat((SysOAuth2ClientConfig entity) -> "{bcrypt}digest".equals(entity.getClientSecret())),
                org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any());
            verify(protocolMapper).insert(argThat((dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig row) ->
                row.getConfiguration().contains("authorization_code,refresh_token") && !row.getConfiguration().contains("password")));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void concurrentProtocolRemediationRejectsStaleLegacyMaintenance() {
        OAuth2ClientForm form = valid();
        form.setAllowedOrigins(Set.of("https://admin.example.test"));
        when(mapper.selectById(2L)).thenReturn(legacyVerificationClient());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(mapper.update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> saveFixture(2L, form));

        assertEquals(409, error.getStatusCode().value());
        verify(externalProviderMapper, never()).delete(any());
        verify(cacheManager, never()).getCache(any());
    }

    @Test
    void rejectsBrowserOriginsBeyondDatabaseCapacity() {
        OAuth2ClientForm form = valid();
        form.setAllowedOrigins(java.util.stream.IntStream.range(0, 100)
            .mapToObj(index -> "https://client-" + index + ".example.test")
            .collect(java.util.stream.Collectors.toSet()));
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        verify(mapper, never()).insert(any(SysOAuth2ClientConfig.class));
    }

    @Test
    void publicMiniappClientCanEnableAuthorizationCodeAndExternalCodeTogether() {
        OAuth2ClientForm form = valid();
        form.setAuthorizationGrantTypes(
            "authorization_code," + ExternalCodeType.GRANT_TYPE);
        form.setApplicationType("MINIAPP");
        form.setEnabledProtocols(java.util.Set.of("OAUTH2_1"));
        form.setDefaultProtocol("OAUTH2_1");
        form.setScopes("message.read");
        form.setMiniappPlatform("WECHAT");
        form.setExternalCodeTypes(Set.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name()));
        form.setExternalProviderBindings(Map.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name(), 7L));
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setId(7L);
        provider.setProviderType("WECHAT");
        provider.setSourceType("WECHAT_MINIAPP");
        provider.setAppId("wx-mini");
        provider.setAppSecret("mini-secret");
        provider.setConfigJson("{\"appId\":\"wx-mini\"}");

        provider.setEnabled(true);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(identityProviderMapper.selectById(7L)).thenReturn(provider);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(null, form));
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name().equals(entity.getExternalCodeTypes())));
            verify(protocolMapper).insert(argThat((dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig row) ->
                row.getConfiguration().contains("authorization_code") && row.getConfiguration().contains(ExternalCodeType.GRANT_TYPE)));
            verify(externalProviderMapper).insert(argThat((SysOAuth2ClientExternalProvider mapping) ->
                "test-client".equals(mapping.getClientId()) &&
                    ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name().equals(mapping.getCodeType()) &&
                    Long.valueOf(7L).equals(mapping.getIdentityProviderId())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** OIDC SDK-only不要求网页回调，仍持久化具体来源许可。 */
    @Test
    void savesOidcSdkOnlyWithConcreteSource() {
        OAuth2ClientForm form = valid();
        form.setAuthorizationGrantTypes(ExternalCodeType.GRANT_TYPE);
        form.setRedirectUris("");
        form.setApplicationType("MINIAPP");
        form.setMiniappPlatform("WECHAT");
        form.setExternalCodeTypes(Set.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name()));
        form.setExternalProviderBindings(Map.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name(), 7L));
        SysIdentityProvider source = new SysIdentityProvider();
        source.setId(7L);
        source.setProviderType("WECHAT");
        source.setSourceType("WECHAT_MINIAPP");
        source.setAppId("fixture-mini");
        source.setAppSecret("fixture-secret");
        source.setConfigJson("{\"appId\":\"fixture-mini\"}");
        source.setEnabled(true);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(identityProviderMapper.selectById(7L)).thenReturn(source);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> saveFixture(null, form));
            verify(protocolMapper).insert(argThat((dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig row) ->
                "OIDC".equals(row.getProtocol()) && row.getConfiguration().contains(ExternalCodeType.GRANT_TYPE)
                    && !row.getConfiguration().contains("authorization_code")));
            form.setExternalProviderBindings(Map.of());
            assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void browserLoginProvidersAreNormalizedAndReturned() {
        OAuth2ClientForm form = valid();
        form.setBrowserLoginProviderTypes(Set.of(BrowserLoginProviderType.WECOM.name(),
            BrowserLoginProviderType.GITHUB.name()));
        form.setBrowserProviderBindings(Map.of("GITHUB", 1L, "WECOM", 2L));
        form.setDirectLoginProviderType(BrowserLoginProviderType.WECOM.name());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        SysIdentityProvider github = new SysIdentityProvider();
        github.setProviderType("GITHUB");
        github.setSourceType("GITHUB_OAUTH");
        github.setEnabled(true);
        SysIdentityProvider wecom = new SysIdentityProvider();
        wecom.setProviderType("WECOM");
        wecom.setSourceType("WECOM_WEB");
        wecom.setEnabled(true);
        when(identityProviderMapper.selectById(1L)).thenReturn(github);
        when(identityProviderMapper.selectById(2L)).thenReturn(wecom);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            var result = saveFixture(null, form);

            assertEquals(Set.of(BrowserLoginProviderType.GITHUB.name(),
                BrowserLoginProviderType.WECOM.name()), result.getBrowserLoginProviderTypes());
            assertEquals(BrowserLoginProviderType.WECOM.name(), result.getDirectLoginProviderType());
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                "GITHUB,WECOM".equals(entity.getBrowserLoginProviderTypes()) &&
                    BrowserLoginProviderType.WECOM.name().equals(entity.getDirectLoginProviderType())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void browserLoginProvidersRequireValidClientAndUniqueEnabledRegistration() {
        OAuth2ClientForm form = valid();
        form.setBrowserLoginProviderTypes(Set.of(BrowserLoginProviderType.WECOM.name()));
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        form.setAuthorizationGrantTypes("refresh_token");
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));

        form.setAuthorizationGrantTypes("authorization_code");
        form.setRedirectUris(null);
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));

        form.setRedirectUris("https://example.test/callback");
        when(identityProviderMapper.selectCount(any())).thenReturn(0L, 2L);
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
    }

    @Test
    void directLoginProviderMustBelongToAllowlist() {
        OAuth2ClientForm form = valid();
        form.setBrowserLoginProviderTypes(Set.of(BrowserLoginProviderType.GITHUB.name()));
        form.setDirectLoginProviderType(BrowserLoginProviderType.WECOM.name());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));

        form.setDirectLoginProviderType(null);
        form.setBrowserLoginProviderTypes(Set.of("UNKNOWN"));
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
    }

    @Test
    void failedInsertOrRolledBackTransactionNeverEvictsClientCache() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("test-secret-123456");
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(encoder.encode("test-secret-123456")).thenReturn("{bcrypt}digest");
        stubCaches();
        doThrow(new IllegalStateException("fixture database failure")).when(mapper)
            .insert(any(SysOAuth2ClientConfig.class));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class, () -> saveFixture(null, form));
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            verify(cache, never()).invalidate();
            verify(originCache, never()).invalidate();
            reset(mapper);
            saveFixture(null, form);
            verify(cache, never()).invalidate();
            verify(originCache, never()).invalidate();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(cache, never()).invalidate();
            verify(originCache, never()).invalidate();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsInvalidUserTypeRedirectAndTokenDuration() {
        OAuth2ClientForm form = valid();
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        form.setUserType("other");
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        form.setUserType("supplier");
        form.setRedirectUris("http://example.test/callback");
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        form.setRedirectUris("https://example.test/callback");
        form.setAccessTokenTimeToLive(30L);
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        verify(mapper, never()).insert(any(SysOAuth2ClientConfig.class));
    }

    /** 未指定类型或后端服务配置用户登录不得进入写库流程。 */
    @Test
    void applicationMustBeExplicitAndCapabilitiesMustMatch() {
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        OAuth2ClientForm missing = valid();
        missing.setApplicationType(null);
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, missing));
        OAuth2ClientForm invalid = valid();
        invalid.setApplicationType("UNKNOWN");
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, invalid));
        OAuth2ClientForm crossTerminal = valid();
        crossTerminal.setApplicationType("SERVICE");
        crossTerminal.setBrowserLoginProviderTypes(Set.of("WECOM"));
        assertThrows(ResponseStatusException.class, () -> saveFixture(null, crossTerminal));
        verify(mapper, never()).insert(any(SysOAuth2ClientConfig.class));
    }

    /** 已分类应用不因none认证或合法来源而获得跨终端Code许可。 */
    @Test
    void rejectsCrossTerminalCodesBeforeWritingBindings() {
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        String[][] combinations = {{"SPA", "WECHAT_OPEN_APP_CODE"}, {"WEB_SERVER", "WECOM_APP_CODE"},
            {"NATIVE", "DINGTALK_WEBVIEW_CODE"}, {"NATIVE", "WECHAT_MINIAPP_LOGIN_CODE"}};
        for (String[] combination : combinations) {
            var form = valid();
            form.setApplicationType(combination[0]);
            form.setAuthorizationGrantTypes("authorization_code," + ExternalCodeType.GRANT_TYPE);
            form.setExternalCodeTypes(Set.of(combination[1]));
            form.setExternalProviderBindings(Map.of(combination[1], 7L));
            var failure = assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
            assertTrue(failure.getReason().contains("不兼容"), combination[0] + ": " + failure.getReason());
        }
        verify(mapper, never()).insert(any(SysOAuth2ClientConfig.class));
    }

    /** 小程序可以只走IAM授权码，不要求填入无用途的平台Code绑定。 */
    @Test
    void miniappCanUseOnlyHostedAuthorizationCode() {
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        var form = valid();
        form.setApplicationType("MINIAPP");
        form.setMiniappPlatform("WECHAT");
        form.setExternalCodeTypes(Set.of());
        form.setExternalProviderBindings(Map.of());
        form.setHostedLoginMethods(null);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            var result = saveFixture(null, form);
            assertEquals("MINIAPP", result.getApplicationType());
            assertTrue(result.getExternalCodeTypes().isEmpty());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 显式应用类型保存到Entity，历史记录不猜测。 */
    @Test
    void applicationIsPersistedAndReturnedWithoutInference() {
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        OAuth2ClientForm form = valid();
        form.setApplicationType("NATIVE");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("NATIVE", saveFixture(null, form).getApplicationType());
            verify(mapper).insert(argThat((SysOAuth2ClientConfig value) -> "NATIVE".equals(value.getApplicationType())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 旧PUT保留类型；EMAIL可以保存与取消，null继承、空集合关闭。 */
    @Test
    void explicitHostedPolicyDistinguishesDefaultAndDisabled() {
        SysOAuth2ClientConfig existing = legacyVerificationClient();
        existing.setApplicationType("WEB_SERVER");
        existing.setHostedLoginMethods("PASSWORD");
        when(mapper.selectById(2L)).thenReturn(existing);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(mapper.update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(1);
        OAuth2ClientForm form = valid();
        form.setHostedLoginMethods(Set.of("PASSWORD"));
        form.setApplicationTypePresent(false);
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            form.setHostedLoginMethods(Set.of("PASSWORD", "EMAIL"));
            assertEquals(Set.of("PASSWORD", "EMAIL"), saveFixture(2L, form).getHostedLoginMethods());
            form.setHostedLoginMethods(Set.of("PASSWORD"));
            assertEquals(Set.of("PASSWORD"), saveFixture(2L, form).getHostedLoginMethods());
            form.setHostedLoginMethods(Set.of());
            assertTrue(saveFixture(2L, form).getHostedLoginMethods().isEmpty());
            form.setHostedLoginMethods(null);
            assertEquals(null, saveFixture(2L, form).getHostedLoginMethods());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 类型一旦确认就不能修改；CAS失败不能替换绑定或注册成功缓存副作用。 */
    @Test
    void immutableTypeAndConcurrentConfirmationRejectBeforeSideEffects() {
        SysOAuth2ClientConfig existing = legacyVerificationClient();
        existing.setApplicationType("WEB_SERVER");
        when(mapper.selectById(2L)).thenReturn(existing);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        OAuth2ClientForm change = valid();
        change.setApplicationType("NATIVE");
        assertThrows(ResponseStatusException.class, () -> saveFixture(2L, change));
        existing.setApplicationType(null);
        OAuth2ClientForm confirm = valid();
        when(mapper.update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            ResponseStatusException conflict = assertThrows(ResponseStatusException.class, () -> saveFixture(2L, confirm));
            assertEquals(409, conflict.getStatusCode().value());
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            verify(externalProviderMapper, never()).delete(any(LambdaQueryWrapper.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 无密钥历史Client可原子初始化，已有密钥不能通过普通保存替换。 */
    @Test
    void initializesOnlyMissingCredentials() {
        SysOAuth2ClientConfig existing = legacyVerificationClient();
        existing.setClientSecret(null);
        when(mapper.selectById(2L)).thenReturn(existing);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(mapper.update(any(SysOAuth2ClientConfig.class),
            org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(1);
        when(encoder.encode("fixture-initial-secret")).thenReturn("{bcrypt}initialized");
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("fixture-initial-secret");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(saveFixture(2L, form).isSecretConfigured());
            assertEquals("{bcrypt}initialized", existing.getClientSecret());
            assertThrows(ResponseStatusException.class, () -> saveFixture(2L, form));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 测试业务Scope必须显式登记，不从旧示例或旧Client推断。 */
    private dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService catalog() {
        var properties = new dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties();
        var scope = new dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties.Scope();
        scope.setId("message.read");
        scope.setResource("urn:macula:system");
        scope.setName("读取消息");
        properties.setBusiness(java.util.List.of(scope));
        return new OAuth2ClientConfigurationCatalogServiceImpl(properties, identityProviderMapper);
    }

    private void stubCaches() {
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        when(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)).thenReturn(originCache);
    }

    /** 创建Web应用的最小合法配置。 */
    /** 基础创建不猜测租户、用户目录或授权参数，Client ID由服务端生成。 */
    @Test
    void createsNonAuthenticatingDraftWithoutInventedRouting() {
        var form = new dev.macula.cloud.system.form.OAuth2ClientDraftForm();
        form.setClientName("Draft App");
        form.setApplicationType("SPA");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            var saved = service.createDraft(form);
            assertEquals("DRAFT",saved.getConfigurationStatus());
            assertEquals(java.util.UUID.fromString(saved.getClientId()).toString(),saved.getClientId());
            assertNull(saved.getTenantId());
            assertNull(saved.getUserType());
            assertFalse(saved.isSecretConfigured());
            assertEquals("",saved.getAuthorizationGrantTypes());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 草稿可缺接入条件，但不能启用或隐藏无效参数。 */
    @Test
    void incompleteDraftCanSaveButCannotAuthenticate() {
        var form = valid();
        form.setConfigurationStatus("DRAFT");
        form.setTenantId(null);
        form.setUserType(null);
        form.setRedirectUris("");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("DRAFT",saveFixture(null,form).getConfigurationStatus());
            form.setConfigurationStatus("ENABLED");
            assertThrows(ResponseStatusException.class,()->saveFixture(null,form));
            form.setConfigurationStatus("DRAFT");
            form.setAccessTokenTimeToLive(0L);
            assertThrows(ResponseStatusException.class,()->saveFixture(null,form));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 关闭纯OAuth协议前必须明确处理其grant依赖，不能静默删除。 */
    @Test
    void oidcOnlyRejectsDirectTokenGrantsEvenForDrafts() {
        var form = valid();
        form.setConfigurationStatus("DRAFT");
        form.setAuthorizationGrantTypes("authorization_code,sms");
        assertThrows(ResponseStatusException.class,()->saveFixture(null,form));
        form.setEnabledProtocols(java.util.Set.of("OIDC","OAUTH2_1"));
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try { assertThrows(ResponseStatusException.class,()->saveFixture(null,form)); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    private OAuth2ClientForm valid() {
        OAuth2ClientForm form = new OAuth2ClientForm();
        form.setClientId("test-client");
        form.setClientName("Test Client");
        form.setProtocolMode("OIDC");
        form.setEnabledProtocols(java.util.Set.of("OIDC"));
        form.setDefaultProtocol("OIDC");
        form.setConfigurationStatus("ENABLED");
        form.setBrowserProviderBindings(java.util.Map.of());
        form.setApplicationType("WEB_SERVER");
        form.setClientAuthenticationMethods("none");
        form.setAuthorizationGrantTypes("authorization_code");
        form.setRedirectUris("https://example.test/callback");
        form.setScopes("openid,profile");
        form.setRequireProofKey(true);
        form.setRequireAuthorizationConsent(false);
        form.setReuseRefreshTokens(true);
        form.setUserType("employee");
        form.setTenantId(1L);
        return form;
    }

    private OAuth2ClientForm legacyVerificationForm() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic,client_secret_post");
        form.setAuthorizationGrantTypes("authorization_code,client_credentials,refresh_token,password,sms");
        form.setRequireProofKey(false);
        return form;
    }

    private SysOAuth2ClientConfig legacyVerificationClient() {
        SysOAuth2ClientConfig entity = new SysOAuth2ClientConfig();
        entity.setId(2L);
        entity.setClientId("test-client");
        entity.setClientSecret("{bcrypt}digest");
        entity.setClientName("Test Client");
        entity.setClientAuthenticationMethods("client_secret_basic,client_secret_post");
        entity.setAuthorizationGrantTypes("authorization_code,client_credentials,refresh_token,password,sms");
        entity.setRedirectUris("https://example.test/callback");
        entity.setScopes("openid,profile");
        entity.setAllowedOrigins("");
        entity.setExternalCodeTypes("");
        entity.setBrowserLoginProviderTypes("");
        entity.setRequireProofKey(false);
        entity.setRequireAuthorizationConsent(false);
        entity.setReuseRefreshTokens(true);
        entity.setUserType("employee");
        entity.setTenantId(1L);
        return entity;
    }
    /** 同平台不同网页能力分别持久化具体注册，不能用服务号冒充网站凭据。 */
    @Test
    void persistsDistinctWechatBrowserCapabilitiesAndRejectsWrongRegistration() {
        var form = valid();
        form.setBrowserCapabilityBindings(Map.of("WECHAT_WEBSITE", 7L));
        for (long id : new long[]{7L, 8L}) {
            var source = new SysIdentityProvider();
            source.setId(id);
            source.setEnabled(true);
            source.setProviderType("WECHAT");
            source.setSourceType(id == 7L ? "WECHAT_WEBSITE" : "WECHAT_OFFICIAL_ACCOUNT");
            source.setAppId(id == 7L ? "website-id" : "service-id");
            source.setAppSecret("fixture-secret");
            when(identityProviderMapper.selectById(id)).thenReturn(source);
        }
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            var saved = saveFixture(null, form);
            assertEquals(Map.of("WECHAT_WEBSITE", 7L), saved.getBrowserCapabilityBindings());
            var official = valid();
            official.setBrowserCapabilityBindings(Map.of("WECHAT_OFFICIAL_ACCOUNT", 8L));
            assertThrows(ResponseStatusException.class, () -> saveFixture(null, official));
            assertEquals(Set.of("WECHAT"), saved.getBrowserLoginProviderTypes());
            var invalid = valid();
            invalid.setBrowserCapabilityBindings(Map.of("WECHAT_WEBSITE", 8L));
            assertThrows(ResponseStatusException.class, () -> saveFixture(null, invalid));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 纯AppLink Code客户端也须登记HTTPS回调；可同时保留授权码本地回调。 */
    @Test
    void appLinkClientRequiresItsOwnCallbackWithoutAuthorizationCode() {
        var form = valid();
        form.setApplicationType("NATIVE");
        form.setEnabledProtocols(Set.of("OAUTH2_1"));
        form.setDefaultProtocol("OAUTH2_1");
        form.setAuthorizationGrantTypes(ExternalCodeType.GRANT_TYPE);
        form.setScopes("message.read");
        form.setExternalCodeTypes(Set.of("FEISHU_APP_CODE"));
        form.setExternalProviderBindings(Map.of("FEISHU_APP_CODE", 7L));
        form.setRedirectUris("");
        var source = new SysIdentityProvider();
        source.setId(7L);
        source.setProviderType("FEISHU");
        source.setSourceType("FEISHU_APPLINK");
        source.setIdentityFlow("FEISHU_APPLINK_V3");
        source.setAppId("fixture-app");
        source.setAppSecret("fixture-secret");
        source.setConfigJson("{\"appId\":\"fixture-app\"}");
        source.setEnabled(true);
        when(identityProviderMapper.selectById(7L)).thenReturn(source);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        var failure = assertThrows(ResponseStatusException.class, () -> saveFixture(null, form));
        assertTrue(failure.getReason().contains("HTTPS授权回调"), failure.getReason());
        form.setRedirectUris("http://127.0.0.1/callback,https://app.example/callback");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try { assertDoesNotThrow(() -> saveFixture(null, form)); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    /** 显式转换保留Client ID与非活动密钥哈希，版本更新仍使用既有CAS。 */
    @Test
    void explicitConversionRetainsIdentityAndSecretHash() {
        var original = legacyVerificationClient();
        original.setPurpose("APPLICATION");
        original.setApplicationType("WEB_SERVER");
        original.setAuthorizationVersion(4L);
        when(mapper.selectById(2L)).thenReturn(original);
        when(mapper.update(any(SysOAuth2ClientConfig.class), org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any())).thenReturn(1);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        var source = new SysIdentityProvider();
        source.setId(8L);
        source.setProviderType("WECHAT");
        source.setSourceType("WECHAT_OFFICIAL_ACCOUNT");
        source.setEnabled(true);
        source.setAppId("official-id");
        source.setAppSecret("fixture-secret");
        when(identityProviderMapper.selectById(8L)).thenReturn(source);
        var form = valid();
        form.setApplicationType("IN_APP");
        form.setConvertToInApp(true);
        form.setHostedLoginMethods(Set.of());
        form.setBrowserCapabilityBindings(Map.of("WECHAT_OFFICIAL_ACCOUNT",8L));
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try {
            var saved = saveFixture(2L,form);
            assertEquals("IN_APP",saved.getApplicationType());
            assertEquals("test-client",saved.getClientId());
            verify(mapper).update(argThat((SysOAuth2ClientConfig row) -> "{bcrypt}digest".equals(row.getClientSecret())
                && row.getAuthorizationVersion() == 5L), org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    /** 缺少转换确认、跨租户或平台管理的Client都不能更换类型。 */
    @Test
    void conversionCannotBypassConfirmationOrRouteOrManagedRestrictions() {
        var original = legacyVerificationClient();
        original.setPurpose("APPLICATION");
        original.setApplicationType("SPA");
        when(mapper.selectById(2L)).thenReturn(original);
        var form = valid();
        form.setApplicationType("IN_APP");
        assertThrows(ResponseStatusException.class,()->saveFixture(2L,form));
        form.setConvertToInApp(true);
        form.setTenantId(2L);
        assertThrows(ResponseStatusException.class,()->saveFixture(2L,form));
        form.setTenantId(1L);
        original.setManagedKey("platform-admin");
        assertThrows(ResponseStatusException.class,()->saveFixture(2L,form));
        verify(mapper,never()).update(any(SysOAuth2ClientConfig.class),org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<SysOAuth2ClientConfig>>any());
    }

    /** 服务Client只需要认证租户与业务Scope，不虚构用户目录。 */
    @Test
    void serviceEnablesWithoutUserDirectory() {
        var form=valid();
        form.setApplicationType("SERVICE");
        form.setUserType(null);
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("fixture-service-secret");
        form.setEnabledProtocols(Set.of("OAUTH2_1"));
        form.setDefaultProtocol("OAUTH2_1");
        form.setAuthorizationGrantTypes("client_credentials");
        form.setScopes("message.read");
        form.setRedirectUris("");
        form.setHostedLoginMethods(Set.of());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(encoder.encode("fixture-service-secret")).thenReturn("{bcrypt}fixture-hash");
        stubCaches();
        TransactionSynchronizationManager.initSynchronization();
        try { assertNull(saveFixture(null,form).getUserType());
        } finally { TransactionSynchronizationManager.clearSynchronization();
        }
    }

}
