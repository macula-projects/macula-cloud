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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 轻量创建白名单；不接受凭据、租户或启用状态。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class OAuth2ClientDraftForm {
    @NotBlank @Size(max = 200)
    private String clientName;
    @NotBlank
    private String applicationType;
    private String miniappPlatform;
    private String protocol;
    private String purpose;
    /** 即使全局JSON允许未知字段，此接口也拒绝非基础信息的创建参数。 */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知应用草稿字段：" + name);
    }
}
