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

package tech.powerjob.server.core.workflow.hanlder;

import tech.powerjob.common.model.PEWorkflowDAG;
import tech.powerjob.server.persistence.remote.model.WorkflowInstanceInfoDO;

/**
 * @author Echo009
 * @since 2021/12/9
 */
public interface TaskNodeHandler extends WorkflowNodeHandlerMarker {

    /**
     * 创建任务实例
     *
     * @param node           目标节点
     * @param dag            DAG
     * @param wfInstanceInfo 工作流实例
     */
    void createTaskInstance(PEWorkflowDAG.Node node, PEWorkflowDAG dag, WorkflowInstanceInfoDO wfInstanceInfo);

    /**
     * 执行任务实例
     *
     * @param node 目标节点
     */
    void startTaskInstance(PEWorkflowDAG.Node node);

}
