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

    private String normalize(String phone) {
        return normalizer.mobile(phone).orElseThrow(() -> new IllegalArgumentException("手机号格式无效"));
    }
}
