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

package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.Set;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 获取最大范围的数据权限
     *
     * @param roles 角色CODE集合
     * @return 数据权限DataScope
     */
    @InterceptorIgnore(tenantLine = "true")
    Integer getMaximumDataScope(Set<String> roles);

    /**
     * 根据群组ID查询角色code
     *
     * @param groupIds 群组ID
     * @return 角色code集合
     */
    @InterceptorIgnore(tenantLine = "true")
    Set<String> listRolesByGroupIds(Long tenantId, Set<Long> groupIds);
}
