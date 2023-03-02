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

package tech.powerjob.server.persistence.remote.model.brief;

import lombok.Data;

/**
 * @author Echo009
 * @since 2022/9/13
 */
@Data
public class BriefInstanceInfo {

    private Long appId;

    private Long id;
    /**
     * 任务ID
     */
    private Long jobId;
    /**
     * 任务所属应用的ID，冗余提高查询效率
     */
    private Long instanceId;
    /**
     * 总共执行的次数（用于重试判断）
     */
    private Long runningTimes;

    public BriefInstanceInfo(Long appId, Long id, Long jobId, Long instanceId) {
        this.appId = appId;
        this.id = id;
        this.jobId = jobId;
        this.instanceId = instanceId;
    }

    public BriefInstanceInfo(Long appId, Long id, Long jobId, Long instanceId, Long runningTimes) {
        this.appId = appId;
        this.id = id;
        this.jobId = jobId;
        this.instanceId = instanceId;
        this.runningTimes = runningTimes;
    }
}
