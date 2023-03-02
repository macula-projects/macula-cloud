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

package tech.powerjob.server.remote.server.election;

import lombok.Data;
import tech.powerjob.common.PowerSerializable;

/**
 * 检测目标机器是否存活
 *
 * @author tjq
 * @since 2020/4/5
 */
@Data
public class Ping implements PowerSerializable {
    private long currentTime;
}
