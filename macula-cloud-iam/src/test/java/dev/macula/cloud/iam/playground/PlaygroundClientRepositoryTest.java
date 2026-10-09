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

package dev.macula.cloud.iam.playground;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证演示注册、冲突、关闭和业务委托，不连接数据库或缓存。
 * @author Rain
 * @since 6.1.0
 */
class PlaygroundClientRepositoryTest {
    final PlaygroundProperties properties = new PlaygroundProperties();
    final MockEnvironment environment = new MockEnvironment();
    final RegisteredClientRepository business = mock(RegisteredClientRepository.class);

    @BeforeEach void configure() {
        properties.setEnabled(true);
        properties.setAllowedProfiles(Set.of("local"));
        environment.setActiveProfiles("local");
    }

    PlaygroundRegisteredClientRepository repository() {
        return new PlaygroundRegisteredClientRepository(business, new PlaygroundAccessPolicy(properties, environment),
            properties, "https://iam.example", PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    @Test void publicUsesPkceOpaqueAndNoRefreshWhileDeviceHasNoOpenid() {
        var repository = repository();
        var client = repository.findByClientId(PlaygroundRegisteredClientRepository.PUBLIC);
        assertThat(client.getId()).isEqualTo(client.getClientId());
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
        assertThat(client.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
        assertThat(client.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(client.getRedirectUris()).containsExactly("https://iam.example/playground/callback");
        var device = repository.findById(PlaygroundRegisteredClientRepository.DEVICE);
        assertThat(device.getScopes()).containsExactly("playground.read");
        assertThat(device.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.DEVICE_CODE);
        assertThat(repository.findByClientId(PlaygroundRegisteredClientRepository.CONFIDENTIAL)).isNull();
    }

    @Test void confidentialSecretIsEncodedAndCompatibleGrantsArePreserved() {
        properties.setClientSecret("fixture-only-secret");
        var client = repository().findByClientId(PlaygroundRegisteredClientRepository.CONFIDENTIAL);
        assertThat(client.getClientSecret()).isNotEqualTo(properties.getClientSecret());
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder()
            .matches(properties.getClientSecret(), client.getClientSecret())).isTrue();
        assertThat(client.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.REFRESH_TOKEN,
            AuthorizationGrantType.CLIENT_CREDENTIALS, new AuthorizationGrantType("password"), new AuthorizationGrantType("sms"));
    }

    @Test void disableHidesPreviouslyBuiltClientsAndDoesNotFallThroughToBusiness() {
        var repository = repository();
        assertThat(repository.findById(PlaygroundRegisteredClientRepository.PUBLIC)).isNotNull();
        clearInvocations(business);
        properties.setEnabled(false);
        assertThat(repository.findById(PlaygroundRegisteredClientRepository.PUBLIC)).isNull();
        assertThat(repository.findByClientId(PlaygroundRegisteredClientRepository.DEVICE)).isNull();
        verifyNoInteractions(business);
    }

    @Test void rejectsDatabaseCollisionAndReservedSave() {
        var collision = RegisteredClient.withId("fixture").clientId(PlaygroundRegisteredClientRepository.PUBLIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();
        when(business.findByClientId(PlaygroundRegisteredClientRepository.PUBLIC)).thenReturn(collision);
        var repository = repository();
        assertThatThrownBy(() -> repository.findByClientId(PlaygroundRegisteredClientRepository.PUBLIC))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.save(collision)).isInstanceOf(IllegalArgumentException.class);
        verify(business, never()).save(any());
    }

    @Test void normalClientsRemainDelegatedAndUnknownReservedClientsNeverFallThrough() {
        var normal = RegisteredClient.withId("normal").clientId("business")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();
        when(business.findById("normal")).thenReturn(normal);
        when(business.findByClientId("business")).thenReturn(normal);
        var repository = repository();
        assertThat(repository.findById("normal")).isSameAs(normal);
        assertThat(repository.findByClientId("business")).isSameAs(normal);
        repository.save(normal);
        verify(business).save(normal);
        clearInvocations(business);
        assertThat(repository.findById("iam-playground-unknown")).isNull();
        verifyNoInteractions(business);
    }
}
