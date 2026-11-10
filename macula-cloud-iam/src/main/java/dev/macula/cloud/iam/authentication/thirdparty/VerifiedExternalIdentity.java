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

import dev.macula.cloud.system.enums.IdentitySourceType;
import org.springframework.util.Assert;
import java.io.Serializable;
import java.util.Map;

/**
 * 仅由服务端平台适配器构造的核验结果；身份键与匹配属性分离。
 * @author Rain
 * @since 6.1.0
 */
public record VerifiedExternalIdentity(Long registrationId, String providerType, String sourceType,
                                      Long sourceVersion, String flow,
                                      String capability, String identityKind, String identityValue,
                                      Map<String, String> attributes) implements Serializable {
    public static final String LOGIN_CAPABILITY_ATTRIBUTE = "_iam_login_capability";
    public static final String BROWSER = "BROWSER";
    public static final String MEMBER_ID = "MEMBER_ID";
    public static final String OPEN_ID = "OPEN_ID";
    public static final String UNION_ID = "UNION_ID";
    public static final String GITHUB_ID = "GITHUB_ID";
    public static final String MOBILE_PROOF = "MOBILE_PROOF";
    /** 要求确定来源、已实现能力以及有界非空身份键。 */
    public VerifiedExternalIdentity {
        Assert.notNull(registrationId, "registration is required");
        Assert.isTrue(registrationId > 0, "registration is invalid");
        IdentitySourceType type = IdentitySourceType.fromValue(sourceType);
        Assert.isTrue(type.providerType().equals(providerType), "source provider mismatch");
        Assert.isTrue(sourceVersion != null && sourceVersion >= 0, "source version is required");
        Assert.hasText(flow, "source flow is required");
        Assert.isTrue(dev.macula.cloud.system.enums.IdentitySourceFlow.declared(type, flow).supports(type), "source flow mismatch");
        Assert.hasText(capability, "capability is required");
        if (BROWSER.equals(capability)) {
            Assert.isTrue(type.browser() && !MOBILE_PROOF.equals(identityKind), "browser source mismatch");
        } else {
            dev.macula.cloud.system.enums.ExternalCodeType code = dev.macula.cloud.system.enums.ExternalCodeType.fromValue(capability);
            Assert.isTrue(type.supports(code), "credential source mismatch");
            Assert.isTrue((code.resultType() == dev.macula.cloud.system.enums.ExternalCodeType.ResultType.MOBILE)
                == MOBILE_PROOF.equals(identityKind), "credential identity kind mismatch");
        }
        boolean correctKind = switch (type.providerType()) {
            case "GITHUB" -> GITHUB_ID.equals(identityKind);
            case "WECOM", "DINGTALK" -> MEMBER_ID.equals(identityKind);
            case "FEISHU" -> OPEN_ID.equals(identityKind);
            case "WECHAT" -> OPEN_ID.equals(identityKind) || UNION_ID.equals(identityKind) || MOBILE_PROOF.equals(identityKind);
            case "ALIYUN_MOBILE" -> MOBILE_PROOF.equals(identityKind);
            default -> false;
        };
        Assert.isTrue(correctKind, "provider identity kind mismatch");
        Assert.isTrue(java.util.Set.of(MEMBER_ID, OPEN_ID, UNION_ID, GITHUB_ID, MOBILE_PROOF).contains(identityKind),
            "identity kind is invalid");
        Assert.hasText(identityValue, "verified identity is required");
        Assert.isTrue(identityValue.length() <= 255, "verified identity is too long");
        attributes = Map.copyOf(attributes);
    }
    /** 构造来自确定配置的服务端结果。 */
    public static VerifiedExternalIdentity of(IdentityProviderConfigService.ProviderConfig source, String capability,
        String kind, String value, Map<String, String> attributes) {
        var verifiedAttributes = new java.util.HashMap<>(attributes);
        verifiedAttributes.put(LOGIN_CAPABILITY_ATTRIBUTE, source.loginCapability());
        return new VerifiedExternalIdentity(source.registrationId(), source.providerType(), source.sourceType().value(),
            source.version(), source.flow().value(), capability, kind, value, verifiedAttributes);
    }
    /** 能力来自适配器核验上下文；旧测试/旧身份仅等价投影原合同。 */
    public String loginCapability() {
        String selected = attributes.get(LOGIN_CAPABILITY_ATTRIBUTE);
        if (selected != null) { return dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(selected).value(); }
        if (!BROWSER.equals(capability)) {
            return dev.macula.cloud.system.enums.IdentityLoginCapability.forCode(
                dev.macula.cloud.system.enums.ExternalCodeType.fromValue(capability)).value();
        }
        var type = IdentitySourceType.fromValue(sourceType);
        return dev.macula.cloud.system.enums.IdentityLoginCapability.browser(type,
            type == IdentitySourceType.GITHUB_OAUTH ? "" : type == IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT ? "WEBVIEW" : "BROWSER").value();
    }
    /** 防止默认record日志泄漏外部身份及联系方式。 */
    @Override
    public String toString() {
        return "VerifiedExternalIdentity[registrationId=" + registrationId + ",identityKind=" + identityKind + "]";
    }
}
