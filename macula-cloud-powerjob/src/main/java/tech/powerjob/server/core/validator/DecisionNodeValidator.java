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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tech.powerjob.common.enums.WorkflowNodeType;
import tech.powerjob.common.exception.PowerJobException;
import tech.powerjob.common.model.PEWorkflowDAG;
import tech.powerjob.server.core.workflow.algorithm.WorkflowDAG;
import tech.powerjob.server.persistence.remote.model.WorkflowNodeInfoDO;

import java.util.Collection;

/**
 * @author Echo009
 * @since 2021/12/14
 */
@Component
@Slf4j
public class DecisionNodeValidator implements NodeValidator {

    public static boolean isValidBooleanStr(String str) {
        return StringUtils.equalsIgnoreCase(str.trim(), "true") || StringUtils.equalsIgnoreCase(str.trim(), "false");
    }

    @Override
    public void complexValidate(WorkflowNodeInfoDO node, WorkflowDAG dag) {
        // 出度固定为 2
        WorkflowDAG.Node nodeWrapper = dag.getNode(node.getId());
        Collection<PEWorkflowDAG.Edge> edges = nodeWrapper.getSuccessorEdgeMap().values();
        if (edges.size() != 2) {
            throw new PowerJobException("DecisionNode‘s out-degree must be 2,node name : " + node.getNodeName());
        }
        // 边的属性必须为 ture 或者 false
        boolean containFalse = false;
        boolean containTrue = false;
        for (PEWorkflowDAG.Edge edge : edges) {
            if (!isValidBooleanStr(edge.getProperty())) {
                throw new PowerJobException(
                    "Illegal property of DecisionNode‘s out-degree edge,node name : " + node.getNodeName());
            }
            boolean b = Boolean.parseBoolean(edge.getProperty());
            if (b) {
                containTrue = true;
            } else {
                containFalse = true;
            }
        }
        if (!containFalse || !containTrue) {
            throw new PowerJobException(
                "Illegal property of DecisionNode‘s out-degree edge,node name : " + node.getNodeName());
        }

    }

    @Override
    public void simpleValidate(WorkflowNodeInfoDO node) {
        // 简单校验
        String nodeParams = node.getNodeParams();
        if (StringUtils.isBlank(nodeParams)) {
            throw new PowerJobException("DecisionNode‘s param must be not null,node name : " + node.getNodeName());
        }
    }

    @Override
    public WorkflowNodeType matchingType() {
        return WorkflowNodeType.DECISION;
    }
}
