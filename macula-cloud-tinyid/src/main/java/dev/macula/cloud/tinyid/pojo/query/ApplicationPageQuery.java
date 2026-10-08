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
package dev.macula.cloud.tinyid.pojo.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Bounded application page query.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class ApplicationPageQuery {
    /** 页码，从 1 开始。 */
    @Min(1)
    private int page = 1;
    /** 每页记录数，最大 100。 */
    @Min(1)
    @Max(100)
    private int pageSize = 20;
    /** 应用备注的模糊查询关键字。 */
    private String keywords;
}
