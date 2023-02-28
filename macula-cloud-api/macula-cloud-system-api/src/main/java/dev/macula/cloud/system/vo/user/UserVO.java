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

package dev.macula.cloud.system.vo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户分页视图对象
 *
 * @author haoxr
 * @since 2022/1/15 9:41
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "用户分页视图对象")
@Data
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "性别")
    private String genderLabel;

    @Schema(description = "用户头像地址")
    private String avatar;

    @Schema(description = "用户邮箱")
    private String email;

    @Schema(description = "用户状态(1:启用;0:禁用)")
    private Integer status;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "角色名称，多个使用英文逗号(,)分割")
    private String roleNames;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;

}
