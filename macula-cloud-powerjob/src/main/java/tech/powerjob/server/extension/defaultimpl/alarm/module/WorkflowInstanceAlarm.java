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

package tech.powerjob.server.extension.defaultimpl.alarm.module;

import lombok.Data;
import tech.powerjob.common.model.PEWorkflowDAG;

/**
 * 工作流执行失败告警对象
 *
 * @author tjq
 * @since 2020/6/12
 */
@Data
public class WorkflowInstanceAlarm implements Alarm {

    private String workflowName;

    /**
     * 任务所属应用的ID，冗余提高查询效率
     */
    private Long appId;
    private Long workflowId;
    /**
     * workflowInstanceId（任务实例表都使用单独的ID作为主键以支持潜在的分表需求）
     */
    private Long wfInstanceId;
    /**
     * workflow 状态（WorkflowInstanceStatus）
     */
    private Integer status;

    private PEWorkflowDAG peWorkflowDAG;
    private String result;

    /**
     * 实际触发时间
     */
    private Long actualTriggerTime;
    /**
     * 结束时间
     */
    private Long finishedTime;

    /**
     * 时间表达式类型（CRON/API/FIX_RATE/FIX_DELAY）
     */
    private Integer timeExpressionType;
    /**
     * 时间表达式，CRON/NULL/LONG/LONG
     */
    private String timeExpression;

    @Override
    public String fetchTitle() {
        return "PowerJob AlarmService: Workflow Running Failed";
    }
}
