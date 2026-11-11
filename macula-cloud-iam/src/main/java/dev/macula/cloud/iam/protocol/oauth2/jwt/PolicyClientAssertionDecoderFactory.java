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
package dev.macula.cloud.iam.protocol.oauth2.jwt;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 按Client当前协议/JWKS/算法隔离有界解码器缓存；密码学与断言claims仍委托框架。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class PolicyClientAssertionDecoderFactory implements JwtDecoderFactory<RegisteredClient> {
    private static final int MAX_DECODERS = 256;
    private final Map<Policy, JwtDecoder> decoders = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * 使用当前受信任配置作为缓存身份，防止同ID旧JWKS或其他协议复用。
     */
    @Override
    public synchronized JwtDecoder createDecoder(RegisteredClient client) {
        var settings = client.getClientSettings();
        var policy = new Policy(client.getId(), settings.getSetting(ApplicationProtocolResolver.EFFECTIVE_PROTOCOL),
                settings.getJwkSetUrl(), settings.getTokenEndpointAuthenticationSigningAlgorithm().getName());
        JwtDecoder decoder = decoders.get(policy);
        if (decoder == null) {
            decoder = new JwtClientAssertionDecoderFactory().createDecoder(client);
            if (decoders.size() >= MAX_DECODERS) {
                decoders.remove(decoders.keySet().iterator().next());
            }
            decoders.put(policy, decoder);
        }
        return decoder;
    }

    /**
     * 缓存身份不含Secret或原始断言，不能从请求覆盖这些配置。
     *
     * @since 6.1.0
     */
    private record Policy(String clientId, String protocol, String jwksUrl, String algorithm) {
    }
}
