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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysIdentityProviderUserBindingMapper;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProviderUserBinding;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;
import dev.macula.cloud.iam.service.application.LoginApplicationResolver;
import dev.macula.cloud.iam.service.identity.directory.IdentityDirectoryService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityDirectoryPolicy;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 浏览器和SDK共用的绑定事务，按Client、来源、账号顺序加锁。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class IdentityProviderUserBindingService {
    private final LoginApplicationResolver applications;
    private final SysIdentityProviderMapper sources;
    private final SysUserMapper users;
    private final SysIdentityProviderUserBindingMapper bindings;
    private final IdentityProviderConfigService configs;
    private final ClientAccountDetailsService details;
    private final UserIdentifierNormalizer normalizer;

    /**
     * 根据权威配置验证该结果仍属于当前Client固定来源。
     */
    public IamApplicationSnapshot requireApplication(String clientId, VerifiedExternalIdentity identity, boolean lock,
            Long expectedTenant, UserTypeEnum expectedUserType) {
        IdentityLoginCapability capability = IdentityLoginCapability.fromValue(identity.loginCapability());
        IamApplicationSnapshot app = applications.trusted(clientId, capability, lock);
        if (app == null || !app.loginEnabled() || app.accountUserType() != UserTypeEnum.EMPLOYEE
            || app.tenantId() == null || !Objects.equals(app.tenantId(), expectedTenant)
            || app.accountUserType() != expectedUserType
            || !identity.registrationId().equals(app.login().sources().get(capability))) {
            throw denied();
        }
        IdentityProviderConfigService.ProviderConfig source;
        if (lock) {
            SysIdentityProvider loaded = sources.selectByIdForUpdate(identity.registrationId());
            // 当前读保留显式Crypto映射，禁止锁后再读旧RR快照。
            source = configs.requireLoaded(loaded, identity.registrationId(), identity.providerType()).forCapability(identity.loginCapability());
        } else {
            source = configs.requireEnabledById(identity.registrationId(), identity.providerType()).forCapability(identity.loginCapability());
        }
        if (!source.sourceType().value().equals(identity.sourceType())
            || !Objects.equals(source.version(), identity.sourceVersion()) || !source.flow().value().equals(identity.flow())) {
            throw denied();
        }
        boolean browser = VerifiedExternalIdentity.BROWSER.equals(identity.capability());
        if (browser ? !capability.browser() || !app.login().type().supportsBrowserCapability(capability,
            app.login().webLoginMode() == null ? null : app.login().webLoginMode().value())
            : capability.code() == null || !capability.code().name().equals(identity.capability())
                || !source.sourceType().supports(capability.code())
                || !app.login().type().supportsExternalCode(capability.code(), app.login().miniappPlatform(),
                    app.login().webLoginMode() == null ? null : app.login().webLoginMode().value())) {
            throw denied();
        }
        return app;
    }

    /**
     * 已有关联通过稳定userId加载，每次重查手机号证明一致性与Client权限。
     */
    @Transactional(readOnly = true)
    public Optional<UserDetails> findBound(String clientId, Long tenantId, UserTypeEnum userType,
            VerifiedExternalIdentity identity) {
        IamApplicationSnapshot client = requireApplication(clientId, identity, false, tenantId, userType);
        List<SysIdentityProviderUserBinding> rows = bindings.selectList(identityQuery(identity));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1 || !client.userType().equals(rows.get(0).getUserType())) {
            throw denied();
        }
        SysUser user = users.selectById(rows.get(0).getUserId());
        return Optional.of(validateAccount(client, identity, user));
    }

    /**
     * 证明目标账号归属和认证范围；此方法不会写绑定。
     */
    public UserDetails validateAccount(IamApplicationSnapshot client, VerifiedExternalIdentity identity, SysUser user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) ||
                Integer.valueOf(1).equals(user.getDeleted())) {
            throw denied();
        }
        if (VerifiedExternalIdentity.MOBILE_PROOF.equals(identity.identityKind())
                && !normalizer.mobile(user.getMobile()).filter(identity.identityValue()::equals).isPresent()) {
            throw denied();
        }
        UserDetails result = details.loadUserByApplication(client.id(), client.tenantId(), client.accountUserType(),
                user.getUsername());
        if (!(result instanceof SysUserDetails local) || !Objects.equals(local.getUserId(), user.getId())
                || !result.isEnabled() || !result.isAccountNonLocked()) {
            throw denied();
        }
        return result;
    }

    /**
     * 原子绑定，automatic=true时必须在锁内重新核对规则及匹配值。
     */
    @Transactional
    public UserDetails bind(String clientId, Long tenantId, UserTypeEnum userType, VerifiedExternalIdentity identity,
            Long userId, boolean automatic) {
        return bind(clientId, tenantId, userType, identity, userId, automatic, null);
    }

    /**
     * 远程目录已在事务外核验；锁内只比较原始快照和当前配置。
     */
    @Transactional
    public UserDetails bind(String clientId, Long tenantId, UserTypeEnum userType, VerifiedExternalIdentity identity,
            Long userId, boolean automatic, IdentityDirectoryService.Admission admission) {
        return bind(clientId, tenantId, userType, identity, userId, automatic, admission, null);
    }

    /**
     * 端内浏览器把发起版本带到锁内，不能在账号证明期间改配后仍写入旧pending绑定。
     */
    @Transactional
    public UserDetails bind(String clientId, Long tenantId, UserTypeEnum userType, VerifiedExternalIdentity identity,
            Long userId, boolean automatic, IdentityDirectoryService.Admission admission, Long expectedClientVersion) {
        IamApplicationSnapshot client = requireApplication(clientId, identity, true, tenantId, userType);
        if (client.login().webLoginMode() == IamWebLoginMode.IN_APP &&
                VerifiedExternalIdentity.BROWSER.equals(identity.capability())
                && (expectedClientVersion == null ||
                !Objects.equals(client.authorizationVersion(), expectedClientVersion))) {
            throw denied();
        }
        SysIdentityProvider lockedSource = sources.selectByIdForUpdate(identity.registrationId());
        IdentityProviderConfigService.ProviderConfig source = configs.requireLoaded(lockedSource,
                identity.registrationId(), identity.providerType()).forCapability(identity.loginCapability());
        if (!Objects.equals(source.version(), identity.sourceVersion())
                || !source.flow().value().equals(identity.flow())) {
            throw denied();
        }
        if (source.directoryPolicy() != IdentityDirectoryPolicy.IDENTITY_ONLY) {
            if (admission == null) {
                throw denied();
            }
            admission.requireCurrent(identity, source);
        }
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, userId).last("FOR UPDATE"));
        UserDetails principal = validateAccount(client, identity, user);
        if (automatic) {
            if (!source.autoBindEnabled() || source.bindingMappings().isEmpty()) {
                throw denied();
            }
            // 与首次候选使用完全相同的完整规则及规范化条件；来源锁内重新证明唯一性。
            List<SysUser> matches = new IdentityBindingMatcher(users).candidates(source.bindingMappings(), identity,
                    true);
            if (matches.size() != 1 || !userId.equals(matches.get(0).getId())) {
                throw denied();
            }
        }
        List<SysIdentityProviderUserBinding> existing = bindings.selectList(identityQuery(identity).last("FOR UPDATE"));
        if (!existing.isEmpty()) {
            if (existing.size() == 1 && userId.equals(existing.get(0).getUserId())
                    && client.userType().equals(existing.get(0).getUserType())) {
                return principal;
            }
            throw denied();
        }
        List<SysIdentityProviderUserBinding> account = bindings.selectList(
                new LambdaQueryWrapper<SysIdentityProviderUserBinding>()
                        .eq(SysIdentityProviderUserBinding::getIdentityProviderId, identity.registrationId())
                        .eq(SysIdentityProviderUserBinding::getIdentityKind, identity.identityKind())
                        .eq(SysIdentityProviderUserBinding::getUserType, client.userType())
                        .eq(SysIdentityProviderUserBinding::getUserId, userId).last("FOR UPDATE"));
        if (!account.isEmpty()) {
            throw denied();
        }
        SysIdentityProviderUserBinding binding = new SysIdentityProviderUserBinding();
        binding.setIdentityProviderId(identity.registrationId());
        binding.setIdentityKind(identity.identityKind());
        binding.setIdentityValue(identity.identityValue());
        binding.setUserType(client.userType());
        binding.setUserId(userId);
        bindings.insert(binding);
        return principal;
    }

    /**
     * 查询精确来源身份键，不对厂商或用户名降级。
     */
    private LambdaQueryWrapper<SysIdentityProviderUserBinding> identityQuery(VerifiedExternalIdentity identity) {
        return new LambdaQueryWrapper<SysIdentityProviderUserBinding>()
                .eq(SysIdentityProviderUserBinding::getIdentityProviderId, identity.registrationId())
                .eq(SysIdentityProviderUserBinding::getIdentityKind, identity.identityKind())
                .eq(SysIdentityProviderUserBinding::getIdentityValue, identity.identityValue());
    }

    /**
     * 拒绝信息不暴露外部标识或匹配账号。
     */
    private BadCredentialsException denied() {
        return new BadCredentialsException("身份绑定或账号访问校验失败");
    }
}
