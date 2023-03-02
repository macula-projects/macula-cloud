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
public interface ControlNodeHandler extends WorkflowNodeHandlerMarker {

    /**
     * 处理控制节点
     *
     * @param node           需要被处理的目标节点
     * @param dag            节点所属 DAG
     * @param wfInstanceInfo 节点所属工作流实例
     */
    void handle(PEWorkflowDAG.Node node, PEWorkflowDAG dag, WorkflowInstanceInfoDO wfInstanceInfo);

}
