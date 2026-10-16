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
package dev.macula.cloud.system.vo;

import dev.macula.cloud.system.form.SecuritySettingsForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全局通用安全配置展示数据，明确标记当前未接入运行时。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySettingsVO {
    private Integer maxConcurrentSessions;
    private Integer sessionTimeoutMinutes;
    private Integer rememberMeDays;
    private Integer captchaValiditySeconds;
    private Integer failureWindowMinutes;
    private Integer maxFailures;
    private Integer unlockMinutes;
    private String contentSecurityPolicy;
    private boolean effective;

    public static SecuritySettingsVO from(SecuritySettingsForm form) {
        return new SecuritySettingsVO(form.getMaxConcurrentSessions(), form.getSessionTimeoutMinutes(),
            form.getRememberMeDays(), form.getCaptchaValiditySeconds(), form.getFailureWindowMinutes(),
            form.getMaxFailures(), form.getUnlockMinutes(), form.getContentSecurityPolicy(), false);
    }
}
