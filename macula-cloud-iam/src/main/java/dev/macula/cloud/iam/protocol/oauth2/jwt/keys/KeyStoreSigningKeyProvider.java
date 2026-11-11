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

import java.security.KeyStore;
import java.util.Arrays;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;

/**
 * JKS资源适配器；演示密钥只在原local/docker边界内回退，其他环境必须外部配置。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class KeyStoreSigningKeyProvider implements SigningKeyProvider {
    private static final String DEMO_LOCATION = "classpath:jwk/jose.jks";
    private static final String DEMO_PASSWORD = "felord.cn";
    private final SigningKeyProperties properties;
    private final ResourceLoader resources;
    private final Environment environment;

    /**
     * 来源配置只能由受信任的服务端环境注入。
     */
    public KeyStoreSigningKeyProvider(SigningKeyProperties properties, ResourceLoader resources,
            Environment environment) {
        this.properties = properties;
        this.resources = resources;
        this.environment = environment;
    }

    /**
     * 加载后清除临时PIN数组，不缓存请求数据或把密钥材料加入异常消息。
     */
    @Override
    public RSAKey loadSigningKey() {
        boolean demo = properties.getLocation().isBlank();
        if (demo && !environment.acceptsProfiles(Profiles.of("local", "docker"))) {
            throw new IllegalStateException("Configure an external IAM signing key outside local development");
        }
        String location = demo ? DEMO_LOCATION : properties.getLocation();
        char[] pin = (demo ? DEMO_PASSWORD : properties.getPassword()).toCharArray();
        try (var input = resources.getResource(location).getInputStream()) {
            KeyStore store = KeyStore.getInstance("jks");
            store.load(input, pin);
            RSAKey key = RSAKey.load(store, properties.getAlias(), pin);
            if (key == null || !key.isPrivate()) {
                throw new IllegalStateException("IAM signing key is not an RSA private key");
            }
            return key;
        } catch (Exception failure) {
            throw new IllegalStateException("IAM signing key could not be loaded", failure);
        } finally {
            Arrays.fill(pin, '\0');
        }
    }
}
