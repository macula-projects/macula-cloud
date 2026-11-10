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
package dev.macula.cloud.system.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * IAM 接入应用创建和修改表单，密钥仅在创建及重置时使用。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
public class OAuth2ClientForm {
    private String purpose;
    private String protocol;
    private dev.macula.cloud.system.protocol.OAuth2ProtocolSettings settings;

    @JsonIgnore
    private java.util.List<dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration> protocolConfigurations = java.util.List.of();
    private String configurationVersion;
    @NotBlank @Size(max = 100)
    private String clientId;
    @NotBlank @Size(max = 200)
    private String clientName;
    @JsonIgnore
    private String protocolMode;
    @JsonIgnore
    private Set<String> enabledProtocols;
    @JsonIgnore
    private String defaultProtocol;
    private String configurationStatus;
    private Map<String, Long> browserProviderBindings = Map.of();
    private Map<String, Long> browserCapabilityBindings;
    @JsonIgnore
    private boolean browserCapabilityBindingsPresent;
    /** 内部投影允许重复赋值；JSON重复字段由独立入口拒绝。 */
    public void setBrowserCapabilityBindings(Map<String, Long> value) {
        browserCapabilityBindings = value;
        browserCapabilityBindingsPresent = true;
    }
    /** 区分省略与显式关闭，拒绝重复JSON键改变固定来源。 */
    @com.fasterxml.jackson.annotation.JsonSetter("browserCapabilityBindings")
    public void readBrowserCapabilityBindings(Map<String, Long> value) {
        if (browserCapabilityBindingsPresent) { throw new IllegalArgumentException("网页能力字段重复"); }
        setBrowserCapabilityBindings(value);
    }
    private String applicationType;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private boolean convertToInApp;
    @JsonIgnore
    private boolean conversionPresent;
    /** 内部投影只复制明确确认标记，不把省略当确认。 */
    public void setConvertToInApp(boolean value) { convertToInApp = value; }
    /** 管理请求必须提交单一布尔值，重复或类型转换不能改变转换意图。 */
    @com.fasterxml.jackson.annotation.JsonSetter("convertToInApp")
    public void readConvertToInApp(Object value) {
        if (conversionPresent || !(value instanceof Boolean confirmed)) { throw new IllegalArgumentException("转换确认无效"); }
        conversionPresent = true;
        convertToInApp = confirmed;
    }
    private String miniappPlatform;
    private Set<String> hostedLoginMethods;
    @JsonIgnore
    private boolean applicationTypePresent;
    @JsonIgnore
    private boolean miniappPlatformPresent;
    @JsonIgnore
    private boolean hostedLoginMethodsPresent;
    private String clientSecret;
    @JsonIgnore
    private String clientAuthenticationMethods;
    @JsonIgnore
    private String authorizationGrantTypes;
    @JsonIgnore
    private String redirectUris;
    @JsonIgnore
    private String scopes;
    private Set<String> externalCodeTypes = Set.of();
    @JsonIgnore
    private Set<String> allowedOrigins = Set.of();
    private Map<String, Long> externalProviderBindings = Map.of();
    private Set<String> browserLoginProviderTypes = Set.of();
    private String directLoginProviderType;
    @JsonIgnore
    private Boolean requireProofKey;
    @JsonIgnore
    private Boolean requireAuthorizationConsent;
    @JsonIgnore
    private String jwkSetUrl;
    @JsonIgnore
    private String signingAlgorithm;
    @JsonIgnore
    private Long authorizationCodeTimeToLive;
    @JsonIgnore
    private Long accessTokenTimeToLive;
    @JsonIgnore
    private String tokenFormat;
    @JsonIgnore
    private Boolean reuseRefreshTokens;
    @JsonIgnore
    private Long refreshTokenTimeToLive;
    @JsonIgnore
    private String idTokenSignatureAlgorithm;
    private String userType;
    private Long tenantId;
    /** 显式设置类型，省略字段时保留旧记录。 */
    public void setApplicationType(String value) {
        this.applicationType = value;
        this.applicationTypePresent = true;
    }

    /** 显式设置平台，区分缺失与清空。 */
    public void setMiniappPlatform(String value) {
        this.miniappPlatform = value;
        this.miniappPlatformPresent = true;
    }

    /** null恢复继承，空集合明确关闭全部本地方式。 */
    public void setHostedLoginMethods(Set<String> value) {
        this.hostedLoginMethods = value;
        this.hostedLoginMethodsPresent = true;
    }
}
