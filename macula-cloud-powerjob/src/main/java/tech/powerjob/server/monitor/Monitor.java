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

/**
 * 监视器
 *
 * @author tjq
 * @since 2022/9/6
 */
public interface Monitor {

    /**
     * 全局上下文绑定 & 初始化
     */
    void init();

    /**
     * 记录监控事件 请注意该方法务必异步不阻塞！！！
     *
     * @param event 事件
     */
    void record(Event event);
}
