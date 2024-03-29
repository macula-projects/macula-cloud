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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.mapper.SysTenantUserMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantUser;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.service.SysTenantUserService;
import dev.macula.cloud.system.service.SysUserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SysTenantUserServiceImpl extends ServiceImpl<SysTenantUserMapper, SysTenantUser>
    implements SysTenantUserService {

    private final SysUserService userService;

    @Override
    public Set<Long> getMeTenantIds() {
        SysUser sysUser = userService.getOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, SecurityUtils.getCurrentUser()));
        Assert.notNull(sysUser, "登录已过期，请重新登录！");
        List<SysTenantUser> userTenantList =
            list(new LambdaQueryWrapper<SysTenantUser>().eq(SysTenantUser::getUserId, sysUser.getId()));
        return userTenantList.stream().map(SysTenantUser::getTenantId).collect(Collectors.toSet());
    }
}
