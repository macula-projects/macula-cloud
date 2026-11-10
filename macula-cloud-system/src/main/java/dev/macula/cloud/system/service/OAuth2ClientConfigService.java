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
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import dev.macula.cloud.system.vo.OAuth2ExternalCodeCatalogVO;

/**
 * IAM 接入应用配置管理服务。
 *
 * @author Rain
 * @since 2026-10-13
 */
public interface OAuth2ClientConfigService {
    IPage<OAuth2ClientVO> page(int pageNum, int pageSize, String keyword);
    OAuth2ExternalCodeCatalogVO externalCodeCatalog();
    OAuth2ClientVO get(Long id);
    /** 创建不具备认证能力的基础信息草稿。 */
    OAuth2ClientVO createDraft(dev.macula.cloud.system.form.OAuth2ClientDraftForm form);
    OAuth2ClientVO save(Long id, OAuth2ClientForm form);
    /** 密钥轮换必须携带当前完整配置版本。 */
    void resetSecret(Long id, String secret, String configurationVersion);
    /** 删除同样拒绝过期配置快照，平台受管身份不能删除。 */
    void delete(Long id, String configurationVersion);
}
