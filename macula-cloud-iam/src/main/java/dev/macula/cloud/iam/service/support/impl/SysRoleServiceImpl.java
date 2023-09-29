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

package dev.macula.cloud.iam.service.support.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.iam.mapper.SysRoleMapper;
import dev.macula.cloud.iam.pojo.entity.SysRole;
import dev.macula.cloud.iam.service.support.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 角色业务实现类
 *
 * @author haoxr
 * @since 2022/6/3
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    @Override
    public Integer getMaximumDataScope(Set<String> roles) {
        return this.baseMapper.getMaximumDataScope(roles);
    }

    @Override
    public Set<String> listRolesByGroupIds(Long tenantId, Set<Long> groupIds) {
        if (CollectionUtil.isNotEmpty(groupIds)) {
            return this.baseMapper.listRolesByGroupIds(tenantId, groupIds);
        }
        return new HashSet<>();
    }
}
