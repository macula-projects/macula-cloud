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
 * TinyID 应用业务授权持久化实体。
 *
 * @author du_imba
 * @author Rain
 * @since 6.1.0
 */
@Data
@TableName("tiny_id_token")
public class TinyIdToken {
    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 应用接入 Token。 */
    private String token;
    /** 授权业务类型。 */
    private String bizType;
    /** Token 对应应用备注。 */
    private String remark;
    /** 创建时间。 */
    private Date createTime;
    /** 更新时间。 */
    private Date updateTime;
}
