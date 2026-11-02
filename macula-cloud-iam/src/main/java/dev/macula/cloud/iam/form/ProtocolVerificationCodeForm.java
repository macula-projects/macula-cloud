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
package dev.macula.cloud.iam.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 协议短信或邮箱验证码申请参数。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class ProtocolVerificationCodeForm {
    @NotBlank
    @Size(max = 100)
    @JsonProperty("client_id")
    private String clientId;
    @NotBlank
    @Size(max = 128)
    @JsonProperty("captcha_challenge")
    private String captchaChallenge;
    @NotBlank
    @Size(max = 32)
    @JsonProperty("captcha_answer")
    private String captchaAnswer;
    @NotBlank
    @Size(max = 320)
    private String target;
}
