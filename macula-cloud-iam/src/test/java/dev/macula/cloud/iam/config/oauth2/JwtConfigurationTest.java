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
package dev.macula.cloud.iam.config.oauth2;

import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import dev.macula.cloud.iam.protocol.oauth2.jwt.keys.KeyStoreSigningKeyProvider;
import dev.macula.cloud.iam.protocol.oauth2.jwt.keys.SigningKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证JWT配置只负责装配，可替换密钥端口，生产环境仍禁止演示回退。
 *
 * @author Macula
 * @since 6.1.0
 */
class JwtConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(
            JwtConfiguration.class);

    /**
     * 原local配置继续加载演示密钥，生成框架解码器。
     */
    @Test
    void localConfigurationUsesDefaultKeyStoreAdapter() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local")).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SigningKeyProvider.class).hasSingleBean(JwtDecoder.class);
            assertThat(context.getBean(SigningKeyProvider.class)).isInstanceOf(KeyStoreSigningKeyProvider.class);
        });
    }

    /**
     * 未提供外部密钥时生产环境必须启动失败。
     */
    @Test
    void productionCannotFallBackToDemoKey() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prd"))
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 替换端口不需要改配置或接触JKS；JWK装配只加载一次密钥。
     */
    @Test
    void customSigningProviderReplacesInfrastructureAdapter() {
        new ApplicationContextRunner().withUserConfiguration(CustomKeys.class, JwtConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prd")).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SigningKeyProvider.class);
                    assertThat(context.getBean(SigningKeyProvider.class)).isNotInstanceOf(KeyStoreSigningKeyProvider.class);
                    assertThat(context.getBean(AtomicInteger.class).get()).isEqualTo(1);
                });
    }

    /**
     * 合成密钥来源，仅用于验证接口替换，不接触本地或业务密钥。
     *
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomKeys {
        /**
         * 记录装配阶段加载次数。
         */
        @Bean
        AtomicInteger loads() {
            return new AtomicInteger();
        }

        /**
         * 生产profile也可使用显式受信任实现，不触发演示回退。
         */
        @Bean
        SigningKeyProvider customKeys(AtomicInteger loads) throws Exception {
            var key = new RSAKeyGenerator(2048).keyID("synthetic-key").generate();
            return () -> {
                loads.incrementAndGet();
                return key;
            };
        }
    }
}
