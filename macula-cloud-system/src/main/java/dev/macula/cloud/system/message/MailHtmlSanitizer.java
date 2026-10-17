/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.macula.cloud.system.message;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 先完整转义再恢复无属性安全标签的严格邮件 HTML 清洗器。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Component
public class MailHtmlSanitizer {
    private static final Set<String> TAGS = Set.of("p", "br", "strong", "b", "em", "i", "ul", "ol", "li");
    private static final Pattern ESCAPED_TAG = Pattern.compile("&lt;(/?)([A-Za-z]+)\s*/?&gt;");

    public String sanitize(String value) {
        String normalized = HtmlUtils.htmlUnescape(value == null ? "" : value);
        Matcher matcher = ESCAPED_TAG.matcher(HtmlUtils.htmlEscape(normalized));
        StringBuffer safe = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group(2).toLowerCase(java.util.Locale.ROOT);
            String replacement = TAGS.contains(tag) ? "<" + matcher.group(1) + tag + ">" : matcher.group();
            matcher.appendReplacement(safe, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(safe);
        return safe.toString();
    }
}
