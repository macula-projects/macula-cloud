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

package tech.powerjob.server.monitor.monitors;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tech.powerjob.server.common.aware.ServerInfoAware;
import tech.powerjob.server.common.module.ServerInfo;
import tech.powerjob.server.monitor.Event;
import tech.powerjob.server.monitor.Monitor;

/**
 * 系统默认实现——基于日志的监控监视器 需要接入方自行基于类 ELK 系统采集
 *
 * @author tjq
 * @since 2022/9/6
 */
@Component
public class LogMonitor implements Monitor, ServerInfoAware {

    private static final String MDC_KEY_SERVER_ID = "serverId";
    /**
     * server 启动依赖 DB，DB会被 monitor，因此最初的几条 log serverInfo 一定为空，在此处简单防空
     */
    private ServerInfo serverInfo = new ServerInfo();

    @Override
    public void init() {
    }

    @Override
    public void record(Event event) {
        MDC.put(MDC_KEY_SERVER_ID, String.valueOf(serverInfo.getId()));
        LoggerFactory.getLogger(event.type()).info(event.message());
    }

    @Override
    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }
}
