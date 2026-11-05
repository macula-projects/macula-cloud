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

package dev.macula.cloud.system.vo.dept;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 部门展示接口对象，审计时间统一使用Date时间点。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class DeptVO {

    private Long id;

    private Long parentId;

    private String name;

    private String treePath;

    private Integer sort;

    private Integer status;

    private String leader;

    private String mobile;

    private String email;

    private List<DeptVO> children;
    private Date createTime;
    private Date updateTime;

}
