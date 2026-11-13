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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.IdentitySourceType;
import dev.macula.cloud.system.form.IamApplicationDraftForm;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import dev.macula.cloud.system.service.IamApplicationService;
import dev.macula.cloud.system.vo.IamApplicationVO;
import dev.macula.cloud.system.vo.OAuth2ExternalCodeCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * IAM应用聚合的事务编排；HTTP适配、公共策略、协议及凭据分别委托独立组件。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class IamApplicationServiceImpl implements IamApplicationService {
    private final SysIamApplicationMapper mapper;
    private final SysIamApplicationIdentityProviderMapper sources;
    private final SysIdentityProviderMapper identityProviders;
    private final IamApplicationProtocolService protocols;
    private final IamApplicationPolicyValidator policy;
    private final IamApplicationWireAdapter wire;
    private final OAuth2ClientCredentialService credentials;
    private final DateTimeConverter dates;
    private final CacheManager cacheManager;
    private final JsonMapper json = JsonMapper.builder().build();

    /** 列表内容及版本共享RR快照，不以来源或协议反推应用架构。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IPage<IamApplicationVO> page(int pageNum, int pageSize, String keyword) {
        require(pageNum > 0 && pageSize > 0 && pageSize <= 100, "分页参数无效");
        return mapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<SysIamApplication>()
            .and(keyword != null && !keyword.isBlank(), query -> query.like(SysIamApplication::getApplicationCode, keyword)
                .or().like(SysIamApplication::getApplicationName, keyword))
            .orderByDesc(SysIamApplication::getId)).convert(app -> wire.view(app, protocols.state(app.getId(), false)));
    }

    /** 兼容目录端点只展示已声明能力，不读取或返回来源凭据。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OAuth2ExternalCodeCatalogVO externalCodeCatalog() {
        var codes = Arrays.stream(ExternalCodeType.values()).map(type -> new OAuth2ExternalCodeCatalogVO.CodeType(
            type.name(), type.providerType(), type.scene().name(), type.resultType().name(), type.capability().name())).toList();
        var registrations = identityProviders.selectList(null).stream().filter(source -> Boolean.TRUE.equals(source.getEnabled()) && source.getSourceType() != null)
            .map(source -> new OAuth2ExternalCodeCatalogVO.ProviderRegistration(source.getId(), source.getName(), source.getProviderType(),
                IdentityLoginCapability.decode(source.getCapabilitiesJson(), IdentitySourceType.fromValue(source.getSourceType()), source.getIdentityFlow())
                    .keySet().stream().map(IdentityLoginCapability::fromValue).filter(capability -> capability.code() != null)
                    .map(capability -> capability.code().capability().name()).collect(Collectors.toUnmodifiableSet()))).toList();
        return new OAuth2ExternalCodeCatalogVO(codes, registrations);
    }

    /** 详情和聚合版本来自同一快照，普通响应不携带协议凭据。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IamApplicationVO get(Long id) {
        var app = requireApplication(id, false);
        return wire.view(app, protocols.state(id, false));
    }

    /** 草稿只固定架构与模式；没有默认租户、协议或凭据。 */
    @Override
    public IamApplicationVO createDraft(IamApplicationDraftForm draft) {
        var form = new IamApplicationForm();
        form.setClientId(UUID.randomUUID().toString());
        form.setClientName(draft.getClientName());
        form.setPurpose(draft.getPurpose());
        form.setApplicationType(draft.getApplicationType());
        form.setMiniappPlatform(draft.getMiniappPlatform());
        form.setConfigurationStatus("DRAFT");
        if (draft.isWebLoginModePresent()) {
            form.setWebLoginMode(draft.getWebLoginMode());
        }
        if ("IN_APP".equals(draft.getWebLoginMode())) {
            form.setHostedLoginMethods(Set.of());
        }
        if (!"RESOURCE_SERVER".equals(draft.getPurpose()) && draft.getProtocol() != null) {
            form.setProtocol(draft.getProtocol());
            form.setSettings(new OAuth2ProtocolSettings());
        }
        return save(null, form);
    }

    /** 主行锁串行化完整配置操作；校验全部通过后统一写主表、协议和来源关系。 */
    @Override
    public IamApplicationVO save(Long id, IamApplicationForm form) {
        var original = id == null ? new SysIamApplication() : requireApplication(id, true);
        var stored = protocols.state(id, id != null);
        if (id != null) {
            protocols.checkVersion(original, stored, form.getConfigurationVersion());
        }
        try {
            policy.metadata(form, original);
            Map<String, Long> routes = wire.routes(form, stored);
            policy.login(form, routes);
            SysIamApplication app = metadata(form, original);
            var registration = wire.selection(form, app, stored);
            if (OAuth2ResourcePolicy.ADMIN_MANAGED_KEY.equals(app.getManagedKey())) {
                require("OIDC".equals(form.getProtocol()) && form.getSettings() != null
                    && OAuth2ProtocolConfiguration.csv(form.getSettings().getAuthorizationGrantTypes()).contains("authorization_code"), "平台Admin必须保留OIDC授权码登录");
            }
            app.setDirectLoginCapability(wire.directCapability(form, routes));
            String hash = credentials.prepare(app, registration, stored.protocol(), form.getClientSecret());
            app.setResourcePermissions("RESOURCE_SERVER".equals(app.getPurpose())
                ? original.getResourcePermissions() == null ? "{}" : original.getResourcePermissions()
                : json.writeValueAsString(wire.resourcePermissions(registration)));
            persist(app, original);
            protocols.save(app, stored, registration, hash);
            replaceSources(app.getId(), routes);
            evictAfterCommit();
            // 重读真实时间精度和审计落值，响应ETag必须等于后续数据库读取的版本。
            return get(app.getId());
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "应用或协议配置无效");
        }
    }

    /** 密钥重置使用同一主锁和完整版本，编码由OAuth专有组件处理。 */
    @Override
    public void resetSecret(Long id, String raw, String configurationVersion) {
        var app = requireApplication(id, true);
        var stored = protocols.state(id, true);
        protocols.checkVersion(app, stored, configurationVersion);
        credentials.rotate(protocols.registration(app, stored), stored.protocol(), raw);
        long previous = app.getAuthorizationVersion();
        app.setAuthorizationVersion(Math.addExact(previous, 1));
        requireUpdated(mapper.update(null, new LambdaUpdateWrapper<SysIamApplication>()
            .eq(SysIamApplication::getId, id).eq(SysIamApplication::getAuthorizationVersion, previous)
            .set(SysIamApplication::getAuthorizationVersion, app.getAuthorizationVersion())));
        evictAfterCommit();
    }

    /** 删除与保存共享完整聚合版本，受管身份拒绝删除。 */
    @Override
    public void delete(Long id, String configurationVersion) {
        var app = requireApplication(id, true);
        protocols.checkVersion(app, protocols.state(id, true), configurationVersion);
        require(app.getManagedKey() == null || app.getManagedKey().isBlank(), "平台内置身份不可删除");
        sources.delete(new LambdaQueryWrapper<SysIamApplicationIdentityProvider>().eq(SysIamApplicationIdentityProvider::getApplicationId, id));
        protocols.delete(id);
        requireUpdated(mapper.deleteById(id));
        evictAfterCommit();
    }

    /** 仅映射公共元数据，OAuth字段不会落入主表。 */
    private SysIamApplication metadata(IamApplicationForm form, SysIamApplication previous) {
        var app = new SysIamApplication();
        BeanUtils.copyProperties(previous, app);
        app.setApplicationCode(form.getClientId());
        app.setApplicationName(form.getClientName().trim());
        app.setApplicationCodeIssuedAt(previous.getApplicationCodeIssuedAt() == null ? dates.instantToLocalDateTime(Instant.now()) : previous.getApplicationCodeIssuedAt());
        app.setPurpose(form.getPurpose());
        app.setConfigurationStatus(form.getConfigurationStatus());
        app.setApplicationType(form.getApplicationType());
        app.setWebLoginMode(form.getWebLoginMode());
        app.setMiniappPlatform(form.getMiniappPlatform());
        app.setHostedLoginMethods(form.getHostedLoginMethods() == null ? null : String.join(",", new TreeSet<>(form.getHostedLoginMethods())));
        app.setTenantId("RESOURCE_SERVER".equals(app.getPurpose()) ? null : form.getTenantId());
        app.setUserType("RESOURCE_SERVER".equals(app.getPurpose()) ? null : form.getUserType());
        app.setAuthorizationVersion(previous.getAuthorizationVersion() == null ? 1L : Math.addExact(previous.getAuthorizationVersion(), 1));
        return app;
    }

    /** 唯一键是创建的最终并发约束，更新还比较主行授权版本。 */
    private void persist(SysIamApplication app, SysIamApplication previous) {
        if (app.getId() == null) {
            require(mapper.selectCount(new LambdaQueryWrapper<SysIamApplication>().eq(SysIamApplication::getApplicationCode, app.getApplicationCode())) == 0, "Client ID已存在");
            mapper.insert(app);
        } else {
            var condition = new LambdaUpdateWrapper<SysIamApplication>().eq(SysIamApplication::getId, app.getId());
            if (previous.getAuthorizationVersion() == null) {
                condition.isNull(SysIamApplication::getAuthorizationVersion);
            } else {
                condition.eq(SysIamApplication::getAuthorizationVersion, previous.getAuthorizationVersion());
            }
            requireUpdated(mapper.update(app, condition));
        }
    }

    /** 来源关系独立于协议许可，按能力排序保持确定性。 */
    private void replaceSources(Long applicationId, Map<String, Long> routes) {
        sources.delete(new LambdaQueryWrapper<SysIamApplicationIdentityProvider>().eq(SysIamApplicationIdentityProvider::getApplicationId, applicationId));
        new TreeMap<>(routes).forEach((capability, provider) -> {
            var row = new SysIamApplicationIdentityProvider();
            row.setApplicationId(applicationId);
            row.setLoginCapability(capability);
            row.setIdentityProviderId(provider);
            sources.insert(row);
        });
    }

    /** 不存在返回原404，锁定读取必须由外部事务保护。 */
    private SysIamApplication requireApplication(Long id, boolean lock) {
        var app = lock ? mapper.selectByIdForUpdate(id) : mapper.selectById(id);
        if (app == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接入应用不存在");
        }
        return app;
    }

    /** 缓存对象预先检查；事务回滚不会执行任何驱逐。 */
    private void evictAfterCommit() {
        Cache clients = cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        Cache origins = cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX);
        if (clients == null || origins == null) {
            throw new IllegalStateException("IAM客户端缓存不可用");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 提交后尝试全部缓存，任一失败向调用者明确报告。 */
            @Override
            public void afterCommit() {
                RuntimeException failure = null;
                for (Cache cache : List.of(clients, origins)) {
                    try {
                        cache.invalidate();
                    } catch (RuntimeException error) {
                        if (failure == null) {
                            failure = error;
                        } else {
                            failure.addSuppressed(error);
                        }
                    }
                }
                if (failure != null) {
                    throw failure;
                }
            }
        });
    }

    /** 影响行数异常不能继续发布协议或缓存。 */
    private void requireUpdated(int changed) {
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "应用配置已变化，请刷新后重试");
        }
    }

    /** 构造既有400输入错误，固定文案不输出秘密。 */
    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
