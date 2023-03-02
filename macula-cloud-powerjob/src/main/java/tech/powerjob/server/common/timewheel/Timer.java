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

package tech.powerjob.server.common.timewheel;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 定时器
 *
 * @author tjq
 * @since 2020/4/2
 */
public interface Timer {

    /**
     * 调度定时任务
     */
    TimerFuture schedule(TimerTask task, long delay, TimeUnit unit);

    /**
     * 停止所有调度任务
     */
    Set<TimerTask> stop();
}
