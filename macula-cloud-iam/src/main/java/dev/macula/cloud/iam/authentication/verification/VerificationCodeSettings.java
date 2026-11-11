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

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 验证码共享参数；不生成可能泄露摘要密钥的toString。
 *
 * @author Macula
 * @since 6.1.0
 */
@Getter
@Setter
public class VerificationCodeSettings {
    @NotNull
    private Duration ttl = Duration.ofMinutes(5);
    @NotNull
    private Duration sendInterval = Duration.ofSeconds(60);
    @Positive
    private int maxClientSendsPerInterval = 200;
    @Positive
    private int maxIpSendsPerInterval = 20;
    @Positive
    private int maxAttempts = 5;
    @NotBlank
    private String digestKey;

    /**
     * 防止配置绕过绑定校验后产生永久或无限验证窗口。
     */
    public void validate() {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || sendInterval == null || sendInterval.isZero()
                || sendInterval.isNegative() || maxClientSendsPerInterval < 1 || maxIpSendsPerInterval < 1
                || maxAttempts < 1 || digestKey == null || digestKey.isBlank()) {
            throw new IllegalStateException("验证码安全配置无效");
        }
    }
}
