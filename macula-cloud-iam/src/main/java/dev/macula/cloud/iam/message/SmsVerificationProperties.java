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
package dev.macula.cloud.iam.message;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 短信验证码有效期、限流、尝试次数和摘要密钥配置。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Data
@Validated
@ConfigurationProperties("macula.cloud.iam.sms-verification")
public class SmsVerificationProperties {
    private Duration ttl = Duration.ofMinutes(5);
    private Duration sendInterval = Duration.ofMinutes(1);
    @Positive
    private int maxIpSendsPerInterval = 20;
    @Positive
    private int maxAttempts = 5;
    @NotBlank
    private String digestKey;
}
