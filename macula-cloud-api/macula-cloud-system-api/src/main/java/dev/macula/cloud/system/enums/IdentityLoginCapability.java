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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 具体注册可提供的登录能力及持久化合同，不包含或推断任何密钥。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityLoginCapability {
    WECHAT_WEBSITE("PC网站扫码", IdentitySourceType.WECHAT_WEBSITE, "BROWSER", null),
    WECHAT_OFFICIAL_ACCOUNT("微信端内网页授权", IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT, "WEBVIEW", null),
    WECHAT_MOBILE_APP("移动OpenSDK", IdentitySourceType.WECHAT_MOBILE_APP, "", ExternalCodeType.WECHAT_OPEN_APP_CODE),
    WECHAT_MINIAPP_LOGIN("小程序登录Code", IdentitySourceType.WECHAT_MINIAPP, "", ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE),
    WECHAT_MINIAPP_PHONE("小程序手机号授权Code", IdentitySourceType.WECHAT_MINIAPP, "", ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE),
    WECOM_BROWSER("PC网页扫码", IdentitySourceType.WECOM_WEB, "BROWSER", null),
    WECOM_WEBVIEW("企微端内网页授权", IdentitySourceType.WECOM_WEB, "WEBVIEW", null),
    WECOM_SDK("移动SDK", IdentitySourceType.WECOM_SDK, "", ExternalCodeType.WECOM_APP_CODE),
    WECOM_MINIAPP("关联小程序登录", IdentitySourceType.WECOM_MINIAPP, "", ExternalCodeType.WECOM_MINIAPP_CODE),
    FEISHU_WEB("网页OAuth授权", IdentitySourceType.FEISHU_WEB, "BROWSER", null),
    FEISHU_APPLINK("移动AppLink授权", IdentitySourceType.FEISHU_APPLINK, "", ExternalCodeType.FEISHU_APP_CODE),
    FEISHU_WEBVIEW("飞书端内JSAPI Code", IdentitySourceType.FEISHU_WEBVIEW, "", ExternalCodeType.FEISHU_WEBVIEW_CODE),
    DINGTALK_WEB("PC网页扫码／授权", IdentitySourceType.DINGTALK_WEB, "BROWSER", null),
    DINGTALK_SDK("移动SDK授权登录", IdentitySourceType.DINGTALK_SDK, "", ExternalCodeType.DINGTALK_APP_CODE),
    DINGTALK_WEBVIEW("钉钉端内免登Code", IdentitySourceType.DINGTALK_WEBVIEW, "", ExternalCodeType.DINGTALK_WEBVIEW_CODE),
    GITHUB_OAUTH("网页OAuth授权", IdentitySourceType.GITHUB_OAUTH, "", null),
    ALIYUN_MOBILE("手机号认证", IdentitySourceType.ALIYUN_MOBILE, "", ExternalCodeType.ALIYUN_MOBILE_TOKEN),
    ALIPAY_CONFIGURATION("凭据管理", IdentitySourceType.ALIPAY_APPLICATION, "", null);
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final String label;
    private final IdentitySourceType source;
    private final String mode;
    private final ExternalCodeType code;
    /** 能力与Code、网页模式使用固定合同，不在失败后自动切换。 */
    IdentityLoginCapability(String label, IdentitySourceType source, String mode, ExternalCodeType code) {
        this.label = label;
         this.source = source;
         this.mode = mode;
         this.code = code;
    }
    /** 返回稳定机器代码。 */
    public String value() { return name(); }
    /** 返回业务能力名称。 */
    public String label() { return label; }
    /** 返回能力使用的厂商处理合同。 */
    public IdentitySourceType source() { return source; }
    /** 网页模式只在发起时选择，回调继续使用state。 */
    public String mode() { return mode; }
    /** 返回直接换Token的凭证类型。 */
    public ExternalCodeType code() { return code; }
    /** 返回真实应用注册种类。 */
    public IdentityCredentialType credentialType() { return IdentityCredentialType.forSource(source); }
    /** 只有真实网页授权能力才能出现在统一登录页。 */
    public boolean browser() { return source.browser(); }
    /** 普通网页能力可以出现在标准Web/SPA的IAM页，与平台专属端内入口分开。 */
    public boolean webLogin() {
        return switch (this) {
            case WECHAT_WEBSITE, WECOM_BROWSER, FEISHU_WEB, DINGTALK_WEB, GITHUB_OAUTH -> true;
            default -> false;
        };
    }
    /** 端内应用仅使用已实现的容器专属授权或JSAPI能力。 */
    public boolean inAppLogin() {
        return switch (this) {
            case WECHAT_OFFICIAL_ACCOUNT, WECOM_WEBVIEW, FEISHU_WEBVIEW, DINGTALK_WEBVIEW -> true;
            default -> false;
        };
    }
    /** 能力以已核实且已实现的兑换合同发布；仅配置型来源不参与登录。 */
    public boolean available() {
        return "CONFIGURATION".equals(source.scene()) || java.util.Arrays.stream(IdentitySourceFlow.values())
            .anyMatch(flow -> flow.supports(source) && flow.implemented());
    }
    /** 企业微信网页两种能力分别限制运行环境。 */
    public Set<IdentityBrowserEnvironment> environments() {
        return switch (this) {
            case WECOM_BROWSER -> Set.of(IdentityBrowserEnvironment.DESKTOP_BROWSER);
            case WECOM_WEBVIEW -> Set.of(IdentityBrowserEnvironment.WECOM_WEBVIEW);
            default -> source.browserEnvironments();
        };
    }
    /** 从受控来源与发起模式明确选择网页能力。 */
    public static IdentityLoginCapability browser(IdentitySourceType source, String mode) {
        for (var capability : values()) {
            if (capability.source == source && capability.browser() && capability.mode.equals(mode)) { return capability; }
        }
        throw new IllegalArgumentException("来源不支持此网页能力");
    }
    /** Code Type只匹配一个能力，不使用客户端自报平台选择凭据。 */
    public static IdentityLoginCapability forCode(ExternalCodeType code) {
        for (var capability : values()) { if (capability.code == code) { return capability; } }
        throw new IllegalArgumentException("凭证类型没有对应能力");
    }
    /** 拒绝未知能力代码，不能按显示名称或前缀猜测。 */
    public static IdentityLoginCapability fromValue(String value) {
        for (var capability : values()) { if (capability.value().equals(value)) { return capability; } }
        throw new IllegalArgumentException("登录能力无效");
    }
    /** 旧来源只投影原有能力，企微旧网页原本就具备PC与端内两条路径。 */
    public static Map<String, String> legacy(IdentitySourceType source, String flow) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var capability : values()) {
            if (capability.source == source) { result.put(capability.value(), flow == null ? "" : flow); }
        }
        return result;
    }
    /** 解析明确能力集合；空集合表示全部关闭，null才兼容旧字段。 */
    public static Map<String, String> decode(String value, IdentitySourceType source, String flow) {
        if (value == null) { return legacy(source, flow); }
        Map<String, String> result = JSON.readValue(value, new TypeReference<Map<String, String>>() { });
        if (result == null) { throw new IllegalArgumentException("能力配置必须为对象"); }
        result.forEach((key, contract) -> {
            var capability = fromValue(key);
            if (capability.credentialType() != IdentityCredentialType.forSource(source) || contract == null) {
                throw new IllegalArgumentException("能力与应用凭据不匹配");
            }
            if (!contract.isBlank() && !"CONFIGURATION".equals(capability.source.scene())) { IdentitySourceFlow.declared(capability.source, contract); }
        });
        return result;
    }
    /** 对外不公开内部版本；持久化仅保存能力代码和服务端选择的合同。 */
    public static String encode(Map<String, String> capabilities) { return JSON.writeValueAsString(capabilities); }
}
