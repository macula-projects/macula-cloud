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

package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.vo.perm.PermPageVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 获取权限分页列表
     *
     * @param page
     * @param queryParams
     * @return
     */
    List<PermPageVO> listPermPages(Page<PermPageVO> page, PermPageQuery queryParams);

    /**
     * 权限<->有权限的角色集合
     *
     * @return
     */
    List<SysPermission> listPermRoles();

    /**
     * 根据角色编码集合获取按钮权限
     *
     * @param roles
     * @return
     */
    List<String> listBtnPermByRoles(List<String> roles);


}
