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

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 六种可管理身份提供商的稳定 code、分类和配置字段规则。
 *
 * @author Rain
 * @since 2026-10-13
 */
public enum IdentityProviderType {
    GITHUB("GITHUB", "GitHub", IdentityProviderCategory.SOCIAL, "clientId", "clientSecret",
        "https://github.com/settings/developers", "Settings → Developer settings → OAuth Apps → New OAuth App", List.of(
        field("clientId", "Client ID", true, false, false, "OAuth Apps → 应用详情 → Client ID。"),
        field("clientSecret", "Client Secret", true, true, false,
            "OAuth Apps → 应用详情 → Client secrets → Generate a new client secret。"))),
    ALIPAY("ALIPAY", "支付宝", IdentityProviderCategory.SOCIAL, "appId", "appPrivateKey",
        "https://open.alipay.com/module/webApp", "网页/移动应用 → 创建应用 → 开发配置", List.of(
        field("appId", "应用 ID", true, false, false, "开放平台 → 网页/移动应用 → 应用详情 → APPID。"),
        field("appPrivateKey", "应用私钥", true, true, true,
            "使用支付宝开放平台密钥工具在本地生成 RSA2 密钥对，填写应用私钥；只向支付宝上传应用公钥，不要上传私钥。"),
        field("alipayPublicKey", "支付宝公钥", true, false, true,
            "应用详情 → 开发配置/接口加签方式；上传应用公钥后复制支付宝公钥，不是应用公钥。"))),
    WECHAT("WECHAT", "微信", IdentityProviderCategory.SOCIAL, "appId", "appSecret",
        "https://open.weixin.qq.com/", "管理中心 → 网站应用 → 创建网站应用；审核通过后查看应用详情", List.of(
        field("appId", "App ID", true, false, false, "微信开放平台 → 管理中心 → 网站应用 → 应用详情 → AppID。"),
        field("appSecret", "App Secret", true, true, false,
            "同一网站应用详情中的 AppSecret；不要使用公众号或小程序的密钥。"))),
    WECOM("WECOM", "企业微信", IdentityProviderCategory.ENTERPRISE, "corpId", "appSecret",
        "https://work.weixin.qq.com/wework_admin/frame#apps", "应用管理 → 自建应用 → 创建应用", List.of(
        field("corpId", "企业 ID", true, false, false, "管理后台 → 我的企业 → 企业信息 → 企业 ID。"),
        field("agentId", "AgentId", true, false, false, "应用管理 → 自建应用 → 应用详情 → AgentId。"),
        field("appSecret", "Secret", true, true, false, "应用管理 → 自建应用 → 应用详情 → Secret。"))),
    FEISHU("FEISHU", "飞书", IdentityProviderCategory.ENTERPRISE, "appId", "appSecret",
        "https://open.feishu.cn/app?lang=zh-CN", "开发者后台 → 创建企业自建应用", List.of(
        field("appId", "App ID", true, false, false, "开发者后台 → 企业自建应用 → 凭证与基础信息 → App ID。"),
        field("appSecret", "App Secret", true, true, false,
            "同一应用的凭证与基础信息 → App Secret。"))),
    DINGTALK("DINGTALK", "钉钉", IdentityProviderCategory.ENTERPRISE, "appKey", "appSecret",
        "https://open.dingtalk.com/", "应用开发 → 钉钉应用 → 创建应用 → 凭证与基础信息", List.of(
        field("appKey", "AppKey", true, false, false,
            "应用详情 → 凭证与基础信息 → Client ID（原 AppKey）。"),
        field("appSecret", "AppSecret", true, true, false,
            "应用详情 → 凭证与基础信息 → Client Secret（原 AppSecret）。"),
        field("corpId", "CorpID", false, false, false, "开发者后台右上角查看 CorpId；此项可选。")));

    /** 元数据和服务端校验共用的字段定义。 */
    @Getter
    @AllArgsConstructor
    public static class Field {
        private final String key;
        private final String label;
        private final boolean required;
        private final boolean secret;
        private final boolean multiline;
        private final String help;
    }

    private final String code;
    private final String label;
    private final IdentityProviderCategory category;
    private final String identifierField;
    private final String credentialField;
    private final String createUrl;
    private final String createHint;
    private final List<Field> fields;

    IdentityProviderType(String code, String label, IdentityProviderCategory category, String identifierField,
        String credentialField, String createUrl, String createHint, List<Field> fields) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.identifierField = identifierField;
        this.credentialField = credentialField;
        this.createUrl = createUrl;
        this.createHint = createHint;
        this.fields = fields;
    }

    private static Field field(String key, String label, boolean required, boolean secret, boolean multiline,
        String help) {
        return new Field(key, label, required, secret, multiline, help);
    }

    public String code() { return code; }
    public String label() { return label; }
    public IdentityProviderCategory category() { return category; }
    public String identifierField() { return identifierField; }
    public String credentialField() { return credentialField; }
    public String createUrl() { return createUrl; }
    public String createHint() { return createHint; }
    public List<Field> fields() { return fields; }

    /** 将来 IAM 各提供商 LoginFilter 共用的只读回调路径约定。 */
    public String callbackUrl() { return "/login/" + code.toLowerCase(Locale.ROOT); }

    public Set<String> requiredFields() {
        return fields.stream().filter(field -> field.isRequired() && !field.isSecret())
            .map(Field::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> optionalFields() {
        return fields.stream().filter(field -> !field.isRequired() && !field.isSecret())
            .map(Field::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> allowedFields() {
        return fields.stream().map(Field::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public static IdentityProviderType fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.code.equals(code))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的身份提供商类型"));
    }

    public static Set<String> typesIn(IdentityProviderCategory category) {
        return Arrays.stream(values()).filter(type -> type.category == category)
            .map(IdentityProviderType::code).collect(Collectors.toUnmodifiableSet());
    }
}
