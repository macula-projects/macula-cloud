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

import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通过真实RSA签验与过期拒绝证明解码适配保留原算法和Spring声明验证链。
 *
 * @author Macula
 * @since 6.1.0
 */
class IamJwtDecoderFactoryTest {
    /**
     * 正确密钥通过，过期和其他签名密钥拒绝。
     */
    @Test
    void validatesSignatureAndSpringTimestampPolicy() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("synthetic-key").generate();
        var decoder = IamJwtDecoderFactory.create(new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())));
        assertThat(decoder.decode(sign(key, Instant.now().plusSeconds(300))).getSubject()).isEqualTo("fixture-user");
        assertThatThrownBy(() -> decoder.decode(sign(key, Instant.now().minusSeconds(300))))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        var wrong = new RSAKeyGenerator(2048).keyID("synthetic-key").generate();
        assertThatThrownBy(() -> decoder.decode(sign(wrong, Instant.now().plusSeconds(300))))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    /**
     * 合成令牌只在测试内签发，不复用生产材料。
     */
    private String sign(com.nimbusds.jose.jwk.RSAKey key, Instant expiry) throws Exception {
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().subject("fixture-user").issueTime(Date.from(Instant.now().minusSeconds(600)))
                        .expirationTime(Date.from(expiry)).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
