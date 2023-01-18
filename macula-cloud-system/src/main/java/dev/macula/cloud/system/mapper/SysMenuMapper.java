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

/**
 * 菜单持久层
 *
 * @author haoxr
 * @since 2022/1/24
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.bo.MenuListBO;
import dev.macula.cloud.system.pojo.bo.MyMenuBO;
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
     * @return
     */
    List<RouteBO> listRoutes();

    /**
     * 获取角色权限集合
     *
     * @param roles
     * @return
     */
    Set<String> listRolePerms(Set<String> roles);

    /**
     * 获取我的菜单列表，根据父菜单id与菜单类型获取菜单列表
     * @param parentId 父菜单id, 根（root）菜单id为0，因为默认自增id最小id为1
     * @param type (1：菜单；2：目录；3：外链；4：按钮)
     * @return
     */
      List<MyMenuBO> getMyMenuList(Long parentId, int type);

  /**
   * 通过父菜单id获取按钮权限信息
   * @param parentId
   * @return
   */
  Set<String> listButtonPermsByParentId(Long parentId);

  /**
   * 通过父菜单id获取菜单管理的菜单列表对象
   * @param parentId 父菜单id, 根（root）菜单id为0
   * @return
   */
  List<MenuListBO> getMenuList(Long parentId);
}
