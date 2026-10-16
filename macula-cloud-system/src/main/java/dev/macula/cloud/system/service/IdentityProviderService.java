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
package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.vo.IdentityProviderCatalogVO;
import dev.macula.cloud.system.vo.IdentityProviderVO;

/**
 * 身份提供商配置的校验、持久化和脱敏读取。
 *
 * @author Rain
 * @since 2026-10-13
 */
public interface IdentityProviderService {
    IdentityProviderCatalogVO catalog();
    IPage<IdentityProviderVO> page(int pageNum, int pageSize, String name, String providerType, String category);
    IdentityProviderVO get(Long id);
    IdentityProviderVO save(Long id, IdentityProviderForm form);
    void delete(Long id);
}
