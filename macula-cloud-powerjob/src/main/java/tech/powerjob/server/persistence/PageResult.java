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

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

/**
 * 分页对象
 *
 * @author tjq
 * @since 2020/4/12
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    /**
     * 当前页数
     */
    private int index;
    /**
     * 页大小
     */
    private int pageSize;
    /**
     * 总页数
     */
    private int totalPages;
    /**
     * 总数据量
     */
    private long totalItems;
    /**
     * 数据
     */
    private List<T> data;

    public PageResult(Page<?> page) {
        index = page.getNumber();
        pageSize = page.getSize();
        totalPages = page.getTotalPages();
        totalItems = page.getTotalElements();
    }
}
