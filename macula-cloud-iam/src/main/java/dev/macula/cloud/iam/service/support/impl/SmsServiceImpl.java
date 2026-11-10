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

package dev.macula.cloud.iam.service.support.impl;

import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.message.LoginSmsDispatcher;
import dev.macula.cloud.iam.message.SmsVerificationCodeStore;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;

/**
 * {@code SmsServiceImpl} 手机号验证服务
 *
 * @author rain
 * @since 2023/4/12 19:34
 */
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {
    private final SmsVerificationCodeStore store;
    private final LoginSmsDispatcher dispatcher;
    private final UserIdentifierNormalizer normalizer;

    /** 实例仅在存储和发送器均已装配时创建。 */
    @Override
    public boolean isAvailable() { return true; }

    @Override
    public boolean verifyCode(String phone, String rawCode) {
        if (phone == null || rawCode == null) {
            return false;
        }
        try {
            return store.consume(normalize(phone), rawCode);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public boolean verifyCode(String phone, String rawCode, String clientId) {
        if (phone == null || rawCode == null || clientId == null) {
            return false;
        }
        try {
            return store.consume(normalize(phone), rawCode, clientId);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void sendCode(String phone) {
        sendCode(phone, "unknown");
    }

    @Override
    public void sendCode(String phone, String remoteAddress) {
        String normalized = normalize(phone);
        String source = remoteAddress == null ? "unknown" : remoteAddress;
        String code = store.issue(normalized, source);
        dispatcher.send(normalized, code);
        store.activate(normalized, code);
    }

    @Override
    public void sendCode(String phone, String remoteAddress, String clientId) {
        String normalized = normalize(phone);
        String source = remoteAddress == null ? "unknown" : remoteAddress;
        String code = store.issue(normalized, source, clientId);
        dispatcher.send(normalized, code);
        store.activate(normalized, code, clientId);
    }

    /** 绑定证明不会消费其他Client或托管登录用途验证码。 */
    @Override
    public boolean verifyCode(String phone, String code, String clientId,
        dev.macula.cloud.iam.authentication.sms.SmsCodePurpose purpose) {
        if (phone == null || code == null || clientId == null || purpose == null) { return false; }
        try { return store.consume(normalize(phone), code, clientId, purpose); }
        catch (IllegalArgumentException ex) { return false; }
    }

    /** 用途域贯穿申请、供应商发送与激活。 */
    @Override
    public void sendCode(String phone, String remoteAddress, String clientId,
        dev.macula.cloud.iam.authentication.sms.SmsCodePurpose purpose) {
        String normalized = normalize(phone);
        String code = store.issue(normalized, remoteAddress == null ? "unknown" : remoteAddress, clientId, purpose);
        dispatcher.send(normalized, code);
        store.activate(normalized, code, clientId, purpose);
    }

    private String normalize(String phone) {
        return normalizer.mobile(phone).orElseThrow(() -> new IllegalArgumentException("手机号格式无效"));
    }
}
