/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

/**
 * API Key 的可编辑配置，不接受租户、应用或凭据覆盖。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class ApplicationApiKeyForm {
    @Size(max = 255)
    private String remark;
    @Size(max = 2000)
    private String accessPath;
    /** 显式拒绝额外身份字段，避免误以为客户端可以指定 Key 或租户。 */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不支持的 API Key 配置字段");
    }
}
