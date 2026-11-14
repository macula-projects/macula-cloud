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
package dev.macula.cloud.iam.service.application.impl;

import dev.macula.cloud.iam.service.application.IamApplicationService;
import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;

import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.iam.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysIamApplicationMapper;
import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.pojo.entity.SysIamApplication;
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.system.enums.HostedLoginMethod;
import dev.macula.cloud.system.enums.IamApplicationPurpose;
import dev.macula.cloud.system.enums.IamApplicationStatus;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistry;
import dev.macula.cloud.system.security.IamApplicationLoginPolicy;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 公共应用读模型；普通读取使用同一RR快照，绑定事务显式使用锁定当前读。
 * 协议专有解析由注册策略处理，凭据仅投影存在性，不进入登录快照。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class IamApplicationServiceImpl implements IamApplicationService {
    private final SysIamApplicationMapper applications;
    private final SysIamApplicationProtocolConfigMapper protocols;
    private final SysIamApplicationIdentityProviderMapper identityProviders;
    private final DateTimeConverter dates;
    private final IamApplicationProtocolRegistry registry;
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    /** 存在的草稿也返回明确视图；调用者必须检查用途和loginEnabled。 */
    @Override
    public IamApplicationSnapshot findById(Long applicationId) {
        return read(applicationId, false);
    }

    /** 共同主行锁由调用方事务持有到提交，不能在本方法退出时提前释放。 */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IamApplicationSnapshot lock(Long applicationId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("锁定应用需要调用者的可写事务");
        }
        return read(applicationId, true);
    }

    /** 只读取当前模式下同一种快照，异常不透传JSON片段或凭据。 */
    private IamApplicationSnapshot read(Long applicationId, boolean lock) {
        if (applicationId == null) {
            return null;
        }
        SysIamApplication app = lock ? applications.selectByIdForUpdate(applicationId) : applications.selectById(applicationId);
        if (app == null) {
            return null;
        }
        try {
            SysIamApplicationProtocolConfig row = lock ? protocols.selectByApplicationIdForUpdate(applicationId)
                : protocols.selectByApplicationId(applicationId);
            var protocol = row == null ? null : registry.read(row.getProtocol(), row.getSchemaVersion(),
                Boolean.TRUE.equals(row.getEnabled()), row.getProtocolIdentifier(), dates.toInstant(row.getIdentifierIssuedAt()),
                row.getConfiguration(), app.getApplicationType(), app.getPurpose());
            var status = IamApplicationStatus.fromValue(app.getConfigurationStatus());
            var purpose = IamApplicationPurpose.valueOf(app.getPurpose());
            boolean enabled = status == IamApplicationStatus.ENABLED;
            if (enabled && (protocol == null || !protocol.assigned() || !protocol.enabled())
                || protocol != null && protocol.enabled() && !enabled) {
                throw invalid();
            }
            if (enabled && (row.getProtocolIdentifier() == null || row.getProtocolIdentifier().isBlank()
                || registry.requiresSecret(protocol) && (row.getCredentialHash() == null || row.getCredentialHash().isBlank()))) {
                throw invalid();
            }
            Map<IdentityLoginCapability, Long> sources = new LinkedHashMap<>();
            var associations = lock ? identityProviders.selectByApplicationIdForUpdate(applicationId)
                : identityProviders.selectByApplicationId(applicationId);
            for (var association : associations) {
                IdentityLoginCapability capability = IdentityLoginCapability.fromValue(association.getLoginCapability());
                if (association.getIdentityProviderId() == null || association.getIdentityProviderId() <= 0
                    || sources.putIfAbsent(capability, association.getIdentityProviderId()) != null) {
                    throw invalid();
                }
            }
            IamApplicationType type = app.getApplicationType() == null ? null : IamApplicationType.valueOf(app.getApplicationType());
            if (enabled && purpose == IamApplicationPurpose.APPLICATION && (app.getTenantId() == null
                || type != IamApplicationType.SERVICE && !Set.of("member", "employee", "odc", "supplier").contains(app.getUserType() == null ? "" : app.getUserType()))) {
                throw invalid();
            }
            IamWebLoginMode mode = type == null ? null : IamWebLoginMode.resolve(app.getApplicationType(), app.getWebLoginMode());
            if (type == null && app.getWebLoginMode() != null) {
                throw invalid();
            }
            IdentityLoginCapability direct = app.getDirectLoginCapability() == null ? null
                : IdentityLoginCapability.fromValue(app.getDirectLoginCapability());
            if (direct != null && (!direct.browser() || !sources.containsKey(direct))) {
                throw invalid();
            }
            validateLogin(type, app, sources, enabled, purpose);
            Map<String, String> permissions = permissions(app.getResourcePermissions());
            var login = new IamApplicationSnapshot.LoginPolicy(type, mode, app.getMiniappPlatform(), hosted(app.getHostedLoginMethods()), direct, sources);
            return new IamApplicationSnapshot(app.getId(), app.getApplicationCode(), dates.toInstant(app.getApplicationCodeIssuedAt()),
                app.getApplicationName(), purpose, app.getManagedKey(), app.getAuthorizationVersion(), status,
                app.getTenantId(), app.getUserType(), OAuth2ResourcePolicy.allowedUserTypes(app.getAllowedUserTypes()), permissions, login, protocol,
                row != null && row.getCredentialHash() != null && !row.getCredentialHash().isBlank());
        } catch (RuntimeException error) {
            throw invalid();
        }
    }

    /** 来源数量与终端边界属于登录领域，不依赖OAuth grant或发布对象。 */
    private void validateLogin(IamApplicationType type, SysIamApplication app, Map<IdentityLoginCapability, Long> sources,
        boolean enabled, IamApplicationPurpose purpose) {
        if (purpose == IamApplicationPurpose.RESOURCE_SERVER) {
            if (!sources.isEmpty() || app.getDirectLoginCapability() != null) {
                throw invalid();
            }
            return;
        }
        if (type == null) {
            if (enabled || !sources.isEmpty()) {
                throw invalid();
            }
            return;
        }
        Map<String, Long> browser = new LinkedHashMap<>();
        Map<String, Long> codes = new LinkedHashMap<>();
        sources.forEach((capability, id) -> {
            if (capability.browser()) {
                browser.put(capability.value(), id);
            } else if (capability.code() != null) {
                codes.put(capability.code().name(), id);
            } else {
                throw invalid();
            }
        });
        IamApplicationLoginPolicy.validate(type, app.getMiniappPlatform(), app.getWebLoginMode(), browser, codes, enabled);
    }

    /** 资源许可只接受字符串映射，不能让JSON数字/布尔值隐式变成资源标识。 */
    private Map<String, String> permissions(String configuration) {
        if (configuration == null) {
            return Map.of();
        }
        Map<String, Object> raw = json.readValue(configuration, new TypeReference<Map<String, Object>>() { });
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((scope, resource) -> {
            if (!(resource instanceof String value) || value.isBlank()) {
                throw invalid();
            }
            result.put(scope, value);
        });
        return result;
    }

    /** null继承、空字符串关闭；未知和重复登录方式均拒绝。 */
    private Set<HostedLoginMethod> hosted(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return Set.of();
        }
        var methods = Arrays.stream(value.split(",", -1)).map(HostedLoginMethod::valueOf).toList();
        if (Set.copyOf(methods).size() != methods.size()) {
            throw invalid();
        }
        return methods.stream().collect(Collectors.toUnmodifiableSet());
    }

    /** 有界配置错误，不能将解析器或数据库原始消息作为cause返回认证层。 */
    private IllegalStateException invalid() {
        return new IllegalStateException("应用配置无效");
    }
}
