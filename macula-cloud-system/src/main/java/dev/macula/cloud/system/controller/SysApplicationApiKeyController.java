/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.ApplicationApiKeyForm;
import dev.macula.cloud.system.service.SysApplicationApiKeyService;
import dev.macula.cloud.system.vo.app.ApplicationApiKeySecretVO;
import dev.macula.cloud.system.vo.app.ApplicationApiKeyVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
 * 应用 API Key 管理协议，完整值仅供授权创建和查看。
 * @author Rain
 * @since 6.1.0
 */
@RestController
@RequestMapping("/api/v1/app/{appId}/api-keys")
@RequiredArgsConstructor
public class SysApplicationApiKeyController {
    private final SysApplicationApiKeyService service;

    /** 获取当前应用的脱敏分页。 */
    @GetMapping
    public Page<ApplicationApiKeyVO> page(@PathVariable Long appId,
        @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return service.page(appId, pageNum, pageSize);
    }

    /** 创建凭据，审计只保存操作元信息。 */
    @PostMapping
    @AuditLog(title = "创建应用 API Key", isSaveRequestData = false, isSaveResponseData = false)
    public ApplicationApiKeySecretVO create(@PathVariable Long appId,
        @Valid @RequestBody ApplicationApiKeyForm form, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return service.create(appId, form);
    }

    /** 编辑备注与独立 URL，不更换凭据。 */
    @PutMapping("/{keyId}")
    @AuditLog(title = "更新应用 API Key", isSaveRequestData = false, isSaveResponseData = false)
    public void update(@PathVariable Long appId, @PathVariable Long keyId,
        @Valid @RequestBody ApplicationApiKeyForm form) {
        service.update(appId, keyId, form);
    }

    /** 撤销凭据并在提交后删除认证缓存。 */
    @DeleteMapping("/{keyId}")
    @AuditLog(title = "删除应用 API Key", isSaveRequestData = false, isSaveResponseData = false)
    public void delete(@PathVariable Long appId, @PathVariable Long keyId) {
        service.delete(appId, keyId);
    }

    /** 显示与复制时按需获取，禁止浏览器和代理缓存。 */
    @GetMapping("/{keyId}/secret")
    @AuditLog(title = "查看应用 API Key", isSaveRequestData = false, isSaveResponseData = false)
    public ApplicationApiKeySecretVO secret(@PathVariable Long appId, @PathVariable Long keyId,
        HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return service.secret(appId, keyId);
    }
}
