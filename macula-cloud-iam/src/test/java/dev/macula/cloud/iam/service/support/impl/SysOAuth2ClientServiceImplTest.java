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
package dev.macula.cloud.iam.service.support.impl;

import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private SysOAuth2ClientService service;
    @Autowired
    private SysOAuth2ClientMapper mapper;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetFixture() {
        reset(mapper);
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
        when(mapper.selectOne(any(), anyBoolean()))
            .thenReturn(client("client-a", "https://current.example"));

        assertThat(service.isAllowedOrigin("client-a", "https://current.example")).isTrue();
        assertThat(service.isAllowedOrigin("client-a", "https://revoked.example")).isFalse();
    }

    @Test
    void originIndexDoesNotCollideWithReservedLookingClientId() {
        String sharedKey = "__allowed_origins__";
        cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)
            .put(sharedKey, "registered-client-marker");
        when(mapper.selectList(any())).thenReturn(List.of(client("client-a", "https://a.example")));

        assertThat(service.allowedOrigins()).containsExactly("https://a.example");
        assertThat(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)
            .get(sharedKey, String.class)).isEqualTo("registered-client-marker");
        assertThat(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)
            .get(sharedKey, Set.class)).containsExactly("https://a.example");
    }

    private SysOAuth2Client client(String clientId, String origins) {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId(clientId);
        client.setAllowedOrigins(origins);
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
        SysOAuth2ClientService clientService(SysOAuth2ClientMapper mapper) {
            SysOAuth2ClientServiceImpl service = new SysOAuth2ClientServiceImpl();
            ReflectionTestUtils.setField(service, "baseMapper", mapper);
            return service;
        }
    }
}
