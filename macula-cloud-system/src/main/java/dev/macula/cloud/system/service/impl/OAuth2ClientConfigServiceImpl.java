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
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
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

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
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
    private static final Set<String> AUTH = Set.of("none", "client_secret_basic", "client_secret_post",
        "private_key_jwt");
    private static final Set<String> GRANTS = Set.of("authorization_code", "refresh_token",
        "client_credentials", "password", "sms");
    private static final Set<String> USERS = Set.of("member", "employee", "odc", "supplier");
    private final SysOAuth2ClientConfigMapper mapper;
    private final SysTenantInfoMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    @Override
    public IPage<OAuth2ClientVO> page(int pageNum, int pageSize, String keyword) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) bad("分页参数无效");
        return mapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .and(StringUtils.hasText(keyword), q -> q.like(SysOAuth2ClientConfig::getClientId, keyword)
                .or().like(SysOAuth2ClientConfig::getClientName, keyword))
            .orderByDesc(SysOAuth2ClientConfig::getId)).convert(this::toVO);
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
        if (id != null && !entity.getClientId().equals(form.getClientId())) bad("clientId 创建后不可修改");
        if (id == null && mapper.selectCount(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .eq(SysOAuth2ClientConfig::getClientId, form.getClientId())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "clientId 已存在");
        }
        boolean publicClient = csv(form.getClientAuthenticationMethods()).contains("none");
        boolean secretRequired = csv(form.getClientAuthenticationMethods()).stream().anyMatch(m -> m.startsWith("client_secret_"));
        if (id == null) {
            entity.setClientId(form.getClientId());
            entity.setClientIdIssuedAt(Instant.now());
            if (secretRequired) entity.setClientSecret(encodeSecret(form.getClientSecret()));
        } else if (StringUtils.hasText(form.getClientSecret())) {
            bad("修改客户端密钥请使用重置接口");
        }
        if (publicClient || !secretRequired) entity.setClientSecret(null);
        else if (!StringUtils.hasText(entity.getClientSecret())) bad("机密客户端必须设置密钥");
        entity.setClientName(form.getClientName());
        entity.setClientAuthenticationMethods(form.getClientAuthenticationMethods());
        entity.setAuthorizationGrantTypes(form.getAuthorizationGrantTypes());
        entity.setRedirectUris(form.getRedirectUris() == null ? "" : form.getRedirectUris());
        entity.setScopes(form.getScopes());
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
        if (id == null) mapper.insert(entity); else mapper.updateById(entity);
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
        mapper.deleteById(id);
        evictAfterCommit(entity.getClientId());
    }

    private void validate(OAuth2ClientForm form) {
        if (tenantMapper.selectById(form.getTenantId()) == null) bad("租户不存在");
        if (!USERS.contains(form.getUserType())) bad("用户类型无效");
        Set<String> auth = csv(form.getClientAuthenticationMethods());
        Set<String> grants = csv(form.getAuthorizationGrantTypes());
        Set<String> scopes = csv(form.getScopes());
        if (auth.isEmpty() || !AUTH.containsAll(auth) || grants.isEmpty() || !GRANTS.containsAll(grants) ||
            scopes.isEmpty()) bad("认证方式、授权方式或 scope 无效");
        if (grants.contains("authorization_code") && csv(form.getRedirectUris()).isEmpty()) bad("授权码客户端必须配置回调 URI");
        boolean publicClient = auth.contains("none");
        if (publicClient && (auth.size() != 1 || !grants.contains("authorization_code") ||
            grants.contains("client_credentials") || !form.getRequireProofKey() ||
            StringUtils.hasText(form.getClientSecret()))) bad("公共客户端必须使用授权码和 S256 PKCE，且不得设置密钥");
        for (String uriValue : csv(form.getRedirectUris())) {
            URI uri;
            try { uri = URI.create(uriValue); } catch (IllegalArgumentException ex) { bad("回调 URI 无效"); return; }
            if (!uri.isAbsolute() || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) ||
                    ("http".equalsIgnoreCase(uri.getScheme()) &&
                        Set.of("localhost", "127.0.0.1").contains(uri.getHost())))) bad("回调 URI 必须使用 HTTPS");
        }
        if (form.getAuthorizationCodeTimeToLive() != null &&
            (form.getAuthorizationCodeTimeToLive() < 30 || form.getAuthorizationCodeTimeToLive() > 3600)) bad("授权码有效期无效");
        if (form.getAccessTokenTimeToLive() != null &&
            (form.getAccessTokenTimeToLive() < 60 || form.getAccessTokenTimeToLive() > 86400)) bad("访问令牌有效期无效");
        if (form.getRefreshTokenTimeToLive() != null &&
            (form.getRefreshTokenTimeToLive() < 300 || form.getRefreshTokenTimeToLive() > 31536000)) bad("刷新令牌有效期无效");
        if (StringUtils.hasText(form.getTokenFormat()) &&
            !Set.of("self-contained", "reference").contains(form.getTokenFormat())) bad("令牌格式无效");
        if (StringUtils.hasText(form.getIdTokenSignatureAlgorithm()) &&
            !Set.of("RS256", "RS384", "RS512").contains(form.getIdTokenSignatureAlgorithm())) bad("ID Token 签名算法无效");
        if (auth.contains("private_key_jwt") && !StringUtils.hasText(form.getJwkSetUrl())) bad("私钥 JWT 认证需要 JWK URL");
        if (auth.contains("private_key_jwt")) {
            Set<String> algorithms = Set.of("RS256", "RS384", "RS512");
            if (!StringUtils.hasText(form.getSigningAlgorithm()) ||
                !algorithms.contains(form.getSigningAlgorithm())) bad("JWT 客户端认证签名算法无效");
        }
        if (StringUtils.hasText(form.getJwkSetUrl())) {
            URI uri;
            try { uri = URI.create(form.getJwkSetUrl()); } catch (IllegalArgumentException ex) { bad("JWK URL 无效"); return; }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) bad("JWK URL 必须使用 HTTPS");
        }
    }

    private Set<String> csv(String value) {
        if (!StringUtils.hasText(value)) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private String encodeSecret(String secret) {
        if (!StringUtils.hasText(secret) || secret.length() < 16) bad("密钥至少 16 个字符");
        return passwordEncoder.encode(secret);
    }

    private SysOAuth2ClientConfig require(Long id) {
        SysOAuth2ClientConfig entity = mapper.selectById(id);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接入应用不存在");
        return entity;
    }

    private OAuth2ClientVO toVO(SysOAuth2ClientConfig e) {
        return new OAuth2ClientVO(e.getId(), e.getClientId(), e.getClientName(), e.getClientIdIssuedAt(),
            e.getClientAuthenticationMethods(), e.getAuthorizationGrantTypes(), e.getRedirectUris(), e.getScopes(),
            Boolean.TRUE.equals(e.getRequireProofKey()), Boolean.TRUE.equals(e.getRequireAuthorizationConsent()),
            e.getJwkSetUrl(), e.getSigningAlgorithm(), e.getAuthorizationCodeTimeToLive(), e.getAccessTokenTimeToLive(),
            e.getTokenFormat(), Boolean.TRUE.equals(e.getReuseRefreshTokens()), e.getRefreshTokenTimeToLive(),
            e.getIdTokenSignatureAlgorithm(), e.getUserType(), e.getTenantId(), StringUtils.hasText(e.getClientSecret()));
    }

    private void evictAfterCommit(String clientId) {
        Cache cache = cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        if (cache == null) throw new IllegalStateException("IAM 客户端缓存不可用");
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
