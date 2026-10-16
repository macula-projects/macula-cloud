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
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.service.IdentityProviderService;
import dev.macula.cloud.system.vo.IdentityProviderCatalogVO;
import dev.macula.cloud.system.vo.IdentityProviderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 身份提供商管理 API，访问权限由平台 URL 权限配置控制。
 *
 * @author Rain
 * @since 2026-10-13
 */
@RestController
@RequestMapping("/api/v1/identity-providers")
@RequiredArgsConstructor
public class IdentityProviderController {
    private final IdentityProviderService service;

    @GetMapping("/catalog")
    public IdentityProviderCatalogVO catalog() {
        return service.catalog();
    }

    @GetMapping
    public IPage<IdentityProviderVO> page(@RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String name,
        @RequestParam(required = false) String providerType, @RequestParam(required = false) String category) {
        return service.page(pageNum, pageSize, name, providerType, category);
    }

    @GetMapping("/{id}")
    public IdentityProviderVO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public IdentityProviderVO create(@Valid @RequestBody IdentityProviderForm form) {
        return service.save(null, form);
    }

    @PutMapping("/{id}")
    public IdentityProviderVO update(@PathVariable Long id, @Valid @RequestBody IdentityProviderForm form) {
        return service.save(id, form);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
