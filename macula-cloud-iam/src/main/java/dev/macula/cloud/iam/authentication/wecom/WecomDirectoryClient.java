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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.directory.ProviderDirectoryAdapter;
import dev.macula.cloud.iam.authentication.thirdparty.directory.ProviderDirectoryResult;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * WECOM固定企业成员查询，复用具体来源的应用Token，不查询全企业或写本地用户。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class WecomDirectoryClient implements ProviderDirectoryAdapter {
    private static final String ENDPOINT = "https://qyapi.weixin.qq.com/cgi-bin/user/get";
    private final RestClient rest;
    private final WecomClient tokens;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 固定超时并禁止重定向，不能将应用Token转发至未知地址。
     */
    @Autowired
    public WecomDirectoryClient(WecomClient tokens) {
        this(RestClient.builder().requestFactory(WecomClient.noRedirectRequestFactory()).build(), tokens);
    }

    /**
     * HTTP合同测试使用同一映射。
     */
    WecomDirectoryClient(RestClient rest, WecomClient tokens) {
        this.rest = rest;
        this.tokens = tokens;
    }

    /**
     * 返回稳定厂商协议值。
     */
    @Override
    public String providerType() {
        return "WECOM";
    }

    /**
     * 身份与应用必须属于固定来源，普通caller corpId不能切换目标企业。
     */
    @Override
    public ProviderDirectoryResult lookup(ProviderConfig source, VerifiedExternalIdentity identity) {
        if (!providerType().equals(source.providerType()) || !identity.registrationId().equals(source.registrationId())
                || !VerifiedExternalIdentity.MEMBER_ID.equals(identity.identityKind())
                || source.organizationId() == null || !source.organizationId().equals(source.require("corpId"))) {
            return ProviderDirectoryResult.unavailable(false);
        }
        try {
            String token = tokens.fetchAccessToken(source);
            String body = rest.get().uri(UriComponentsBuilder.fromUriString(ENDPOINT)
                            .queryParam("access_token", token).queryParam("userid", identity.identityValue()).build().encode()
                            .toUri())
                    .retrieve().body(String.class);
            JsonNode response = json.readTree(body);
            // 未核实的业务拒绝不作为OPTIONAL可忽略故障，避免非成员被误放行。
            if (response.path("errcode").asInt(-1) != 0) {
                return ProviderDirectoryResult.unavailable(false);
            }
            JsonNode member = response;
            String memberId = member.path("userid").asText();
            if (memberId.isBlank()) {
                return ProviderDirectoryResult.unavailable(true);
            }
            if (!identity.identityValue().equals(memberId)) {
                return result(ProviderDirectoryResult.Status.NOT_MEMBER, source, Map.of(), false);
            }
            int status = member.path("status").asInt(1);
            if (status == 2) {
                return result(ProviderDirectoryResult.Status.DISABLED, source, Map.of(), false);
            }
            if (status != 1) {
                return ProviderDirectoryResult.unavailable(false);
            }
            Map<String, String> profile = new HashMap<>();
            // 本轮仅使用成员ID；新应用手机号/邮箱需本人敏感资料授权，不猜测job_number。
            profile.put("member_id", memberId);
            return result(ProviderDirectoryResult.Status.MEMBER, source, profile, false);
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value() == 403
                    ? result(ProviderDirectoryResult.Status.PERMISSION_DENIED, source, Map.of(), true)
                    : ProviderDirectoryResult.unavailable(ex.getStatusCode().is5xxServerError());
        } catch (dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException ex) {
            return ProviderDirectoryResult.unavailable(true);
        } catch (RestClientException ex) {
            return ProviderDirectoryResult.unavailable(true);
        } catch (RuntimeException ex) {
            return ProviderDirectoryResult.unavailable(false);
        }
    }

    /**
     * 组织来自已核对的应用凭据，结果不公开资料到错误日志。
     */
    private ProviderDirectoryResult result(ProviderDirectoryResult.Status status, ProviderConfig source,
            Map<String, String> profile, boolean tolerable) {
        return new ProviderDirectoryResult(status, source.organizationId(), profile, Instant.now(), tolerable);
    }
}
