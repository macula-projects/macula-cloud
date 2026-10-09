/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.util.Map;
import org.springframework.security.oauth2.jwt.*;

/**
 * 使用固定 IAM JWKS 验证 ID Token，不把 JWT 解码等同于验证。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundOidcVerifier {
    private final PlaygroundProtocolClient client;
    public PlaygroundOidcVerifier(PlaygroundProtocolClient client) { this.client = client; }

    public Jwt verify(String token, String clientId, String nonce) {
        return verify(token, clientId, nonce, false);
    }

    public Jwt verify(String token, String clientId, String nonce, boolean refresh) {
        var keys = client.call(PlaygroundProtocolClient.Endpoint.JWKS, Map.of(), null, null, null);
        if (!keys.successful()) throw new IllegalStateException("Cannot verify ID Token: JWKS unavailable");
        try {
            var processor = new DefaultJWTProcessor<SecurityContext>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(JWKSet.parse(keys.body()))));
            processor.setJWTClaimsSetVerifier((claims, context) -> { });
            var decoder = new NimbusJwtDecoder(processor);
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(client.issuer()));
            Jwt jwt = decoder.decode(token);
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getSubject() == null
                || !jwt.getAudience().contains(clientId)
                || (jwt.getAudience().size() > 1 && !clientId.equals(jwt.getClaimAsString("azp")))
                || (jwt.hasClaim("azp") && !clientId.equals(jwt.getClaimAsString("azp")))
                || nonce == null || ((!refresh || jwt.hasClaim("nonce")) && !nonce.equals(jwt.getClaimAsString("nonce")))) {
                throw new IllegalArgumentException("Invalid ID Token binding");
            }
            return jwt;
        } catch (Exception ex) {
            // Never attach a parser exception that might contain the compact token.
            throw new IllegalArgumentException("ID Token signature or claims validation failed");
        }
    }
}
