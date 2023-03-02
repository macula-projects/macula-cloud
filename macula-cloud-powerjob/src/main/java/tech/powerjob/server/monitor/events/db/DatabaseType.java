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

/**
 * DatabaseEventType
 *
 * @author tjq
 * @since 2022/9/6
 */
public enum DatabaseType {
    /**
     * 本地存储库，H2
     */
    LOCAL,
    /**
     * 远程核心库
     */
    CORE,
    /**
     * 扩展库
     */
    EXTRA
}
