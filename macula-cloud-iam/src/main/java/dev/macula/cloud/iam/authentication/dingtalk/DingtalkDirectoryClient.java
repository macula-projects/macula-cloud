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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.directory.ProviderDirectoryAdapter;
import dev.macula.cloud.iam.authentication.thirdparty.directory.ProviderDirectoryResult;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DINGTALK固定企业成员查询，复用具体来源的应用Token，不查询全企业或写本地用户。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class DingtalkDirectoryClient implements ProviderDirectoryAdapter {
    private static final String ENDPOINT = DingtalkClient.MEMBER_DETAILS;
    private final RestClient rest;
    private final DingtalkClient tokens;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 固定超时并禁止重定向，不能将应用Token转发至未知地址。
     */
    @Autowired
    public DingtalkDirectoryClient(DingtalkClient tokens) {
        this(RestClient.builder().requestFactory(DingtalkClient.noRedirectRequestFactory()).build(), tokens);
    }

    /**
     * HTTP合同测试使用同一映射。
     */
    DingtalkDirectoryClient(RestClient rest, DingtalkClient tokens) {
        this.rest = rest;
        this.tokens = tokens;
    }

    /**
     * 返回稳定厂商协议值。
     */
    @Override
    public String providerType() {
        return "DINGTALK";
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
            String token = tokens.appAccessToken(source);
            String uri = UriComponentsBuilder.fromUriString(ENDPOINT).queryParam("access_token", token)
                    .build().encode().toUriString();
            String body = rest.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userid", identity.identityValue())).retrieve().body(String.class);
            JsonNode response = json.readTree(body);
            // 未核实的业务拒绝不作为OPTIONAL可忽略故障，避免非成员被误放行。
            if (response.path("errcode").asInt(-1) != 0) {
                return ProviderDirectoryResult.unavailable(false);
            }
            JsonNode member = response.path("result");
            String memberId = member.path("userid").asText();
            if (memberId.isBlank()) {
                return ProviderDirectoryResult.unavailable(true);
            }
            if (!identity.identityValue().equals(memberId)) {
                return result(ProviderDirectoryResult.Status.NOT_MEMBER, source, Map.of(), false);
            }
            if (member.has("active") && !member.path("active").asBoolean()) {
                return result(ProviderDirectoryResult.Status.DISABLED, source, Map.of(), false);
            }
            Map<String, String> profile = new HashMap<>();
            var normalizer = new UserIdentifierNormalizer();
            String directoryMember = member.path("userid").asText();
            if (!directoryMember.isBlank() && directoryMember.length() <= 255) {
                profile.put("member_id", directoryMember);
            }
            String employee = member.path("job_number").asText();
            if (!employee.isBlank() && employee.length() <= 255) {
                profile.put("employee_no", employee);
            }
            normalizer.mobile(member.path("mobile").asText()).ifPresent(value -> profile.put("mobile", value));
            normalizer.email(member.path("email").asText()).ifPresent(value -> profile.put("email", value));
            return result(ProviderDirectoryResult.Status.MEMBER, source, profile, false);
        } catch (RestClientResponseException ex) {
            return ex.getStatusCode().value() == 403
                    ? result(ProviderDirectoryResult.Status.PERMISSION_DENIED, source, Map.of(), true)
                    : ProviderDirectoryResult.unavailable(ex.getStatusCode().is5xxServerError());
        } catch (ExternalCodeInfrastructureException ex) {
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
