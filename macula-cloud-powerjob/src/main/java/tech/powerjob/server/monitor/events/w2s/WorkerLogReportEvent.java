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
 * description
 *
 * @author tjq
 * @since 2022/9/11
 */
@Setter
@Accessors(chain = true)
public class WorkerLogReportEvent implements Event {

    private String workerAddress;

    /**
     * 日志条数
     */
    private long logNum;

    /**
     * 日志大小，用于统计 IO 压力
     */
    private long logSize;

    private Status status;

    public enum Status {
        SUCCESS, REJECTED, EXCEPTION
    }

    @Override
    public String type() {
        return "MONITOR_LOGGER_WORKER_LOG_REPORT";
    }

    @Override
    public String message() {
        return SJ.MONITOR_JOINER.join(workerAddress, logNum, logSize, status);
    }
}
