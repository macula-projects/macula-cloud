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
package dev.macula.cloud.iam.authentication.challenge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 无 Session 登录 CAPTCHA 的有效期、限流和摘要配置。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@Validated
@ConfigurationProperties("macula.cloud.iam.login-captcha")
public class LoginCaptchaProperties {
    private Duration ttl = Duration.ofMinutes(2);
    private Duration rateWindow = Duration.ofMinutes(1);
    @Positive
    private int maxClientIssuesPerWindow = 200;
    @Positive
    private int maxIssuesPerWindow = 20;
    @NotBlank
    private String digestKey;
}
