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
 * 监控事件
 *
 * @author tjq
 * @since 2022/9/6
 */
public interface Event {

    /**
     * 监控事件的类型
     *
     * @return 监控类型
     */
    String type();

    /**
     * 监控事件的内容
     *
     * @return 监控事件的内容
     */
    String message();
}
