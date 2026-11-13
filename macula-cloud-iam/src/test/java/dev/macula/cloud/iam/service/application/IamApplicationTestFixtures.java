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

import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2LoginApplicationResolver;
import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import dev.macula.cloud.iam.service.account.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.account.impl.DefaultClientAccountDetailsService;
import dev.macula.cloud.iam.service.account.SysUserService;
import dev.macula.cloud.iam.service.account.UserAuthInfoService;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.HostedLoginMethod;
import dev.macula.cloud.system.enums.IamApplicationPurpose;
import dev.macula.cloud.system.enums.IamApplicationStatus;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistration;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 历史协议测试素材到中立应用端口的桥接；请求校验仍执行生产OAuth适配器。
 * 仅隔离存储，不作为事务一致性或真实数据库证据。
 * @author Rain
 * @since 6.1.0
 */
public final class IamApplicationTestFixtures {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 工具类不允许实例化。 */
    private IamApplicationTestFixtures() { }

    /** 将合成协议投影转换为独立应用快照，保留显式关闭和能力映射。 */
    public static IamApplicationSnapshot snapshot(OAuth2ClientRegistration row) {
        if (row.getProtocolConfigurations() == null || row.getProtocolConfigurations().isEmpty()) {
            ProtocolTestFixtures.configure(row);
        }
        Map<IdentityLoginCapability, Long> routes = new HashMap<>();
        if (row.getBrowserCapabilityBindings() != null) {
            Map<String, Long> browser = JSON.readValue(row.getBrowserCapabilityBindings(), new TypeReference<>() { });
            browser.forEach((key, value) -> routes.put(IdentityLoginCapability.fromValue(key), value));
        }
        if (row.getBrowserCapabilityBindings() == null && row.getBrowserProviderBindings() != null) {
            Map<String, Long> fixed = JSON.readValue(row.getBrowserProviderBindings(), new TypeReference<>() { });
            Set<String> allowed = csv(row.getBrowserLoginProviderTypes());
            if (!fixed.keySet().equals(allowed)) { throw new IllegalArgumentException("合成网页策略不一致"); }
            fixed.forEach((provider, id) -> {
                BrowserLoginProviderType.fromValue(provider);
                IdentityLoginCapability cap = switch (provider) {
                    case "GITHUB" -> IdentityLoginCapability.GITHUB_OAUTH;
                    case "WECOM" -> "IN_APP".equals(row.getWebLoginMode())
                        ? IdentityLoginCapability.WECOM_WEBVIEW : IdentityLoginCapability.WECOM_BROWSER;
                    case "WECHAT" -> "IN_APP".equals(row.getWebLoginMode())
                        ? IdentityLoginCapability.WECHAT_OFFICIAL_ACCOUNT : IdentityLoginCapability.WECHAT_WEBSITE;
                    case "FEISHU" -> IdentityLoginCapability.FEISHU_WEB;
                    case "DINGTALK" -> IdentityLoginCapability.DINGTALK_WEB;
                    default -> throw new IllegalArgumentException("合成网页来源未知");
                };
                routes.put(cap, id);
            });
        }
        if (row.getExternalProviderBindings() != null) {
            row.getExternalProviderBindings().forEach((key, value) -> routes.put(
                IdentityLoginCapability.forCode(ExternalCodeType.fromValue(key)), value));
        }
        var config = row.getProtocolConfigurations().get(0);
        var payload = new OAuth2ApplicationProtocolSettings();
        payload.setOauth2(OAuth2ProtocolConfiguration.settings(config));
        payload.setExternalCodeTypes(csv(row.getExternalCodeTypes()).stream().map(ExternalCodeType::fromValue)
            .collect(Collectors.toUnmodifiableSet()));
        Set<HostedLoginMethod> methods = row.getHostedLoginMethods() == null ? null
            : csv(row.getHostedLoginMethods()).stream().map(HostedLoginMethod::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        IdentityLoginCapability direct = null;
        if (row.getDirectLoginProviderType() != null && !row.getDirectLoginProviderType().isBlank()) {
            direct = routes.keySet().stream().filter(cap -> cap.browser()
                && cap.source().providerType().equals(row.getDirectLoginProviderType())).findFirst().orElseThrow(() -> new IllegalArgumentException("合成直接入口未关联"));
        }
        return new IamApplicationSnapshot(row.getId() == null ? 1L : row.getId(), row.getClientId(),
            row.getClientIdIssuedAt(), row.getClientName(), IamApplicationPurpose.valueOf(row.getPurpose()),
            row.getManagedKey(), row.getAuthorizationVersion(), IamApplicationStatus.valueOf(row.getConfigurationStatus()),
            row.getTenantId(), row.getUserType() == null ? null : row.getUserType().getType(), Map.of(),
            new IamApplicationSnapshot.LoginPolicy(IamApplicationType.valueOf(row.getApplicationType()),
                row.getWebLoginMode() == null ? null : IamWebLoginMode.valueOf(row.getWebLoginMode()),
                row.getMiniappPlatform(), methods, direct, routes),
            new IamApplicationProtocolRegistration<>(config.protocol(), 2, config.enabled(), row.getClientId(),
                row.getClientIdIssuedAt(), payload), row.getClientSecret() != null);
    }

    /** 创建执行生产SavedRequest校验和能力许可的协议端口。 */
    public static LoginApplicationResolver resolver(OAuth2ClientRegistrationService clients,
        AuthorizationServerSettings settings) {
        return ports(clients, settings).resolver();
    }

    /** 为旧账号测试注入内部ID与可信引用端口，不在生产服务保留OAuth构造器。 */
    public static ClientAccountDetailsService accounts(SysUserService users, OAuth2ClientRegistrationService clients,
        UserAuthInfoService external, ClientAwareAccountResolver identifiers) {
        var ports = ports(clients, AuthorizationServerSettings.builder().build());
        return new DefaultClientAccountDetailsService(users, ports.applications(), ports.resolver(), external, identifiers);
    }

    /** 两个端口共享本例的合成存储，读取时重新投影可变测试配置。 */
    public static Ports ports(OAuth2ClientRegistrationService clients, AuthorizationServerSettings settings) {
        Map<Long, OAuth2ClientRegistration> rows = new HashMap<>();
        var applications = mock(IamApplicationService.class);
        var protocols = mock(SysIamApplicationProtocolConfigMapper.class);
        var observed = new OAuth2ClientRegistrationService() {
            /** 记录真实协议查询结果对应的应用ID。 */
            @Override
            public OAuth2ClientRegistration getClientByClientId(String reference) {
                var row = clients.getClientByClientId(reference);
                if (row != null) {
                    if (row.getId() == null) { row.setId(1L); }
                    if (row.getClientId() == null) { row.setClientId(reference); }
                    rows.put(row.getId(), row);
                }
                return row;
            }
            /** 维持原Origin端口委托。 */
            @Override
            public Set<String> allowedOrigins() { return clients.allowedOrigins(); }
            /** 维持逐Client校验端口委托。 */
            @Override
            public boolean isAllowedOrigin(String id, String origin) { return clients.isAllowedOrigin(id, origin); }
        };
        when(protocols.selectApplicationIdByOAuth2Identifier(any())).thenAnswer(call -> {
            var row = observed.getClientByClientId(call.getArgument(0));
            return row == null ? null : row.getId();
        });
        when(applications.findById(any())).thenAnswer(call -> {
            var row = rows.get(call.getArgument(0));
            return row == null ? null : snapshot(row);
        });
        return new Ports(applications, new OAuth2LoginApplicationResolver(observed, applications, protocols, settings));
    }

    /** 解析测试中保留的旧CSV输入；不参与生产转换。 */
    private static Set<String> csv(String value) {
        return value == null || value.isBlank() ? Set.of()
            : Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    /** 本例共用的非持久化端口，避免生产代码增加测试分支。 @since 6.1.0 */
    public record Ports(IamApplicationService applications, LoginApplicationResolver resolver) { }
}
