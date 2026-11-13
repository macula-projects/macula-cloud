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

import dev.macula.cloud.system.form.IamApplicationDraftForm;
import dev.macula.cloud.system.service.IamApplicationCatalogService;
import dev.macula.cloud.system.vo.OAuth2ClientConfigurationCatalogVO;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.service.IamApplicationService;
import dev.macula.cloud.system.service.OAuth2IssuerMetadataService;
import dev.macula.cloud.system.vo.OAuth2IssuerMetadataVO;
import dev.macula.cloud.system.vo.IamApplicationVO;
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
public class IamApplicationController {
    private final IamApplicationService service;
    private final OAuth2IssuerMetadataService metadataService;
    private final IamApplicationCatalogService configurationCatalog;

    /** 返回与保存校验一致的协议、Scope和具体来源目录。 */
    @GetMapping("/configuration-catalog")
    public OAuth2ClientConfigurationCatalogVO configurationCatalog() {
        return configurationCatalog.catalog();
    }


    /** 复用Client管理权限，只读取服务器配置的可信元数据。 */
    @GetMapping("/metadata")
    public OAuth2IssuerMetadataVO metadata(@RequestParam(defaultValue = "OIDC") String protocol) {
        return metadataService.get(protocol);
    }


    @GetMapping
    public IPage<IamApplicationVO> page(@RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword) {
        return service.page(pageNum, pageSize, keyword);
    }

    @GetMapping("/external-code-catalog")
    public OAuth2ExternalCodeCatalogVO externalCodeCatalog() {
        return service.externalCodeCatalog();
    }

    @GetMapping("/{id}")
    public IamApplicationVO get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 创建草稿，不允许调用方指定认证能力或客户端ID。 */
    @PostMapping("/drafts")
    public IamApplicationVO createDraft(@Valid @RequestBody IamApplicationDraftForm form) {
        return service.createDraft(form);
    }

    @PostMapping
    public IamApplicationVO create(@Valid @RequestBody IamApplicationForm form) {
        return service.save(null, form);
    }

    @PutMapping("/{id}")
    public IamApplicationVO update(@PathVariable Long id, @Valid @RequestBody IamApplicationForm form) {
        return service.save(id, form);
    }

    @PutMapping("/{id}/secret")
    public void resetSecret(@PathVariable Long id, @Valid @RequestBody SecretResetForm form) {
        service.resetSecret(id, form.getSecret(), form.getConfigurationVersion());
    }

    /** 删除时校验列表返回的完整配置版本，防止误删并发更新后的应用。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestParam String configurationVersion) {
        service.delete(id, configurationVersion);
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
        @NotBlank
        private String configurationVersion;
    }
}
