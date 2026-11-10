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

import java.util.Set;

/**
 * 具体身份源及其已实现能力，不承载任何密钥值或运行时服务。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentitySourceType {
    WECHAT_WEBSITE("WECHAT_WEBSITE", "微信网站应用", "WECHAT", "BROWSER", Set.of(), Set.of("appId", "appSecret")),
    WECHAT_OFFICIAL_ACCOUNT("WECHAT_OFFICIAL_ACCOUNT", "微信公众号", "WECHAT", "WEBVIEW", Set.of(), Set.of("appId", "appSecret")),
    WECHAT_MOBILE_APP("WECHAT_MOBILE_APP", "微信移动应用 SDK", "WECHAT", "APP", Set.of(), Set.of("appId", "appSecret")),
    WECHAT_MINIAPP("WECHAT_MINIAPP", "微信小程序", "WECHAT", "MINIAPP", Set.of(IdentityBindingRule.VERIFIED_MOBILE_TO_MOBILE), Set.of("appId", "appSecret")),
    WECOM_WEB("WECOM_WEB", "企业微信网页", "WECOM", "BROWSER", Set.of(IdentityBindingRule.MEMBER_ID_TO_USERNAME), Set.of("corpId", "agentId", "appSecret")),
    WECOM_SDK("WECOM_SDK", "企业微信 SDK", "WECOM", "APP", Set.of(IdentityBindingRule.MEMBER_ID_TO_USERNAME), Set.of("corpId", "agentId", "appSecret")),
    WECOM_MINIAPP("WECOM_MINIAPP", "企业微信小程序", "WECOM", "MINIAPP", Set.of(IdentityBindingRule.MEMBER_ID_TO_USERNAME), Set.of("corpId", "agentId", "appSecret")),
    FEISHU_WEB("FEISHU_WEB", "飞书网页", "FEISHU", "BROWSER", Set.of(IdentityBindingRule.EMPLOYEE_NO_TO_USERNAME), Set.of("appId", "appSecret")),
    FEISHU_APPLINK("FEISHU_APPLINK", "飞书 AppLink", "FEISHU", "APP", Set.of(IdentityBindingRule.EMPLOYEE_NO_TO_USERNAME), Set.of("appId", "appSecret")),
    FEISHU_WEBVIEW("FEISHU_WEBVIEW", "飞书端内网页", "FEISHU", "WEBVIEW", Set.of(IdentityBindingRule.EMPLOYEE_NO_TO_USERNAME), Set.of("appId", "appSecret")),
    DINGTALK_WEB("DINGTALK_WEB", "钉钉网页", "DINGTALK", "BROWSER", Set.of(IdentityBindingRule.MEMBER_ID_TO_USERNAME), Set.of("appKey", "appSecret", "corpId")),
    DINGTALK_SDK("DINGTALK_SDK", "钉钉 SDK", "DINGTALK", "APP", Set.of(IdentityBindingRule.MEMBER_ID_TO_USERNAME), Set.of("appKey", "appSecret", "corpId")),
    DINGTALK_WEBVIEW("DINGTALK_WEBVIEW", "钉钉端内网页", "DINGTALK", "WEBVIEW", Set.of(IdentityBindingRule.MEMBER_ID_TO_USERNAME), Set.of("appKey", "appSecret", "corpId")),
    GITHUB_OAUTH("GITHUB_OAUTH", "GitHub OAuth", "GITHUB", "BROWSER", Set.of(), Set.of("clientId", "clientSecret")),
    ALIYUN_MOBILE("ALIYUN_MOBILE", "阿里云号码认证", "ALIYUN_MOBILE", "APP", Set.of(IdentityBindingRule.VERIFIED_MOBILE_TO_MOBILE), Set.of("accessKeyId", "accessKeySecret")),
    ALIPAY_APPLICATION("ALIPAY_APPLICATION", "支付宝应用（仅凭据管理）", "ALIPAY", "CONFIGURATION", Set.of(), Set.of("appId", "appPrivateKey", "alipayPublicKey"));
    private final String value;
    private final String label;
    private final String providerType;
    private final String scene;
    private final Set<IdentityBindingRule> bindingRules;
    private final Set<String> requiredFields;
    /** 定义具体来源的独立凭据和匹配能力。 */
    IdentitySourceType(String value, String label, String providerType, String scene,
        Set<IdentityBindingRule> bindingRules, Set<String> requiredFields) {
        this.value = value;
        this.label = label;
        this.providerType = providerType;
        this.scene = scene;
        this.bindingRules = Set.copyOf(bindingRules);
        this.requiredFields = Set.copyOf(requiredFields);
    }
    /** 返回稳定来源值。 */
    public String value() { return value; }
    /** 返回界面名称。 */
    public String label() { return label; }
    /** 返回厂商协议值。 */
    public String providerType() { return providerType; }
    /**
     * 返回当前已核实网页授权的环境，网站扫码不作为手机端SDK替代品。
     * @return 已实现并核实的网页环境；AppLink与JSAPI另走自己的能力
     */
    public Set<IdentityBrowserEnvironment> browserEnvironments() {
        return switch (this) {
            case GITHUB_OAUTH -> Set.of(IdentityBrowserEnvironment.DESKTOP_BROWSER, IdentityBrowserEnvironment.MOBILE_BROWSER);
            case WECOM_WEB -> Set.of(IdentityBrowserEnvironment.DESKTOP_BROWSER, IdentityBrowserEnvironment.WECOM_WEBVIEW);
            case WECHAT_OFFICIAL_ACCOUNT -> Set.of(IdentityBrowserEnvironment.WECHAT_WEBVIEW);
            case FEISHU_WEB -> Set.of(IdentityBrowserEnvironment.DESKTOP_BROWSER, IdentityBrowserEnvironment.MOBILE_BROWSER,
                IdentityBrowserEnvironment.FEISHU_WEBVIEW);
            case WECHAT_WEBSITE -> Set.of(IdentityBrowserEnvironment.DESKTOP_BROWSER);
            case DINGTALK_WEB -> Set.of(IdentityBrowserEnvironment.DESKTOP_BROWSER, IdentityBrowserEnvironment.MOBILE_BROWSER);
            default -> Set.of();
        };
    }

    /**
     * 发起阶段同时核对环境和模式；回调必须继续使用state中的既定模式，不能按UA切换。
     * @param environment 当前交互环境，不代表可信身份
     * @param mode 现有登录模式值
     * @return 来源是否支持该网页发起组合
     */
    public boolean supportsBrowserEnvironment(IdentityBrowserEnvironment environment, String mode) {
        if (!browserEnvironments().contains(environment)) { return false; }
        return switch (this) {
            case GITHUB_OAUTH -> "".equals(mode);
            case WECHAT_OFFICIAL_ACCOUNT -> "WEBVIEW".equals(mode);
            case WECOM_WEB -> environment == IdentityBrowserEnvironment.WECOM_WEBVIEW
                ? "WEBVIEW".equals(mode) : "BROWSER".equals(mode);
            default -> "BROWSER".equals(mode);
        };
    }

    /** 返回支持的取得凭据场景。 */
    public String scene() { return scene; }
    /** 返回服务端实现的绑定规则。 */
    public Set<IdentityBindingRule> bindingRules() { return bindingRules; }
    /** 企业来源可补充组织权威属性，不能把普通社交资料当匹配证明。 */
    public boolean enterprise() { return Set.of("WECOM", "FEISHU", "DINGTALK").contains(providerType); }
    /** 返回已经实现的可信属性；企微敏感资料须另行核实本人授权合同。 */
    public java.util.List<IdentityBindingSourceField> bindingSourceFields() {
        return switch (providerType) {
            case "WECOM" -> java.util.List.of(IdentityBindingSourceField.MEMBER_ID);
            case "FEISHU", "DINGTALK" -> java.util.List.of(IdentityBindingSourceField.MEMBER_ID,
                IdentityBindingSourceField.EMPLOYEE_NO, IdentityBindingSourceField.MOBILE, IdentityBindingSourceField.EMAIL);
            case "WECHAT" -> this == WECHAT_MINIAPP ? java.util.List.of(IdentityBindingSourceField.MOBILE) : java.util.List.of();
            case "ALIYUN_MOBILE" -> java.util.List.of(IdentityBindingSourceField.MOBILE);
            default -> java.util.List.of();
        };
    }
    /** 返回独立凭据字段。 */
    public Set<String> requiredFields() { return requiredFields; }
    /** 返回此具体来源自己的公开应用标识字段。 */
    public String identifierField() {
        return switch (providerType) {
            case "GITHUB" -> "clientId";
            case "WECOM" -> "corpId";
            case "DINGTALK" -> "appKey";
            case "ALIYUN_MOBILE" -> "accessKeyId";
            default -> "appId";
        };
    }
    /** 返回唯一主凭据字段，不使用同厂商其他来源的密钥。 */
    public String credentialField() {
        return switch (providerType) {
            case "GITHUB" -> "clientSecret";
            case "ALIPAY" -> "appPrivateKey";
            case "ALIYUN_MOBILE" -> "accessKeySecret";
            default -> "appSecret";
        };
    }
    /** 身份域字段不可普通编辑，公钥等可轮换材料不属于账号身份域。 */
    public Set<String> identityFields() {
        return switch (providerType) {
            case "WECOM" -> Set.of(identifierField(), "agentId");
            case "DINGTALK" -> Set.of(identifierField(), "corpId");
            default -> Set.of(identifierField());
        };
    }
    /** 返回是否支持浏览器发起，公众号仅在微信端内使用。 */
    public boolean browser() { return "BROWSER".equals(scene) || this == WECHAT_OFFICIAL_ACCOUNT; }
    /** 只允许与当前具体来源匹配的底层凭证。 */
    public boolean supports(ExternalCodeType code) {
        return switch (this) {
            case WECHAT_MOBILE_APP -> code == ExternalCodeType.WECHAT_OPEN_APP_CODE;
            case WECHAT_MINIAPP -> code == ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE
                || code == ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE;
            case WECOM_SDK -> code == ExternalCodeType.WECOM_APP_CODE;
            case WECOM_MINIAPP -> code == ExternalCodeType.WECOM_MINIAPP_CODE;
            case FEISHU_APPLINK -> code == ExternalCodeType.FEISHU_APP_CODE;
            case FEISHU_WEBVIEW -> code == ExternalCodeType.FEISHU_WEBVIEW_CODE;
            case DINGTALK_SDK -> code == ExternalCodeType.DINGTALK_APP_CODE;
            case DINGTALK_WEBVIEW -> code == ExternalCodeType.DINGTALK_WEBVIEW_CODE;
            case ALIYUN_MOBILE -> code == ExternalCodeType.ALIYUN_MOBILE_TOKEN;
            default -> false;
        };
    }
    /** 解析来源类型，未知/缺失值要求管理员重新配置。 */
    public static IdentitySourceType fromValue(String value) {
        for (IdentitySourceType type : values()) {
            if (type.value.equals(value)) { return type; }
        }
        throw new IllegalArgumentException("请选择具体身份源类型");
    }
}
