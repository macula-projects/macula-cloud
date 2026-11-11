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
package dev.macula.cloud.iam.authentication.wechat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Map;

import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
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

/**
 * 使用微信固定 HTTPS 端点交换授权码并提取开放平台 unionid。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class WechatClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WechatClient.class);
    private static final String BROWSER_AUTHORIZE = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String WEBVIEW_AUTHORIZE = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String TOKEN = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String MINIAPP_SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String STABLE_TOKEN = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String MINIAPP_PHONE = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    private static final String APP_ID_CONFIG = "appId";
    private static final String MOBILE_APP_ID_CONFIG = "mobileAppId";
    private static final String MOBILE_APP_SECRET_CONFIG = "mobileAppSecret";
    private static final String MINI_APP_ID_CONFIG = "miniAppId";
    private static final String MINI_APP_SECRET_CONFIG = "miniAppSecret";
    private static final String OFFICIAL_APP_ID_CONFIG = "officialAccountAppId";
    private static final String OFFICIAL_APP_SECRET_CONFIG = "officialAccountAppSecret";
    private static final String APP_ID_PARAMETER = "appid";
    private static final String SECRET_PARAMETER = "secret";
    private static final String JS_CODE_PARAMETER = "js_code";
    private static final String PHONE_INFO = "phone_info";
    private static final String PHONE_NUMBER = "phoneNumber";
    private static final String CLIENT_CREDENTIAL = "client_credential";
    private static final String UNION_ID = "unionid";
    private static final String ERROR_CODE = "errcode";
    private static final String BROWSER_SCOPE = "snsapi_login";
    private static final String WEBVIEW_SCOPE = "snsapi_base";
    private static final String WECHAT_REDIRECT_FRAGMENT = "wechat_redirect";
    private static final String TOKEN_STAGE = "token";
    private final RestClient restClient;
    private final boolean httpContractTest;
    private final JsonMapper json = JsonMapper.builder().build();
    private dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderApplicationTokenCache applicationTokens;

    public WechatClient() {
        this(RestClient.builder().requestFactory(noRedirectRequestFactory()).build(), false);
    }

    /**
     * 显式HTTP合同测试可跳过Redis，仅此包内构造方式允许。
     */
    WechatClient(RestClient restClient) {
        this(restClient, true);
    }

    /**
     * 默认生产构造不能在缓存缺失时直接返回未经过owner栅栏的结果。
     */
    private WechatClient(RestClient restClient, boolean httpContractTest) {
        this.restClient = restClient;
        this.httpContractTest = httpContractTest;
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

    /**
     * 为固定小程序应用Token装配安全缓存。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void applicationTokenCache(
            dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderApplicationTokenCache cache) {
        this.applicationTokens = cache;
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri, WechatLoginMode mode) {
        String endpoint;
        String appId;
        String scope;
        if (mode == WechatLoginMode.BROWSER) {
            endpoint = BROWSER_AUTHORIZE;
            appId = config.require(APP_ID_CONFIG);
            scope = BROWSER_SCOPE;
        } else if (mode == WechatLoginMode.WEBVIEW) {
            endpoint = WEBVIEW_AUTHORIZE;
            appId = config.require(APP_ID_CONFIG);
            scope = WEBVIEW_SCOPE;
        } else {
            throw failed();
        }
        return UriComponentsBuilder.fromUriString(endpoint)
                .queryParam(APP_ID_PARAMETER, appId)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, OAuth2AuthorizationResponseType.CODE.getValue())
                .queryParam(OAuth2ParameterNames.SCOPE, scope)
                .queryParam(OAuth2ParameterNames.STATE, state)
                .fragment(WECHAT_REDIRECT_FRAGMENT).build().encode().toUriString();
    }

    public String fetchOpenId(ProviderConfig config, String code, WechatLoginMode mode) {
        try {
            String appId;
            String secret;
            if (mode == WechatLoginMode.BROWSER) {
                appId = config.require(APP_ID_CONFIG);
                secret = config.appSecret();
            } else if (mode == WechatLoginMode.WEBVIEW) {
                appId = config.require(APP_ID_CONFIG);
                secret = config.appSecret();
            } else {
                throw failed();
            }
            String body = restClient.get().uri(UriComponentsBuilder.fromUriString(TOKEN)
                    .queryParam(APP_ID_PARAMETER, appId)
                    .queryParam(SECRET_PARAMETER, secret)
                    .queryParam(OAuth2ParameterNames.CODE, code)
                    .queryParam(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                    .build().encode().toUri()).retrieve().body(String.class);
            JsonNode response = json.readTree(body);
            if (response.has(ERROR_CODE)) {
                LOGGER.warn("第三方接口返回错误 provider=WECHAT stage={} failure=provider_error errcode={}",
                        TOKEN_STAGE, response.path(ERROR_CODE).asInt());
                throw failed();
            }
            String unionId = response.path("openid").asText();
            if (unionId.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=WECHAT stage={} failure=missing_openid",
                        TOKEN_STAGE);
                throw failed();
            }
            return unionId;
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("第三方接口 HTTP 失败 provider=WECHAT stage={} status={}", TOKEN_STAGE,
                    ex.getStatusCode().value());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=WECHAT stage={} failure=transport_error exception={}",
                    TOKEN_STAGE, ex.getClass().getSimpleName());
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=WECHAT stage={} "
                            + "failure=invalid_response exception={}",
                    TOKEN_STAGE, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    public String fetchOpenAppOpenId(ProviderConfig config, String code) {
        return exchangeOpenId(config.require(APP_ID_CONFIG), config.appSecret(),
                code);
    }

    public String fetchMiniappOpenId(ProviderConfig config, String code) {
        try {
            String body = restClient.get().uri(UriComponentsBuilder.fromUriString(MINIAPP_SESSION)
                    .queryParam(APP_ID_PARAMETER, config.require(APP_ID_CONFIG))
                    .queryParam(SECRET_PARAMETER, config.appSecret())
                    .queryParam(JS_CODE_PARAMETER, code)
                    .queryParam(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                    .build().encode().toUri()).retrieve().body(String.class);
            return requireOpenId(json.readTree(body));
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            logHttpFailure(ex);
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            logTransportFailure(ex);
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            logResponseFailure(ex);
            throw failed();
        }
    }

    public String fetchMiniappMobile(ProviderConfig config, String code) {
        try {
            if (applicationTokens == null && !httpContractTest) {
                throw new ExternalCodeInfrastructureException(
                        dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeConsumptionState.NOT_SUBMITTED);
            }
            String accessToken = applicationTokens == null ? loadMiniappApplicationToken(config).token()
                    : applicationTokens.get(config,
                    dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderApplicationTokenCache.APPLICATION_TOKEN,
                    () -> loadMiniappApplicationToken(config));
            String phoneBody = restClient.post().uri(UriComponentsBuilder.fromUriString(MINIAPP_PHONE)
                            .queryParam(OAuth2ParameterNames.ACCESS_TOKEN, accessToken).build().encode().toUri())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of(OAuth2ParameterNames.CODE, code))
                    .retrieve().body(String.class);
            JsonNode phoneResponse = json.readTree(phoneBody);
            requireSuccess(phoneResponse);
            String mobile = phoneResponse.path(PHONE_INFO).path(PHONE_NUMBER).asText();
            if (mobile.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=WECHAT stage={} failure=missing_mobile", TOKEN_STAGE);
                throw failed();
            }
            return mobile;
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            logHttpFailure(ex);
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            logTransportFailure(ex);
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            logResponseFailure(ex);
            throw failed();
        }
    }

    /**
     * 仅取得应用Token，未向手机号接口提交用户Code。
     */
    private dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderApplicationTokenCache.TokenValue loadMiniappApplicationToken(
            ProviderConfig config) {
        try {
            String appId = config.require(APP_ID_CONFIG);
            String secret = config.appSecret();
            String tokenBody = restClient.post().uri(STABLE_TOKEN).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(OAuth2ParameterNames.GRANT_TYPE, CLIENT_CREDENTIAL,
                            APP_ID_PARAMETER, appId, SECRET_PARAMETER, secret))
                    .retrieve().body(String.class);
            JsonNode tokenResponse = json.readTree(tokenBody);
            requireSuccess(tokenResponse);
            String accessToken = tokenResponse.path(OAuth2ParameterNames.ACCESS_TOKEN).asText();
            if (accessToken.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=WECHAT stage={} failure=missing_access_token",
                        TOKEN_STAGE);
                throw failed();
            }
            return new dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderApplicationTokenCache.TokenValue(
                    accessToken, Duration.ofSeconds(tokenResponse.path("expires_in").asLong()));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException(
                        dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeConsumptionState.NOT_SUBMITTED);
            }
            throw failed();
        } catch (RestClientException ex) {
            throw new ExternalCodeInfrastructureException(
                    dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeConsumptionState.NOT_SUBMITTED);
        }
    }

    private String exchangeOpenId(String appId, String secret, String code) {
        try {
            String body = restClient.get().uri(UriComponentsBuilder.fromUriString(TOKEN)
                    .queryParam(APP_ID_PARAMETER, appId)
                    .queryParam(SECRET_PARAMETER, secret)
                    .queryParam(OAuth2ParameterNames.CODE, code)
                    .queryParam(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                    .build().encode().toUri()).retrieve().body(String.class);
            return requireOpenId(json.readTree(body));
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            logHttpFailure(ex);
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            logTransportFailure(ex);
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            logResponseFailure(ex);
            throw failed();
        }
    }

    private String requireOpenId(JsonNode response) {
        requireSuccess(response);
        String unionId = response.path("openid").asText();
        if (unionId.isBlank()) {
            LOGGER.warn("第三方接口响应无效 provider=WECHAT stage={} failure=missing_openid", TOKEN_STAGE);
            throw failed();
        }
        return unionId;
    }

    private void requireSuccess(JsonNode response) {
        if (response.has(ERROR_CODE) && response.path(ERROR_CODE).asInt() != 0) {
            LOGGER.warn("第三方接口返回错误 provider=WECHAT stage={} failure=provider_error errcode={}",
                    TOKEN_STAGE, response.path(ERROR_CODE).asInt());
            throw failed();
        }
    }

    private void logHttpFailure(RestClientResponseException ex) {
        LOGGER.warn("第三方接口 HTTP 失败 provider=WECHAT stage={} status={}", TOKEN_STAGE,
                ex.getStatusCode().value());
    }

    private void logTransportFailure(RestClientException ex) {
        LOGGER.warn("第三方接口调用失败 provider=WECHAT stage={} failure=transport_error exception={}",
                TOKEN_STAGE, ex.getClass().getSimpleName());
    }

    private void logResponseFailure(RuntimeException ex) {
        LOGGER.error("第三方接口响应处理失败 provider=WECHAT stage={} failure=invalid_response exception={}",
                TOKEN_STAGE, ex.getClass().getSimpleName());
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("微信身份校验失败");
    }
}
