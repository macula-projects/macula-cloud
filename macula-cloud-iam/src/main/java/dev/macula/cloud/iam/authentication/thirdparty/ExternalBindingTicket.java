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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.enums.UserTypeEnum;

/**
 * 服务端保存的外部身份绑定票据内容，原始 ticket 永不进入该对象。
 *
 * @author Rain
 * @since 6.1.0
 */
public record ExternalBindingTicket(String clientId, Long tenantId, UserTypeEnum userType,
                                    BindingType bindingType, String providerType, String subject,
                                    String mobile, int failedAttempts) {
    /**
     * 待绑定数据类别。
     *
     * @since 6.1.0
     */
    public enum BindingType {
        EXTERNAL_IDENTITY,
        MOBILE
    }
}
