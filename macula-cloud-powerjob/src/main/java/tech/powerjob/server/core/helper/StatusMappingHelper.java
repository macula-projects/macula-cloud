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

package tech.powerjob.server.core.helper;

import tech.powerjob.common.enums.InstanceStatus;
import tech.powerjob.common.enums.WorkflowInstanceStatus;

/**
 * @author Echo009
 * @since 2021/12/13
 */
public class StatusMappingHelper {

    private StatusMappingHelper() {

    }

    /**
     * 工作流实例状态转任务实例状态
     */
    public static InstanceStatus toInstanceStatus(WorkflowInstanceStatus workflowInstanceStatus) {
        switch (workflowInstanceStatus) {
            case FAILED:
                return InstanceStatus.FAILED;
            case SUCCEED:
                return InstanceStatus.SUCCEED;
            case RUNNING:
                return InstanceStatus.RUNNING;
            case STOPPED:
                return InstanceStatus.STOPPED;
            default:
                return null;
        }
    }

}
