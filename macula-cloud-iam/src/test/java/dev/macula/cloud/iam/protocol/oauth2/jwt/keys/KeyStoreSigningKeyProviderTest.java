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
package dev.macula.cloud.iam.protocol.oauth2.jwt.keys;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JKS适配器的环境与配置边界；测试只使用仓库原有公开演示密钥。
 *
 * @author Macula
 * @since 6.1.0
 */
class KeyStoreSigningKeyProviderTest {
    /**
     * 演示回退仅在既有local/docker profile内有效。
     */
    @Test
    void localAndDockerKeepOriginalDemoFallback() {
        for (String profile : new String[]{"local", "docker"}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            assertThat(
                    new KeyStoreSigningKeyProvider(new SigningKeyProperties(), new DefaultResourceLoader(), environment)
                            .loadSigningKey().isPrivate()).isTrue();
        }
    }

    /**
     * 显式配置错误不允许退回演示密钥。
     */
    @Test
    void explicitMissingResourceFailsClosedEvenLocally() {
        var properties = new SigningKeyProperties();
        properties.setLocation("classpath:missing-signing-key.jks");
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatThrownBy(() -> new KeyStoreSigningKeyProvider(properties, new DefaultResourceLoader(),
                environment).loadSigningKey())
                .isInstanceOf(IllegalStateException.class).hasMessage("IAM signing key could not be loaded");
    }
}
