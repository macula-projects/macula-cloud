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
package dev.macula.cloud.iam.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 使用独立审计密钥生成消息目标 HMAC 摘要。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Component
public class MessageAuditHasher {
    private final byte[] key;

    public MessageAuditHasher(@Value("${macula.message-audit.hmac-key}") String key) {
        if (key == null || key.length() < 32) {
            throw new IllegalArgumentException("消息审计 HMAC 密钥至少需要 32 个字符");
        }
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String target) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("消息审计摘要失败", ex);
        }
    }
}
