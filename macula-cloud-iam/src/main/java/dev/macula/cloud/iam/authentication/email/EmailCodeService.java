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

import java.util.Objects;

import dev.macula.cloud.iam.authentication.verification.EmailVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.VerificationChannel;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeService;
import dev.macula.cloud.iam.message.dispatch.LoginMailDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;

/**
 * 邮箱验证码通道适配；只承担规范化与投递，不复制验证码状态算法。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public final class EmailCodeService implements VerificationCodeChannelService {
    private final VerificationCodeService codes;
    private final LoginMailDispatcher dispatcher;
    private final UserIdentifierNormalizer normalizer;
    private final EmailVerificationProperties settings;

    /** 完整设施可用，渠道凭据仍在提交时校验。 */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /** 规范化后提交投递，额度和激活时序由共享内核控制。 */
    @Override
    public void sendCode(String target, String remoteAddress, VerificationCodeContext context) {
        VerificationCodeScope scope = scope(context);
        String normalized = normalize(target);
        codes.send(scope, normalized, remoteAddress, settings, code -> dispatcher.send(normalized, code, context.clientName(), settings.getTtl()));
    }

    /** 错误标识或非法用途不进入消费，不查询账号存在性。 */
    @Override
    public boolean verifyCode(String target, String rawCode, VerificationCodeContext context) {
        if (target == null || rawCode == null || context == null) {
            return false;
        }
        try {
            return codes.verify(scope(context), normalize(target), rawCode, settings);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 固定通道，保留原Client/用途摘要合同。 */
    private VerificationCodeScope scope(VerificationCodeContext context) {
        Objects.requireNonNull(context, "验证码上下文不能为空");
        if (context.purpose() != VerificationCodePurpose.LOGIN) {
            throw new IllegalArgumentException("邮箱不支持此验证码用途");
        }
        return new VerificationCodeScope(VerificationChannel.EMAIL, context.purpose(), context.clientId());
    }

    /** 复用账号目录的标识规范化，不更改历史存量数据。 */
    private String normalize(String target) {
        return normalizer.email(target).orElseThrow(() -> new IllegalArgumentException("邮箱格式无效"));
    }
}
