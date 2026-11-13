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
package dev.macula.cloud.iam.protocol.oauth2;

import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 请求协议视图独立于基础身份，且不能跨请求复用可变策略。
 *
 * @author Rain
 * @since 6.1.0
 */
class ProtocolAwareRegisteredClientRepositoryTest {
    /**
     * 每例清除请求线程上下文。
     */
    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 同一Client独立认证、回调、Origin、TTL及格式，基础对象保持不变。
     */
    @Test
    @SuppressWarnings("unchecked")
    void exactProfilesHaveIndependentNativeSettings() {
        var row = new OAuth2ClientRegistration();
        row.setClientId("client");
        ProtocolTestFixtures.configure(row, "OIDC,OAUTH2_1");

        row.setClientSecret("{noop}shared");
        ProtocolTestFixtures.configure(row);
        var oidc = (OAuth2ProtocolSettings) row.getProtocolConfigurations().get(0).settings();
        var oauth = new OAuth2ProtocolSettings();
        BeanUtils.copyProperties(oidc, oauth);
        oauth.setScopes("profile,api.read");
        oidc.setAccessTokenTimeToLive(600L);
        oidc.setClientAuthenticationMethods("client_secret_basic");
        oauth.setAccessTokenTimeToLive(120L);
        oauth.setTokenFormat("self-contained");
        oauth.setClientAuthenticationMethods("client_secret_post");
        oauth.setRedirectUris("https://oauth.example/callback");
        oauth.setAllowedOrigins(Set.of("https://oauth.example"));
        var identity = RegisteredClient.withId("client").clientId("client").clientSecret("{noop}shared")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback").scope("openid").scope("api.read").build();
        var base = mock(RegisteredClientRepository.class);
        when(base.findByClientId("client")).thenReturn(identity);
        var clients = mock(OAuth2ClientRegistrationService.class);
        when(clients.getClientByClientId("client")).thenReturn(row);
        var authorizations = mock(OAuth2AuthorizationService.class);
        var provider = (ObjectProvider<OAuth2AuthorizationService>) mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(authorizations);
        var repository = new ProtocolAwareRegisteredClientRepository(base, clients, provider);
        request("openid profile");
        var first = repository.findByClientId("client");
        assertThat(first.getClientSettings().<String>getSetting(ExternalCodeClientSettings.WEB_LOGIN_MODE)).isEqualTo("BROWSER");
        row.setWebLoginMode("IN_APP");
        row.setHostedLoginMethods("");
        row.setBrowserCapabilityBindings("{\"WECOM_WEBVIEW\":17}");
        row.setProtocolConfigurations(List.of(
                new ApplicationProtocolConfiguration("OAUTH2_1", 1, true, oauth)));
        row.setAuthorizationVersion(2L);
        request("api.read");
        var second = repository.findByClientId("client");
        assertThat(second.getClientSettings().<String>getSetting(ExternalCodeClientSettings.WEB_LOGIN_MODE)).isEqualTo("IN_APP");
        assertThat(identity.getClientSettings().getSettings()).doesNotContainKey(ExternalCodeClientSettings.WEB_LOGIN_MODE);
        assertThat(first.getClientAuthenticationMethods()).containsExactly(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(second.getClientAuthenticationMethods()).containsExactly(
                ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(first.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofSeconds(600));
        assertThat(second.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofSeconds(120));
        assertThat(second.getTokenSettings().getAccessTokenFormat().getValue()).isEqualTo("self-contained");
        assertThat(second.getRedirectUris()).containsExactly("https://oauth.example/callback");
        assertThat(first.getRedirectUris()).containsExactly("https://client.example/callback");
        assertThat(identity.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(5));
        verifyNoInteractions(authorizations);
    }

    /** 已移除类型在基础缓存拒绝时也返回授权端点专用错误，不出现框架强转异常。 */
    @Test
    @SuppressWarnings("unchecked")
    void adaptsRemovedArchitectureFailureAtAuthorizationEndpoint() {
        var base = mock(RegisteredClientRepository.class);
        var failure = new OAuth2AuthenticationException("unauthorized_client");
        when(base.findByClientId("removed")).thenThrow(failure);
        var clients = mock(OAuth2ClientRegistrationService.class);
        var provider = (ObjectProvider<OAuth2AuthorizationService>) mock(ObjectProvider.class);
        var repository = new ProtocolAwareRegisteredClientRepository(base, clients, provider);
        var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Assertions.assertThatThrownBy(() -> repository.findByClientId("removed"))
            .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        request.setRequestURI("/oauth2/token");
        Assertions.assertThatThrownBy(() -> repository.findByClientId("removed")).isSameAs(failure);
        verifyNoInteractions(clients);
    }

    /**
     * 内省请求必须覆盖陈旧基础缓存中的凭据、认证方式和授权版本。
     */
    @Test
    @SuppressWarnings("unchecked")
    void introspectionUsesFreshCredentialsEvenWhenBaseCacheIsStale() {
        var cached = RegisteredClient.withId("resource").clientId("resource").clientSecret("{noop}old")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();
        var base = mock(RegisteredClientRepository.class);
        when(base.findByClientId("resource")).thenReturn(cached);
        var row = new OAuth2ClientRegistration();
        row.setClientId("resource");
        row.setPurpose("RESOURCE_SERVER");
        row.setAuthorizationVersion(2L);
        row.setClientSecret("{noop}new");
        row.setClientAuthenticationMethods("client_secret_post");
        var clients = mock(OAuth2ClientRegistrationService.class);
        when(clients.getClientByClientId("resource")).thenReturn(row);
        var provider = (ObjectProvider<OAuth2AuthorizationService>) mock(ObjectProvider.class);
        var request = new MockHttpServletRequest("POST", "/oauth2/introspect");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var actual = new ProtocolAwareRegisteredClientRepository(base, clients, provider).findByClientId("resource");
        assertThat(actual.getClientSecret()).isEqualTo("{noop}new");
        assertThat(actual.getClientAuthenticationMethods()).containsExactly(
                ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(actual.getAuthorizationGrantTypes()).doesNotContain(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat((Number) actual.getClientSettings()
                .getSetting(OAuth2ResourcePolicy.AUTHORIZATION_VERSION)).isEqualTo(2L);
        verifyNoInteractions(provider);
    }

    /**
     * 模拟当前实际HTTP请求，不将默认协议当用途。
     */
    private void request(String scopes) {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.addParameter("scope", scopes);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
