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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮件或短信服务配置写入表单，空凭据表示保留。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class MessageProviderForm {
    @NotBlank
    @Size(max = 32)
    private String providerType;
    @Size(max = 255)
    private String endpoint;
    private Integer port;
    @Size(max = 16)
    private String securityMode;
    @Size(max = 255)
    private String account;
    @Size(max = 2048)
    private String credential;
    @Size(max = 64)
    private String regionCode;
    @Size(max = 255)
    private String sender;
    @Size(max = 64)
    private String smsSdkAppId;
    @NotNull
    private Boolean enabled;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知服务配置字段：" + name);
    }
}
