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
 * 验证码用途是服务端固定域，不能由请求改写。
 * @author Rain
 * @since 6.1.0
 */
public enum SmsCodePurpose {
    LOGIN("login"), IDENTITY_BINDING("identity-binding");
    private final String value;
    /** 保存稳定Redis域值。 */
    SmsCodePurpose(String value) { this.value = value; }
    /** 返回显式用途。 */
    public String value() { return value; }
}
