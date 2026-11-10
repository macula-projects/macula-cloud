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

package dev.macula.cloud.iam.service.oauth2;

import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户端注册模型默认值与 PKCE 兼容策略回归。
 * @author rain
 * @since 6.1.0
 */
class MaculaRegisteredClientRepositoryTest {
    @Test
    void unknownClientReturnsNull() {
        assertThat(repository(mock(SysOAuth2ClientService.class)).findByClientId("absent")).isNull();
    }

    @Test
    void publicClientRequiresPkceAndDefaultsToFiveMinuteCode() {
        var client = entity("none");
        var service = mock(SysOAuth2ClientService.class);
        when(service.getClientByClientId("fixture")).thenReturn(client);
        var result = repository(service).findByClientId("fixture");
        assertThat(result.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(result.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofMinutes(5));
    }

    /** 注册策略保留服务端应用类型，Token请求不能自报另一终端来获得Code能力。 */
    @Test
    void carriesTrustedApplicationTypeIntoExternalCodeSettings() {
        var row = entity("none");
        row.setApplicationType("NATIVE");
        var service = mock(SysOAuth2ClientService.class);
        when(service.getClientByClientId("fixture")).thenReturn(row);
        var result = repository(service).findByClientId("fixture");
        assertThat(result.getClientSettings().<String>getSetting(
            dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings.APPLICATION_TYPE)).isEqualTo("NATIVE");
    }

    @Test
    void confidentialCodeClientRequiresPkceAndRejectsPassword() {
        var client = entity("client_secret_basic");
        ((dev.macula.cloud.system.protocol.OAuth2ProtocolSettings) client.getProtocolConfigurations().get(0).settings()).setRequireProofKey(false);
        var service = mock(SysOAuth2ClientService.class);
        when(service.getClientByClientId("fixture")).thenReturn(client);
        var repository = repository(service);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.findByClientId("fixture"))
            .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
        client.setRequireProofKey(true);
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        assertThat(repository.findByClientId("fixture").getClientSettings().isRequireProofKey()).isTrue();
        ((dev.macula.cloud.system.protocol.OAuth2ProtocolSettings) client.getProtocolConfigurations().get(0).settings()).setAuthorizationGrantTypes("password");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.findByClientId("fixture"))
            .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }

    private SysOAuth2Client entity(String method) {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId("fixture");
        client.setClientName("测试应用");
        client.setClientAuthenticationMethods(method);
        client.setProtocolMode("OIDC");
        client.setEnabledProtocols("OIDC");
        client.setDefaultProtocol("OIDC");
        client.setConfigurationStatus("ENABLED");
        client.setRequireProofKey(true);
        client.setAuthorizationGrantTypes("none".equals(method) ? "authorization_code" : "authorization_code,refresh_token");
        client.setRedirectUris("https://client.example/callback");
        client.setScopes("openid,profile");
        return dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
    }

    private MaculaRegisteredClientRepository repository(SysOAuth2ClientService service) {
        SysOAuth2ClientExternalProviderMapper mapper = mock(SysOAuth2ClientExternalProviderMapper.class);
        when(mapper.selectList(any())).thenReturn(java.util.List.of());
        return new MaculaRegisteredClientRepository(service, mapper);
    }
}
