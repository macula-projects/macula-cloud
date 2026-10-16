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

import dev.macula.cloud.system.form.PasswordPolicyForm;
import dev.macula.cloud.system.service.PasswordPolicyService;
import dev.macula.cloud.system.vo.PasswordPolicyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 全局密码策略 API，访问权限由平台 URL 权限配置控制。
 *
 * @author Rain
 * @since 2026-10-13
 */
@RestController
@RequestMapping("/api/v1/password-policy")
@RequiredArgsConstructor
public class PasswordPolicyController {
    private final PasswordPolicyService service;

    @GetMapping
    public PasswordPolicyVO get() {
        return service.get();
    }

    @PutMapping
    public PasswordPolicyVO save(@Valid @RequestBody PasswordPolicyForm form) {
        return service.save(form);
    }
}
