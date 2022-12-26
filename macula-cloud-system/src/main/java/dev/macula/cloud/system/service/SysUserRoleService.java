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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysUserRole;

import java.util.List;

public interface SysUserRoleService extends IService<SysUserRole> {

    /**
     * 保存用户角色
     *
     * @param userId
     * @param roleIds
     * @return
     */
    boolean saveUserRoles(Long userId, List<Long> roleIds);
}
