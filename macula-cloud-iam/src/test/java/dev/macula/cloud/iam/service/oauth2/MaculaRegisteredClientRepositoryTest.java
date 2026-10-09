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

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 客户端注册模型默认值与 PKCE 兼容策略回归。
 * @author rain
 * @since 6.1.0
 */
class MaculaRegisteredClientRepositoryTest {
    @Test void unknownClientReturnsNull() {
        assertThat(new MaculaRegisteredClientRepository(mock(SysOAuth2ClientService.class)).findByClientId("absent")).isNull();
    }

    @Test void publicClientRequiresPkceAndDefaultsToFiveMinuteCode() {
        var client = entity("none");
        var service = mock(SysOAuth2ClientService.class);
        when(service.getClientByClientId("fixture")).thenReturn(client);
        var result = new MaculaRegisteredClientRepository(service).findByClientId("fixture");
        assertThat(result.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(result.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test void confidentialClientKeepsExplicitPkcePolicyAndPasswordExtension() {
        var client = entity("client_secret_basic");
        client.setRequireProofKey(false);
        var service = mock(SysOAuth2ClientService.class);
        when(service.getClientByClientId("fixture")).thenReturn(client);
        var repository = new MaculaRegisteredClientRepository(service);
        assertThat(repository.findByClientId("fixture").getClientSettings().isRequireProofKey()).isFalse();
        client.setRequireProofKey(true);
        assertThat(repository.findByClientId("fixture").getClientSettings().isRequireProofKey()).isTrue();
        assertThat(repository.findByClientId("fixture").getAuthorizationGrantTypes())
            .extracting(org.springframework.security.oauth2.core.AuthorizationGrantType::getValue).contains("password");
    }

    private SysOAuth2Client entity(String method) {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId("fixture"); client.setClientName("测试应用");
        client.setClientAuthenticationMethods(method);
        client.setAuthorizationGrantTypes("authorization_code,password,refresh_token");
        client.setRedirectUris("https://client.example/callback"); client.setScopes("openid,profile");
        return client;
    }
}
