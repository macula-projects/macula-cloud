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

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import static dev.macula.cloud.iam.playground.PlaygroundProtocolClient.Endpoint;

/**
 * 编排当前会话的真实协议请求；所有客户端、scope、回调与后续令牌引用由服务端选择。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundFlowService {
    private final PlaygroundProtocolClient protocol;
    private final PlaygroundOidcVerifier verifier;
    private final PlaygroundSessionStore sessions;
    private final RegisteredClientRepository clients;
    private final PlaygroundProperties properties;

    public PlaygroundFlowService(PlaygroundProtocolClient protocol, PlaygroundOidcVerifier verifier,
        PlaygroundSessionStore sessions, RegisteredClientRepository clients, PlaygroundProperties properties) {
        this.protocol = protocol;
        this.verifier = verifier;
        this.sessions = sessions;
        this.clients = clients;
        this.properties = properties;
    }

    public Map<String, Object> configuration() {
        return Map.of("issuer", protocol.issuer(), "callback", callback(),
            "publicClient", PlaygroundRegisteredClientRepository.PUBLIC, "deviceClient", PlaygroundRegisteredClientRepository.DEVICE,
            "confidentialConfigured", clients.findByClientId(PlaygroundRegisteredClientRepository.CONFIDENTIAL) != null);
    }

    public PlaygroundResponse start(HttpSession session, PlaygroundRequest request) {
        if (request.scenario() == null) throw new IllegalArgumentException("Choose a scenario");
        String clientId = clientId(request.scenario());
        requireClient(clientId);
        boolean codeFlow = isCode(request.scenario());
        if (codeFlow) {
            randomBinding(request.state()); randomBinding(request.nonce());
            if (request.challenge() == null || !request.challenge().matches("[A-Za-z0-9_-]{43}")) {
                throw new IllegalArgumentException("S256 challenge required");
            }
        }
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("scope", codeFlow ? "openid profile playground.read" : "playground.read");
        if (request.scenario() == PlaygroundFlow.Scenario.PASSWORD) {
            parameters.put("username", required(request.username(), 128));
            parameters.put("password", required(request.password(), 256));
        } else if (request.scenario() == PlaygroundFlow.Scenario.SMS) {
            parameters.put("phone", required(request.phone(), 32));
            parameters.put("captcha", required(request.captcha(), 32));
        }
        var flow = sessions.create(session, request.scenario());
        return sessions.use(session, flow.id(), current -> {
            if (codeFlow) {
                current.bind(request.state(), request.nonce(), request.challenge());
                parameters.put("response_type", "code");
                parameters.put("client_id", clientId);
                parameters.put("redirect_uri", callback());
                parameters.put("state", request.state());
                parameters.put("nonce", request.nonce());
                parameters.put("code_challenge", request.challenge());
                parameters.put("code_challenge_method", "S256");
                return response(current, null, protocol.issuer() + "/oauth2/authorize?" + PlaygroundProtocolClient.form(parameters), Map.of());
            }
            if (request.scenario() == PlaygroundFlow.Scenario.DEVICE) {
                parameters.put("client_id", clientId);
                var result = protocol.call(Endpoint.DEVICE, parameters, null, null, null);
                Map<String, Object> data = Map.of();
                if (result.successful()) {
                    String deviceCode = string(result.body(), "device_code");
                    String userCode = string(result.body(), "user_code");
                    long expiry = number(result.body(), "expires_in", 0);
                    current.device(deviceCode, number(result.body(), "interval", 5), expiry, Instant.now());
                    // Do not forward an arbitrary verification_uri from an upstream response.
                    data = Map.of("userCode", userCode, "verificationUri", protocol.issuer() + "/playground/device",
                        "verificationUriComplete", protocol.issuer() + "/playground/device?user_code="
                            + java.net.URLEncoder.encode(userCode, StandardCharsets.UTF_8));
                }
                return response(current, result, null, data);
            }
            parameters.put("grant_type", switch (request.scenario()) {
                case CLIENT_CREDENTIALS -> "client_credentials";
                case PASSWORD -> "password";
                case SMS -> "sms";
                default -> throw new IllegalArgumentException("Unsupported scenario");
            });
            var result = confidential(Endpoint.TOKEN, parameters);
            acceptTokens(current, result, false);
            return response(current, result, null, Map.of());
        });
    }

    public PlaygroundResponse exchange(HttpSession session, String id, PlaygroundRequest.Callback request) {
        return sessions.use(session, id, flow -> {
            if (!isCode(flow.scenario())) throw new IllegalArgumentException("Not an authorization-code flow");
            requireClient(clientId(flow.scenario()));
            String code = required(request.code(), 4096);
            String codeVerifier = required(request.verifier(), 128);
            if (!codeVerifier.matches("[A-Za-z0-9._~-]{43,128}") || !challenge(codeVerifier).equals(flow.challenge())) {
                throw new IllegalArgumentException("PKCE verifier does not match this flow");
            }
            flow.consumeCallback(request.state());
            var parameters = new LinkedHashMap<String, String>();
            parameters.put("grant_type", "authorization_code");
            parameters.put("redirect_uri", callback());
            parameters.put("code", code);
            parameters.put("code_verifier", codeVerifier);
            PlaygroundProtocolClient.Result result;
            if (flow.scenario() == PlaygroundFlow.Scenario.PUBLIC_CODE) {
                parameters.put("client_id", PlaygroundRegisteredClientRepository.PUBLIC);
                result = protocol.call(Endpoint.TOKEN, parameters, null, null, null);
            } else result = confidential(Endpoint.TOKEN, parameters);
            acceptTokens(flow, result, true);
            return response(flow, result, null, Map.of());
        });
    }

    public PlaygroundResponse state(HttpSession session, String id) {
        protocol.issuer();
        return sessions.use(session, id, flow -> response(flow, null, null, Map.of()));
    }

    public PlaygroundResponse action(HttpSession session, String id, String action) {
        protocol.issuer();
        return sessions.use(session, id, flow -> {
            requireClient(clientId(flow.scenario()));
            if ("stop".equals(action)) {
                flow.stopPolling();
                return response(flow, null, null, Map.of("stopped", true));
            }
            if ("reveal".equals(action)) return response(flow, null, null, Map.of("tokens", flow.tokens()));
            if ("poll".equals(action)) return poll(flow);
            if ("userinfo".equals(action)) {
                if (!flow.oidcVerified()) throw new IllegalArgumentException("Verified OIDC flow required");
                var result = protocol.call(Endpoint.USERINFO, Map.of(), null, null, token(flow, "access_token"));
                Map<String, Object> data = Map.of();
                if (result.successful()) {
                    if (!Objects.equals(flow.subject(), result.body().get("sub"))) {
                        throw new IllegalArgumentException("UserInfo subject does not match ID Token");
                    }
                    var profile = new LinkedHashMap<String, Object>();
                    for (String key : List.of("sub", "preferred_username", "nickname", "name")) {
                        if (result.body().get(key) instanceof String value) profile.put(key, value);
                    }
                    data = Map.of("userInfo", profile);
                }
                return response(flow, result, null, data);
            }
            if ("logout".equals(action)) {
                if (!flow.oidcVerified()) throw new IllegalArgumentException("Verified OIDC flow required");
                var data = Map.<String, Object>of("action", protocol.issuer() + "/connect/logout",
                    "idTokenHint", token(flow, "id_token"), "postLogoutRedirectUri", protocol.issuer() + "/playground");
                sessions.clear(session);
                return response(flow, null, null, data);
            }
            if (flow.scenario() == PlaygroundFlow.Scenario.PUBLIC_CODE || flow.scenario() == PlaygroundFlow.Scenario.DEVICE) {
                throw new IllegalArgumentException("This action requires the confidential demonstration client");
            }
            var parameters = new LinkedHashMap<String, String>();
            Endpoint endpoint;
            switch (action) {
                case "refresh" -> {
                    parameters.put("grant_type", "refresh_token");
                    parameters.put("refresh_token", token(flow, "refresh_token"));
                    endpoint = Endpoint.TOKEN;
                }
                case "introspect", "revoke-access", "revoke-refresh" -> {
                    String kind = "revoke-refresh".equals(action) ? "refresh_token" : "access_token";
                    parameters.put("token", token(flow, kind));
                    parameters.put("token_type_hint", kind);
                    endpoint = "introspect".equals(action) ? Endpoint.INTROSPECT : Endpoint.REVOKE;
                }
                default -> throw new IllegalArgumentException("Unsupported playground action");
            }
            var result = confidential(endpoint, parameters);
            if ("refresh".equals(action)) acceptTokens(flow, result, false);
            return response(flow, result, null, Map.of());
        });
    }

    public void reset(HttpSession session) { sessions.clear(session); }

    private PlaygroundResponse poll(PlaygroundFlow flow) {
        if (flow.scenario() != PlaygroundFlow.Scenario.DEVICE) throw new IllegalArgumentException("Not a device flow");
        flow.beginPoll(Instant.now());
        var result = protocol.call(Endpoint.TOKEN, Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
            "client_id", PlaygroundRegisteredClientRepository.DEVICE, "device_code", flow.deviceCode()), null, null, null);
        if (result.successful()) {
            acceptTokens(flow, result, false);
            flow.stopPolling();
        } else {
            String error = Objects.toString(result.body().get("error"), "");
            if ("slow_down".equals(error) || result.status() == 0) flow.slowDown(Instant.now());
            else if (!"authorization_pending".equals(error)) flow.stopPolling();
        }
        return response(flow, result, null, Map.of());
    }

    private void acceptTokens(PlaygroundFlow flow, PlaygroundProtocolClient.Result result, boolean requireIdToken) {
        if (!result.successful()) return;
        string(result.body(), "access_token");
        boolean verified = false;
        String subject = null;
        if (result.body().get("id_token") instanceof String idToken) {
            // Security 7 omits nonce on refreshed ID Tokens; validate it when present,
            // and always retain subject/client/issuer binding from the verified initial login.
            var jwt = verifier.verify(idToken, clientId(flow.scenario()), flow.nonce(), flow.oidcVerified() && !requireIdToken);
            subject = jwt.getSubject();
            if (flow.subject() != null && !flow.subject().equals(subject)) {
                throw new IllegalArgumentException("Refreshed ID Token subject changed");
            }
            verified = true;
        } else if (requireIdToken) throw new IllegalArgumentException("OIDC response is missing ID Token");
        var values = new LinkedHashMap<>(result.body());
        // A provider may omit unchanged refresh/ID tokens on refresh.
        for (String key : List.of("refresh_token", "id_token")) {
            if (!values.containsKey(key) && flow.tokens().containsKey(key)) values.put(key, flow.tokens().get(key));
        }
        flow.tokens(values, Instant.now());
        if (verified) flow.verified(subject);
    }

    private PlaygroundProtocolClient.Result confidential(Endpoint endpoint, Map<String, String> parameters) {
        requireClient(PlaygroundRegisteredClientRepository.CONFIDENTIAL);
        return protocol.call(endpoint, parameters, PlaygroundRegisteredClientRepository.CONFIDENTIAL,
            properties.getClientSecret(), null);
    }

    private PlaygroundResponse response(PlaygroundFlow flow, PlaygroundProtocolClient.Result result, String url,
        Map<String, Object> details) {
        var data = new LinkedHashMap<>(details);
        data.put("hasAccessToken", flow.tokens().containsKey("access_token"));
        data.put("hasRefreshToken", flow.tokens().containsKey("refresh_token"));
        data.put("hasIdToken", flow.tokens().containsKey("id_token"));
        data.put("polling", flow.deviceCode() != null);
        Long wait = flow.deviceCode() == null ? null : Math.max(1,
            java.time.Duration.between(Instant.now(), flow.nextPollAt()).toSeconds() + 1);
        return new PlaygroundResponse(flow.id(), flow.scenario().name(), flow.expiresAt().toString(), url,
            flow.oidcVerified() ? "verified" : "not_verified", wait,
            result == null ? null : PlaygroundResponse.transcript(result), Map.copyOf(data));
    }

    private String callback() { return protocol.issuer() + "/playground/callback"; }
    private void requireClient(String id) {
        if (clients.findByClientId(id) == null) throw new IllegalStateException("Demo client is disabled or not configured");
    }
    private static boolean isCode(PlaygroundFlow.Scenario scenario) {
        return scenario == PlaygroundFlow.Scenario.PUBLIC_CODE || scenario == PlaygroundFlow.Scenario.CONFIDENTIAL_CODE;
    }
    private static String clientId(PlaygroundFlow.Scenario scenario) {
        return scenario == PlaygroundFlow.Scenario.PUBLIC_CODE ? PlaygroundRegisteredClientRepository.PUBLIC
            : scenario == PlaygroundFlow.Scenario.DEVICE ? PlaygroundRegisteredClientRepository.DEVICE
            : PlaygroundRegisteredClientRepository.CONFIDENTIAL;
    }
    private static String token(PlaygroundFlow flow, String key) { return string(flow.tokens(), key); }
    private static String string(Map<String, Object> body, String key) {
        if (!(body.get(key) instanceof String value) || value.isBlank()) throw new IllegalArgumentException("Required protocol value is missing");
        return value;
    }
    private static long number(Map<String, Object> body, String key, long fallback) {
        return body.get(key) instanceof Number number ? number.longValue() : fallback;
    }
    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Required input is missing or too long");
        return value;
    }
    private static void randomBinding(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43,128}")) throw new IllegalArgumentException("Random state and nonce are required");
    }
    static String challenge(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
