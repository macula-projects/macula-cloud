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

/**
 * OAuth Client 可显式授权的外部短期凭证类型。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum ExternalCodeType {
    FEISHU_APP_CODE("FEISHU", Scene.APP, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    WECOM_APP_CODE("WECOM", Scene.APP, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    DINGTALK_APP_CODE("DINGTALK", Scene.APP, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    WECHAT_OPEN_APP_CODE("WECHAT", Scene.APP, ResultType.EXTERNAL_IDENTITY, Capability.WECHAT_MOBILE_APP),
    ALIYUN_MOBILE_TOKEN("ALIYUN_MOBILE", Scene.APP, ResultType.MOBILE, Capability.DEFAULT),
    WECHAT_MINIAPP_LOGIN_CODE("WECHAT", Scene.MINIAPP, ResultType.EXTERNAL_IDENTITY,
        Capability.WECHAT_MINIAPP),
    WECHAT_MINIAPP_PHONE_CODE("WECHAT", Scene.MINIAPP, ResultType.MOBILE, Capability.WECHAT_MINIAPP),
    WECOM_MINIAPP_CODE("WECOM", Scene.MINIAPP, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    FEISHU_WEBVIEW_CODE("FEISHU", Scene.WEBVIEW, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    DINGTALK_WEBVIEW_CODE("DINGTALK", Scene.WEBVIEW, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    FEISHU_BROWSER_CODE("FEISHU", Scene.BROWSER, ResultType.EXTERNAL_IDENTITY,
        Capability.FEISHU_EXTERNAL_REDIRECT),
    DINGTALK_BROWSER_CODE("DINGTALK", Scene.BROWSER, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    WECOM_BROWSER_CODE("WECOM", Scene.BROWSER, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT),
    WECHAT_OPEN_BROWSER_CODE("WECHAT", Scene.BROWSER, ResultType.EXTERNAL_IDENTITY, Capability.DEFAULT);

    /** external_code 扩展 grant 的协议值。 */
    public static final String GRANT_TYPE = "urn:macula:params:oauth:grant-type:external_code";

    private final String providerType;
    private final Scene scene;
    private final ResultType resultType;
    private final Capability capability;

    ExternalCodeType(String providerType, Scene scene, ResultType resultType, Capability capability) {
        this.providerType = providerType;
        this.scene = scene;
        this.resultType = resultType;
        this.capability = capability;
    }

    public String providerType() {
        return providerType;
    }

    public Scene scene() {
        return scene;
    }

    public ResultType resultType() {
        return resultType;
    }

    public Capability capability() {
        return capability;
    }

    public static ExternalCodeType fromValue(String value) {
        return Arrays.stream(values()).filter(type -> type.name().equals(value))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的外部凭证类型"));
    }

    /**
     * 端侧取得外部凭证的来源场景。
     *
     * @since 6.1.0
     */
    public enum Scene {
        APP,
        MINIAPP,
        WEBVIEW,
        BROWSER
    }

    /**
     * 服务端核验凭证后允许产生的身份结果类别。
     *
     * @since 6.1.0
     */
    public enum ResultType {
        EXTERNAL_IDENTITY,
        MOBILE
    }

    /**
     * 身份源 registration 必须具备的服务端配置能力。
     *
     * @since 6.1.0
     */
    public enum Capability {
        DEFAULT,
        FEISHU_EXTERNAL_REDIRECT,
        WECHAT_MOBILE_APP,
        WECHAT_MINIAPP
    }
}
