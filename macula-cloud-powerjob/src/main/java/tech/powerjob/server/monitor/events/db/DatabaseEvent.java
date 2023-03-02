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

package tech.powerjob.server.monitor.events.db;

import lombok.Setter;
import lombok.experimental.Accessors;
import tech.powerjob.server.common.SJ;
import tech.powerjob.server.monitor.Event;

/**
 * 数据库操作事件
 *
 * @author tjq
 * @since 2022/9/6
 */
@Setter
@Accessors(chain = true)
public class DatabaseEvent implements Event {

    private DatabaseType type;

    private String serviceName;

    private String methodName;

    private Status status;

    private Integer rows;

    private long cost;

    private String errorMsg;

    private String extra;

    @Override
    public String type() {
        return "MONITOR_LOGGER_DB_OPERATION";
    }

    @Override
    public String message() {
        return SJ.MONITOR_JOINER.join(type, serviceName, methodName, status, rows, cost, errorMsg, extra);
    }

    public enum Status {
        SUCCESS, FAILED
    }
}
