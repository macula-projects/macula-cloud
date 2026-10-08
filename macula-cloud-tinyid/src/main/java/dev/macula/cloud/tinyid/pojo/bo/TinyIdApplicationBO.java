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
package dev.macula.cloud.tinyid.pojo.bo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接入应用聚合业务对象。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class TinyIdApplicationBO {
    /** 应用主键。 */
    private Long appId;
    /** 接入 Token。 */
    private String token;
    /** 应用备注。 */
    private String remark;
    /** 授权业务类型。 */
    private List<String> bizTypes;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
