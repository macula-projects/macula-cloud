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

import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.system.form.IamApplicationDraftForm;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolDefinition;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.service.IamApplicationCatalogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 聚合行为回归：公共策略、协议/凭据分层、完整版本及提交后缓存边界。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationServiceImplTest {
    private Fixture f;

    /** 单元反馈只模拟存储层，实际锁/映射/并发另由真实MySQL集成测试证明。 */
    @BeforeEach
    void setup() {
        f = new Fixture();
        TransactionSynchronizationManager.initSynchronization();
    }

    /** 测试自行控制提交回调，不把模拟事务当成数据库事务证据。 */
    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** 资源服务器允许集合可多选及清空，普通业务应用不能携带该策略。 */
    @Test
    void resourceAllowedUserTypesRoundTripAndClear() {
        var form = f.form();
        form.setPurpose("RESOURCE_SERVER");
        form.setApplicationType("SERVICE");
        form.setWebLoginMode(null);
        form.setProtocol(null);
        form.getSettings().setAuthorizationGrantTypes("");
        form.getSettings().setScopes("");
        form.getSettings().setRedirectUris(null);
        form.getSettings().setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("synthetic-resource-secret");
        form.setAllowedUserTypes(Set.of("employee", "member"));
        var saved = f.service.save(null, form);
        assertThat(saved.getAllowedUserTypes()).containsExactlyInAnyOrder("employee", "member");
        var update = f.wire.form(saved);
        update.setAllowedUserTypes(Set.of());
        assertThat(f.service.save(saved.getId(), update).getAllowedUserTypes()).isEmpty();
        var business = f.form();
        business.setAllowedUserTypes(Set.of("employee"));
        assertThrows(ResponseStatusException.class, () -> f.service.save(null, business));
    }

    /** 没有协议的草稿不能生成凭据、租户或默认协议。 */
    @Test
    void createsEmptyDraftWithoutInventingProtocolOrRoute() {
        var draft = new IamApplicationDraftForm();
        draft.setClientName("fixture");
        draft.setApplicationType("SPA");
        var view = f.service.createDraft(draft);
        assertThat(view.getConfigurationStatus()).isEqualTo("DRAFT");
        assertThat(view.getProtocol()).isNull();
        assertThat(view.getTenantId()).isNull();
        assertThat(view.isSecretConfigured()).isFalse();
        assertThat(f.protocolRows).isEmpty();
        verifyNoInteractions(f.encoder);
        verify(f.clientCache, never()).invalidate();
    }

    /** 显式Secret只编码一次，普通编辑保留原哈希，提交前不驱逐缓存。 */
    @Test
    void encodesOnlyExplicitCreationAndPreservesHashOnOrdinaryEdit() {
        var form = f.form();
        form.setClientSecret("synthetic-secret-value");
        var view = f.service.save(null, form);
        var originalHash = f.protocolRows.get(view.getId()).getCredentialHash();
        assertThat(originalHash).isEqualTo("encoded:synthetic-secret-value");
        verify(f.clientCache, never()).invalidate();
        var edit = f.wire.form(view);
        edit.setClientName("changed");
        f.service.save(view.getId(), edit);
        assertThat(f.protocolRows.get(view.getId()).getCredentialHash()).isEqualTo(originalHash);
        verify(f.encoder).encode("synthetic-secret-value");
        assertThat(view.toString()).doesNotContain(originalHash);
        TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCommit());
        verify(f.clientCache, Mockito.times(2)).invalidate();
    }

    /** 旧槽无协议编辑不丢失凭据/有效期，也不把旧策略自动激活。 */
    @Test
    void legacySlotSurvivesMetadataEditUntilExplicitProtocolSelection() {
        var initial = f.form();
        initial.setConfigurationStatus("DRAFT");
        initial.setProtocol(null);
        initial.setSettings(null);
        var view = f.service.save(null, initial);
        var slot = new SysIamApplicationProtocolConfig();
        slot.setId(900L);
        slot.setApplicationId(view.getId());
        slot.setProtocolIdentifier(view.getClientId());
        slot.setSchemaVersion(0);
        slot.setEnabled(false);
        slot.setCredentialHash("synthetic-existing-hash");
        slot.setCredentialExpiresAt(LocalDateTime.parse("2035-01-01T00:00:00"));
        slot.setConfiguration("{}");
        f.protocolRows.put(view.getId(), slot);
        var edit = f.wire.form(f.service.get(view.getId()));
        edit.setClientName("renamed");
        var changed = f.service.save(view.getId(), edit);
        assertThat(changed.getProtocol()).isNull();
        assertThat(changed.getSettings()).isNull();
        assertThat(changed.isSecretConfigured()).isTrue();
        assertThat(f.protocolRows.get(view.getId()).getCredentialExpiresAt()).isEqualTo(slot.getCredentialExpiresAt());
        verifyNoInteractions(f.encoder);
    }

    /** OAuth退役grant和无效协议不能因草稿、私密客户端或普通编辑绕过。 */
    @ParameterizedTest
    @ValueSource(strings = {"password", "sms", "email", "client_credentials"})
    void rejectsInvalidOidcGrantBeforeAnyWrite(String grant) {
        var form = f.form();
        form.getSettings().setAuthorizationGrantTypes(grant);
        form.setConfigurationStatus("DRAFT");
        assertThrows(ResponseStatusException.class, () -> f.service.save(null, form));
        verify(f.apps, never()).insert(any(SysIamApplication.class));
        assertThat(f.protocolRows).isEmpty();
    }

    /** 地址、有效期、目录、PKCE和秘密需求都必须在持久化前校验。 */
    @ParameterizedTest
    @ValueSource(strings = {"redirect", "ttl", "tenant", "user", "pkce", "secret", "scope", "protocol", "origin", "jwt"})
    void rejectsInvalidConfigurationBeforeSideEffects(String problem) {
        var form = f.form();
        form.setClientSecret("synthetic-secret-value");
        switch (problem) {
            case "redirect" -> form.getSettings().setRedirectUris("https://client.example/cb#fragment");
            case "ttl" -> form.getSettings().setAccessTokenTimeToLive(1L);
            case "tenant" -> form.setTenantId(null);
            case "user" -> form.setUserType("unknown");
            case "pkce" -> form.getSettings().setRequireProofKey(false);
            case "secret" -> form.setClientSecret(null);
            case "scope" -> form.getSettings().setScopes("openid,unregistered");
            case "protocol" -> form.setProtocol("CAS");
            case "origin" -> form.getSettings().setAllowedOrigins(Set.of("https://client.example/path"));
            case "jwt" -> {
                form.getSettings().setClientAuthenticationMethods("private_key_jwt");
                form.getSettings().setJwkSetUrl("http://untrusted.example/jwks");
            }
            default -> throw new IllegalArgumentException();
        }
        assertThrows(ResponseStatusException.class, () -> f.service.save(null, form));
        assertThat(f.records).isEmpty();
        assertThat(f.protocolRows).isEmpty();
        verify(f.clientCache, never()).invalidate();
    }

    /** 模式创建即固定，含草稿；失败时不能更新协议/关系或轮换凭据。 */
    @Test
    void immutableModeAndStaleCredentialVersionRejectBeforeWrites() {
        var form = f.form();
        form.setClientSecret("synthetic-secret-value");
        var view = f.service.save(null, form);
        String configuration = f.protocolRows.get(view.getId()).getConfiguration();
        var edit = f.wire.form(view);
        edit.setWebLoginMode("IN_APP");
        assertThrows(ResponseStatusException.class, () -> f.service.save(view.getId(), edit));
        assertThat(f.protocolRows.get(view.getId()).getConfiguration()).isEqualTo(configuration);
        var staleEdit = f.wire.form(view);
        f.protocolRows.get(view.getId()).setCredentialHash("concurrently-changed-synthetic-hash");
        IamApplicationForm stale = staleEdit;
        var error = assertThrows(ResponseStatusException.class, () -> f.service.save(view.getId(), stale));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 来源关系只固定能力，不会为缺失external_code授予自动补权。 */
    @Test
    void nativeSdkRequiresExactSourceAndGrant() {
        var form = f.form();
        form.setApplicationType("NATIVE");
        form.setWebLoginMode(null);
        form.getSettings().setClientAuthenticationMethods("none");
        form.getSettings().setAuthorizationGrantTypes("urn:macula:params:oauth:grant-type:external_code");
        form.setExternalCodeTypes(Set.of("DINGTALK_APP_CODE"));
        form.setExternalProviderBindings(Map.of("DINGTALK_APP_CODE", 11L));
        var source = new SysIdentityProvider();
        source.setId(11L);
        source.setEnabled(true);
        source.setProviderType("DINGTALK");
        source.setSourceType("DINGTALK_SDK");
        source.setCredentialType("DINGTALK_APPLICATION");
        source.setCapabilitiesJson("{\"DINGTALK_SDK\":\"DINGTALK_OAUTH_V1\"}");
        source.setAppId("synthetic-id");
        source.setAppSecret("synthetic-material");
        when(f.identities.selectByIdForUpdate(11L)).thenReturn(source);
        var view = f.service.save(null, form);
        assertThat(view.getExternalCodeTypes()).containsExactly("DINGTALK_APP_CODE");
        assertThat(f.relations.get(view.getId()).get(0).getLoginCapability()).isEqualTo("DINGTALK_SDK");
        var edit = f.wire.form(view);
        edit.getSettings().setAuthorizationGrantTypes("authorization_code");
        assertThrows(ResponseStatusException.class, () -> f.service.save(view.getId(), edit));
    }

    /** 缓存异常发生在提交回调且仍尝试另一区域；回滚不调用回调。 */
    @Test
    void committedCacheFailureStillAttemptsOtherCache() {
        var form = f.form();
        form.setClientSecret("synthetic-secret-value");
        f.service.save(null, form);
        doThrow(new IllegalStateException("fixture")).when(f.clientCache).invalidate();
        assertThrows(IllegalStateException.class, () -> TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit());
        verify(f.originCache).invalidate();
    }

    /**
     * 单元存储替身只模拟主键和Mapper读写，不模拟事务隔离或加解密插件。
     * @since 6.1.0
     */
    static final class Fixture {
        final SysIamApplicationMapper apps = mock(SysIamApplicationMapper.class);
        final SysIamApplicationProtocolConfigMapper rows = mock(SysIamApplicationProtocolConfigMapper.class);
        final SysIamApplicationIdentityProviderMapper sources = mock(SysIamApplicationIdentityProviderMapper.class);
        final SysIdentityProviderMapper identities = mock(SysIdentityProviderMapper.class);
        final SysTenantInfoMapper tenants = mock(SysTenantInfoMapper.class);
        final PasswordEncoder encoder = mock(PasswordEncoder.class);
        final CacheManager caches = mock(CacheManager.class);
        final Cache clientCache = mock(Cache.class);
        final Cache originCache = mock(Cache.class);
        final IamApplicationCatalogService catalog = mock(IamApplicationCatalogService.class);
        final Map<Long, SysIamApplication> records = new HashMap<>();
        final Map<Long, SysIamApplicationProtocolConfig> protocolRows = new HashMap<>();
        final Map<Long, List<SysIamApplicationIdentityProvider>> relations = new HashMap<>();
        final IamApplicationProtocolService protocols;
        final IamApplicationWireAdapter wire;
        final IamApplicationServiceImpl service;

        /** 装配相同策略组件并以Mapper替身隔离外部环境。 */
        Fixture() {
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "fixture");
            for (Class<?> type : List.of(SysIamApplication.class, SysIamApplicationProtocolConfig.class, SysIamApplicationIdentityProvider.class)) {
                TableInfoHelper.initTableInfo(assistant, type);
            }
            var sequence = new AtomicLong(100);
            when(apps.selectById(anyLong())).thenAnswer(call -> records.get(call.getArgument(0)));
            when(apps.selectByIdForUpdate(anyLong())).thenAnswer(call -> records.get(call.getArgument(0)));
            when(apps.insert(any(SysIamApplication.class))).thenAnswer(call -> {
                SysIamApplication row = call.getArgument(0);
                row.setId(sequence.incrementAndGet());
                records.put(row.getId(), row);
                return 1;
            });
            when(apps.update(any(SysIamApplication.class), any(Wrapper.class))).thenAnswer(call -> {
                SysIamApplication row = call.getArgument(0);
                records.put(row.getId(), row);
                return 1;
            });
            when(rows.selectByApplicationId(anyLong())).thenAnswer(call -> protocolRows.get(call.getArgument(0)));
            when(rows.selectByApplicationIdForUpdate(anyLong())).thenAnswer(call -> protocolRows.get(call.getArgument(0)));
            when(rows.insert(any(SysIamApplicationProtocolConfig.class))).thenAnswer(call -> {
                SysIamApplicationProtocolConfig row = call.getArgument(0);
                row.setId(sequence.incrementAndGet());
                protocolRows.put(row.getApplicationId(), row);
                return 1;
            });
            when(rows.updateById(any(SysIamApplicationProtocolConfig.class))).thenAnswer(call -> {
                SysIamApplicationProtocolConfig row = call.getArgument(0);
                protocolRows.put(row.getApplicationId(), row);
                return 1;
            });
            when(sources.selectByApplicationId(anyLong())).thenAnswer(call -> relations.getOrDefault(call.getArgument(0), List.of()));
            when(sources.selectByApplicationIdForUpdate(anyLong())).thenAnswer(call -> relations.getOrDefault(call.getArgument(0), List.of()));
            when(sources.delete(any(Wrapper.class))).thenAnswer(call -> {
                AbstractWrapper<?, ?, ?> wrapper = call.getArgument(0);
                wrapper.getSqlSegment();
                Long id = (Long) wrapper.getParamNameValuePairs().values().iterator().next();
                relations.remove(id);
                return 1;
            });
            when(sources.insert(any(SysIamApplicationIdentityProvider.class))).thenAnswer(call -> {
                SysIamApplicationIdentityProvider row = call.getArgument(0);
                relations.computeIfAbsent(row.getApplicationId(), key -> new ArrayList<>()).add(row);
                return 1;
            });
            when(tenants.selectById(1L)).thenReturn(new SysTenantInfo());
            when(caches.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(clientCache);
            when(caches.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)).thenReturn(originCache);
            when(encoder.encode(any())).thenAnswer(call -> "encoded:" + call.getArgument(0));
            when(catalog.allowedScopes(any(), any(), any())).thenReturn(Set.of("openid", "profile", "system:access"));
            when(catalog.resourcesForScopes(any())).thenCallRealMethod();
            protocols = ProtocolConfigurationTestFixtures.protocols(rows, sources);
            var policy = new IamApplicationPolicyValidator(tenants, identities);
            var dates = new DateTimeConverter("UTC");
            wire = new IamApplicationWireAdapter(protocols, OAuth2ProtocolDefinition.registry(), policy, catalog, dates);
            service = new IamApplicationServiceImpl(apps, sources, identities, protocols, policy, wire,
                new OAuth2ClientCredentialService(encoder, rows), dates, caches);
        }

        /** 生成合法合成Web协议，不包含用户或平台真实凭据。 */
        IamApplicationForm form() {
            var form = new IamApplicationForm();
            form.setClientId("fixture-app");
            form.setClientName("fixture");
            form.setPurpose("APPLICATION");
            form.setConfigurationStatus("ENABLED");
            form.setApplicationType("WEB_SERVER");
            form.setWebLoginMode("BROWSER");
            form.setProtocol("OIDC");
            form.setTenantId(1L);
            form.setUserType("employee");
            var settings = new OAuth2ProtocolSettings();
            settings.setClientAuthenticationMethods("client_secret_basic");
            settings.setAuthorizationGrantTypes("authorization_code");
            settings.setRedirectUris("https://client.example/callback");
            settings.setScopes("openid,profile");
            settings.setRequireProofKey(true);
            settings.setRequireAuthorizationConsent(false);
            form.setSettings(settings);
            return form;
        }
    }
}
