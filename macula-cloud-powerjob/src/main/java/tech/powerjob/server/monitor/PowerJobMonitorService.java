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

package tech.powerjob.server.monitor;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PowerJob 服务端监控
 *
 * @author tjq
 * @since 2022/9/10
 */
@Slf4j
@Component
public class PowerJobMonitorService implements MonitorService {

    private final List<Monitor> monitors = Lists.newLinkedList();

    public PowerJobMonitorService(List<Monitor> monitors) {
        monitors.forEach(m -> {
            log.info("[MonitorService] register monitor: {}", m.getClass().getName());
            this.monitors.add(m);
        });
    }

    @Override
    public void monitor(Event event) {
        monitors.forEach(m -> m.record(event));
    }
}
