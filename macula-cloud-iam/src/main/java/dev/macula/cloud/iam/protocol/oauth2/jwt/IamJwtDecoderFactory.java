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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 将受信任JWK来源适配为框架JwtDecoder，签名算法与声明验证职责保持原合同。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class IamJwtDecoderFactory {
    /**
     * 仅提供框架适配。
     */
    private IamJwtDecoderFactory() {
    }

    /**
     * 保留RS256验签；声明验证由NimbusJwtDecoder的Spring验证链执行。
     */
    public static JwtDecoder create(JWKSource<SecurityContext> keys) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });
        return new NimbusJwtDecoder(processor);
    }
}
