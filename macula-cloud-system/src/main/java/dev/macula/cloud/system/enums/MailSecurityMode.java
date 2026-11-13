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
package dev.macula.cloud.system.enums;

/**
 * SMTP 传输安全模式及强制 TLS 配置。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum MailSecurityMode {
    NONE, SSL, STARTTLS;

    /** 判断配置是否属于支持的传输模式。 */
    public static boolean supports(String value) {
        return java.util.Arrays.stream(values()).anyMatch(mode -> mode.name().equals(value));
    }

    /** STARTTLS 必须协商成功；任何加密模式都不能通过关闭证书校验绕过错误。 */
    public static void configure(java.util.Properties properties, String value) {
        if (!supports(value)) {
            throw new IllegalArgumentException("SMTP 安全模式无效");
        }
        boolean startTls = STARTTLS.name().equals(value);
        properties.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        properties.put("mail.smtp.starttls.required", String.valueOf(startTls));
        properties.put("mail.smtp.ssl.enable", String.valueOf(SSL.name().equals(value)));
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
    }
}
