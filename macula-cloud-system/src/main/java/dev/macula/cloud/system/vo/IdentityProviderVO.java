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
package dev.macula.cloud.system.vo;

import lombok.Data;

import java.util.Map;

/**
 * 身份提供商脱敏展示数据。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
public class IdentityProviderVO {
    private Long id;
    private String name;
    private String providerType;
    private String category;
    private String callbackUrl;
    private String remark;
    private Map<String, String> config;
    private Map<String, Boolean> configuredSecrets;
    private boolean enabled;
    private boolean visible;
}
