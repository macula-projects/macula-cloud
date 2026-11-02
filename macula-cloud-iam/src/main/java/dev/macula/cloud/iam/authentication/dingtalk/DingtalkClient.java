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
package dev.macula.cloud.iam.authentication.dingtalk;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用钉钉固定 HTTPS 端点将 APP、网页或端内授权码解析为组织成员 userId。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class DingtalkClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DingtalkClient.class);
    private static final String AUTHORIZE = "https://login.dingtalk.com/oauth2/auth";
    private static final String USER_TOKEN = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private static final String CURRENT_USER = "https://api.dingtalk.com/v1.0/contact/users/me";
    private static final String APP_TOKEN = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
    private static final String USER_BY_UNION_ID = "https://oapi.dingtalk.com/topapi/user/getbyunionid";
    private static final String WEBVIEW_USER = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo";
    private static final String APP_KEY_CONFIG = "appKey";
    private static final String CORP_ID_CONFIG = "corpId";
    private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String UNION_ID = "unionId";
    private static final String LEGACY_UNION_ID = "unionid";
    private static final String USER_ID = "userid";
    private static final String ERROR_CODE = "errcode";
    private static final String RESULT = "result";
    private static final String ACCESS_TOKEN_HEADER = "x-acs-dingtalk-access-token";
    private static final String PROMPT_PARAMETER = "prompt";
    private static final String CONSENT = "consent";
    private static final String OPENID_SCOPE = "openid";
    private static final String TOKEN_STAGE = "token";
    private static final String USER_STAGE = "user";
    private static final String MEMBER_STAGE = "member";
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public DingtalkClient() {
        this(RestClient.builder().requestFactory(noRedirectRequestFactory()).build());
    }

    DingtalkClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri, DingtalkLoginMode mode) {
        if (mode != DingtalkLoginMode.BROWSER) {
            throw failed();
        }
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
            .queryParam(OAuth2ParameterNames.CLIENT_ID, config.require(APP_KEY_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, OAuth2AuthorizationResponseType.CODE.getValue())
            .queryParam(OAuth2ParameterNames.SCOPE, OPENID_SCOPE)
            .queryParam(OAuth2ParameterNames.STATE, state)
            .queryParam(PROMPT_PARAMETER, CONSENT).build().encode().toUriString();
    }

    public String fetchUserId(ProviderConfig config, String code, DingtalkLoginMode mode) {
        String stage = TOKEN_STAGE;
        try {
            config.require(CORP_ID_CONFIG);
            if (mode != DingtalkLoginMode.APP && mode != DingtalkLoginMode.BROWSER
                && mode != DingtalkLoginMode.WEBVIEW) {
                throw failed();
            }
            String appToken = appAccessToken(config);
            if (mode == DingtalkLoginMode.WEBVIEW) {
                stage = MEMBER_STAGE;
                String uri = UriComponentsBuilder.fromUriString(WEBVIEW_USER)
                    .queryParam(OAuth2ParameterNames.ACCESS_TOKEN, appToken).build().encode().toUriString();
                String body = restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(OAuth2ParameterNames.CODE, code))
                    .retrieve().body(String.class);
                JsonNode response = json.readTree(body);
                requireSuccess(stage, response);
                return requireUserId(stage, response.path(RESULT));
            }
            String userToken = userAccessToken(config, code);
            stage = USER_STAGE;
            String currentUserBody = restClient.get().uri(CURRENT_USER)
                .header(ACCESS_TOKEN_HEADER, userToken).retrieve().body(String.class);
            String unionId = json.readTree(currentUserBody).path(UNION_ID).asText();
            if (unionId.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=DINGTALK stage={} failure=missing_union_id", stage);
                throw failed();
            }
            stage = MEMBER_STAGE;
            String memberUri = UriComponentsBuilder.fromUriString(USER_BY_UNION_ID)
                .queryParam(OAuth2ParameterNames.ACCESS_TOKEN, appToken).build().encode().toUriString();
            String memberBody = restClient.post().uri(memberUri).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(LEGACY_UNION_ID, unionId))
                .retrieve().body(String.class);
            JsonNode memberResponse = json.readTree(memberBody);
            requireSuccess(stage, memberResponse);
            return requireUserId(stage, memberResponse.path(RESULT));
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("第三方接口 HTTP 失败 provider=DINGTALK stage={} status={}", stage,
                ex.getStatusCode().value());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=DINGTALK stage={} failure=transport_error exception={}",
                stage, ex.getClass().getSimpleName());
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=DINGTALK stage={} "
                    + "failure=invalid_response exception={}",
                stage, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    private String userAccessToken(ProviderConfig config, String code) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put(CLIENT_ID, config.require(APP_KEY_CONFIG));
        request.put(CLIENT_SECRET, config.appSecret());
        request.put(OAuth2ParameterNames.CODE, code);
        request.put(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        String body = restClient.post().uri(USER_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .body(request).retrieve().body(String.class);
        return requireToken(TOKEN_STAGE, json.readTree(body));
    }

    private String appAccessToken(ProviderConfig config) {
        String body = restClient.post().uri(APP_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(CLIENT_ID, config.require(APP_KEY_CONFIG), CLIENT_SECRET, config.appSecret()))
            .retrieve().body(String.class);
        return requireToken(TOKEN_STAGE, json.readTree(body));
    }

    private String requireToken(String stage, JsonNode response) {
        String token = response.path(ACCESS_TOKEN).asText();
        if (token.isBlank()) {
            LOGGER.warn("第三方接口响应无效 provider=DINGTALK stage={} failure=missing_access_token", stage);
            throw failed();
        }
        return token;
    }

    private String requireUserId(String stage, JsonNode response) {
        String userId = response.path(USER_ID).asText();
        if (userId.isBlank()) {
            LOGGER.warn("第三方接口响应无效 provider=DINGTALK stage={} "
                + "failure=not_organization_member", stage);
            throw failed();
        }
        return userId;
    }

    private void requireSuccess(String stage, JsonNode response) {
        int errorCode = response.path(ERROR_CODE).asInt(Integer.MIN_VALUE);
        if (errorCode != 0) {
            LOGGER.warn("第三方接口业务失败 provider=DINGTALK stage={} errcode={}", stage, errorCode);
            throw failed();
        }
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("钉钉身份校验失败");
    }

    static SimpleClientHttpRequestFactory noRedirectRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return factory;
    }
}
