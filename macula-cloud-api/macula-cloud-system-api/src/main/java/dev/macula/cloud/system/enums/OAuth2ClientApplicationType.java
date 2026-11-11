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
package dev.macula.cloud.system.enums;

/**
 * 应用架构类型；与登录渠道和OAuth grant分开建模。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum OAuth2ClientApplicationType {
    WEB_SERVER, SPA, NATIVE, SERVICE, MINIAPP, IN_APP;

    /**
     * 按实际取码场景校验能力；公共Client认证不是跨终端使用SDK的许可。
     * @param code 平台凭证类型
     * @param miniappPlatform 已保存的小程序平台
     * @return 当前架构是否允许该场景
     */
    public boolean supportsExternalCode(ExternalCodeType code, String miniappPlatform) {
        if (code == null) { return false; }
        return switch (this) {
            case IN_APP -> code.scene() == ExternalCodeType.Scene.WEBVIEW;
            case WEB_SERVER, SPA -> false;
            case NATIVE -> code.scene() == ExternalCodeType.Scene.APP;
            case MINIAPP -> code.scene() == ExternalCodeType.Scene.MINIAPP
                && code.providerType().equals(miniappPlatform);
            case SERVICE -> false;
        };
    }

    /**
     * 网页登录按应用架构和精确能力筛选；环境只影响入口体验，不构成平台身份证明。
     * @param source 已配置的具体来源
     * @return 是否可关联网页能力
     */
    public boolean supportsBrowserSource(IdentitySourceType source) {
        return source != null && java.util.Arrays.stream(IdentityLoginCapability.values())
            .anyMatch(capability -> capability.source() == source && supportsBrowserCapability(capability));
    }

    /** 精确到能力，避免企微共用来源时把PC扫码和端内授权当成同一入口。 */
    public boolean supportsBrowserCapability(IdentityLoginCapability capability) {
        if (capability == null || !capability.browser()) { return false; }
        return switch (this) {
            case WEB_SERVER, SPA -> capability.webLogin();
            case IN_APP -> capability.inAppLogin();
            case NATIVE, MINIAPP, SERVICE -> false;
        };
    }
    /** 内置托管登录与直接平台Code可以并存，但端内应用不能回退标准页。 */
    public boolean supportsHostedLogin() { return this != IN_APP && this != SERVICE; }
    /** 在统一Token边界约束实际请求；仅允许标准授权和受控第三方Code扩展。 */
    public boolean supportsGrant(String grant) {
        if (this == SERVICE) { return "client_credentials".equals(grant); }
        if (this == IN_APP) { return "authorization_code".equals(grant) || ExternalCodeType.GRANT_TYPE.equals(grant); }
        if (ExternalCodeType.GRANT_TYPE.equals(grant)) { return this == NATIVE || this == MINIAPP; }
        return java.util.Set.of("authorization_code", "refresh_token").contains(grant);
    }
}
