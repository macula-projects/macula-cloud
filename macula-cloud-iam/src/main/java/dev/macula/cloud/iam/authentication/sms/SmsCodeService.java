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
package dev.macula.cloud.iam.authentication.sms;

import java.util.Objects;

import dev.macula.cloud.iam.authentication.verification.SmsVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.VerificationChannel;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeService;
import dev.macula.cloud.iam.message.dispatch.LoginSmsDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;

/**
 * 手机号验证码通道适配；只承担规范化与投递，不复制验证码状态算法。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public final class SmsCodeService implements VerificationCodeChannelService {
    private final VerificationCodeService codes;
    private final LoginSmsDispatcher dispatcher;
    private final UserIdentifierNormalizer normalizer;
    private final SmsVerificationProperties settings;

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
        codes.send(scope, normalized, remoteAddress, settings, code -> dispatcher.send(normalized, code));
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
        return new VerificationCodeScope(VerificationChannel.SMS, context.purpose(), context.clientId());
    }

    /** 复用账号目录的标识规范化，不更改历史存量数据。 */
    private String normalize(String target) {
        return normalizer.mobile(target).orElseThrow(() -> new IllegalArgumentException("手机号格式无效"));
    }
}
