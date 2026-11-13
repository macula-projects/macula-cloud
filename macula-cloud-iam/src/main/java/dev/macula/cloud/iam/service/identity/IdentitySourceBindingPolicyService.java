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
package dev.macula.cloud.iam.service.identity;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.identity.directory.IdentityDirectoryService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * 所有身份源使用同一个首次绑定策略，不含企业厂商默认自动匹配。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public class IdentitySourceBindingPolicyService {
    private final IdentityProviderUserBindingService bindings;
    private final IdentityProviderConfigService configs;
    private final SysUserMapper users;
    private final IdentityDirectoryService directory;

    /**
     * 生产统一准入必须覆盖已有绑定。
     */
    @Autowired
    public IdentitySourceBindingPolicyService(IdentityProviderUserBindingService bindings,
            IdentityProviderConfigService configs, SysUserMapper users, IdentityDirectoryService directory) {
        this.bindings = bindings;
        this.configs = configs;
        this.users = users;
        this.directory = Objects.requireNonNull(directory);
    }

    /**
     * 有效已绑定身份优先；否则执行该来源完整映射集合并要求命中一致。
     */
    public Optional<UserDetails> resolve(String clientId, Long tenantId,
            UserTypeEnum userType, VerifiedExternalIdentity identity) {
        return resolve(clientId, tenantId, userType, identity, null);
    }

    /**
     * 浏览器携带服务端发起版本；直接Code继续由认证Client及Token版本边界约束。
     */
    public Optional<UserDetails> resolve(String clientId, Long tenantId,
            UserTypeEnum userType,
            VerifiedExternalIdentity identity, Long expectedClientVersion) {
        var admission = directory.check(identity);
        VerifiedExternalIdentity verified = directory.enrich(identity, admission);
        Optional<UserDetails> bound = bindings.findBound(clientId, tenantId, userType, verified);
        if (bound.isPresent()) {
            directory.bound(verified);
            return bound;
        }
        IdentityProviderConfigService.ProviderConfig source = configs.requireEnabledById(verified.registrationId(),
                verified.providerType()).forCapability(verified.loginCapability());
        if (!source.autoBindEnabled() || source.bindingMappings().isEmpty()) {
            return Optional.empty();
        }
        List<SysUser> matches = new IdentityBindingMatcher(users).candidates(source.bindingMappings(), verified, false);
        if (matches.size() != 1) {
            return Optional.empty();
        }
        UserDetails result = expectedClientVersion != null
                ? bindings.bind(clientId, tenantId, userType, verified, matches.get(0).getId(), true, admission,
                expectedClientVersion)
                : bindings.bind(clientId, tenantId, userType, verified, matches.get(0).getId(), true, admission);
        directory.bound(verified);
        return Optional.of(result);
    }
}
