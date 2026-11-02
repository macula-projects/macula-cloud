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

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import dev.macula.cloud.system.vo.OAuth2ExternalCodeCatalogVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 接入应用管理 API，访问权限由平台 URL 权限配置控制。
 *
 * @author Rain
 * @since 2026-10-13
 */
@RestController
@RequestMapping("/api/v1/oauth2-clients")
@RequiredArgsConstructor
public class OAuth2ClientConfigController {
    private final OAuth2ClientConfigService service;

    @GetMapping
    public IPage<OAuth2ClientVO> page(@RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword) {
        return service.page(pageNum, pageSize, keyword);
    }

    @GetMapping("/external-code-catalog")
    public OAuth2ExternalCodeCatalogVO externalCodeCatalog() {
        return service.externalCodeCatalog();
    }

    @GetMapping("/{id}")
    public OAuth2ClientVO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public OAuth2ClientVO create(@Valid @RequestBody OAuth2ClientForm form) {
        return service.save(null, form);
    }

    @PutMapping("/{id}")
    public OAuth2ClientVO update(@PathVariable Long id, @Valid @RequestBody OAuth2ClientForm form) {
        return service.save(id, form);
    }

    @PutMapping("/{id}/secret")
    public void resetSecret(@PathVariable Long id, @Valid @RequestBody SecretResetForm form) {
        service.resetSecret(id, form.getSecret());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /**
     * 管理员临时输入的密钥不通过读接口回显。
     *
     * @since 6.1.0
     */
    @Data
    public static class SecretResetForm {
        @NotBlank
        private String secret;
    }
}
