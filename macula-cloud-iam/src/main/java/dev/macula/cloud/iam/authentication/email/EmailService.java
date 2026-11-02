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
package dev.macula.cloud.iam.authentication.email;

import dev.macula.cloud.iam.message.EmailVerificationCodeStore;
import dev.macula.cloud.iam.message.EmailVerificationProperties;
import dev.macula.cloud.iam.message.LoginMailDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 签发并核验绑定到 OAuth Client 的邮箱登录验证码。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class EmailService {
    private final EmailVerificationCodeStore store;
    private final EmailVerificationProperties properties;
    private final LoginMailDispatcher dispatcher;
    private final UserIdentifierNormalizer normalizer;

    public void sendCode(String email, String remoteAddress, String clientId, String clientName) {
        String normalized = normalize(email);
        String code = store.issue(normalized, remoteAddress, clientId);
        dispatcher.send(normalized, code, clientName, properties.getTtl());
        store.activate(normalized, code, clientId);
    }

    public boolean verifyCode(String email, String code, String clientId) {
        if (code == null || clientId == null) {
            return false;
        }
        return normalizer.email(email).map(value -> store.consume(value, code, clientId)).orElse(false);
    }

    public String normalize(String email) {
        return normalizer.email(email).orElseThrow(() -> new IllegalArgumentException("邮箱格式无效"));
    }
}
