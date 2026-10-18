/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
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
package dev.macula.cloud.iam.authentication.github;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * 使用 GitHub 固定 HTTPS 端点交换授权码并读取稳定用户 ID。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class GithubClient {
    private static final String AUTHORIZE = "https://github.com/login/oauth/authorize";
    private static final String TOKEN = "https://github.com/login/oauth/access_token";
    private static final String USER = "https://api.github.com/user";
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public GithubClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    GithubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
            .queryParam("client_id", config.require("clientId"))
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state).build().encode().toUriString();
    }

    public String fetchSubject(ProviderConfig config, String code, String redirectUri) {
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", config.require("clientId"));
            form.add("client_secret", config.appSecret());
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            String tokenBody = restClient.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(String.class);
            JsonNode tokenJson = json.readTree(tokenBody);
            String accessToken = tokenJson.path("access_token").asText();
            if (accessToken.isBlank()) throw failed();
            String userBody = restClient.get().uri(USER)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve().body(String.class);
            JsonNode userJson = json.readTree(userBody);
            if (!userJson.path("id").isIntegralNumber()) throw failed();
            return userJson.path("id").asText();
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failed();
        }
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("GitHub 身份校验失败");
    }
}
