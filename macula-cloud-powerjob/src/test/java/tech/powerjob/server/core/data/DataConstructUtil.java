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

package tech.powerjob.server.core.data;

import com.google.common.collect.Lists;
import tech.powerjob.common.model.PEWorkflowDAG;

import java.util.List;

/**
 * @author Echo009
 * @since 2021/12/10
 */
public class DataConstructUtil {

    public static void addNodes(PEWorkflowDAG dag, PEWorkflowDAG.Node... nodes) {
        for (PEWorkflowDAG.Node node : nodes) {
            dag.getNodes().add(node);
        }
    }

    public static void addEdges(PEWorkflowDAG dag, PEWorkflowDAG.Edge... edges) {
        for (PEWorkflowDAG.Edge edge : edges) {
            dag.getEdges().add(edge);
        }
    }

    public static PEWorkflowDAG constructEmptyDAG() {
        List<PEWorkflowDAG.Node> nodes = Lists.newLinkedList();
        List<PEWorkflowDAG.Edge> edges = Lists.newLinkedList();
        return new PEWorkflowDAG(nodes, edges);
    }

}
