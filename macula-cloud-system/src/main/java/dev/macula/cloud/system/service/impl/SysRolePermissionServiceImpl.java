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

package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysRolePermissionMapper;
import dev.macula.cloud.system.pojo.entity.SysRolePermission;
import dev.macula.cloud.system.service.SysRolePermissionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色权限实现类
 */
@Service
public class SysRolePermissionServiceImpl extends ServiceImpl<SysRolePermissionMapper, SysRolePermission>
    implements SysRolePermissionService {

    /**
     * 获取角色拥有的权限ID集合
     *
     * @param roleId 角色ID
     * @return 权限ID集合
     */
    @Override
    public List<Long> listPermIdsByRoleId(Long roleId) {
        return this.baseMapper.listPermIdsByRoleId(roleId);
    }

}
