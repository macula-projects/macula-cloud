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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 只调用配置中 IAM 的固定端点；无 cookie 转发、任意 URL、重定向或原始正文日志。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundProtocolClient {
    public enum Endpoint {
        TOKEN("/oauth2/token"), DEVICE("/oauth2/device_authorization"), USERINFO("/userinfo"),
        INTROSPECT("/oauth2/introspect"), REVOKE("/oauth2/revoke"), JWKS("/oauth2/jwks");
        private final String path;
        Endpoint(String path) { this.path = path; }
        public String path() { return path; }
    }
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final PlaygroundAccessPolicy policy;
    private final String configuredIssuer;

    public PlaygroundProtocolClient(PlaygroundAccessPolicy policy, String issuer) {
        this.policy = policy;
        this.configuredIssuer = issuer;
    }

    public String issuer() {
        if (!policy.isEnabled()) throw new IllegalStateException("Playground is disabled");
        return policy.issuer(configuredIssuer).toString();
    }

    public Result call(Endpoint endpoint, Map<String, String> form, String clientId, String secret, String bearer) {
        String method = endpoint == Endpoint.JWKS || endpoint == Endpoint.USERINFO ? "GET" : "POST";
        var builder = HttpRequest.newBuilder(URI.create(issuer() + endpoint.path())).timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json");
        if (secret != null) {
            String basic = encode(clientId) + ":" + encode(secret);
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        } else if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if ("GET".equals(method)) builder.GET();
        else builder.header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(form)));
        var future = client.sendAsync(builder.build(), info -> new LimitedBody());
        try {
            var response = future.get(10, TimeUnit.SECONDS);
            Map<String, Object> body = Map.of();
            if (response.body().length > 0) {
                try { body = json.readValue(response.body(), new TypeReference<Map<String, Object>>() {}); }
                catch (RuntimeException ex) { body = Map.of("error", "invalid_upstream_response"); }
            }
            return new Result(method, endpoint.path(), response.statusCode(), Map.copyOf(form),
                body == null ? Map.of("error", "invalid_upstream_response") : body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return failure(method, endpoint, form, "upstream_interrupted");
        } catch (ExecutionException | TimeoutException ex) {
            future.cancel(true);
            return failure(method, endpoint, form, "upstream_unavailable");
        }
    }

    private Result failure(String method, Endpoint endpoint, Map<String, String> form, String error) {
        return new Result(method, endpoint.path(), 0, Map.copyOf(form), Map.of("error", error));
    }

    static String form(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    /** Raw output is transient and must be sanitized before crossing the controller boundary. */
    public record Result(String method, String path, int status, Map<String, String> parameters, Map<String, Object> body) {
        public boolean successful() { return status >= 200 && status < 300 && !body.containsKey("error"); }
        @Override public String toString() { return method + " " + path + " -> " + status; }
    }

    /** Bound memory before buffering JSON; whole response also has the caller's ten-second deadline. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> chunks) {
            long size = chunks.stream().mapToLong(ByteBuffer::remaining).sum();
            if (received + size > 262144) {
                subscription.cancel();
                delegate.onError(new IllegalStateException("IAM response exceeds playground limit"));
                return;
            }
            received += (int) size;
            delegate.onNext(chunks);
        }
        @Override public void onError(Throwable failure) { delegate.onError(failure); }
        @Override public void onComplete() { delegate.onComplete(); }
    }
}
