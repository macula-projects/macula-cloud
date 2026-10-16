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
package dev.macula.cloud.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮件或短信服务配置写入表单，空凭据表示保留。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
public class MessageProviderForm {
    @NotBlank
    private String channel;
    @NotBlank
    private String providerType;
    @NotBlank @Size(max = 100)
    private String name;
    private String endpoint;
    private String account;
    private String credential;
    private String regionCode;
    private String sender;
    private String configJson;
    private Boolean enabled;
}
