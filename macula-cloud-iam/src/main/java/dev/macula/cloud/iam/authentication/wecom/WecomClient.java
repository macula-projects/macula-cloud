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
package dev.macula.cloud.iam.authentication.wecom;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 使用企业微信固定 HTTPS 端点获取成员 UserId。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class WecomClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WecomClient.class);
    private static final String AUTHORIZE = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String BROWSER_AUTHORIZE = "https://login.work.weixin.qq.com/wwlogin/sso/login";
    private static final String TOKEN = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String USER = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    private static final String APP_USER = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo";
    private static final String MINIAPP_SESSION =
        "https://qyapi.weixin.qq.com/cgi-bin/miniprogram/jscode2session";
    private static final String CORP_ID_CONFIG = "corpId";
    private static final String AGENT_ID_CONFIG = "agentId";
    private static final String APP_ID_PARAMETER = "appid";
    private static final String AGENT_ID_PARAMETER = "agentid";
    private static final String CORP_ID_PARAMETER = "corpid";
    private static final String CORP_SECRET_PARAMETER = "corpsecret";
    private static final String LOGIN_TYPE_PARAMETER = "login_type";
    private static final String CORP_APP_LOGIN_TYPE = "CorpApp";
    private static final String ERROR_CODE = "errcode";
    private static final String USER_ID = "userid";
    private static final String APP_USER_ID = "UserId";
    private static final String JS_CODE_PARAMETER = "js_code";
    private static final String BASE_SCOPE = "snsapi_base";
    private static final String WECHAT_REDIRECT_FRAGMENT = "wechat_redirect";
    private static final String TOKEN_STAGE = "token";
    private static final String USER_STAGE = "user";
    private static final String MINIAPP_SESSION_STAGE = "miniapp_session";
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public WecomClient() {
        this(RestClient.builder().requestFactory(noRedirectRequestFactory()).build());
    }

    WecomClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
            .queryParam(APP_ID_PARAMETER, config.require(CORP_ID_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, OAuth2AuthorizationResponseType.CODE.getValue())
            .queryParam(OAuth2ParameterNames.SCOPE, BASE_SCOPE)
            .queryParam(OAuth2ParameterNames.STATE, state)
            .queryParam(AGENT_ID_PARAMETER, config.require(AGENT_ID_CONFIG))
            .fragment(WECHAT_REDIRECT_FRAGMENT).build().encode().toUriString();
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri,
        WecomLoginMode mode) {
        if (mode == WecomLoginMode.WEBVIEW) {
            return authorizationUri(config, state, redirectUri);
        }
        if (mode != WecomLoginMode.BROWSER) {
            throw failed();
        }
        return UriComponentsBuilder.fromUriString(BROWSER_AUTHORIZE)
            .queryParam(LOGIN_TYPE_PARAMETER, CORP_APP_LOGIN_TYPE)
            .queryParam(APP_ID_PARAMETER, config.require(CORP_ID_CONFIG))
            .queryParam(AGENT_ID_PARAMETER, config.require(AGENT_ID_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.STATE, state)
            .build().encode().toUriString();
    }

    public String fetchUserId(ProviderConfig config, String code) {
        return fetchUserId(config, code, USER, USER_ID);
    }

    public String fetchAppUserId(ProviderConfig config, String code) {
        return fetchUserId(config, code, APP_USER, APP_USER_ID);
    }

    private String fetchUserId(ProviderConfig config, String code, String endpoint, String userIdField) {
        String stage = TOKEN_STAGE;
        try {
            String accessToken = fetchAccessToken(config);
            stage = USER_STAGE;
            String userBody = restClient.get().uri(UriComponentsBuilder.fromUriString(endpoint)
                .queryParam(OAuth2ParameterNames.ACCESS_TOKEN, accessToken)
                .queryParam(OAuth2ParameterNames.CODE, code)
                .build().encode().toUri()).retrieve().body(String.class);
            JsonNode userJson = json.readTree(userBody);
            if (userJson.path(ERROR_CODE).asInt() != 0) {
                logProviderError(stage, userJson);
                throw failed();
            }
            String userId = userJson.path(userIdField).asText();
            if (userId.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=WECOM stage={} failure=missing_member_userid", stage);
                throw failed();
            }
            return userId;
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("第三方接口 HTTP 失败 provider=WECOM stage={} status={}", stage,
                ex.getStatusCode().value());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=WECOM stage={} failure=transport_error exception={}",
                stage, ex.getClass().getSimpleName());
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=WECOM stage={} "
                + "failure=invalid_response exception={}", stage, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    public String fetchMiniAppUserId(ProviderConfig config, String code) {
        String stage = TOKEN_STAGE;
        try {
            String accessToken = fetchAccessToken(config);
            stage = MINIAPP_SESSION_STAGE;
            String sessionBody = restClient.get().uri(UriComponentsBuilder.fromUriString(MINIAPP_SESSION)
                .queryParam(OAuth2ParameterNames.ACCESS_TOKEN, accessToken)
                .queryParam(JS_CODE_PARAMETER, code)
                .queryParam(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                .build().encode().toUri()).retrieve().body(String.class);
            JsonNode sessionJson = json.readTree(sessionBody);
            if (sessionJson.path(ERROR_CODE).asInt() != 0) {
                logProviderError(stage, sessionJson);
                throw failed();
            }
            String userId = sessionJson.path(USER_ID).asText();
            if (userId.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=WECOM stage={} failure=missing_member_userid", stage);
                throw failed();
            }
            return userId;
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("第三方接口 HTTP 失败 provider=WECOM stage={} status={}", stage,
                ex.getStatusCode().value());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=WECOM stage={} failure=transport_error exception={}",
                stage, ex.getClass().getSimpleName());
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=WECOM stage={} "
                + "failure=invalid_response exception={}", stage, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    private String fetchAccessToken(ProviderConfig config) {
        String tokenBody = restClient.get().uri(UriComponentsBuilder.fromUriString(TOKEN)
            .queryParam(CORP_ID_PARAMETER, config.require(CORP_ID_CONFIG))
            .queryParam(CORP_SECRET_PARAMETER, config.appSecret())
            .build().encode().toUri()).retrieve().body(String.class);
        JsonNode tokenJson = json.readTree(tokenBody);
        if (tokenJson.path(ERROR_CODE).asInt() != 0) {
            logProviderError(TOKEN_STAGE, tokenJson);
            throw failed();
        }
        String accessToken = tokenJson.path(OAuth2ParameterNames.ACCESS_TOKEN).asText();
        if (accessToken.isBlank()) {
            LOGGER.warn("第三方接口响应无效 provider=WECOM stage={} failure=missing_access_token",
                TOKEN_STAGE);
            throw failed();
        }
        return accessToken;
    }

    private void logProviderError(String stage, JsonNode response) {
        LOGGER.warn("第三方接口返回错误 provider=WECOM stage={} failure=provider_error errcode={}",
            stage, response.path(ERROR_CODE).asInt());
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("企业微信身份校验失败");
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
