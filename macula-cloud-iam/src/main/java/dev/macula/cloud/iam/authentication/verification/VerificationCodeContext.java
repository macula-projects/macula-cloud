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

import java.util.Objects;

/**
 * 服务端确定的验证码用途与Client上下文，不接受HTTP直接绑定，也不携带凭据。
 *
 * @author Macula
 * @since 6.1.0
 */
public record VerificationCodeContext(String clientId, VerificationCodePurpose purpose, String clientName) {
    /** 绑定证明不得退回无Client的门户域。 */
    public VerificationCodeContext {
        Objects.requireNonNull(purpose, "验证码用途不能为空");
        if (purpose == VerificationCodePurpose.IDENTITY_BINDING && (clientId == null || clientId.isBlank())) {
            throw new IllegalArgumentException("绑定验证码缺少可信Client");
        }
    }

    /** 创建登录上下文；空Client仅表示原有门户兼容域。 */
    public static VerificationCodeContext login(String clientId) {
        return login(clientId, null);
    }

    /** 显示名称仅来自服务端Client配置，用于邮件模板。 */
    public static VerificationCodeContext login(String clientId, String clientName) {
        return new VerificationCodeContext(clientId, VerificationCodePurpose.LOGIN, clientName);
    }

    /** 绑定用途只能由已核验的服务端流程选择。 */
    public static VerificationCodeContext binding(String clientId) {
        return new VerificationCodeContext(clientId, VerificationCodePurpose.IDENTITY_BINDING, null);
    }
}
