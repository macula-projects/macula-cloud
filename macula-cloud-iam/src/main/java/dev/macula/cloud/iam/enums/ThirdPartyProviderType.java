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
package dev.macula.cloud.iam.enums;

import java.util.Set;

/**
 * IAM 已接入第三方登录的稳定身份源类型与回调路径。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum ThirdPartyProviderType {
    GITHUB("GITHUB", "/login/github", Set.of("clientId"), Set.of()),
    WECOM("WECOM", "/login/wecom", Set.of("corpId", "agentId"), Set.of()),
    FEISHU("FEISHU", "/login/feishu", Set.of("appId"), Set.of()),
    DINGTALK("DINGTALK", "/login/dingtalk", Set.of("appKey", "corpId"), Set.of()),
    WECHAT("WECHAT", "/login/wechat", Set.of("appId", "officialAccountAppId"),
        Set.of("officialAccountAppSecret", "mobileAppSecret", "miniAppSecret")),
    ALIYUN_MOBILE("ALIYUN_MOBILE", "", Set.of("accessKeyId"), Set.of());

    private final String providerType;
    private final String loginPath;
    private final Set<String> requiredPublicFields;
    private final Set<String> additionalSecretFields;

    ThirdPartyProviderType(String providerType, String loginPath, Set<String> requiredPublicFields,
        Set<String> additionalSecretFields) {
        this.providerType = providerType;
        this.loginPath = loginPath;
        this.requiredPublicFields = requiredPublicFields;
        this.additionalSecretFields = additionalSecretFields;
    }

    public String providerType() {
        return providerType;
    }

    public String loginPath() {
        return loginPath;
    }

    public Set<String> additionalSecretFields() {
        return additionalSecretFields;
    }

    public Set<String> requiredAdditionalSecretFields() {
        if (this == WECHAT) {
            return Set.of("officialAccountAppSecret");
        }
        return additionalSecretFields;
    }

    public Set<String> requiredPublicFields() {
        return requiredPublicFields;
    }

    public static ThirdPartyProviderType fromProviderType(String providerType) {
        for (ThirdPartyProviderType type : values()) {
            if (type.providerType.equals(providerType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的第三方登录类型");
    }
}
