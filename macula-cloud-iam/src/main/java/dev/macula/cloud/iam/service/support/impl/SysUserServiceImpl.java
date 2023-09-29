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
import dev.macula.boot.enums.DataScopeEnum;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.support.SysRoleService;
import dev.macula.cloud.iam.service.support.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 用户业务实现类
 *
 * @author haoxr
 * @since 2022/1/14
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleService roleService;

    @Override
    public UserAuthInfo getUserAuthInfo(Long tenantId, String username, Set<Long> groupIds) {
        // 按照用户名和租户获取角色集合
        UserAuthInfo userAuthInfo = this.baseMapper.getUserAuthInfo(tenantId, username);

        if (userAuthInfo != null) {
            if (userAuthInfo.getRoles() == null) {
                userAuthInfo.setRoles(new HashSet<>());
            }

            // 按照人群包ID和租户获取角色集合
            Set<String> groupRoles = roleService.listRolesByGroupIds(tenantId, groupIds);
            if (CollectionUtil.isNotEmpty(groupRoles)) {
                userAuthInfo.getRoles().addAll(groupRoles);
            }

            // 处理排他角色（如果存在排他角色，则只取优先级最高的一个排他角色）
            Optional<String> exclusivityRole = userAuthInfo.getRoles().stream().filter(s -> s.startsWith("@"))
                .min(Comparator.comparing(s -> Long.parseLong(s.substring(1, s.indexOf("#")))))
                .map(s -> s.substring(s.indexOf("#") + 1));

            if (exclusivityRole.isPresent()) {
                userAuthInfo.getRoles().clear();
                userAuthInfo.getRoles().add(exclusivityRole.get());
            }

            // 获取最大范围的数据权限
            Set<String> roles = userAuthInfo.getRoles();
            if (CollectionUtil.isNotEmpty(roles)) {
                Integer dataScope = roleService.getMaximumDataScope(roles);
                userAuthInfo.setDataScope(dataScope);
            } else {
                userAuthInfo.setDataScope(DataScopeEnum.ALL.getValue());
            }
        }
        return userAuthInfo;
    }
}
