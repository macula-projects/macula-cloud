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

import dev.macula.cloud.iam.authentication.verification.EmailVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.VerificationChannel;
import dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeService;
import dev.macula.cloud.iam.message.mail.LoginMailDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;

/**
 * 托管邮箱验证码适配，和SMS共用状态算法但不共享凭据命名空间。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class EmailCodeService {
    private final VerificationCodeService codes;
    private final LoginMailDispatcher mail;
    private final UserIdentifierNormalizer normalizer;
    private final EmailVerificationProperties settings;

    /**
     * 收件地址合法即可受限发送，不在发码接口枚举本地账号。
     */
    public void send(String email, String remote, String clientId, String clientName) {
        String target = normalize(email);
        codes.send(scope(clientId), target, remote, settings,
                code -> mail.send(target, code, clientName, settings.getTtl()));
    }

    /**
     * 消费码后仍由认证Provider加载当前Client的唯一合法账号。
     */
    public boolean verify(String email, String code, String clientId) {
        if (email == null || code == null) {
            return false;
        }
        try {
            return codes.verify(scope(clientId), normalize(email), code, settings);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 场景由服务端固定，不允许调用方选择绑定或协议grant用途。
     */
    private VerificationCodeScope scope(String clientId) {
        return new VerificationCodeScope(VerificationChannel.EMAIL, VerificationCodePurpose.LOGIN, clientId);
    }

    /**
     * 与账号目录复用相同邮箱规范化规则。
     */
    private String normalize(String value) {
        return normalizer.email(value).orElseThrow(() -> new IllegalArgumentException("邮箱格式无效"));
    }
}
