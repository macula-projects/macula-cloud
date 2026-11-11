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
 * 验证码在通道、用途和可信Client内隔离；门户上下文保持原browser值。
 *
 * @author Macula
 * @since 6.1.0
 */
public record VerificationCodeScope(VerificationChannel channel, VerificationCodePurpose purpose, String clientId) {
    /**
     * 通道及用途必须由服务端确定，Client来自托管策略。
     */
    public VerificationCodeScope {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(purpose);
    }

    /**
     * 返回与旧SMS完全一致的Client域。
     */
    public String clientContext() {
        return clientId == null || clientId.isBlank() ? "browser" : "client\0" + clientId;
    }

    /**
     * 绑定用途不能消费登录验证码。
     */
    public String context() {
        return purpose.value() + "\0" + clientContext();
    }
}
