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
package dev.macula.cloud.iam.service.application;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.system.enums.HostedLoginMethod;
import dev.macula.cloud.system.enums.IamApplicationPurpose;
import dev.macula.cloud.system.enums.IamApplicationStatus;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 一致性事务内组装的中立应用快照；包含非秘密协议视图，不携带凭据或Mapper实体。
 * @param id 内部应用主键
 * @param applicationCode 稳定应用代码
 * @param codeIssuedAt 原始代码签发时间
 * @param applicationName 应用名称
 * @param purpose 应用用途
 * @param managedKey 平台受管标识
 * @param authorizationVersion 既有授权版本
 * @param status 配置状态
 * @param tenantId 认证租户
 * @param userType 用户目录代码
 * @param resourcePermissions Scope资源许可
 * @param login 公共登录策略
 * @param protocol 唯一非秘密协议登记；空草稿为null
 * @param hasCredential 只指示已有凭据，不返回摘要
 * @author Rain
 * @since 6.1.0
 */
public record IamApplicationSnapshot(Long id, String applicationCode, Instant codeIssuedAt, String applicationName,
    IamApplicationPurpose purpose, String managedKey, Long authorizationVersion, IamApplicationStatus status,
    Long tenantId, String userType, Map<String, String> resourcePermissions, LoginPolicy login,
    IamApplicationProtocolRegistration<?> protocol, boolean hasCredential) {

    /** 防止调用者修改公共权限集合，协议设置由本次事务独立解析。 */
    public IamApplicationSnapshot {
        resourcePermissions = Map.copyOf(resourcePermissions);
    }

    /** 按既有用户目录代码取得类型，不能把未知值回退为员工目录。 */
    public UserTypeEnum accountUserType() {
        if (userType == null) {
            return null;
        }
        return Arrays.stream(UserTypeEnum.values()).filter(type -> type.getType().equals(userType)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("用户目录无效"));
    }

    /** 登录必须有启用的已分配协议；资源管理身份与旧保留槽均不能进入登录。 */
    public boolean loginEnabled() {
        return purpose == IamApplicationPurpose.APPLICATION && status == IamApplicationStatus.ENABLED
            && protocol != null && protocol.assigned() && protocol.enabled();
    }

    /**
     * 登录策略与协议grant分离；null托管方式继承平台，空集合明确全部关闭。
     * @param type 应用架构，未确认的历史草稿可空
     * @param webLoginMode Web/SPA模式，其他架构为空
     * @param miniappPlatform 小程序平台
     * @param hostedMethods 本地登录方式
     * @param directCapability 浏览器直接发起能力
     * @param sources 能力到固定身份源的映射
     * @since 6.1.0
     */
    public record LoginPolicy(IamApplicationType type, IamWebLoginMode webLoginMode, String miniappPlatform,
        Set<HostedLoginMethod> hostedMethods, IdentityLoginCapability directCapability,
        Map<IdentityLoginCapability, Long> sources) {
        /** 保留继承/关闭的区别并复制来源映射。 */
        public LoginPolicy {
            hostedMethods = hostedMethods == null ? null : Set.copyOf(hostedMethods);
            sources = Map.copyOf(sources);
        }
    }
}
