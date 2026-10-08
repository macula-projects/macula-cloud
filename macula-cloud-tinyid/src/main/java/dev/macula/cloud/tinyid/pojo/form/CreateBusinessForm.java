/*
 * Copyright (c) 2023 Macula
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
package dev.macula.cloud.tinyid.pojo.form;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Form used to create one immutable TinyID business configuration across every current data source.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class CreateBusinessForm {
    /** 业务类型，全局代表一个独立的发号业务。 */
    @NotBlank
    @Size(max = 63)
    private String bizType;
    /** 每次从数据库申请的 ID 区间步长。 */
    @Min(1)
    @NotNull
    private Integer step;
    /** 多数据库实例的预留总数，所有数据源保持一致。 */
    @Min(1)
    private Integer delta = 10;

    /**
     * 拒绝未在表单契约中声明的字段，避免客户端设置 beginId、maxId 或 remainder。
     *
     * @param field 未识别的字段名
     * @param value 未识别字段的值
     */
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported business field: " + field);
    }
}
