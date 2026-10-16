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
package dev.macula.cloud.system.controller;

import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.boot.starter.auditlog.enums.BusinessType;
import dev.macula.cloud.system.form.PasswordPolicyConfigForm;
import dev.macula.cloud.system.form.SecurityBasicConfigForm;
import dev.macula.cloud.system.form.SecurityDefensePolicyConfigForm;
import dev.macula.cloud.system.service.SecurityPolicyService;
import dev.macula.cloud.system.vo.PasswordPolicyConfigResult;
import dev.macula.cloud.system.vo.SecurityBasicConfigResult;
import dev.macula.cloud.system.vo.SecurityDefensePolicyConfigResult;
import dev.macula.cloud.system.vo.SecurityPolicyResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前租户统一安全策略 API，访问权限由平台 URL 权限配置控制。
 *
 * @author Rain
 * @since 2026-10-16
 */
@RestController
@RequestMapping("/api/v1/security-policy")
@RequiredArgsConstructor
public class SecurityPolicyController {
    private final SecurityPolicyService service;

    @GetMapping
    public SecurityPolicyResult get() {
        return service.get();
    }

    @PutMapping("/basic")
    @AuditLog(title = "保存安全策略键：sessionValidTime,verifyCodeValidTime,rememberMeValidTime,sessionMaximum",
        businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public SecurityBasicConfigResult saveBasic(@Valid @RequestBody SecurityBasicConfigForm form) {
        return service.saveBasic(form);
    }

    @PutMapping("/defense")
    @AuditLog(title = "保存安全策略键：loginFailureDuration,loginFailureCount,autoUnlockTime,contentSecurityPolicy",
        businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public SecurityDefensePolicyConfigResult saveDefense(@Valid @RequestBody SecurityDefensePolicyConfigForm form) {
        return service.saveDefense(form);
    }

    @PutMapping("/password")
    @AuditLog(title = "保存安全策略键组：password（12项，不记录配置值）",
        businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public PasswordPolicyConfigResult savePassword(@Valid @RequestBody PasswordPolicyConfigForm form) {
        return service.savePassword(form);
    }
}
