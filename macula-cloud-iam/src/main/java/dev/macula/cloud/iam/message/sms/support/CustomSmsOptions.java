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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * 自定义短信地址与认证 Header 的受控解析，不保存供应商任意脚本。
 * @author Rain
 * @since 6.1.0
 */
public record CustomSmsOptions(URI endpoint, Map<String, String> headers) {
    public static final String PROVIDER_TYPE = "CUSTOM_HTTP";
    private static final int MAX_CREDENTIAL_LENGTH = 2048;
    private static final Set<String> RESERVED_HEADERS = Set.of("host", "content-length", "content-type",
            "connection", "transfer-encoding", "expect", "upgrade", "trailer", "te");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 仅接收管理员保存的 HTTP(S) 地址，不允许 URL 凭据或片段。 */
    public static URI endpoint(String value) {
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("短信 POST URL 无效");
        }
    }

    /** 序列化前校验 Header；大小写不同的同名 Header 也必须拒绝。 */
    public static String encodeHeaders(Map<String, String> headers) {
        Map<String, String> checked = checkedHeaders(headers);
        String value = JSON.writeValueAsString(checked);
        if (value.length() > MAX_CREDENTIAL_LENGTH) {
            throw new IllegalArgumentException("认证 Header 总长度不能超过 2048 字符");
        }
        return value;
    }

    /** 从专用凭据字段恢复认证信息，损坏配置不进入出站调用。 */
    public static CustomSmsOptions parse(String endpoint, String credential) {
        try {
            if (credential == null || credential.length() > MAX_CREDENTIAL_LENGTH) {
                throw new IllegalArgumentException();
            }
            Map<?, ?> parsed = JSON.readValue(credential, Map.class);
            Map<String, String> headers = new LinkedHashMap<>();
            for (var entry : parsed.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) {
                    throw new IllegalArgumentException();
                }
                headers.put(key, value);
            }
            return new CustomSmsOptions(endpoint(endpoint), Map.copyOf(checkedHeaders(headers)));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("自定义短信配置无效");
        }
    }

    /** 限制传输控制字段和注入字符，认证值只允许合法 HTTP Header 字符。 */
    private static Map<String, String> checkedHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("认证 Header 不能为空");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (var entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || !key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value == null || value.isBlank()
                    || value.chars().anyMatch(c -> c < 32 || c > 126)
                    || RESERVED_HEADERS.contains(key.toLowerCase(Locale.ROOT))
                    || key.toLowerCase(Locale.ROOT).startsWith("proxy-")
                    || normalized.putIfAbsent(key.toLowerCase(Locale.ROOT), value) != null) {
                throw new IllegalArgumentException("认证 Header 名称或值无效");
            }
        }
        return new LinkedHashMap<>(headers);
    }
}
