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

/**
 * 菜单持久层
 *
 * @author haoxr
 * @since 2022/1/24
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.bo.RouteBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 获取路由列表
     *
     * @return 路由列表
     */
    List<RouteBO> listRoutes();

    /**
     * 获取角色权限集合
     *
     * @param roles 角色CODE集合
     * @return 按钮权限集合
     */
    Set<String> listRolePerms(Set<String> roles);
}
