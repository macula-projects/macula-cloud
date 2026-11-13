/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.protocol.oauth2.persistence;

import dev.macula.cloud.iam.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户端注册模型默认值与 PKCE 兼容策略回归。
 *
 * @author rain
 * @since 6.1.0
 */
class MaculaRegisteredClientRepositoryTest {
    @Test
    void unknownClientReturnsNull() {
        assertThat(repository(mock(OAuth2ClientRegistrationService.class)).findByClientId("absent")).isNull();
    }

    @Test
    void publicClientRequiresPkceAndDefaultsToFiveMinuteCode() {
        var client = entity("none");
        var service = mock(OAuth2ClientRegistrationService.class);
        when(service.getClientByClientId("fixture")).thenReturn(client);
        var result = repository(service).findByClientId("fixture");
        assertThat(result.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(result.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * 注册策略保留服务端应用类型，Token请求不能自报另一终端来获得Code能力。
     */
    @Test
    void carriesTrustedApplicationTypeIntoExternalCodeSettings() {
        var row = entity("none");
        row.setApplicationType("NATIVE");
        var service = mock(OAuth2ClientRegistrationService.class);
        when(service.getClientByClientId("fixture")).thenReturn(row);
        var result = repository(service).findByClientId("fixture");
        assertThat(result.getClientSettings().<String>getSetting(
                ExternalCodeClientSettings.APPLICATION_TYPE)).isEqualTo(
                "NATIVE");
    }

    /** 基础缓存投影历史缺省与显式端内模式；未知旧架构不能生成可执行Client。 */
    @Test
    void carriesEffectiveWebModeAndRejectsRemovedArchitecture() {
        var row = entity("none");
        row.setApplicationType("SPA");
        var service = mock(OAuth2ClientRegistrationService.class);
        when(service.getClientByClientId("fixture")).thenReturn(row);
        var repository = repository(service);
        assertThat(repository.findByClientId("fixture").getClientSettings().<String>getSetting(
            ExternalCodeClientSettings.WEB_LOGIN_MODE)).isEqualTo("BROWSER");
        row.setWebLoginMode("IN_APP");
        row.setHostedLoginMethods("");
        row.setBrowserCapabilityBindings("{\"WECOM_WEBVIEW\":17}");
        assertThat(repository.findByClientId("fixture").getClientSettings().<String>getSetting(
            ExternalCodeClientSettings.WEB_LOGIN_MODE)).isEqualTo("IN_APP");
        row.setApplicationType("IN_APP");
        Assertions.assertThatThrownBy(() -> repository.findByClientId("fixture")).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void confidentialCodeClientRequiresPkceAndRejectsPassword() {
        var client = entity("client_secret_basic");
        ((OAuth2ProtocolSettings) client.getProtocolConfigurations().get(0)
                .settings()).setRequireProofKey(false);
        var service = mock(OAuth2ClientRegistrationService.class);
        when(service.getClientByClientId("fixture")).thenReturn(client);
        var repository = repository(service);
        Assertions.assertThatThrownBy(() -> repository.findByClientId("fixture"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        client.setRequireProofKey(true);
        ProtocolTestFixtures.configure(client);
        assertThat(repository.findByClientId("fixture").getClientSettings().isRequireProofKey()).isTrue();
        ((OAuth2ProtocolSettings) client.getProtocolConfigurations().get(0)
                .settings()).setAuthorizationGrantTypes("password");
        Assertions.assertThatThrownBy(() -> repository.findByClientId("fixture"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    /** 大小写兼容查询后仍使用登记原值，资源管理身份不获得业务grant。 */
    @Test
    void resourceIdentityKeepsCanonicalIdentifierAndManagementOnlyGrant() {
        var row = new OAuth2ClientRegistration();
        row.setId(999L);
        row.setPurpose("RESOURCE_SERVER");
        row.setClientId("canonical-resource");
        row.setClientSecret("{synthetic}preserved-hash");
        row.setClientAuthenticationMethods("client_secret_basic");
        row.setClientIdIssuedAt(Instant.parse("2024-01-01T00:00:00Z"));
        var clients = mock(OAuth2ClientRegistrationService.class);
        when(clients.getClientByClientId("CANONICAL-RESOURCE")).thenReturn(row);
        var registered = repository(clients).findByClientId("CANONICAL-RESOURCE");
        assertThat(registered.getId()).isEqualTo("canonical-resource");
        assertThat(registered.getClientId()).isEqualTo("canonical-resource");
        assertThat(registered.getClientIdIssuedAt()).isEqualTo(row.getClientIdIssuedAt());
        assertThat(registered.getClientSecret()).isEqualTo("{synthetic}preserved-hash");
        assertThat(registered.getAuthorizationGrantTypes()).extracting(
            AuthorizationGrantType::getValue)
            .containsExactly(OAuth2ResourcePolicy.INTROSPECTION_CAPABILITY);
        assertThat(registered.getScopes()).isEmpty();
        assertThat(registered.getRedirectUris()).isEmpty();
    }

    private OAuth2ClientRegistration entity(String method) {
        OAuth2ClientRegistration client = new OAuth2ClientRegistration();
        client.setClientId("fixture");
        client.setClientName("测试应用");
        client.setClientAuthenticationMethods(method);

        ProtocolTestFixtures.configure(client, "OIDC");

        client.setConfigurationStatus("ENABLED");
        client.setRequireProofKey(true);
        client.setAuthorizationGrantTypes(
                "none".equals(method) ? "authorization_code" : "authorization_code,refresh_token");
        client.setRedirectUris("https://client.example/callback");
        client.setScopes("openid,profile");
        return ProtocolTestFixtures.configure(client);
    }

    private MaculaRegisteredClientRepository repository(OAuth2ClientRegistrationService service) {
        SysIamApplicationIdentityProviderMapper mapper = mock(SysIamApplicationIdentityProviderMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        return new MaculaRegisteredClientRepository(service);
    }
}
