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

package tech.powerjob.server.web.response;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.BeanUtils;
import tech.powerjob.common.OmsConstant;
import tech.powerjob.common.model.PEWorkflowDAG;
import tech.powerjob.server.persistence.remote.model.WorkflowInstanceInfoDO;

/**
 * 工作流实例视图层展示对象
 *
 * @author tjq
 * @since 2020/5/31
 */
@Data
public class WorkflowInstanceInfoVO {

    /**
     * workflowInstanceId（任务实例表都使用单独的ID作为主键以支持潜在的分表需求）
     */
    private String wfInstanceId;

    private String workflowId;
    /**
     * 工作流名称，通过 workflowId 查询获取
     */
    private String workflowName;

    /**
     * workflow 状态（WorkflowInstanceStatus）
     */
    private Integer status;
    /**
     * 工作流启动参数
     */
    private String wfInitParams;

    /**
     * 工作流上下文
     */
    private String wfContext;

    private PEWorkflowDAG pEWorkflowDAG;
    private String result;

    /**
     * 预计触发时间
     */
    private String expectedTriggerTime;
    /**
     * 实际触发时间（需要格式化为人看得懂的时间）
     */
    private String actualTriggerTime;
    /**
     * 结束时间（同理，需要格式化）
     */
    private String finishedTime;

    public static WorkflowInstanceInfoVO from(WorkflowInstanceInfoDO wfInstanceDO, String workflowName) {
        WorkflowInstanceInfoVO vo = new WorkflowInstanceInfoVO();
        BeanUtils.copyProperties(wfInstanceDO, vo);

        vo.setWorkflowName(workflowName);
        vo.setPEWorkflowDAG(JSONObject.parseObject(wfInstanceDO.getDag(), PEWorkflowDAG.class));

        // JS精度丢失问题
        vo.setWfInstanceId(String.valueOf(wfInstanceDO.getWfInstanceId()));
        vo.setWorkflowId(String.valueOf(wfInstanceDO.getWorkflowId()));

        // 格式化时间
        if (wfInstanceDO.getExpectedTriggerTime() != null) {
            vo.setExpectedTriggerTime(
                DateFormatUtils.format(wfInstanceDO.getExpectedTriggerTime(), OmsConstant.TIME_PATTERN));
        }
        vo.setActualTriggerTime(DateFormatUtils.format(wfInstanceDO.getActualTriggerTime(), OmsConstant.TIME_PATTERN));
        if (wfInstanceDO.getFinishedTime() == null) {
            vo.setFinishedTime(OmsConstant.NONE);
        } else {
            vo.setFinishedTime(DateFormatUtils.format(wfInstanceDO.getFinishedTime(), OmsConstant.TIME_PATTERN));
        }

        return vo;
    }
}
