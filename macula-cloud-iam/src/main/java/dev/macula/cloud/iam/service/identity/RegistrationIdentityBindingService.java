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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.mapper.SysRegistrationIdentityBindingMapper;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.iam.pojo.entity.SysRegistrationIdentityBinding;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import dev.macula.cloud.iam.service.identity.directory.IdentityDirectoryService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 浏览器和SDK共用的绑定事务，按Client、来源、账号顺序加锁。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class RegistrationIdentityBindingService {
    private final SysOAuth2ClientMapper clients;
    private final dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols;
    private final SysIdentityProviderMapper sources;
    private final SysOAuth2ClientExternalProviderMapper externalSources;
    private final SysUserMapper users;
    private final SysRegistrationIdentityBindingMapper bindings;
    private final IdentityProviderConfigService configs;
    private final ClientAccountDetailsService details;
    private final UserIdentifierNormalizer normalizer;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 根据权威配置验证该结果仍属于当前Client固定来源。
     */
    public SysOAuth2Client requireClient(String clientId, VerifiedExternalIdentity identity, boolean lock,
            Long expectedTenant, UserTypeEnum expectedUserType) {
        LambdaQueryWrapper<SysOAuth2Client> query = new LambdaQueryWrapper<SysOAuth2Client>()
                .eq(SysOAuth2Client::getClientId, clientId);
        if (lock) {
            query.last("FOR UPDATE");
        }
        SysOAuth2Client client = clients.selectOne(query);
        if (client == null || client.getUserType() != UserTypeEnum.EMPLOYEE || client.getTenantId() == null
                || !Objects.equals(client.getTenantId(), expectedTenant) || client.getUserType() != expectedUserType) {
            throw denied();
        }
        var protocolQuery = new LambdaQueryWrapper<dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig>()
                .eq(dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig::getApplicationId, client.getId());
        if (lock) {
            protocolQuery.last("FOR UPDATE");
        }
        try {
            client.setProtocolConfigurations(protocols.selectList(protocolQuery).stream().map(row -> {
                if (row.getConfiguration() == null ||
                        row.getConfiguration().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                                > dev.macula.cloud.system.protocol.ApplicationProtocolRegistry.MAX_CONFIG_BYTES) {
                    throw denied();
                }
                return new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration(row.getProtocol(),
                        row.getSchemaVersion(),
                        Boolean.TRUE.equals(row.getEnabled()), json.readValue(row.getConfiguration(),
                        dev.macula.cloud.system.protocol.OAuth2ProtocolSettings.class));
            }).toList());
        } catch (RuntimeException ex) {
            throw denied();
        }
        if (!dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.enabled(client)) {
            throw denied();
        }
        IdentityProviderConfigService.ProviderConfig source;
        if (lock) {
            SysIdentityProvider loaded = sources.selectOne(new LambdaQueryWrapper<SysIdentityProvider>()
                    .eq(SysIdentityProvider::getId, identity.registrationId()).last("FOR UPDATE"));
            // 锁定读是当前读；不能在MySQL RR下再用普通SELECT拿回先前快照。
            source = configs.requireLoaded(loaded, identity.registrationId(), identity.providerType())
                    .forCapability(identity.loginCapability());
        } else {
            source = configs.requireEnabledById(identity.registrationId(), identity.providerType())
                    .forCapability(identity.loginCapability());
        }
        if (!source.sourceType().value().equals(identity.sourceType())
                || !Objects.equals(source.version(), identity.sourceVersion()) ||
                !source.flow().value().equals(identity.flow())) {
            throw denied();
        }
        if (VerifiedExternalIdentity.BROWSER.equals(identity.capability())) {
            Map<String, Long> fixed = client.getBrowserProviderBindings() == null ? Map.of()
                    : json.readValue(client.getBrowserProviderBindings(), new TypeReference<>() {
            });
            if (client.getBrowserCapabilityBindings() != null) {
                fixed = json.readValue(client.getBrowserCapabilityBindings(), new TypeReference<>() {
                });
            }
            String bindingKey = client.getBrowserCapabilityBindings() == null ? identity.providerType() :
                    identity.loginCapability();
            if (!source.sourceType().browser() || !identity.registrationId().equals(fixed.get(bindingKey))
                    || !csv(client.getBrowserLoginProviderTypes()).contains(identity.providerType())
                    || !client.getProtocolConfigurations().stream()
                    .filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
                    .map(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration::settings)
                    .anyMatch(settings -> csv(settings.getAuthorizationGrantTypes()).contains("authorization_code"))) {
                throw denied();
            }
        } else {
            // 来源许可从唯一有效协议读取；OIDC与OAuth2.1共享同一真实绑定边界。
            String protocol = client.getProtocolConfigurations().get(0).protocol();
            ExternalCodeType type = ExternalCodeType.fromValue(identity.capability());
            if (!source.sourceType().supports(type) || !csv(client.getExternalCodeTypes()).contains(type.name())
                    || !csv(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(
                            dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.configuration(client,
                                    protocol))
                    .getAuthorizationGrantTypes()).contains(ExternalCodeType.GRANT_TYPE)) {
                throw denied();
            }
            List<SysOAuth2ClientExternalProvider> fixed = externalSources.selectList(
                    new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>().eq(
                                    SysOAuth2ClientExternalProvider::getClientId,
                                    clientId).eq(SysOAuth2ClientExternalProvider::getCodeType, type.name())
                            .last(lock, "FOR UPDATE"));
            if (fixed.size() != 1 || !identity.registrationId().equals(fixed.get(0).getIdentityProviderId())) {
                throw denied();
            }
        }
        // 绑定锁内重查整个注册集合；仅验证当前Code会漏掉跨来源SDK和小程序双Code。
        var type = dev.macula.cloud.system.enums.OAuth2ClientApplicationType.valueOf(client.getApplicationType());
        Map<String, Long> browser = client.getBrowserCapabilityBindings() == null ? Map.of()
                : json.readValue(client.getBrowserCapabilityBindings(), new TypeReference<>() {
        });
        Map<String, Long> allExternal = externalSources.selectList(
                        new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
                                .eq(SysOAuth2ClientExternalProvider::getClientId, clientId).last(lock, "FOR UPDATE")).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SysOAuth2ClientExternalProvider::getCodeType,
                        SysOAuth2ClientExternalProvider::getIdentityProviderId));
        try {
            dev.macula.cloud.system.security.OAuth2ApplicationLoginPolicy.validate(type, client.getMiniappPlatform(),
                    browser, allExternal, true);
            var capability = dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(
                    identity.loginCapability());
            if (VerifiedExternalIdentity.BROWSER.equals(identity.capability()) ?
                    !type.supportsBrowserCapability(capability)
                    : !type.supportsExternalCode(ExternalCodeType.fromValue(identity.capability()),
                    client.getMiniappPlatform())) {
                throw denied();
            }
        } catch (RuntimeException ex) {
            throw denied();
        }
        return client;
    }

    /**
     * 已有关联通过稳定userId加载，每次重查手机号证明一致性与Client权限。
     */
    @Transactional(readOnly = true)
    public Optional<UserDetails> findBound(String clientId, Long tenantId, UserTypeEnum userType,
            VerifiedExternalIdentity identity) {
        SysOAuth2Client client = requireClient(clientId, identity, false, tenantId, userType);
        List<SysRegistrationIdentityBinding> rows = bindings.selectList(identityQuery(identity));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1 || !client.getUserType().getType().equals(rows.get(0).getUserType())) {
            throw denied();
        }
        SysUser user = users.selectById(rows.get(0).getUserId());
        return Optional.of(validateAccount(client, identity, user));
    }

    /**
     * 证明目标账号归属和认证范围；此方法不会写绑定。
     */
    public UserDetails validateAccount(SysOAuth2Client client, VerifiedExternalIdentity identity, SysUser user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) ||
                Integer.valueOf(1).equals(user.getDeleted())) {
            throw denied();
        }
        if (VerifiedExternalIdentity.MOBILE_PROOF.equals(identity.identityKind())
                && !normalizer.mobile(user.getMobile()).filter(identity.identityValue()::equals).isPresent()) {
            throw denied();
        }
        UserDetails result = details.loadUserByClient(client.getClientId(), client.getTenantId(), client.getUserType(),
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
        SysOAuth2Client client = requireClient(clientId, identity, true, tenantId, userType);
        if ("IN_APP".equals(client.getApplicationType()) &&
                VerifiedExternalIdentity.BROWSER.equals(identity.capability())
                && (expectedClientVersion == null ||
                !Objects.equals(client.getAuthorizationVersion(), expectedClientVersion))) {
            throw denied();
        }
        SysIdentityProvider lockedSource = sources.selectOne(new LambdaQueryWrapper<SysIdentityProvider>()
                .eq(SysIdentityProvider::getId, identity.registrationId()).last("FOR UPDATE"));
        IdentityProviderConfigService.ProviderConfig source = configs.requireLoaded(lockedSource,
                identity.registrationId(), identity.providerType()).forCapability(identity.loginCapability());
        if (!java.util.Objects.equals(source.version(), identity.sourceVersion())
                || !source.flow().value().equals(identity.flow())) {
            throw denied();
        }
        if (source.directoryPolicy() != dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY) {
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
        List<SysRegistrationIdentityBinding> existing = bindings.selectList(identityQuery(identity).last("FOR UPDATE"));
        if (!existing.isEmpty()) {
            if (existing.size() == 1 && userId.equals(existing.get(0).getUserId())
                    && client.getUserType().getType().equals(existing.get(0).getUserType())) {
                return principal;
            }
            throw denied();
        }
        List<SysRegistrationIdentityBinding> account = bindings.selectList(
                new LambdaQueryWrapper<SysRegistrationIdentityBinding>()
                        .eq(SysRegistrationIdentityBinding::getRegistrationId, identity.registrationId())
                        .eq(SysRegistrationIdentityBinding::getIdentityKind, identity.identityKind())
                        .eq(SysRegistrationIdentityBinding::getUserType, client.getUserType().getType())
                        .eq(SysRegistrationIdentityBinding::getUserId, userId).last("FOR UPDATE"));
        if (!account.isEmpty()) {
            throw denied();
        }
        SysRegistrationIdentityBinding binding = new SysRegistrationIdentityBinding();
        binding.setRegistrationId(identity.registrationId());
        binding.setIdentityKind(identity.identityKind());
        binding.setIdentityValue(identity.identityValue());
        binding.setUserType(client.getUserType().getType());
        binding.setUserId(userId);
        bindings.insert(binding);
        return principal;
    }

    /**
     * 查询精确来源身份键，不对厂商或用户名降级。
     */
    private LambdaQueryWrapper<SysRegistrationIdentityBinding> identityQuery(VerifiedExternalIdentity identity) {
        return new LambdaQueryWrapper<SysRegistrationIdentityBinding>()
                .eq(SysRegistrationIdentityBinding::getRegistrationId, identity.registrationId())
                .eq(SysRegistrationIdentityBinding::getIdentityKind, identity.identityKind())
                .eq(SysRegistrationIdentityBinding::getIdentityValue, identity.identityValue());
    }

    /**
     * 读取明确列表。
     */
    private java.util.Set<String> csv(String value) {
        return value == null ? java.util.Set.of() : org.springframework.util.StringUtils.commaDelimitedListToSet(value);
    }

    /**
     * 拒绝信息不暴露外部标识或匹配账号。
     */
    private BadCredentialsException denied() {
        return new BadCredentialsException("身份绑定或账号访问校验失败");
    }
}
