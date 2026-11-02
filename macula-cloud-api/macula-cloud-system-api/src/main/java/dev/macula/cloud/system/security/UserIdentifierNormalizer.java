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
package dev.macula.cloud.system.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 统一规范化用户的用户名、手机号和邮箱登录标识。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class UserIdentifierNormalizer {
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern MOBILE_SEPARATORS = Pattern.compile("[\\s-]");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?[0-9]{6,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /**
     * 规范化用户名输入。
     *
     * @param value 原始输入
     * @return 非空用户名
     */
    public Optional<String> username(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    /**
     * 规范化手机号，移除空白和分隔横线并保留可选的国际区号前缀。
     *
     * @param value 原始输入
     * @return 格式有效的规范化手机号
     */
    public Optional<String> mobile(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String normalized = MOBILE_SEPARATORS.matcher(value.trim()).replaceAll("");
        return MOBILE_PATTERN.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }

    /**
     * 以 trim 和小写规则规范化邮箱。
     *
     * @param value 原始输入
     * @return 格式有效的规范化邮箱
     */
    public Optional<String> email(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        boolean valid = normalized.length() <= MAX_EMAIL_LENGTH && EMAIL_PATTERN.matcher(normalized).matches();
        return valid ? Optional.of(normalized) : Optional.empty();
    }
}
