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
package dev.macula.cloud.iam.service.client.impl;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Origin 固定索引缓存与按 Client 权威读取。
 *
 * @author Rain
 * @since 6.1.0
 */
@SpringJUnitConfig(SysOAuth2ClientServiceImplTest.Config.class)
class SysOAuth2ClientServiceImplTest {
    private final java.util.Map<String, SysOAuth2Client> rows = new java.util.HashMap<>();
    @Autowired
    private SysOAuth2ClientService service;
    @Autowired
    private SysOAuth2ClientMapper mapper;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols;
    @Autowired
    private dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper externalProviders;

    /**
     * 清理缓存及Mapper，协议与来源快照均来自本例数据。
     */
    @BeforeEach
    void resetFixture() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig.class);
        reset(mapper, protocols, externalProviders);
        when(externalProviders.selectList(any())).thenReturn(List.of());
        rows.clear();
        when(mapper.selectOne(any(), anyBoolean())).thenAnswer(call -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) call.getArgument(0);
            if (wrapper == null) {
                return null;
            }
            wrapper.getSqlSegment();
            return rows.get(wrapper.getParamNameValuePairs().values().iterator().next());
        });
        when(protocols.selectList(any())).thenAnswer(call -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) call.getArgument(0);
            wrapper.getSqlSegment();
            Object id = wrapper.getParamNameValuePairs().values().iterator().next();
            var row = rows.values().stream().filter(value -> java.util.Objects.equals(value.getId(), id)).findFirst()
                    .orElseThrow();
            return row.getProtocolConfigurations().stream().map(configuration -> {
                var value = new dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig();
                value.setProtocol(configuration.protocol());
                value.setSchemaVersion(configuration.schemaVersion());
                value.setEnabled(configuration.enabled());
                value.setConfiguration(tools.jackson.databind.json.JsonMapper.builder().build()
                        .writeValueAsString(configuration.settings()));
                return value;
            }).toList();
        });
        cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY).clear();
        cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX).clear();
    }

    @Test
    void concurrentMissLoadsOneNormalizedOriginIndex() throws Exception {
        SysOAuth2Client first = client("a", "https://a.example,https://shared.example");
        SysOAuth2Client second = client("b", "https://shared.example,https://b.example");
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            loadStarted.countDown();
            assertThat(releaseLoad.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(first, second);
        });
        int callers = 8;
        CyclicBarrier start = new CyclicBarrier(callers);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Set<String>>> results = java.util.stream.IntStream.range(0, callers)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return service.allowedOrigins();
                    })).toList();
            assertThat(loadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releaseLoad.countDown();
            for (Future<Set<String>> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS))
                        .containsExactlyInAnyOrder("https://a.example", "https://shared.example", "https://b.example");
            }
            verify(mapper, times(1)).selectList(any());
        } finally {
            releaseLoad.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cacheLoadFailureIsNotConvertedToAllow() {
        when(mapper.selectList(any())).thenThrow(new IllegalStateException("fixture database unavailable"));

        assertThatThrownBy(service::allowedOrigins).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void authoritativeClientLookupIgnoresStaleGlobalIndex() {
        org.mockito.Mockito.doReturn(client("client-a", "https://current.example")).when(mapper)
                .selectOne(any(), anyBoolean());

        assertThat(service.isAllowedOrigin("client-a", "https://current.example")).isTrue();
        assertThat(service.isAllowedOrigin("client-a", "https://revoked.example")).isFalse();
    }

    @Test
    void originIndexDoesNotCollideWithReservedLookingClientId() {
        String sharedKey = OAuth2ClientCacheNames.ORIGIN_INDEX_KEY;
        cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)
                .put(sharedKey, "registered-client-marker");
        when(mapper.selectList(any())).thenReturn(List.of(client("client-a", "https://a.example")));

        assertThat(service.allowedOrigins()).containsExactly("https://a.example");
        assertThat(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)
                .get(sharedKey, String.class)).isEqualTo("registered-client-marker");
        assertThat(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)
                .get(sharedKey, Set.class)).containsExactly("https://a.example");
    }

    /**
     * 来源集合装配为不可变快照，Native的旧浏览器引用不会关闭合法内置授权。
     */
    @Test
    void loadsCurrentBindingsWithoutGloballyDisablingNativeHostedLogin() {
        var client = client("native", "https://native.example");
        client.setApplicationType("NATIVE");
        client.setClientAuthenticationMethods("none");
        client.setAuthorizationGrantTypes("authorization_code");
        client.setExternalCodeTypes("WECOM_APP_CODE");
        client.setBrowserProviderBindings("{\"GITHUB\":1}");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        var binding = new dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider();
        binding.setCodeType("WECOM_APP_CODE");
        binding.setIdentityProviderId(17L);
        when(externalProviders.selectList(any())).thenReturn(List.of(binding));
        var current = service.getClientByClientId("native");
        assertThat(current).isNotNull();
        assertThat(current.getExternalProviderBindings()).containsEntry("WECOM_APP_CODE", 17L);
        assertThat(current.getBrowserProviderBindings()).isEqualTo("{\"GITHUB\":1}");
        assertThatThrownBy(() -> current.getExternalProviderBindings().put("other", 18L)).isInstanceOf(
                UnsupportedOperationException.class);
    }

    /**
     * 损坏的重复映射不能按列表第一条选择固定来源。
     */
    @Test
    void duplicateMappingsFailClosed() {
        client("duplicate", "https://app.example");
        var first = new dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider();
        first.setCodeType("WECOM_APP_CODE");
        first.setIdentityProviderId(17L);
        var second = new dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider();
        second.setCodeType("WECOM_APP_CODE");
        second.setIdentityProviderId(18L);
        when(externalProviders.selectList(any())).thenReturn(List.of(first, second));
        assertThat(service.getClientByClientId("duplicate")).isNull();
    }

    private SysOAuth2Client client(String clientId, String origins) {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId(clientId);
        client.setConfigurationStatus("ENABLED");
        client.setEnabledProtocols("OAUTH2_1");
        client.setDefaultProtocol("OAUTH2_1");
        client.setAllowedOrigins(origins);
        client.setId((long) clientId.hashCode());
        client.setAuthorizationGrantTypes("client_credentials");
        client.setClientAuthenticationMethods("client_secret_basic");
        client.setScopes("api.read");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        rows.put(clientId, client);
        return client;
    }

    /**
     * 测试缓存代理与 Mapper 装配。
     *
     * @author Rain
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class Config {
        @Bean
        SysOAuth2ClientMapper mapper() {
            return mock(SysOAuth2ClientMapper.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConstants.OAUTH2_CLIENT_CACHE_KEY,
                    OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX);
        }

        @Bean
        dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols() {
            return mock(dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper.class);
        }

        /**
         * 生产读取必须提供固定来源Mapper，不从基础缓存推断关联。
         */
        @Bean
        dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper externalProviders() {
            return mock(dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper.class);
        }

        /**
         * 使用生产来源装配构造，缓存代理仍沿用现有配置。
         */
        @Bean
        SysOAuth2ClientService clientService(SysOAuth2ClientMapper mapper,
                dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols,
                dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper externalProviders) {
            SysOAuth2ClientServiceImpl service = new SysOAuth2ClientServiceImpl(protocols, externalProviders);
            ReflectionTestUtils.setField(service, "baseMapper", mapper);
            return service;
        }
    }
}
