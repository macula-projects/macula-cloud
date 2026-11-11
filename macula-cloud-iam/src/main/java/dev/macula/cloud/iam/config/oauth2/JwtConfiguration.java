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

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.macula.cloud.iam.protocol.oauth2.jwt.IamJwtDecoderFactory;
import dev.macula.cloud.iam.protocol.oauth2.jwt.keys.KeyStoreSigningKeyProvider;
import dev.macula.cloud.iam.protocol.oauth2.jwt.keys.SigningKeyProperties;
import dev.macula.cloud.iam.protocol.oauth2.jwt.keys.SigningKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * 协议侧JWT基础设施装配；仅提供签名/JWK/解码器，不注册用户登录Provider或Filter。
 *
 * @author Macula
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SigningKeyProperties.class)
public class JwtConfiguration {
    /**
     * 默认使用JKS来源，可按接口替换，配置类不感知密钥加载细节。
     */
    @Bean
    @ConditionalOnMissingBean(SigningKeyProvider.class)
    SigningKeyProvider signingKeyProvider(SigningKeyProperties properties, ResourceLoader resources,
            Environment environment) {
        return new KeyStoreSigningKeyProvider(properties, resources, environment);
    }

    /**
     * 同一个不可变JWK快照用于签名和公钥选择，不在每次请求重读密钥。
     */
    @Bean
    @ConditionalOnMissingBean(JWKSource.class)
    JWKSource<SecurityContext> jwkSource(SigningKeyProvider keys) {
        return new ImmutableJWKSet<>(new JWKSet(keys.loadSigningKey()));
    }

    /**
     * 使用框架JwtDecoder接口，部署方可替换适配器而无需修改装配代码。
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnClass(BearerTokenAuthenticationFilter.class)
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> keys) {
        return IamJwtDecoderFactory.create(keys);
    }
}
