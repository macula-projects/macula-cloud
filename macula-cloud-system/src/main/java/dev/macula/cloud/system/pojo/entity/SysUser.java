/*
 * Copyright (c) 2022 Macula
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

package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;


@Data
public class SysUser extends BaseEntity {

    private String username;

    private String nickname;

    private String mobile;

    private Integer gender;

    private String avatar;

    private String password;

    private String email;

    private Integer status;

    private Long deptId;

    @Schema(description = "逻辑删除标识(1:删除;0:正常)")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private List<Long> roleIds;

    @TableField(exist = false)
    private String roleNames;

    @TableField(exist = false)
    private List<String> roles;

}
