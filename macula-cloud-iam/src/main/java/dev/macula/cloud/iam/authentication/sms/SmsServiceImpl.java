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

import dev.macula.cloud.iam.authentication.verification.SmsVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.VerificationChannel;
import dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeService;
import dev.macula.cloud.iam.message.sms.LoginSmsDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;

/**
 * 短信认证适配器；规范化手机号，验证码生命周期由共用服务执行。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {
    private final VerificationCodeService codes;
    private final LoginSmsDispatcher dispatcher;
    private final UserIdentifierNormalizer normalizer;
    private final SmsVerificationProperties settings;

    /**
     * 装配完整设施时提供短信能力，具体渠道配置在提交时验证。
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * 保留无Client门户的原browser域。
     */
    @Override
    public boolean verifyCode(String phone, String code) {
        return verify(phone, code, null, VerificationCodePurpose.LOGIN);
    }

    /**
     * 显式Client验证不能退回门户域。
     */
    @Override
    public boolean verifyCode(String phone, String code, String clientId) {
        return clientId != null && verify(phone, code, clientId, VerificationCodePurpose.LOGIN);
    }

    /**
     * 用途是服务端常量，绑定码与登录码互不消费。
     */
    @Override
    public boolean verifyCode(String phone, String code, String clientId, VerificationCodePurpose purpose) {
        return clientId != null && verify(phone, code, clientId, purpose);
    }

    /**
     * 保留原默认来源标记。
     */
    @Override
    public void sendCode(String phone) {
        sendCode(phone, "unknown");
    }

    /**
     * 门户发码仍使用原SMS协议域。
     */
    @Override
    public void sendCode(String phone, String remote) {
        send(phone, remote, null, VerificationCodePurpose.LOGIN);
    }

    /**
     * 当前Client来自受控策略，而非发码请求自报。
     */
    @Override
    public void sendCode(String phone, String remote, String clientId) {
        send(phone, remote, clientId, VerificationCodePurpose.LOGIN);
    }

    /**
     * 绑定证明与托管登录共用发送限流但隔离凭据。
     */
    @Override
    public void sendCode(String phone, String remote, String clientId, VerificationCodePurpose purpose) {
        send(phone, remote, clientId, purpose);
    }

    /**
     * 规范化失败不进入Redis，也不把它当成有效凭据。
     */
    private boolean verify(String phone, String code, String clientId, VerificationCodePurpose purpose) {
        if (phone == null || code == null || purpose == null) {
            return false;
        }
        try {
            return codes.verify(scope(clientId, purpose), normalize(phone), code, settings);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 发送提交后才激活，失败不会取消滥用限流。
     */
    private void send(String phone, String remote, String clientId, VerificationCodePurpose purpose) {
        String normalized = normalize(phone);
        codes.send(scope(clientId, purpose), normalized, remote, settings, code -> dispatcher.send(normalized, code));
    }

    /**
     * 通道由适配器固定，不能由HTTP参数切换。
     */
    private VerificationCodeScope scope(String clientId, VerificationCodePurpose purpose) {
        return new VerificationCodeScope(VerificationChannel.SMS, purpose, clientId);
    }

    /**
     * 复用平台标识规范化，不猜测区号。
     */
    private String normalize(String phone) {
        return normalizer.mobile(phone).orElseThrow(() -> new IllegalArgumentException("手机号格式无效"));
    }
}
