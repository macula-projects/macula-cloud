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
package dev.macula.cloud.iam.authentication.verification;

/**
 * 验证码通道的稳定命名空间，不能使用枚举名称代替既有Redis协议。
 *
 * @author Macula
 * @since 6.1.0
 */
public enum VerificationChannel {
    SMS("sms"), EMAIL("email-login");
    private final String value;

    /**
     * 固定键与HMAC用途域。
     */
    VerificationChannel(String value) {
        this.value = value;
    }

    /**
     * 返回稳定存储域。
     */
    public String value() {
        return value;
    }
}
