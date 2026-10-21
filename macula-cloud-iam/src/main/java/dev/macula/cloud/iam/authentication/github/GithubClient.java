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
package dev.macula.cloud.iam.authentication.github;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(GithubClient.class);
    private static final String AUTHORIZE = "https://github.com/login/oauth/authorize";
    private static final String TOKEN = "https://github.com/login/oauth/access_token";
    private static final String USER = "https://api.github.com/user";
    private static final String CLIENT_ID_CONFIG = "clientId";
    private static final String TOKEN_STAGE = "token";
    private static final String USER_STAGE = "user";
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
            .queryParam(OAuth2ParameterNames.CLIENT_ID, config.require(CLIENT_ID_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.STATE, state).build().encode().toUriString();
    }

    public String fetchSubject(ProviderConfig config, String code, String redirectUri) {
        String stage = TOKEN_STAGE;
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add(OAuth2ParameterNames.CLIENT_ID, config.require(CLIENT_ID_CONFIG));
            form.add(OAuth2ParameterNames.CLIENT_SECRET, config.appSecret());
            form.add(OAuth2ParameterNames.CODE, code);
            form.add(OAuth2ParameterNames.REDIRECT_URI, redirectUri);
            String tokenBody = restClient.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(String.class);
            JsonNode tokenJson = json.readTree(tokenBody);
            String accessToken = tokenJson.path(OAuth2ParameterNames.ACCESS_TOKEN).asText();
            if (accessToken.isBlank()) {
                String failure = tokenJson.path(OAuth2ParameterNames.ERROR).asText().isBlank()
                    ? "missing_access_token" : "provider_rejected";
                LOGGER.warn("第三方接口响应无效 provider=GITHUB stage={} failure={}", stage, failure);
                throw failed();
            }
            stage = USER_STAGE;
            String userBody = restClient.get().uri(USER)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve().body(String.class);
            JsonNode userJson = json.readTree(userBody);
            if (!userJson.path("id").isIntegralNumber()) {
                LOGGER.warn("第三方接口响应无效 provider=GITHUB stage={} failure=missing_or_invalid_subject", stage);
                throw failed();
            }
            return userJson.path("id").asText();
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("第三方接口 HTTP 失败 provider=GITHUB stage={} status={}", stage,
                ex.getStatusCode().value());
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=GITHUB stage={} failure=transport_error exception={}",
                stage, ex.getClass().getSimpleName());
            throw failed();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=GITHUB stage={} failure=invalid_response exception={}",
                stage, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("GitHub 身份校验失败");
    }
}
