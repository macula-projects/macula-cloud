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
 * 真实第三方应用注册的凭据类型，与可多选的登录能力分离。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityCredentialType {
    WECHAT_WEBSITE("微信网站应用", IdentitySourceType.WECHAT_WEBSITE),
    WECHAT_OFFICIAL_ACCOUNT("微信公众号／服务号", IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT),
    WECHAT_MOBILE_APP("微信移动应用", IdentitySourceType.WECHAT_MOBILE_APP),
    WECHAT_MINIAPP("微信小程序", IdentitySourceType.WECHAT_MINIAPP),
    WECOM_APPLICATION("企业微信应用", IdentitySourceType.WECOM_WEB),
    FEISHU_APPLICATION("飞书应用", IdentitySourceType.FEISHU_WEB),
    DINGTALK_APPLICATION("钉钉应用", IdentitySourceType.DINGTALK_WEB),
    GITHUB_OAUTH("GitHub OAuth应用", IdentitySourceType.GITHUB_OAUTH),
    ALIYUN_MOBILE("阿里云号码认证", IdentitySourceType.ALIYUN_MOBILE),
    ALIPAY_APPLICATION("支付宝应用（凭据管理）", IdentitySourceType.ALIPAY_APPLICATION);
    private final String label;
    private final IdentitySourceType anchor;
    /** 固定注册类型的展示名称及旧字段兼容锚点。 */
    IdentityCredentialType(String label, IdentitySourceType anchor) { this.label = label;
     this.anchor = anchor;
     }
    /** 返回显式注册标识。 */
    public String value() { return name(); }
    /** 返回应用注册名称。 */
    public String label() { return label; }
    /** 只用于新建兼容字段，不代表已启用任何能力。 */
    public IdentitySourceType anchor() { return anchor; }
    /** 同企业不同AgentID、同微信账号不同AppID始终为不同注册。 */
    public static IdentityCredentialType forSource(IdentitySourceType source) {
        return switch (source.providerType()) {
            case "WECOM" -> WECOM_APPLICATION;
            case "FEISHU" -> FEISHU_APPLICATION;
            case "DINGTALK" -> DINGTALK_APPLICATION;
            default -> fromValue(source.value());
        };
    }
    /** 未知注册不能凭名称推断为其他类型。 */
    public static IdentityCredentialType fromValue(String value) {
        for (var type : values()) { if (type.value().equals(value)) { return type; } }
        throw new IllegalArgumentException("请选择真实应用类型");
    }
}
