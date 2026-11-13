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
package dev.macula.cloud.iam.message.sms.support;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;

/**
 * 有界 HTTP 短信投递，不跟随重定向，不自动重试或记录供应商敏感响应。
 * @author Rain
 * @since 6.1.0
 */
public final class CustomSmsHttpClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** 固定 JSON 发送协议，只有 2xx 且 success 为布尔 true 才表示服务已受理。 */
    public void send(CustomSmsOptions options, Map<String, Object> payload) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        LimitedSubscriber subscriber = new LimitedSubscriber();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(options.endpoint()).timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
            options.headers().forEach(request::header);
            pending = client.sendAsync(request.build(), response -> subscriber);
            // get 的总预算覆盖慢响应体；超时必须取消订阅与交换，不能留下后台读取。
            HttpResponse<byte[]> response = pending.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            var result = JSON.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !result.path("success").isBoolean()
                    || !result.path("success").booleanValue()) {
                throw new IllegalStateException("自定义短信提交失败");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("自定义短信提交中断");
        } catch (Exception ex) {
            throw new IllegalStateException("自定义短信提交失败");
        } finally {
            subscriber.cancel();
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    /**
     * 在接收数据时执行上限，不先聚合无限响应再检查长度。
     * @since 6.1.0
     */
    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private volatile Flow.Subscription subscription;

        /** 供 HTTP 交换等待完整且有界的响应。 */
        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        /** 超时可能早于订阅建立，已结束时立即取消迟到的订阅。 */
        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            if (body.isDone()) {
                value.cancel();
            } else {
                value.request(1);
            }
        }

        /** 按缓冲区逐步读取，超过边界即取消连接。 */
        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    cancel();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            if (!body.isDone()) {
                subscription.request(1);
            }
        }

        /** 保留失败信号供上层转换，不传播供应商内容到公开错误。 */
        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        /** 流正常结束才发布响应正文。 */
        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }

        /** 取消迟到数据和未结束的响应体。 */
        private void cancel() {
            body.completeExceptionally(new IllegalStateException("短信响应已取消或过大"));
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
        }
    }
}
