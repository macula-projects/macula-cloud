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

import tech.powerjob.common.enums.WorkflowNodeType;

/**
 * @author Echo009
 * @since 2021/12/9
 */
public interface WorkflowNodeHandlerMarker {

    /**
     * 返回能够处理的节点类型
     *
     * @return matching node type
     */
    WorkflowNodeType matchingType();

}