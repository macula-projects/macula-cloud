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

import dev.macula.cloud.iam.authentication.verification.AbstractVerificationCodeAuthenticationFilter;

/**
 * Sms表单入口，字段边界及可信上下文由共用模板校验。
 *
 * @author Macula
 * @since 6.1.0
 */
public class SmsAuthenticationFilter extends AbstractVerificationCodeAuthenticationFilter<SmsAuthenticationToken> {
    public static final String PATH = "/login/sms";

    /** 固定该通道的入口与标识字段。 */
    public SmsAuthenticationFilter() {
        super(PATH, "phone", 24);
    }

    /** 保留原载体，不更改持久化类型与字段。 */
    @Override
    protected SmsAuthenticationToken createToken(String identifier, String code, String clientId) {
        return new SmsAuthenticationToken(identifier, code, clientId);
    }
}
