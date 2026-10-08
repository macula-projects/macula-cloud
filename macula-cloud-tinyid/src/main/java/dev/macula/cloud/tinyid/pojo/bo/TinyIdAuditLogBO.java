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

/**
 * TinyID 管理审计业务对象。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class TinyIdAuditLogBO {
    /** 主键。 */
    private Long id;
    /** 操作人。 */
    private String operator;
    /** 操作名称。 */
    private String action;
    /** 请求方法。 */
    private String requestMethod;
    /** 请求路径。 */
    private String requestUri;
    /** 是否成功。 */
    private boolean success;
    /** 脱敏错误摘要。 */
    private String errorSummary;
    /** 客户端地址。 */
    private String clientIp;
    /** 创建时间。 */
    private LocalDateTime createTime;
}
