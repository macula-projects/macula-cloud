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

import dev.macula.cloud.system.enums.MessageTemplateType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仅替换场景白名单变量的消息模板渲染器。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Component
public class TemplateRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");

    public String render(MessageTemplateType type, String template, Map<String, String> variables) {
        Map<String, String> values = variables == null ? Map.of() : variables;
        if (values.keySet().stream().anyMatch(key -> !type.variables().contains(key))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板包含未知变量");
        }
        if (type.variables().stream().anyMatch(key -> values.get(key) == null || values.get(key).isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板变量缺失或不受支持");
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!type.variables().contains(key) || values.get(key) == null || values.get(key).isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板变量缺失或不受支持");
            }
            String value = "MAIL".equals(type.channel()) ? escape(values.get(key)) : values.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        if (result.indexOf("${") >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板包含未识别变量");
        }
        return result.toString();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
