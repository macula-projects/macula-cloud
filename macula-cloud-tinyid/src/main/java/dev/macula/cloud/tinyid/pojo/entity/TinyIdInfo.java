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

package dev.macula.cloud.tinyid.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * TinyID 发号业务持久化实体。
 *
 * @author du_imba
 * @author Rain
 * @since 6.1.0
 */
@Data
@TableName("tiny_id_info")
public class TinyIdInfo {
    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务类型。 */
    private String bizType;
    /** 初始 ID。 */
    private Long beginId;
    /** 当前最大 ID。 */
    private Long maxId;
    /** 号段步长。 */
    private Integer step;
    /** 多数据库预留实例数。 */
    private Integer delta;
    /** 当前实例余数。 */
    private Integer remainder;
    /** 创建时间。 */
    private Date createTime;
    /** 更新时间。 */
    private Date updateTime;
    /** 乐观锁版本。 */
    private Long version;
}
