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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Form used to create a TinyID client application.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class CreateApplicationForm {
    /** 应用说明，用于标识 Token 对应的接入应用。 */
    @NotBlank
    @Size(max = 255)
    private String remark;
    /** 应用初始授权的业务类型列表。 */
    @NotEmpty
    private List<@NotBlank @Size(max = 63) String> bizTypes;
}
