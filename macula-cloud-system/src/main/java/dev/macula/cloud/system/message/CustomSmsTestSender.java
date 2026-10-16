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
package dev.macula.cloud.system.message;

import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * 受主机白名单限制的自定义短信 HTTP POST 测试发送器。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Component
public class CustomSmsTestSender {
    private final JsonMapper json = JsonMapper.builder().build();
    private final Set<String> allowedHosts;
    private final HttpClient client;
    private final Function<String, InetAddress[]> addresses;

    @Autowired
    public CustomSmsTestSender(@Value("${macula.message.custom-sms-allowed-hosts:}") String allowedHosts) {
        this(allowedHosts, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build(), host -> {
                try { return InetAddress.getAllByName(host); }
                catch (UnknownHostException ex) { throw new IllegalArgumentException("短信主机解析失败", ex); }
            });
    }

    CustomSmsTestSender(String allowedHosts, HttpClient client, Function<String, InetAddress[]> addresses) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(",")).map(String::trim)
            .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.client = client;
        this.addresses = addresses;
    }

    public void send(SysMessageProvider provider, String target, String content, String templateCode) {
        URI uri;
        try { uri = URI.create(provider.getEndpoint()); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信接口 URL 无效"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
            uri.getUserInfo() != null || !allowedHosts.contains(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信接口主机不在允许列表");
        }
        try {
            for (InetAddress address : addresses.apply(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                    address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信接口地址不允许访问内网");
                }
            }
            Options options = json.readValue(provider.getConfigJson(), Options.class);
            if (!StringUtils.hasText(options.getPhoneParameter()) || !StringUtils.hasText(options.getContentParameter())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信参数映射不完整");
            }
            Map<String, String> body = new HashMap<>(options.getFixedParameters() == null
                ? Map.of() : options.getFixedParameters());
            body.put(options.getPhoneParameter(), target);
            body.put(options.getContentParameter(), content);
            if (StringUtils.hasText(options.getTemplateParameter())) body.put(options.getTemplateParameter(), templateCode);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json");
            if (options.getHeaders() != null) options.getHeaders().forEach((key, value) -> {
                if (!"Accept".equalsIgnoreCase(key) ||
                    !Set.of("application/json", "text/plain").contains(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "固定请求头仅支持非敏感的 Accept 类型");
                }
                request.header(key, value);
            });
            if (StringUtils.hasText(options.getAuthHeader()) && StringUtils.hasText(provider.getCredential())) {
                request.header(options.getAuthHeader(),
                    (options.getAuthPrefix() == null ? "" : options.getAuthPrefix()) + provider.getCredential());
            }
            HttpResponse<String> response = client.send(
                request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "短信接口返回非成功状态");
            }
            if (StringUtils.hasText(options.getSuccessField())) {
                Map<?, ?> result = json.readValue(response.body(), Map.class);
                if (!String.valueOf(result.get(options.getSuccessField())).equals(options.getSuccessValue())) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "短信接口返回失败");
                }
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "短信接口连接失败");
        }
    }

    /** 固定参数、字段映射、鉴权头和响应判定。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Options {
        private String phoneParameter;
        private String contentParameter;
        private String templateParameter;
        private Map<String, String> fixedParameters;
        private Map<String, String> headers;
        private String authHeader;
        private String authPrefix;
        private String successField;
        private String successValue;
    }
}
