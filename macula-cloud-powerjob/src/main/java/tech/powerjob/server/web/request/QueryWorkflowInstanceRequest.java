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

package tech.powerjob.server.web.request;

import lombok.Data;

/**
 * 查询工作流实例请求
 *
 * @author tjq
 * @since 2020/5/31
 */
@Data
public class QueryWorkflowInstanceRequest {

    /**
     * 任务所属应用ID
     */
    private Long appId;
    /**
     * 当前页码
     */
    private Integer index;
    /**
     * 页大小
     */
    private Integer pageSize;
    /**
     * 查询条件（NORMAL/WORKFLOW）
     */
    private Long wfInstanceId;

    private Long workflowId;

    private String status;
}
