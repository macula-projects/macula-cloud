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

package tech.powerjob.server.monitor.events.w2s;

import lombok.Setter;
import lombok.experimental.Accessors;
import tech.powerjob.server.common.SJ;
import tech.powerjob.server.monitor.Event;

/**
 * worker 心跳事件监控
 *
 * @author tjq
 * @since 2022/9/9
 */
@Setter
@Accessors(chain = true)
public class WorkerHeartbeatEvent implements Event {

    private String appName;
    /**
     * 虽然和 AppName 冗余，但考虑到其他日志使用 appId 监控，此处可方便潜在的其他处理
     */
    private Long appId;
    private String version;

    private String protocol;

    private String tag;
    private String workerAddress;
    /**
     * worker 上报时间与 server 之间的延迟
     */
    private long delayMs;
    private Integer score;

    @Override
    public String type() {
        return "MONITOR_LOGGER_WORKER_HEART_BEAT";
    }

    @Override
    public String message() {
        return SJ.MONITOR_JOINER.join(appName, appId, version, protocol, tag, workerAddress, delayMs, score);
    }
}
