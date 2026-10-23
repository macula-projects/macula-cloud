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
package dev.macula.cloud.iam.authentication.feishu;

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
 * 使用飞书固定 HTTPS 端点交换授权码并读取员工编号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class FeishuClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeishuClient.class);
    private static final String AUTHORIZE = "https://accounts.feishu.cn/open-apis/authen/v1/authorize";
    private static final String OAUTH_TOKEN = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";
    private static final String APP_TOKEN = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    private static final String WEBVIEW_TOKEN = "https://open.feishu.cn/open-apis/authen/v1/access_token";
    private static final String USER = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    private static final String APP_ID_CONFIG = "appId";
    private static final String APP_ID_PARAMETER = "app_id";
    private static final String APP_SECRET_PARAMETER = "app_secret";
    private static final String APP_ACCESS_TOKEN = "app_access_token";
    private static final String EMPLOYEE_NO = "employee_no";
    private static final String DATA = "data";
    private static final String ERROR_CODE = "code";
    private static final String TOKEN_STAGE = "token";
    private static final String USER_STAGE = "user";
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public FeishuClient() {
        this(RestClient.builder().requestFactory(noRedirectRequestFactory()).build());
    }

    FeishuClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String authorizationUri(ProviderConfig config, String state, String redirectUri, FeishuLoginMode mode) {
        if (mode != FeishuLoginMode.BROWSER) {
            throw failed();
        }
        return UriComponentsBuilder.fromUriString(AUTHORIZE)
            .queryParam(APP_ID_PARAMETER, config.require(APP_ID_CONFIG))
            .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
            .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, OAuth2AuthorizationResponseType.CODE.getValue())
            .queryParam(OAuth2ParameterNames.STATE, state).build().encode().toUriString();
    }

    public String fetchEmployeeNo(ProviderConfig config, String code, String redirectUri, FeishuLoginMode mode) {
        String stage = TOKEN_STAGE;
        try {
            String accessToken;
            if (mode == FeishuLoginMode.APP) {
                accessToken = oauthAccessToken(config, code, null);
            } else if (mode == FeishuLoginMode.BROWSER) {
                accessToken = oauthAccessToken(config, code, redirectUri);
            } else if (mode == FeishuLoginMode.WEBVIEW) {
                accessToken = webviewAccessToken(config, code);
            } else {
                throw failed();
            }
            stage = USER_STAGE;
            String userBody = restClient.get().uri(USER)
                .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(String.class);
            JsonNode user = successfulData(stage, userBody);
            String employeeNo = user.path(EMPLOYEE_NO).asText();
            if (employeeNo.isBlank()) {
                LOGGER.warn("第三方接口响应无效 provider=FEISHU stage={} failure=missing_employee_no", stage);
                throw failed();
            }
            return employeeNo;
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("第三方接口 HTTP 失败 provider=FEISHU stage={} status={}", stage,
                ex.getStatusCode().value());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new ExternalCodeInfrastructureException();
            }
            throw failed();
        } catch (RestClientException ex) {
            LOGGER.warn("第三方接口调用失败 provider=FEISHU stage={} failure=transport_error exception={}",
                stage, ex.getClass().getSimpleName());
            throw new ExternalCodeInfrastructureException();
        } catch (RuntimeException ex) {
            LOGGER.error("第三方接口响应处理失败 provider=FEISHU stage={} "
                    + "failure=invalid_response exception={}",
                stage, ex.getClass().getSimpleName());
            throw failed();
        }
    }

    private String oauthAccessToken(ProviderConfig config, String code, String redirectUri) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.put(OAuth2ParameterNames.CLIENT_ID, config.require(APP_ID_CONFIG));
        request.put(OAuth2ParameterNames.CLIENT_SECRET, config.appSecret());
        request.put(OAuth2ParameterNames.CODE, code);
        if (redirectUri != null) {
            request.put(OAuth2ParameterNames.REDIRECT_URI, redirectUri);
        }
        String body = restClient.post().uri(OAUTH_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .body(request).retrieve().body(String.class);
        JsonNode response = json.readTree(body);
        requireProviderSuccess(TOKEN_STAGE, response);
        return requireToken(TOKEN_STAGE, response, OAuth2ParameterNames.ACCESS_TOKEN);
    }

    private String webviewAccessToken(ProviderConfig config, String code) {
        Map<String, String> appRequest = Map.of(APP_ID_PARAMETER, config.require(APP_ID_CONFIG),
            APP_SECRET_PARAMETER, config.appSecret());
        String appBody = restClient.post().uri(APP_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .body(appRequest).retrieve().body(String.class);
        JsonNode appResponse = json.readTree(appBody);
        requireProviderSuccess(TOKEN_STAGE, appResponse);
        String appToken = requireToken(TOKEN_STAGE, appResponse, APP_ACCESS_TOKEN);
        Map<String, String> tokenRequest = Map.of(
            OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
            OAuth2ParameterNames.CODE, code);
        String tokenBody = restClient.post().uri(WEBVIEW_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(appToken)).body(tokenRequest).retrieve().body(String.class);
        JsonNode response = json.readTree(tokenBody);
        requireProviderSuccess(TOKEN_STAGE, response);
        return requireToken(TOKEN_STAGE, response.path(DATA), OAuth2ParameterNames.ACCESS_TOKEN);
    }

    private JsonNode successfulData(String stage, String body) {
        JsonNode response = json.readTree(body);
        requireProviderSuccess(stage, response);
        return response.path(DATA);
    }

    private void requireProviderSuccess(String stage, JsonNode response) {
        if (response.has(ERROR_CODE) && response.path(ERROR_CODE).asInt() != 0) {
            logProviderError(stage, response);
            throw failed();
        }
    }

    private String requireToken(String stage, JsonNode response, String field) {
        String token = response.path(field).asText();
        if (token.isBlank()) {
            LOGGER.warn("第三方接口响应无效 provider=FEISHU stage={} failure=missing_access_token", stage);
            throw failed();
        }
        return token;
    }

    private void logProviderError(String stage, JsonNode response) {
        LOGGER.warn("第三方接口返回错误 provider=FEISHU stage={} failure=provider_error errcode={}",
            stage, response.path(ERROR_CODE).asInt());
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("飞书身份校验失败");
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
