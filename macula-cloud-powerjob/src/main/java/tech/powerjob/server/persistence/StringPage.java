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

package tech.powerjob.server.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本分页
 *
 * @author tjq
 * @since 2020/5/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StringPage {
    /**
     * 当前页数
     */
    private long index;
    /**
     * 总页数
     */
    private long totalPages;
    /**
     * 文本数据
     */
    private String data;

    public static StringPage simple(String data) {
        StringPage sp = new StringPage();
        sp.index = 0;
        sp.totalPages = 1;
        sp.data = data;
        return sp;
    }
}
