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

package tech.powerjob.server.core.validator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import tech.powerjob.common.enums.WorkflowNodeType;
import tech.powerjob.common.exception.PowerJobException;
import tech.powerjob.common.model.PEWorkflowDAG;
import tech.powerjob.server.core.workflow.algorithm.WorkflowDAG;
import tech.powerjob.server.core.workflow.algorithm.WorkflowDAGUtils;
import tech.powerjob.server.persistence.remote.model.WorkflowNodeInfoDO;

import static tech.powerjob.server.core.data.DataConstructUtil.*;

/**
 * @author Echo009
 * @since 2021/12/14
 */
class NodeValidatorTest {

    private final DecisionNodeValidator decisionNodeValidator = new DecisionNodeValidator();

    @Test
    void testDecisionNodeValidator() {

        PEWorkflowDAG peWorkflowDAG = constructEmptyDAG();
        PEWorkflowDAG.Node node1 = new PEWorkflowDAG.Node(1L, WorkflowNodeType.DECISION.getCode());
        // decision node return true
        node1.setNodeParams("true;");
        PEWorkflowDAG.Node node2 = new PEWorkflowDAG.Node(2L);
        PEWorkflowDAG.Node node3 = new PEWorkflowDAG.Node(3L);
        PEWorkflowDAG.Node node4 = new PEWorkflowDAG.Node(4L);
        addNodes(peWorkflowDAG, node1, node2, node3, node4);

        PEWorkflowDAG.Edge edge1_2 = new PEWorkflowDAG.Edge(1L, 2L, "z");
        PEWorkflowDAG.Edge edge1_3 = new PEWorkflowDAG.Edge(1L, 3L, "true");
        PEWorkflowDAG.Edge edge2_4 = new PEWorkflowDAG.Edge(2L, 4L);
        addEdges(peWorkflowDAG, edge1_2, edge1_3, edge2_4);
        WorkflowDAG dag = WorkflowDAGUtils.convert(peWorkflowDAG);

        final WorkflowNodeInfoDO workflowNodeInfo1 = new WorkflowNodeInfoDO();
        BeanUtils.copyProperties(node1, workflowNodeInfo1);
        workflowNodeInfo1.setId(node1.getNodeId());
        Assertions.assertThrows(PowerJobException.class,
            () -> decisionNodeValidator.complexValidate(workflowNodeInfo1, dag));

    }

}
