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
import dev.macula.cloud.iam.authentication.thirdparty.ProviderDirectoryAdapter;
import dev.macula.cloud.iam.authentication.thirdparty.ProviderDirectoryResult;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.macula.cloud.iam.authentication.thirdparty.ProviderApplicationTokenCache;
import java.time.Duration;

/**
 * 飞书通讯录只使用目标来源的tenant_access_token及明确open_id，用户Token不混用。
 * @author Rain
 * @since 6.1.0
 */
@Component
public class FeishuDirectoryClient implements ProviderDirectoryAdapter {
    private static final String TENANT_TOKEN = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String USER = "https://open.feishu.cn/open-apis/contact/v3/users";
    private static final String TOKEN_PURPOSE = "feishu-tenant-token-v3-internal";
    private final ProviderApplicationTokenCache tokens;
    private final RestClient rest;
    private final JsonMapper json = JsonMapper.builder().build();
    /** 使用官方SDK仍提供的固定自建应用tenant token合同，不失败后切换版本。 */
    @Autowired
    public FeishuDirectoryClient(ProviderApplicationTokenCache tokens) {
        this(RestClient.builder().requestFactory(FeishuClient.noRedirectRequestFactory()).build(), tokens);
    }
    /** HTTP请求测试复用相同合同。 */
    FeishuDirectoryClient(RestClient rest, ProviderApplicationTokenCache tokens) {
        this.rest = rest;
        this.tokens = tokens;
    }
    /** 返回稳定厂商标识。 */
    @Override
    public String providerType() { return "FEISHU"; }
    /** tenant_key来自平台已核验用户信息，不能由请求自报目标企业。 */
    @Override
    public ProviderDirectoryResult lookup(ProviderConfig source, VerifiedExternalIdentity identity) {
        if (!providerType().equals(source.providerType()) || !identity.registrationId().equals(source.registrationId())
            || !VerifiedExternalIdentity.OPEN_ID.equals(identity.identityKind()) || source.organizationId() == null
            || !source.organizationId().equals(identity.attributes().get("tenant_key"))) {
            return ProviderDirectoryResult.unavailable(false);
        }
        try {
            String token = tokens.get(source, TOKEN_PURPOSE, () -> loadTenantToken(source));
            String body = rest.get().uri(UriComponentsBuilder.fromUriString(USER).pathSegment(identity.identityValue())
                .queryParam("user_id_type", "open_id").build().encode().toUri())
                .headers(headers -> headers.setBearerAuth(token)).retrieve().body(String.class);
            JsonNode response = json.readTree(body);
            int code = response.path("code").asInt(-1);
            // 41050经官方通用错误码和contact-v3文档确认是权限范围，不是非成员。
            if (code == 41050) { return result(ProviderDirectoryResult.Status.PERMISSION_DENIED, source, Map.of(), true); }
            if (code != 0) { return ProviderDirectoryResult.unavailable(false); }
            JsonNode member = response.path("data").path("user");
            String openId = member.path("open_id").asText();
            if (openId.isBlank()) { return ProviderDirectoryResult.unavailable(true); }
            if (!identity.identityValue().equals(openId)) {
                return result(ProviderDirectoryResult.Status.NOT_MEMBER, source, Map.of(), false);
            }
            JsonNode status = member.path("status");
            if (status.path("is_frozen").asBoolean() || status.path("is_resigned").asBoolean()
                || status.has("is_activated") && !status.path("is_activated").asBoolean()) {
                return result(ProviderDirectoryResult.Status.DISABLED, source, Map.of(), false);
            }
            Map<String, String> profile = new HashMap<>();
            var normalizer = new dev.macula.cloud.system.security.UserIdentifierNormalizer();
            String directoryMember = member.path("user_id").asText();
            if (!directoryMember.isBlank() && directoryMember.length() <= 255) { profile.put("member_id", directoryMember); }
            String employee = member.path("employee_no").asText();
            if (!employee.isBlank() && employee.length() <= 255) { profile.put("employee_no", employee); }
            normalizer.mobile(member.path("mobile").asText()).ifPresent(value -> profile.put("mobile", value));
            normalizer.email(member.path("email").asText()).ifPresent(value -> profile.put("email", value));
            return result(ProviderDirectoryResult.Status.MEMBER, source, profile, false);
        } catch (RestClientResponseException ex) {
            try {
                if (json.readTree(ex.getResponseBodyAsString()).path("code").asInt(-1) == 41050) {
                    return result(ProviderDirectoryResult.Status.PERMISSION_DENIED, source, Map.of(), true);
                }
            } catch (RuntimeException ignored) { /* 无法确认错误合同则保留失败关闭。 */ }
            return ex.getStatusCode().value() == 403
                ? result(ProviderDirectoryResult.Status.PERMISSION_DENIED, source, Map.of(), true)
                : ProviderDirectoryResult.unavailable(ex.getStatusCode().is5xxServerError());
        } catch (dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException ex) {
            return ProviderDirectoryResult.unavailable(true);
        } catch (RestClientException ex) { return ProviderDirectoryResult.unavailable(true); }
        catch (RuntimeException ex) { return ProviderDirectoryResult.unavailable(false); }
    }
    /** 仅保存应用Token；官方SDK字段expire决定寿命，不能将用户Token写入缓存。 */
    private ProviderApplicationTokenCache.TokenValue loadTenantToken(ProviderConfig source) {
        String body = rest.post().uri(TENANT_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("app_id", source.require("appId"), "app_secret", source.appSecret()))
            .retrieve().body(String.class);
        JsonNode response = json.readTree(body);
        if (response.path("code").asInt(-1) != 0 || response.path("tenant_access_token").asText().isBlank()) {
            throw new IllegalStateException("飞书应用Token不可用");
        }
        return new ProviderApplicationTokenCache.TokenValue(response.path("tenant_access_token").asText(),
            Duration.ofSeconds(response.path("expire").asLong()));
    }
    /** 只返回最小目录投影，不保留平台用户Token。 */
    private ProviderDirectoryResult result(ProviderDirectoryResult.Status status, ProviderConfig source,
        Map<String, String> profile, boolean tolerable) {
        return new ProviderDirectoryResult(status, source.organizationId(), profile, Instant.now(), tolerable);
    }
}
