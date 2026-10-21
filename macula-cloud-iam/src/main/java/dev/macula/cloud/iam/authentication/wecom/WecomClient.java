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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
    private static final String CORP_ID_CONFIG = "corpId";
    private static final String AGENT_ID_CONFIG = "agentId";
    private static final String APP_ID_PARAMETER = "appid";
    private static final String AGENT_ID_PARAMETER = "agentid";
    private static final String CORP_ID_PARAMETER = "corpid";
    private static final String CORP_SECRET_PARAMETER = "corpsecret";
    private static final String TOKEN_STAGE = "token";
    private static final String USER_STAGE = "user";
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public WecomClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    WecomClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
            .queryParam(APP_ID_PARAMETER, config.require(CORP_ID_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, OAuth2AuthorizationResponseType.CODE.getValue())
            .queryParam(OAuth2ParameterNames.SCOPE, "snsapi_base")
            .queryParam(OAuth2ParameterNames.STATE, state)
            .queryParam(AGENT_ID_PARAMETER, config.require(AGENT_ID_CONFIG))
            .fragment("wechat_redirect").build().encode().toUriString();
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
            .queryParam("login_type", "CorpApp")
            .queryParam(APP_ID_PARAMETER, config.require(CORP_ID_CONFIG))
            .queryParam(AGENT_ID_PARAMETER, config.require(AGENT_ID_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.STATE, state)
            .build().encode().toUriString();
    }

    public String fetchUserId(ProviderConfig config, String code) {
        String stage = TOKEN_STAGE;
        try {
            String tokenBody = restClient.get().uri(UriComponentsBuilder.fromUriString(TOKEN)
                .queryParam(CORP_ID_PARAMETER, config.require(CORP_ID_CONFIG))
                .queryParam(CORP_SECRET_PARAMETER, config.appSecret())
                .build().encode().toUri()).retrieve().body(String.class);
            JsonNode tokenJson = json.readTree(tokenBody);
            if (tokenJson.path("errcode").asInt() != 0) {
                logProviderError(stage, tokenJson);
                throw failed();
            }
            String accessToken = tokenJson.path(OAuth2ParameterNames.ACCESS_TOKEN).asText();
            if (accessToken.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=WECOM stage={} failure=missing_access_token", stage);
                throw failed();
            }
            stage = USER_STAGE;
            String userBody = restClient.get().uri(UriComponentsBuilder.fromUriString(USER)
                .queryParam(OAuth2ParameterNames.ACCESS_TOKEN, accessToken)
                .queryParam(OAuth2ParameterNames.CODE, code)
                .build().encode().toUri()).retrieve().body(String.class);
            JsonNode userJson = json.readTree(userBody);
            if (userJson.path("errcode").asInt() != 0) {
                logProviderError(stage, userJson);
                throw failed();
            }
            String userId = userJson.path("userid").asText();
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
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=WECOM stage={} failure=transport_error exception={}",
                stage, ex.getClass().getSimpleName());
            throw failed();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=WECOM stage={} failure=invalid_response exception={}",
                stage, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    private void logProviderError(String stage, JsonNode response) {
        LOGGER.warn("第三方接口返回错误 provider=WECOM stage={} failure=provider_error errcode={}",
            stage, response.path("errcode").asInt());
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("企业微信身份校验失败");
    }
}
