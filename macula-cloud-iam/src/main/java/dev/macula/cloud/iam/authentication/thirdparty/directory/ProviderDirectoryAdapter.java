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
package dev.macula.cloud.iam.authentication.thirdparty.directory;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;

/**
 * 已核验身份的固定企业目录适配，不能接受调用方URL、Secret或另一个企业标识。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface ProviderDirectoryAdapter {
    /**
     * 对应明确厂商配置，而非全局择源。
     */
    String providerType();

    /**
     * 仅在数据库事务外查询已验证主体，返回最小资料投影。
     */
    ProviderDirectoryResult lookup(IdentityProviderConfigService.ProviderConfig source,
            VerifiedExternalIdentity identity);
}
