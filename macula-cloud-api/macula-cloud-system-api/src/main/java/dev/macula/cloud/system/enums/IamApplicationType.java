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

import java.util.Arrays;
import java.util.Set;

/**
 * 应用架构类型；与登录渠道和OAuth grant分开建模。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum IamApplicationType {
    WEB_SERVER, SPA, NATIVE, SERVICE, MINIAPP;

    /** Web架构具有独立的网页登录模式，其他架构保持各自终端语义。 */
    public boolean webApplication() { return this == WEB_SERVER || this == SPA; }

    /**
     * 按实际取码场景校验能力；公共Client认证不是跨终端使用SDK的许可。
     * @param code 平台凭证类型
     * @param miniappPlatform 已保存的小程序平台
     * @param webLoginMode Web/SPA登录模式，其他架构为空
     * @return 当前架构是否允许该场景
     */
    public boolean supportsExternalCode(ExternalCodeType code, String miniappPlatform, String webLoginMode) {
        boolean inApp = IamWebLoginMode.isInApp(name(), webLoginMode);
        if (code == null) { return false; }
        return switch (this) {
            case WEB_SERVER, SPA -> inApp && code.scene() == ExternalCodeType.Scene.WEBVIEW;
            case NATIVE -> code.scene() == ExternalCodeType.Scene.APP;
            case MINIAPP -> code.scene() == ExternalCodeType.Scene.MINIAPP
                && code.providerType().equals(miniappPlatform);
            case SERVICE -> false;
        };
    }

    /**
     * 网页登录按应用架构和精确能力筛选；环境只影响入口体验，不构成平台身份证明。
     * @param source 已配置的具体来源
     * @param webLoginMode Web/SPA登录模式，其他架构为空
     * @return 是否可关联网页能力
     */
    public boolean supportsBrowserSource(IdentitySourceType source, String webLoginMode) {
        return source != null && Arrays.stream(IdentityLoginCapability.values())
            .anyMatch(capability -> capability.source() == source && supportsBrowserCapability(capability, webLoginMode));
    }

    /** 精确到能力，避免企微共用来源时把PC扫码和端内授权当成同一入口。 */
    public boolean supportsBrowserCapability(IdentityLoginCapability capability, String webLoginMode) {
        boolean inApp = IamWebLoginMode.isInApp(name(), webLoginMode);
        if (capability == null || !capability.browser()) { return false; }
        return switch (this) {
            case WEB_SERVER, SPA -> inApp ? capability.inAppLogin() : capability.webLogin();
            case NATIVE, MINIAPP, SERVICE -> false;
        };
    }
    /** 内置托管登录与直接平台Code可以并存，但端内应用不能回退标准页。 */
    public boolean supportsHostedLogin(String webLoginMode) {
        return !IamWebLoginMode.isInApp(name(), webLoginMode) && this != SERVICE;
    }
    /** 在统一Token边界约束实际请求；仅允许标准授权和受控第三方Code扩展。 */
    public boolean supportsGrant(String grant, String webLoginMode) {
        boolean inApp = IamWebLoginMode.isInApp(name(), webLoginMode);
        if (this == SERVICE) { return "client_credentials".equals(grant); }
        if (inApp) { return "authorization_code".equals(grant) || ExternalCodeType.GRANT_TYPE.equals(grant); }
        if (ExternalCodeType.GRANT_TYPE.equals(grant)) { return this == NATIVE || this == MINIAPP; }
        return Set.of("authorization_code", "refresh_token").contains(grant);
    }
}
