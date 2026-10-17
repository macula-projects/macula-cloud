/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.macula.cloud.system.form;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

/**
 * 邮件模板安全预览参数。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class MessagePreviewForm {
    private Map<String, String> variables;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知预览字段：" + name);
    }
}
