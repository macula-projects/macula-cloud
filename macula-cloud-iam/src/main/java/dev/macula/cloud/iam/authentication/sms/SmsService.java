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
package dev.macula.cloud.iam.authentication.sms;

/**
 * 校验手机号短信验证码的服务契约。
 *
 * @author n1
 * @since 6.1.0
 */
public interface SmsService {

    /**
     * verify code
     *
     * @param phone   phone
     * @param rawCode rawCode
     * @return isVerified
     */
    boolean verifyCode(String phone, String rawCode);

    /**
     * 校验绑定到 OAuth Client 的短信验证码。
     *
     * @param phone 手机号
     * @param rawCode 验证码
     * @param clientId OAuth Client ID
     * @return 是否通过
     */
    boolean verifyCode(String phone, String rawCode, String clientId);

    /**
     * 发送验证码
     * @param phone 手机号
     */
    void sendCode(String phone);

    /**
     * 发送验证码并对来源地址执行限流。
     * @param phone 手机号
     * @param remoteAddress 来源地址
     */
    void sendCode(String phone, String remoteAddress);

    /**
     * 发送绑定到 OAuth Client 的协议短信验证码。
     *
     * @param phone 手机号
     * @param remoteAddress 来源地址
     * @param clientId OAuth Client ID
     */
    void sendCode(String phone, String remoteAddress, String clientId);
}
