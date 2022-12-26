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

package dev.macula.cloud.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysUserRoleMapper;
import dev.macula.cloud.system.pojo.entity.SysUserRole;
import dev.macula.cloud.system.service.SysUserRoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    /**
     * 保存用户角色
     *
     * @param userId
     * @param roleIds
     * @return
     */
    @Override
    public boolean saveUserRoles(Long userId, List<Long> roleIds) {

        if (userId == null || CollectionUtil.isEmpty(roleIds)) {
            return false;
        }

        // 用户原角色ID集合
        List<Long> userRoleIds = this.list(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(item -> item.getRoleId())
                .collect(Collectors.toList());

        // 新增用户角色
        List<Long> saveRoleIds;
        if (CollectionUtil.isEmpty(userRoleIds)) {
            saveRoleIds = roleIds;
        } else {
            saveRoleIds = roleIds.stream()
                    .filter(roleId -> !userRoleIds.contains(roleId))
                    .collect(Collectors.toList());
        }

        List<SysUserRole> saveUserRoles = saveRoleIds
                .stream()
                .map(roleId -> new SysUserRole(userId, roleId))
                .collect(Collectors.toList());
        this.saveBatch(saveUserRoles);

        // 删除用户角色
        if (CollectionUtil.isNotEmpty(userRoleIds)) {
            List<Long> removeRoleIds = userRoleIds.stream()
                    .filter(roleId -> !roleIds.contains(roleId))
                    .collect(Collectors.toList());

            if (CollectionUtil.isNotEmpty(removeRoleIds)) {
                this.remove(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .in(SysUserRole::getRoleId, removeRoleIds)
                );
            }
        }
        return true;

    }
}
