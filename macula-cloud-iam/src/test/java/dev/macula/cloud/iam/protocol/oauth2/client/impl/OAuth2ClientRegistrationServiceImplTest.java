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
package dev.macula.cloud.iam.protocol.oauth2.client.impl;

import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;

import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures;
import dev.macula.cloud.iam.service.application.IamApplicationService;
import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;
import dev.macula.cloud.iam.service.application.IamApplicationTestFixtures;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * OAuth投影从中立聚合及保护凭据组装；验证规范ID、哈希原值、草稿拒绝与Origin权威读取。
 * @author Rain
 * @since 6.1.0
 */
@SpringJUnitConfig(OAuth2ClientRegistrationServiceImplTest.Config.class)
class OAuth2ClientRegistrationServiceImplTest {
    @Autowired private OAuth2ClientRegistrationService service;
    @Autowired private SysIamApplicationProtocolConfigMapper protocols;
    @Autowired private IamApplicationService applications;
    @Autowired private CacheManager caches;
    private final Map<String, SysIamApplicationProtocolConfig> registrations = new ConcurrentHashMap<>();
    private final Map<Long, IamApplicationSnapshot> rows = new ConcurrentHashMap<>();

    /** 每例使用独立合成配置；从生产Bean测试缓存代理。 */
    @BeforeEach
    void setup() {
        reset(protocols, applications);
        rows.clear();
        registrations.clear();
        caches.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX).clear();
        when(protocols.selectByOAuth2Identifier(any())).thenAnswer(call -> registrations.get(call.getArgument(0)));
        when(applications.findById(any())).thenAnswer(call -> call.getArgument(0) == null ? null : rows.get(call.getArgument(0)));
        when(protocols.selectEnabledOAuth2Identifiers()).thenAnswer(call -> List.copyOf(registrations.keySet()));
    }

    /** OAuth身份保持原公开Client ID；保护哈希和有效期只进入协议投影。 */
    @Test
    void preservesCanonicalOAuthIdentityAndOriginalEncodedCredential() {
        var stored = fixture("canonical", "https://a.example");
        registrations.put("CANONICAL", stored);
        stored.setCredentialHash("{synthetic}unchanged-hash");
        var dates = new DateTimeConverter("Asia/Shanghai");
        stored.setCredentialExpiresAt(dates.instantToLocalDateTime(Instant.parse("2035-01-01T00:00:00Z")));
        var result = service.getClientByClientId("CANONICAL");
        assertThat(result.getClientId()).isEqualTo("canonical");
        assertThat(result.getClientSecret()).isEqualTo("{synthetic}unchanged-hash");
        assertThat(result.getClientSecretExpiresAt()).isEqualTo(Instant.parse("2035-01-01T00:00:00Z"));
        assertThat(result.getId()).isEqualTo(stored.getApplicationId());
    }

    /** Origin索引旧值不能覆盖逐Client当前拒绝。 */
    @Test
    void readsCurrentClientEvenWhenIndexContainsOldOrigin() {
        fixture("app", "https://a.example");
        assertThat(service.allowedOrigins()).containsExactly("https://a.example");
        assertThat(service.isAllowedOrigin("app", "https://a.example")).isTrue();
        rows.clear();
        assertThat(service.allowedOrigins()).containsExactly("https://a.example");
        assertThat(service.isAllowedOrigin("app", "https://a.example")).isFalse();
    }

    /** 缺失登记、空标识与非法聚合均不能创建RegisteredClient。 */
    @Test
    void rejectsMissingAndInvalidAggregate() {
        assertThat(service.getClientByClientId(null)).isNull();
        assertThat(service.getClientByClientId(" ")).isNull();
        assertThat(service.getClientByClientId("missing")).isNull();
        fixture("draft", "https://a.example");
        when(applications.findById(any())).thenThrow(new IllegalArgumentException("synthetic-invalid"));
        assertThat(service.getClientByClientId("draft")).isNull();
    }

    /** 同时未命中只加载一次非秘密索引，多个Client的Origin去重。 */
    @Test
    void concurrentMissLoadsOneOriginIndex() throws Exception {
        fixture("a", "https://a.example,https://shared.example");
        fixture("b", "https://shared.example,https://b.example");
        var loads = new AtomicInteger();
        when(protocols.selectEnabledOAuth2Identifiers()).thenAnswer(call -> {
            loads.incrementAndGet();
            return List.of("a", "b");
        });
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CyclicBarrier start = new CyclicBarrier(8);
        try {
            var results = IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.allowedOrigins();
            })).toList();
            for (var result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).containsExactlyInAnyOrder(
                    "https://a.example", "https://shared.example", "https://b.example");
            }
            assertThat(loads.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 模拟已完成中立聚合验证的数据，与受保护协议行保持同一内部ID。 */
    private SysIamApplicationProtocolConfig fixture(String reference, String origins) {
        var legacy = new OAuth2ClientRegistration();
        legacy.setId((long) rows.size() + 1);
        legacy.setClientId(reference);
        legacy.setAllowedOrigins(origins);
        ProtocolTestFixtures.configure(legacy);
        rows.put(legacy.getId(), IamApplicationTestFixtures.snapshot(legacy));
        var stored = new SysIamApplicationProtocolConfig();
        stored.setApplicationId(legacy.getId());
        stored.setProtocol("OIDC");
        stored.setSchemaVersion(2);
        stored.setEnabled(true);
        stored.setProtocolIdentifier(reference);
        registrations.put(reference, stored);
        return stored;
    }

    /** 仅启用真实缓存代理；存储由本例合成Mapper隔离。 @since 6.1.0 */
    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class Config {
        /** 合成保护登记读端口。 */
        @Bean SysIamApplicationProtocolConfigMapper protocols() { return mock(SysIamApplicationProtocolConfigMapper.class); }
        /** 合成公共聚合端口。 */
        @Bean IamApplicationService applications() { return mock(IamApplicationService.class); }
        /** 每例在内存验证缓存并发，不声称跨实例证明。 */
        @Bean CacheManager caches() { return new ConcurrentMapCacheManager(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX); }
        /** 装配生产投影服务。 */
        @Bean OAuth2ClientRegistrationService service(SysIamApplicationProtocolConfigMapper protocols,
            IamApplicationService applications) {
            return new OAuth2ClientRegistrationServiceImpl(protocols, applications, new DateTimeConverter("Asia/Shanghai"));
        }
    }
}
