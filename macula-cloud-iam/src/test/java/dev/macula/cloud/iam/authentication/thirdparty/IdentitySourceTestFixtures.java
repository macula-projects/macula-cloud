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
package dev.macula.cloud.iam.authentication.thirdparty;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import dev.macula.cloud.iam.authentication.thirdparty.browser.BrowserLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.RegistrationIdentityBindingService;
import dev.macula.cloud.system.enums.IdentitySourceType;

/**
 * 测试显式构造新身份源及Client路由；不向生产代码添加旧契约构造器。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class IdentitySourceTestFixtures {
    /**
     * 工具类不可实例化。
     */
    private IdentitySourceTestFixtures() {
    }

    /**
     * 仅为测试构造确定来源。
     */
    public static IdentityProviderConfigService.ProviderConfig source(IdentitySourceType type, String vendor,
            String appId, String secret, Map<String, String> values) {
        return source(type, vendor, appId, secret, values, Map.of());
    }

    /**
     * 测试可独立覆盖公开值和密钥，不参与运行时推断。
     */
    public static IdentityProviderConfigService.ProviderConfig source(IdentitySourceType type, String vendor,
            String appId, String secret, Map<String, String> values, Map<String, String> secrets) {
        return new IdentityProviderConfigService.ProviderConfig(1L, type, false, null, 0L, vendor, appId,
                secret, values, secrets, true,
                java.util.Arrays.stream(dev.macula.cloud.system.enums.IdentitySourceFlow.values())
                        .filter(item -> item.supports(type) && item.implemented()).findFirst().orElseThrow(),
                dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY, null,
                type == IdentitySourceType.FEISHU_APPLINK ? "https://fixture.example/callback" : null);
    }

    /**
     * 测试显式固定来源flow，不让生产运行时猜测未核实版本。
     */
    public static String flow(String source) {
        return java.util.Arrays.stream(dev.macula.cloud.system.enums.IdentitySourceFlow.values())
                .filter(item -> item.supports(IdentitySourceType.fromValue(source))).findFirst().orElseThrow().value();
    }

    /**
     * 为旧测试素材明确指定新浏览器来源和用户目录。
     */
    public static ThirdPartyLoginContext context(String vendor, String mode, String client, Instant at, String target) {
        IdentitySourceType type = switch (vendor) {
            case "GITHUB" -> IdentitySourceType.GITHUB_OAUTH;
            case "WECOM" -> IdentitySourceType.WECOM_WEB;
            case "FEISHU" -> IdentitySourceType.FEISHU_WEB;
            case "DINGTALK" -> IdentitySourceType.DINGTALK_WEB;
            case "WECHAT" -> "WEBVIEW".equals(mode) ? IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT :
                    IdentitySourceType.WECHAT_WEBSITE;
            default -> throw new IllegalArgumentException();
        };
        return new ThirdPartyLoginContext(vendor, mode, client, at, target, 1L, type.value(), 1L, UserTypeEnum.EMPLOYEE,
                0L, dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow(type.value()),
                "https://iam.example/login/" + vendor.toLowerCase());
    }

    /**
     * 构造来源明确的浏览器核验结果。
     */
    public static VerifiedExternalIdentity browserIdentity(ThirdPartyLoginContext context, String subject) {
        String kind = switch (context.providerType()) {
            case "GITHUB" -> VerifiedExternalIdentity.GITHUB_ID;
            case "FEISHU", "WECHAT" -> VerifiedExternalIdentity.OPEN_ID;
            default -> VerifiedExternalIdentity.MEMBER_ID;
        };
        Map<String, String> attrs = VerifiedExternalIdentity.MEMBER_ID.equals(kind)
                ? Map.of("member_id", subject, VerifiedExternalIdentity.LOGIN_CAPABILITY_ATTRIBUTE,
                context.loginCapability())
                : Map.of(VerifiedExternalIdentity.LOGIN_CAPABILITY_ATTRIBUTE, context.loginCapability());
        return new VerifiedExternalIdentity(context.registrationId(), context.providerType(), context.sourceType(), 0L,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow(context.sourceType()),
                VerifiedExternalIdentity.BROWSER, kind, subject, attrs);
    }

    /**
     * 构造测试待绑定异常。
     */
    public static ThirdPartyIdentityResolutionService.BindingRequiredException bindingRequired(String vendor,
            String subject,
            ThirdPartyLoginContext context) {
        return new ThirdPartyIdentityResolutionService.BindingRequiredException(browserIdentity(context, subject),
                context);
    }

    /**
     * 构造明确SDK来源的成员身份。
     */
    public static ExternalCodeVerification sdkIdentity(String vendor, String subject) {
        IdentitySourceType type = switch (vendor) {
            case "WECOM" -> IdentitySourceType.WECOM_SDK;
            case "DINGTALK" -> IdentitySourceType.DINGTALK_SDK;
            case "FEISHU" -> IdentitySourceType.FEISHU_APPLINK;
            case "WECHAT" -> IdentitySourceType.WECHAT_MOBILE_APP;
            default -> throw new IllegalArgumentException();
        };
        String code = switch (vendor) {
            case "WECOM" -> "WECOM_APP_CODE";
            case "DINGTALK" -> "DINGTALK_APP_CODE";
            case "FEISHU" -> "FEISHU_APP_CODE";
            default -> "WECHAT_OPEN_APP_CODE";
        };
        String kind = Set.of("FEISHU", "WECHAT").contains(vendor) ? VerifiedExternalIdentity.OPEN_ID :
                VerifiedExternalIdentity.MEMBER_ID;
        return new ExternalCodeVerification(new VerifiedExternalIdentity(1L, vendor, type.value(), 0L,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow(type.value()), code,
                kind, subject,
                VerifiedExternalIdentity.MEMBER_ID.equals(kind) ? Map.of("member_id", subject) : Map.of()));
    }

    /**
     * 号码证明没有伪造的平台永久subject。
     */
    public static ExternalCodeVerification mobile(String phone) {
        return new ExternalCodeVerification(new VerifiedExternalIdentity(1L, "ALIYUN_MOBILE", "ALIYUN_MOBILE", 0L,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow("ALIYUN_MOBILE"),
                "ALIYUN_MOBILE_TOKEN", VerifiedExternalIdentity.MOBILE_PROOF, phone, Map.of("verified_mobile", phone)));
    }

    /**
     * 测试中显式固定浏览器来源和员工目录。
     */
    public static BrowserLoginClientPolicyService.ClientPolicy policy(String clientId,
            Set<dev.macula.cloud.system.enums.BrowserLoginProviderType> providers,
            dev.macula.cloud.system.enums.BrowserLoginProviderType direct) {
        Map<String, Long> fixed = providers.stream().collect(java.util.stream.Collectors.toMap(Enum::name, v -> 1L));
        return new BrowserLoginClientPolicyService.ClientPolicy(clientId, providers, direct, fixed, 1L,
                UserTypeEnum.EMPLOYEE);
    }

    /**
     * 绑定查询用真实Service，Mapper模拟只隔离数据库；稳定ID与孤儿关系由Service校验。
     */
    public static RegistrationIdentityBindingService registrationService(
            dev.macula.cloud.iam.pojo.entity.SysUser account) {
        return registrationService(account, "OAUTH2_1");
    }

    /**
     * 同一真实绑定校验链必须支持两种单协议，不能靠Mock绕过协议判断。
     */
    public static RegistrationIdentityBindingService registrationService(
            dev.macula.cloud.iam.pojo.entity.SysUser account, String protocol) {
        var clients = org.mockito.Mockito.mock(dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper.class);
        var sources = org.mockito.Mockito.mock(dev.macula.cloud.iam.mapper.SysIdentityProviderMapper.class);
        var external = org.mockito.Mockito.mock(
                dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper.class);
        var users = org.mockito.Mockito.mock(dev.macula.cloud.iam.mapper.SysUserMapper.class);
        var rows = org.mockito.Mockito.mock(dev.macula.cloud.iam.mapper.SysRegistrationIdentityBindingMapper.class);
        var configs = org.mockito.Mockito.mock(IdentityProviderConfigService.class);
        var details = org.mockito.Mockito.mock(dev.macula.cloud.iam.service.account.ClientAccountDetailsService.class);
        var client = new dev.macula.cloud.iam.pojo.entity.SysOAuth2Client();
        client.setClientId("app");
        client.setApplicationType("NATIVE");
        client.setUserType(UserTypeEnum.EMPLOYEE);
        client.setTenantId(1L);
        client.setProtocolMode(protocol);
        client.setEnabledProtocols(protocol);
        client.setDefaultProtocol(protocol);
        client.setConfigurationStatus("ENABLED");
        client.setClientAuthenticationMethods("none");
        client.setRequireProofKey(true);
        client.setScopes("OIDC".equals(protocol) ? "openid,profile" : "profile");
        client.setExternalCodeTypes("WECOM_APP_CODE");
        client.setAuthorizationGrantTypes(dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE);
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(client.getProtocolConfigurations().get(0))
                .setAuthorizationGrantTypes(dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE);
        var protocols = org.mockito.Mockito.mock(dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper.class);
        org.mockito.Mockito.when(protocols.selectList(org.mockito.ArgumentMatchers.any())).thenAnswer(call ->
                client.getProtocolConfigurations().stream().map(configuration -> {
                    var row = new dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig();
                    row.setProtocol(configuration.protocol());
                    row.setSchemaVersion(1);
                    row.setEnabled(true);
                    row.setConfiguration(tools.jackson.databind.json.JsonMapper.builder().build()
                            .writeValueAsString(configuration.settings()));
                    return row;
                }).toList());
        org.mockito.Mockito.when(clients.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(client);
        org.mockito.Mockito.when(configs.requireEnabledById(1L, "WECOM"))
                .thenReturn(source(IdentitySourceType.WECOM_SDK,
                        "WECOM", "corp", "secret", Map.of("corpId", "corp", "agentId", "agent")));
        var mapping = new dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider();
        mapping.setIdentityProviderId(1L);
        mapping.setCodeType("WECOM_APP_CODE");
        org.mockito.Mockito.when(external.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(mapping));
        var row = new dev.macula.cloud.iam.pojo.entity.SysRegistrationIdentityBinding();
        row.setUserId(9L);
        row.setUserType("employee");
        org.mockito.Mockito.when(rows.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(row));
        org.mockito.Mockito.when(users.selectById(9L)).thenReturn(account);
        if (account != null) {
            account.setStatus(1);
            var principal = new dev.macula.cloud.iam.service.userdetails.SysUserDetails();
            principal.setUserId(account.getId());
            principal.setTenantId(1L);
            principal.setUsername(account.getUsername());
            principal.setEnabled(true);
            org.mockito.Mockito.when(details.loadUserByClient("app", 1L, UserTypeEnum.EMPLOYEE, account.getUsername()))
                    .thenReturn(principal);
        }
        return new RegistrationIdentityBindingService(clients, protocols, sources, external, users, rows, configs,
                details,
                new dev.macula.cloud.system.security.UserIdentifierNormalizer());
    }

}
