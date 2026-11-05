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

package dev.macula.cloud.system.vo.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 审计日志展示接口对象，审计时间统一使用Date时间点。
 * @author Rain
 * @since 6.1.0
 */
@Schema(description = "审计日志分页视图对象")
@Data
public class AuditLogVO {
    @Schema(description = "操作IP")
    private String opIp;

    @Schema(description = "请求路径")
    private String opUrl;

    @Schema(description = "操作人")
    private String opName;

    @Schema(description = "操作标题")
    private String opTitle;

    @Schema(description = "请求方法")
    private String opMethod;

    @Schema(description = "请求方式")
    private String opRequestMethod;

    @Schema(description = "请求参数")
    private String opParam;

    @Schema(description = "操作状态（0-成功; 1-失败）")
    private Integer opStatus;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "响应结果信息")
    private String jsonResult;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createBy;
}
