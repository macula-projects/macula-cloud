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
package dev.macula.cloud.tinyid.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Persistence command for a sanitized TinyID management audit event.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@TableName("tiny_id_audit_log")
public class TinyIdAuditLog {
    /** 审计日志主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作人账号。 */
    @TableField("operator_name")
    private String operator;
    /** 审计动作名称。 */
    @TableField("action_name")
    private String action;
    /** HTTP 请求方法。 */
    private String requestMethod;
    /** HTTP 请求路径。 */
    private String requestUri;
    /** 操作是否成功。 */
    @TableField("result_status")
    private boolean success;
    /** 失败时的脱敏错误摘要。 */
    private String errorSummary;
    /** 客户端 IP 地址。 */
    private String clientIp;
    /** 日志创建时间。 */
    private LocalDateTime createTime;
}
