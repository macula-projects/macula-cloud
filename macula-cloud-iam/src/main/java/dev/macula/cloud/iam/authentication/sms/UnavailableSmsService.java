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

/**
 * 短信基础设施未配置时的失败关闭实现，登录与绑定都不能误判验证码成功。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class UnavailableSmsService implements SmsService {

    /**
     * 未配置短信时所有绑定用途同样失败关闭。
     */
    @Override
    public boolean verifyCode(String phone, String code, String clientId,
            dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose purpose) {
        return false;
    }

    /**
     * 禁止绑定页面绕过短信基础设施配置。
     */
    @Override
    public void sendCode(String phone, String ip, String clientId,
            dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose purpose) {
        sendCode(phone);
    }

    /**
     * 未配置存储时不能核验验证码。
     */
    @Override
    public boolean verifyCode(String phone, String rawCode) {
        return false;
    }

    /**
     * Client范围不能绕过短信基础设施缺失。
     */
    @Override
    public boolean verifyCode(String phone, String rawCode, String clientId) {
        return false;
    }

    /**
     * 缺少发送设施时明确失败，不伪装为成功。
     */
    @Override
    public void sendCode(String phone) {
        throw new IllegalStateException("短信服务未配置");
    }

    /**
     * 来源地址不改变未配置时的拒绝规则。
     */
    @Override
    public void sendCode(String phone, String remoteAddress) {
        sendCode(phone);
    }

    /**
     * Client限定发送同样失败关闭。
     */
    @Override
    public void sendCode(String phone, String remoteAddress, String clientId) {
        sendCode(phone);
    }
}
