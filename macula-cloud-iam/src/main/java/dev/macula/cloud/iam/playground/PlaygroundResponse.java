/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * 对外仅输出经过白名单和脱敏处理的协议轨迹；不反射上游任意错误正文。
 * @author Rain
 * @since 6.1.0
 */
public record PlaygroundResponse(String flowId, String scenario, String expiresAt, String authorizeUrl,
    String verification, Long pollAfterSeconds, Transcript transcript, Map<String, Object> data) {

    public record Transcript(String method, String path, int status, Map<String, Object> parameters, Map<String, Object> body) {}
    private static final Set<String> SAFE_REQUEST = Set.of("grant_type", "client_id", "scope", "redirect_uri",
        "code_challenge_method", "code_challenge", "token_type_hint");
    private static final Set<String> SAFE_RESPONSE = Set.of("active", "expires_in", "interval", "token_type", "scope",
        "client_id", "iss", "aud", "exp", "iat");
    private static final Set<String> ERRORS = Set.of("invalid_request", "invalid_client", "invalid_grant", "invalid_scope",
        "unauthorized_client", "unsupported_grant_type", "access_denied", "authorization_pending", "slow_down",
        "expired_token", "invalid_token", "bad_credentials", "upstream_unavailable", "upstream_interrupted",
        "invalid_upstream_response", "server_error", "temporarily_unavailable");

    public static Transcript transcript(PlaygroundProtocolClient.Result result) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        result.parameters().forEach((key, value) -> parameters.put(key, SAFE_REQUEST.contains(key) ? value : "[REDACTED]"));
        Map<String, Object> body = new LinkedHashMap<>();
        result.body().forEach((key, value) -> {
            if (SAFE_RESPONSE.contains(key) && (value instanceof String || value instanceof Number || value instanceof Boolean)) {
                body.put(key, value);
            } else if ("error".equals(key)) body.put(key, value instanceof String && ERRORS.contains(value) ? value : "upstream_error");
            else if (Set.of("access_token", "refresh_token", "id_token", "device_code", "user_code", "sub",
                "verification_uri", "verification_uri_complete", "error_description", "error_uri").contains(key)) {
                body.put(key, "[REDACTED]");
            }
        });
        return new Transcript(result.method(), result.path(), result.status(), Map.copyOf(parameters), Map.copyOf(body));
    }

    @Override public String toString() { return "PlaygroundResponse[redacted]"; }
}
