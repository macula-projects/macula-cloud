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
package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.security.OAuth2LoginGrantTypes;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import dev.macula.cloud.system.vo.OAuth2ExternalCodeCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 管理与 IAM 共表的 OAuth 客户端，并在事务提交后清理共享缓存。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
public class OAuth2ClientConfigServiceImpl implements OAuth2ClientConfigService {
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final String CLIENT_CREDENTIALS_GRANT = "client_credentials";
    private static final String SMS_GRANT = "sms";
    private static final Set<String> AUTH = Set.of("none", "client_secret_basic", "client_secret_post",
        "private_key_jwt");
    private static final Set<String> GRANTS = Set.of(AUTHORIZATION_CODE_GRANT, "refresh_token",
        CLIENT_CREDENTIALS_GRANT, "password", SMS_GRANT, OAuth2LoginGrantTypes.EMAIL, ExternalCodeType.GRANT_TYPE);
    private static final Set<String> USERS = Set.of("member", "employee", "odc", "supplier");
    private static final String EXTERNAL_REDIRECT_URI = "externalRedirectUri";
    private static final String MOBILE_APP_ID = "mobileAppId";
    private static final String MOBILE_APP_SECRET = "mobileAppSecret";
    private static final String MINI_APP_ID = "miniAppId";
    private static final String MINI_APP_SECRET = "miniAppSecret";
    private final JsonMapper json = JsonMapper.builder().build();
    private final SysOAuth2ClientConfigMapper mapper;
    private final SysOAuth2ClientExternalProviderMapper externalProviderMapper;
    private final SysIdentityProviderMapper identityProviderMapper;
    private final SysTenantInfoMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    @Override
    public IPage<OAuth2ClientVO> page(int pageNum, int pageSize, String keyword) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            bad("分页参数无效");
        }
        return mapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .and(StringUtils.hasText(keyword), q -> q.like(SysOAuth2ClientConfig::getClientId, keyword)
                .or().like(SysOAuth2ClientConfig::getClientName, keyword))
            .orderByDesc(SysOAuth2ClientConfig::getId)).convert(this::toVO);
    }

    @Override
    public OAuth2ExternalCodeCatalogVO externalCodeCatalog() {
        List<OAuth2ExternalCodeCatalogVO.CodeType> codeTypes = Arrays.stream(ExternalCodeType.values())
            .map(type -> new OAuth2ExternalCodeCatalogVO.CodeType(type.name(), type.providerType(),
                type.scene().name(), type.resultType().name(), type.capability().name()))
            .toList();
        List<OAuth2ExternalCodeCatalogVO.ProviderRegistration> registrations = identityProviderMapper.selectList(
            new LambdaQueryWrapper<SysIdentityProvider>().eq(SysIdentityProvider::getEnabled, true)
                .orderByAsc(SysIdentityProvider::getProviderType).orderByAsc(SysIdentityProvider::getName))
            .stream().map(provider -> new OAuth2ExternalCodeCatalogVO.ProviderRegistration(provider.getId(),
                provider.getName(), provider.getProviderType(), providerCapabilities(provider))).toList();
        return new OAuth2ExternalCodeCatalogVO(codeTypes, registrations);
    }

    private Set<String> providerCapabilities(SysIdentityProvider provider) {
        Set<String> capabilities = new TreeSet<>();
        capabilities.add(ExternalCodeType.Capability.DEFAULT.name());
        Map<String, String> config = jsonObject(provider.getConfigJson());
        Map<String, String> secrets = jsonObject(provider.getSecretConfigJson());
        if (StringUtils.hasText(config.get(EXTERNAL_REDIRECT_URI))) {
            capabilities.add(ExternalCodeType.Capability.FEISHU_EXTERNAL_REDIRECT.name());
        }
        if (StringUtils.hasText(config.get(MOBILE_APP_ID)) && StringUtils.hasText(secrets.get(MOBILE_APP_SECRET))) {
            capabilities.add(ExternalCodeType.Capability.WECHAT_MOBILE_APP.name());
        }
        if (StringUtils.hasText(config.get(MINI_APP_ID)) && StringUtils.hasText(secrets.get(MINI_APP_SECRET))) {
            capabilities.add(ExternalCodeType.Capability.WECHAT_MINIAPP.name());
        }
        return Collections.unmodifiableSet(capabilities);
    }

    @Override
    public OAuth2ClientVO get(Long id) {
        return toVO(require(id));
    }

    @Override
    @Transactional
    public OAuth2ClientVO save(Long id, OAuth2ClientForm form) {
        validate(form);
        SysOAuth2ClientConfig entity = id == null ? new SysOAuth2ClientConfig() : require(id);
        if (id != null && !entity.getClientId().equals(form.getClientId())) {
            bad("clientId 创建后不可修改");
        }
        if (id == null && mapper.selectCount(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .eq(SysOAuth2ClientConfig::getClientId, form.getClientId())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "clientId 已存在");
        }
        boolean publicClient = csv(form.getClientAuthenticationMethods()).contains("none");
        boolean secretRequired = csv(form.getClientAuthenticationMethods()).stream()
            .anyMatch(method -> method.startsWith("client_secret_"));
        if (id == null) {
            entity.setClientId(form.getClientId());
            entity.setClientIdIssuedAt(Instant.now());
            if (secretRequired) {
                entity.setClientSecret(encodeSecret(form.getClientSecret()));
            }
        } else if (StringUtils.hasText(form.getClientSecret())) {
            bad("修改客户端密钥请使用重置接口");
        }
        if (publicClient || !secretRequired) {
            entity.setClientSecret(null);
        } else if (!StringUtils.hasText(entity.getClientSecret())) {
            bad("机密客户端必须设置密钥");
        }
        entity.setClientName(form.getClientName());
        entity.setClientAuthenticationMethods(form.getClientAuthenticationMethods());
        entity.setAuthorizationGrantTypes(form.getAuthorizationGrantTypes());
        entity.setRedirectUris(form.getRedirectUris() == null ? "" : form.getRedirectUris());
        entity.setScopes(form.getScopes());
        entity.setExternalCodeTypes(csvValue(form.getExternalCodeTypes()));
        entity.setAllowedOrigins(csvValue(normalizeOrigins(form.getAllowedOrigins())));
        Set<BrowserLoginProviderType> browserProviders = browserLoginProviderTypes(
            form.getBrowserLoginProviderTypes());
        entity.setBrowserLoginProviderTypes(enumCsvValue(browserProviders));
        entity.setDirectLoginProviderType(normalizeDirectLoginProviderType(
            form.getDirectLoginProviderType(), browserProviders));
        entity.setRequireProofKey(publicClient || form.getRequireProofKey());
        entity.setRequireAuthorizationConsent(form.getRequireAuthorizationConsent());
        entity.setJwkSetUrl(form.getJwkSetUrl());
        entity.setSigningAlgorithm(form.getSigningAlgorithm());
        entity.setAuthorizationCodeTimeToLive(form.getAuthorizationCodeTimeToLive());
        entity.setAccessTokenTimeToLive(form.getAccessTokenTimeToLive());
        entity.setTokenFormat(form.getTokenFormat());
        entity.setReuseRefreshTokens(form.getReuseRefreshTokens());
        entity.setRefreshTokenTimeToLive(form.getRefreshTokenTimeToLive());
        entity.setIdTokenSignatureAlgorithm(form.getIdTokenSignatureAlgorithm());
        entity.setUserType(form.getUserType());
        entity.setTenantId(form.getTenantId());
        if (id == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        replaceExternalProviders(entity.getClientId(), form.getExternalProviderBindings());
        evictAfterCommit(entity.getClientId());
        return toVO(entity);
    }

    @Override
    @Transactional
    public void resetSecret(Long id, String secret) {
        SysOAuth2ClientConfig entity = require(id);
        if (csv(entity.getClientAuthenticationMethods()).stream().noneMatch(m -> m.startsWith("client_secret_"))) {
            bad("该认证方式不使用客户端密钥");
        }
        entity.setClientSecret(encodeSecret(secret));
        mapper.updateById(entity);
        evictAfterCommit(entity.getClientId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysOAuth2ClientConfig entity = require(id);
        externalProviderMapper.delete(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getClientId, entity.getClientId()));
        mapper.deleteById(id);
        evictAfterCommit(entity.getClientId());
    }

    private void validate(OAuth2ClientForm form) {
        if (tenantMapper.selectById(form.getTenantId()) == null) {
            bad("租户不存在");
        }
        if (!USERS.contains(form.getUserType())) {
            bad("用户类型无效");
        }
        Set<String> auth = csv(form.getClientAuthenticationMethods());
        Set<String> grants = csv(form.getAuthorizationGrantTypes());
        Set<String> scopes = csv(form.getScopes());
        Set<ExternalCodeType> externalCodeTypes = externalCodeTypes(form.getExternalCodeTypes());
        Map<String, Long> externalProviderBindings = nullSafeMap(form.getExternalProviderBindings());
        Set<BrowserLoginProviderType> browserProviders = browserLoginProviderTypes(
            form.getBrowserLoginProviderTypes());
        normalizeDirectLoginProviderType(form.getDirectLoginProviderType(), browserProviders);
        if (auth.isEmpty() || !AUTH.containsAll(auth) || grants.isEmpty() || !GRANTS.containsAll(grants) ||
            scopes.isEmpty()) {
            bad("认证方式、授权方式或 scope 无效");
        }
        if (grants.contains(AUTHORIZATION_CODE_GRANT) && csv(form.getRedirectUris()).isEmpty()) {
            bad("授权码客户端必须配置回调 URI");
        }
        if (!browserProviders.isEmpty() && (!grants.contains(AUTHORIZATION_CODE_GRANT) ||
            csv(form.getRedirectUris()).isEmpty())) {
            bad("网页登录提供商只能配置给具有回调 URI 的授权码客户端");
        }
        boolean publicClient = auth.contains("none");
        if (publicClient && (auth.size() != 1 || grants.contains(CLIENT_CREDENTIALS_GRANT) ||
            !Boolean.TRUE.equals(form.getRequireProofKey()) || StringUtils.hasText(form.getClientSecret()))) {
            bad("公共客户端必须启用 S256 PKCE、不得使用客户端凭据模式或设置密钥");
        }
        if ((!externalCodeTypes.isEmpty() || grants.contains(ExternalCodeType.GRANT_TYPE)) &&
            (!publicClient || !grants.contains(ExternalCodeType.GRANT_TYPE) || externalCodeTypes.isEmpty())) {
            bad("外部凭证登录必须由公共客户端同时配置 grant 和非空 code type");
        }
        Set<String> allowedOrigins = normalizeOrigins(form.getAllowedOrigins());
        boolean verificationCodeGrant = grants.contains(SMS_GRANT) || grants.contains(OAuth2LoginGrantTypes.EMAIL);
        if (verificationCodeGrant && (!publicClient || allowedOrigins.isEmpty())) {
            bad("短信或邮箱验证码 grant 必须由公共客户端配置非空精确 Origin");
        }
        if (!verificationCodeGrant && !allowedOrigins.isEmpty()) {
            bad("未启用短信或邮箱验证码 grant 时不得配置验证码 Origin");
        }
        validateExternalProviderBindings(externalCodeTypes, externalProviderBindings);
        validateBrowserLoginProviders(browserProviders);
        for (String uriValue : csv(form.getRedirectUris())) {
            URI uri;
            try {
                uri = URI.create(uriValue);
            } catch (IllegalArgumentException ex) {
                bad("回调 URI 无效");
                return;
            }
            if (!uri.isAbsolute() || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) ||
                    ("http".equalsIgnoreCase(uri.getScheme()) &&
                        Set.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
                bad("回调 URI 必须使用 HTTPS");
            }
        }
        if (form.getAuthorizationCodeTimeToLive() != null
            && (form.getAuthorizationCodeTimeToLive() < 30 || form.getAuthorizationCodeTimeToLive() > 3600)) {
            bad("授权码有效期无效");
        }
        if (form.getAccessTokenTimeToLive() != null
            && (form.getAccessTokenTimeToLive() < 60 || form.getAccessTokenTimeToLive() > 86400)) {
            bad("访问令牌有效期无效");
        }
        if (form.getRefreshTokenTimeToLive() != null
            && (form.getRefreshTokenTimeToLive() < 300 || form.getRefreshTokenTimeToLive() > 31536000)) {
            bad("刷新令牌有效期无效");
        }
        if (StringUtils.hasText(form.getTokenFormat())
            && !Set.of("self-contained", "reference").contains(form.getTokenFormat())) {
            bad("令牌格式无效");
        }
        if (StringUtils.hasText(form.getIdTokenSignatureAlgorithm())
            && !Set.of("RS256", "RS384", "RS512").contains(form.getIdTokenSignatureAlgorithm())) {
            bad("ID Token 签名算法无效");
        }
        if (auth.contains("private_key_jwt") && !StringUtils.hasText(form.getJwkSetUrl())) {
            bad("私钥 JWT 认证需要 JWK URL");
        }
        if (auth.contains("private_key_jwt")) {
            Set<String> algorithms = Set.of("RS256", "RS384", "RS512");
            if (!StringUtils.hasText(form.getSigningAlgorithm()) ||
                !algorithms.contains(form.getSigningAlgorithm())) {
                bad("JWT 客户端认证签名算法无效");
            }
        }
        if (StringUtils.hasText(form.getJwkSetUrl())) {
            URI uri;
            try {
                uri = URI.create(form.getJwkSetUrl());
            } catch (IllegalArgumentException ex) {
                bad("JWK URL 无效");
                return;
            }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                bad("JWK URL 必须使用 HTTPS");
            }
        }
    }

    private Set<String> csv(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private Set<ExternalCodeType> externalCodeTypes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        try {
            return values.stream().map(ExternalCodeType::fromValue).collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            bad("外部凭证类型无效");
            return Set.of();
        }
    }

    private Set<BrowserLoginProviderType> browserLoginProviderTypes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        try {
            return values.stream().map(BrowserLoginProviderType::fromValue)
                .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            bad("网页登录提供商类型无效");
            return Set.of();
        }
    }

    private String normalizeDirectLoginProviderType(String value,
        Set<BrowserLoginProviderType> browserProviders) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        BrowserLoginProviderType direct;
        try {
            direct = BrowserLoginProviderType.fromValue(value.trim());
        } catch (IllegalArgumentException ex) {
            bad("直接登录提供商类型无效");
            return null;
        }
        if (!browserProviders.contains(direct)) {
            bad("直接登录提供商必须包含在网页登录提供商中");
        }
        return direct.name();
    }

    private void validateBrowserLoginProviders(Set<BrowserLoginProviderType> browserProviders) {
        for (BrowserLoginProviderType providerType : browserProviders) {
            long count = identityProviderMapper.selectCount(new LambdaQueryWrapper<SysIdentityProvider>()
                .eq(SysIdentityProvider::getProviderType, providerType.name())
                .eq(SysIdentityProvider::getEnabled, true));
            if (count != 1) {
                bad("每个网页登录提供商必须且只能有一个启用的身份源配置");
            }
        }
    }

    private void validateExternalProviderBindings(Set<ExternalCodeType> codeTypes, Map<String, Long> bindings) {
        Set<String> expected = codeTypes.stream().map(Enum::name).collect(Collectors.toSet());
        if (!expected.equals(bindings.keySet())) {
            bad("每个外部凭证类型必须且只能绑定一个身份源配置");
        }
        for (ExternalCodeType codeType : codeTypes) {
            SysIdentityProvider provider = identityProviderMapper.selectById(bindings.get(codeType.name()));
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled()) ||
                !codeType.providerType().equals(provider.getProviderType())) {
                bad("外部凭证类型绑定的身份源配置无效");
            }
            validateProviderCapability(codeType, provider);
        }
    }

    private void validateProviderCapability(ExternalCodeType codeType, SysIdentityProvider provider) {
        Map<String, String> config = jsonObject(provider.getConfigJson());
        Map<String, String> secrets = jsonObject(provider.getSecretConfigJson());
        boolean capable = switch (codeType.capability()) {
            case FEISHU_EXTERNAL_REDIRECT -> StringUtils.hasText(config.get(EXTERNAL_REDIRECT_URI));
            case WECHAT_MOBILE_APP -> StringUtils.hasText(config.get(MOBILE_APP_ID))
                && StringUtils.hasText(secrets.get(MOBILE_APP_SECRET));
            case WECHAT_MINIAPP ->
                StringUtils.hasText(config.get(MINI_APP_ID)) && StringUtils.hasText(secrets.get(MINI_APP_SECRET));
            case DEFAULT -> true;
        };
        if (!capable) {
            bad("身份源配置不具备所选外部凭证类型的服务端能力");
        }
    }

    private Map<String, String> jsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return json.readValue(value, new TypeReference<>() { });
        } catch (RuntimeException ex) {
            bad("身份源配置无效");
            return Map.of();
        }
    }

    private Set<String> normalizeOrigins(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> origins = new TreeSet<>();
        for (String value : values) {
            try {
                URI uri = new URI(value == null ? "" : value.trim()).normalize();
                boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme()) &&
                    Set.of("localhost", "127.0.0.1").contains(uri.getHost());
                if ((!"https".equalsIgnoreCase(uri.getScheme()) && !loopbackHttp) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
                    (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))) {
                    bad("允许 Origin 必须是 HTTPS origin");
                }
                int port = uri.getPort();
                if (("https".equalsIgnoreCase(uri.getScheme()) && port == 443) ||
                    ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)) {
                    port = -1;
                }
                origins.add(new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(),
                    port, null, null, null).toASCIIString());
            } catch (URISyntaxException | IllegalArgumentException ex) {
                bad("允许 Origin 无效");
            }
        }
        return Collections.unmodifiableSet(origins);
    }

    private void replaceExternalProviders(String clientId, Map<String, Long> bindings) {
        externalProviderMapper.delete(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getClientId, clientId));
        new TreeSet<>(nullSafeMap(bindings).keySet()).forEach(codeType -> {
            SysOAuth2ClientExternalProvider mapping = new SysOAuth2ClientExternalProvider();
            mapping.setClientId(clientId);
            mapping.setCodeType(codeType);
            mapping.setIdentityProviderId(bindings.get(codeType));
            externalProviderMapper.insert(mapping);
        });
    }

    private Map<String, Long> externalProviderBindings(String clientId) {
        return externalProviderMapper.selectList(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getClientId, clientId).orderByAsc(
                SysOAuth2ClientExternalProvider::getCodeType)).stream().collect(Collectors.toMap(
                    SysOAuth2ClientExternalProvider::getCodeType,
                    SysOAuth2ClientExternalProvider::getIdentityProviderId,
                    (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, Long> nullSafeMap(Map<String, Long> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private String csvValue(Set<String> values) {
        return String.join(",", new TreeSet<>(values == null ? Set.of() : values));
    }

    private String enumCsvValue(Set<BrowserLoginProviderType> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private String encodeSecret(String secret) {
        if (!StringUtils.hasText(secret) || secret.length() < 16) {
            bad("密钥至少 16 个字符");
        }
        return passwordEncoder.encode(secret);
    }

    private SysOAuth2ClientConfig require(Long id) {
        SysOAuth2ClientConfig entity = mapper.selectById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接入应用不存在");
        }
        return entity;
    }

    private OAuth2ClientVO toVO(SysOAuth2ClientConfig e) {
        return new OAuth2ClientVO(e.getId(), e.getClientId(), e.getClientName(), e.getClientIdIssuedAt(),
            e.getClientAuthenticationMethods(), e.getAuthorizationGrantTypes(), e.getRedirectUris(), e.getScopes(),
            csv(e.getExternalCodeTypes()), csv(e.getAllowedOrigins()), externalProviderBindings(e.getClientId()),
            csv(e.getBrowserLoginProviderTypes()), e.getDirectLoginProviderType(),
            Boolean.TRUE.equals(e.getRequireProofKey()), Boolean.TRUE.equals(e.getRequireAuthorizationConsent()),
            e.getJwkSetUrl(), e.getSigningAlgorithm(), e.getAuthorizationCodeTimeToLive(), e.getAccessTokenTimeToLive(),
            e.getTokenFormat(), Boolean.TRUE.equals(e.getReuseRefreshTokens()), e.getRefreshTokenTimeToLive(),
            e.getIdTokenSignatureAlgorithm(), e.getUserType(), e.getTenantId(),
            StringUtils.hasText(e.getClientSecret()));
    }

    private void evictAfterCommit(String clientId) {
        Cache cache = cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        if (cache == null) {
            throw new IllegalStateException("IAM 客户端缓存不可用");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(clientId);
            }
        });
    }

    private static void bad(String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
