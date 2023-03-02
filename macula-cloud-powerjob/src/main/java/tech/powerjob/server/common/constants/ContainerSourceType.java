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

package tech.powerjob.server.common.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 容器类型
 *
 * @author tjq
 * @since 2020/5/15
 */
@Getter
@AllArgsConstructor
public enum ContainerSourceType {

    FatJar(1, "Jar文件"), Git(2, "Git代码库");

    private final int v;
    private final String des;

    public static ContainerSourceType of(int v) {
        for (ContainerSourceType type : values()) {
            if (type.v == v) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown ContainerSourceType of " + v);
    }
}
