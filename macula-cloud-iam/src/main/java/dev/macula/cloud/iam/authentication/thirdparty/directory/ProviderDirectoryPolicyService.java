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
import dev.macula.cloud.iam.authentication.thirdparty.provider.ProviderIntegrationProperties;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.directory.IdentityDirectoryService;
import dev.macula.cloud.system.enums.IdentityDirectoryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 两条登录链路共用企业目录准入，不因存在绑定而跳过停用/非成员判断。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public class ProviderDirectoryPolicyService implements IdentityDirectoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderDirectoryPolicyService.class);
    private final IdentityProviderConfigService configs;
    private final List<ProviderDirectoryAdapter> adapters;
    private final ProviderMemberSnapshotStore snapshots;
    private final ProviderDirectoryRefreshService refresh;
    private final ProviderIntegrationProperties properties;

    /**
     * 适配器只能来自受控Bean，调用方不能传入兑换端点。
     */
    public ProviderDirectoryPolicyService(IdentityProviderConfigService configs,
            List<ProviderDirectoryAdapter> adapters,
            ProviderMemberSnapshotStore snapshots, ProviderDirectoryRefreshService refresh,
            ProviderIntegrationProperties properties) {
        this.configs = configs;
        this.adapters = List.copyOf(adapters);
        this.snapshots = snapshots;
        this.refresh = refresh;
        this.properties = properties;
    }

    /**
     * 网络必须在绑定数据库事务之前完成，返回有限期只读准入快照。
     */
    public Admission check(VerifiedExternalIdentity identity) {
        var source = current(identity);
        var policy = source.directoryPolicy();
        // 用户本轮暂缓企微敏感资料：新来源只消费基础授权已核实的企业成员ID，不额外要求通讯录权限。
        // 历史REQUIRED/BACKGROUND仍走原路径，不能因界面隐藏而降低既有准入。
        if (policy == IdentityDirectoryPolicy.AUTO && "WECOM".equals(source.providerType())
                && VerifiedExternalIdentity.MEMBER_ID.equals(identity.identityKind())
                && identity.identityValue().equals(identity.attributes().get("member_id"))
                && Objects.equals(source.organizationId(), source.require("corpId"))) {
            return admission(identity, source.organizationId(),
                    Map.of("member_id", identity.identityValue()));
        }
        if (policy == IdentityDirectoryPolicy.IDENTITY_ONLY) {
            return admission(identity, source.organizationId());
        }
        if (policy == IdentityDirectoryPolicy.BACKGROUND) {
            var stored = snapshots.find(identity);
            if (stored != null) {
                enforce(stored, source, false);
            }
            return admission(identity, source.organizationId());
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw unavailable();
        }
        ProviderDirectoryResult result = adapter(source.providerType()).lookup(source, identity);
        current(identity);
        enforce(result, source, policy == IdentityDirectoryPolicy.REQUIRED);
        try {
            snapshots.save(identity, result);
        } catch (RuntimeException ex) {
            LoggerFactory.getLogger(ProviderDirectoryPolicyService.class)
                    .warn("Directory profile snapshot unavailable category=INFRASTRUCTURE");
        }
        return admission(identity, source.organizationId(), result.status() == ProviderDirectoryResult.Status.MEMBER
                ? result.profile() : Map.of());
    }

    /**
     * 仅补充实时平台服务端核实的白名单资料，不改变身份键、来源或本地账号。
     */
    public VerifiedExternalIdentity enrich(VerifiedExternalIdentity identity, Admission admission) {
        if (!sameIdentity(identity, admission.identity())) {
            throw unavailable();
        }
        if (admission.profile().isEmpty()) {
            return identity;
        }
        var attributes = new HashMap<>(identity.attributes());
        admission.profile().forEach((key, value) -> {
            // 资料不能替换已经由登录合同证明的成员ID或手机号；不把任意显示字段当身份。
            if (attributes.containsKey(key) && !Objects.equals(attributes.get(key), value)) {
                throw unavailable();
            }
            attributes.put(key, value);
        });
        return new VerifiedExternalIdentity(identity.registrationId(), identity.providerType(), identity.sourceType(),
                identity.sourceVersion(), identity.flow(), identity.capability(), identity.identityKind(),
                identity.identityValue(), attributes);
    }

    /**
     * 仅账号解析/绑定成功后入队，不遍历企业，也不为未绑定身份创建无限任务。
     */
    public void bound(VerifiedExternalIdentity identity) {
        var source = current(identity);
        if (source.directoryPolicy() == IdentityDirectoryPolicy.BACKGROUND) {
            try {
                refresh.enqueueBound(identity);
            } catch (RuntimeException ex) {
                LoggerFactory.getLogger(ProviderDirectoryPolicyService.class)
                        .warn("Directory refresh enqueue unavailable category=INFRASTRUCTURE");
            }
        }
    }

    /**
     * 来源版本原值必须仍然有效，不能拿当前配置补到旧核验结果上。
     */
    private IdentityProviderConfigService.ProviderConfig current(VerifiedExternalIdentity identity) {
        var source = configs.requireEnabledById(identity.registrationId(), identity.providerType())
                .forCapability(identity.loginCapability());
        if (!Objects.equals(source.version(), identity.sourceVersion()) ||
                !source.flow().value().equals(identity.flow())) {
            throw unavailable();
        }
        return source;
    }

    /**
     * 每次只使用具体来源所属平台的一个固定适配器，不做失败后换接口。
     */
    private ProviderDirectoryAdapter adapter(String provider) {
        return adapters.stream().filter(item -> item.providerType().equals(provider)).findFirst()
                .orElseThrow(this::unavailable);
    }

    /**
     * 非成员、停用与未知平台业务拒绝即使OPTIONAL也不能放过。
     */
    private void enforce(ProviderDirectoryResult result, IdentityProviderConfigService.ProviderConfig source,
            boolean required) {
        if (result == null || result.status() == ProviderDirectoryResult.Status.NOT_MEMBER
                || result.status() == ProviderDirectoryResult.Status.DISABLED) {
            logRejection(result, required, "member_rejected");
            throw unavailable();
        }
        if (result.status() == ProviderDirectoryResult.Status.MEMBER) {
            if (!Objects.equals(source.organizationId(), result.organizationId())) {
                logRejection(result, required, "organization_mismatch");
                throw unavailable();
            }
            return;
        }
        if (required || !result.tolerableProfileFailure()) {
            logRejection(result, required, "proof_unavailable");
            throw unavailable();
        }
    }

    /** 仅输出受控枚举和策略，不记录来源、组织、外部主体或目录资料。 */
    private void logRejection(ProviderDirectoryResult result, boolean required, String reason) {
        LOGGER.warn("第三方目录准入拒绝 stage=admission status={} required={} tolerable={} reason={}",
                result == null ? "MISSING" : result.status(), required,
                result != null && result.tolerableProfileFailure(), reason);
    }

    /**
     * 快照最多保持配置的资料TTL，不延长原来源证明。
     */
    private Admission admission(VerifiedExternalIdentity identity, String organizationId) {
        return admission(identity, organizationId, Map.of());
    }

    /**
     * 只有实时核实的同一成员目录资料可补充绑定凭据；不使用BACKGROUND旧快照自动匹配。
     */
    private Admission admission(VerifiedExternalIdentity identity, String organizationId,
            Map<String, String> profile) {
        var ttl = properties.getMemberSnapshotTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw unavailable();
        }
        return new Admission(identity, identity.sourceVersion(), identity.flow(), organizationId,
                Instant.now().plus(ttl), profile);
    }

    /**
     * 不同registration/应用/主体不能复用同一份准入快照；资料补充不改变身份键。
     */
    private boolean sameIdentity(VerifiedExternalIdentity left, VerifiedExternalIdentity right) {
        return Objects.equals(left.registrationId(), right.registrationId())
                && left.identityKind().equals(right.identityKind()) &&
                left.identityValue().equals(right.identityValue())
                && left.flow().equals(right.flow()) && Objects.equals(left.sourceVersion(), right.sourceVersion());
    }

    /**
     * 对客户端不公开企业成员身份与具体目录权限。
     */
    private AuthenticationServiceException unavailable() {
        return new AuthenticationServiceException("身份源成员准入暂不可用");
    }
}
