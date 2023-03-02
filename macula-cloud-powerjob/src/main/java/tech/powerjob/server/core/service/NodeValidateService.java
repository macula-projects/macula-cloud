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

package tech.powerjob.server.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.powerjob.common.enums.WorkflowNodeType;
import tech.powerjob.server.core.validator.NodeValidator;
import tech.powerjob.server.core.workflow.algorithm.WorkflowDAG;
import tech.powerjob.server.persistence.remote.model.WorkflowNodeInfoDO;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author Echo009
 * @since 2021/12/14
 */
@Service
@Slf4j
public class NodeValidateService {

    private final Map<WorkflowNodeType, NodeValidator> nodeValidatorMap;

    public NodeValidateService(List<NodeValidator> nodeValidators) {
        nodeValidatorMap = new EnumMap<>(WorkflowNodeType.class);
        nodeValidators.forEach(e -> nodeValidatorMap.put(e.matchingType(), e));
    }

    public void complexValidate(WorkflowNodeInfoDO node, WorkflowDAG dag) {
        NodeValidator nodeValidator = getNodeValidator(node);
        if (nodeValidator == null) {
            // 默认不需要校验
            return;
        }
        nodeValidator.complexValidate(node, dag);
    }

    public void simpleValidate(WorkflowNodeInfoDO node) {
        NodeValidator nodeValidator = getNodeValidator(node);
        if (nodeValidator == null) {
            // 默认不需要校验
            return;
        }
        nodeValidator.simpleValidate(node);
    }

    private NodeValidator getNodeValidator(WorkflowNodeInfoDO node) {
        Integer nodeTypeCode = node.getType();
        if (nodeTypeCode == null) {
            // 前向兼容，默认为 任务节点
            return nodeValidatorMap.get(WorkflowNodeType.JOB);
        }
        return nodeValidatorMap.get(WorkflowNodeType.of(nodeTypeCode));
    }
}
