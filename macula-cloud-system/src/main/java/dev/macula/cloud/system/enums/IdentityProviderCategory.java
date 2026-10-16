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

import java.util.Arrays;

/**
 * 管理端身份提供商分类及稳定 API code。
 *
 * @author Rain
 * @since 2026-10-14
 */
public enum IdentityProviderCategory {
    SOCIAL("social", "社交身份提供商"),
    ENTERPRISE("enterprise", "企业身份提供商");

    private final String code;
    private final String label;

    IdentityProviderCategory(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }
    public String label() { return label; }

    public static IdentityProviderCategory fromCode(String code) {
        return Arrays.stream(values()).filter(category -> category.code.equals(code))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("认证源分类无效"));
    }
}
