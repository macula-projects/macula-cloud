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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.form.ApplicationApiKeyForm;
import dev.macula.cloud.system.vo.app.ApplicationApiKeySecretVO;
import dev.macula.cloud.system.vo.app.ApplicationApiKeyVO;

/**
 * 当前租户应用的 API Key 生命周期管理。
 * @author Rain
 * @since 6.1.0
 */
public interface SysApplicationApiKeyService {
    /** 获取应用的脱敏凭据分页。 */
    Page<ApplicationApiKeyVO> page(Long appId, int pageNum, int pageSize);
    /** 生成并缓存新的凭据。 */
    ApplicationApiKeySecretVO create(Long appId, ApplicationApiKeyForm form);
    /** 独立修改备注和 URL；空 URL 恢复缓存时使用应用 URL。 */
    void update(Long appId, Long keyId, ApplicationApiKeyForm form);
    /** 撤销凭据并清理缓存。 */
    void delete(Long appId, Long keyId);
    /** 授权获取完整凭据。 */
    ApplicationApiKeySecretVO secret(Long appId, Long keyId);
}
