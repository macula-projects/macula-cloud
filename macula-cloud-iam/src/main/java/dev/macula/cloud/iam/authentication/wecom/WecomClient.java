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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
    private static final String AUTHORIZE = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String TOKEN = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String USER = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
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
            .queryParam("appid", config.require("corpId"))
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "snsapi_base")
            .queryParam("state", state)
            .queryParam("agentid", config.require("agentId"))
            .fragment("wechat_redirect").build().encode().toUriString();
    }

    public String fetchUserId(ProviderConfig config, String code) {
        try {
            String tokenBody = restClient.get().uri(UriComponentsBuilder.fromUriString(TOKEN)
                .queryParam("corpid", config.require("corpId"))
                .queryParam("corpsecret", config.appSecret()).build().encode().toUri()).retrieve().body(String.class);
            JsonNode tokenJson = json.readTree(tokenBody);
            if (tokenJson.path("errcode").asInt() != 0) throw failed();
            String accessToken = tokenJson.path("access_token").asText();
            if (accessToken.isBlank()) throw failed();
            String userBody = restClient.get().uri(UriComponentsBuilder.fromUriString(USER)
                .queryParam("access_token", accessToken).queryParam("code", code)
                .build().encode().toUri()).retrieve().body(String.class);
            JsonNode userJson = json.readTree(userBody);
            if (userJson.path("errcode").asInt() != 0) throw failed();
            String userId = userJson.path("UserId").asText();
            if (userId.isBlank()) throw failed();
            return userId;
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failed();
        }
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("企业微信身份校验失败");
    }
}
