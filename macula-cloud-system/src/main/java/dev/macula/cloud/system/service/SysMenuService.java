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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.MenuForm;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.vo.menu.MenuVO;
import dev.macula.cloud.system.vo.menu.ResourceVO;
import dev.macula.cloud.system.vo.menu.RouteVO;

import java.util.List;
import java.util.Set;

/**
 * 菜单业务接口
 *
 * @author haoxr
 * @since 2020/11/06
 */
public interface SysMenuService extends IService<SysMenu> {
    /**
     * 获取菜单表格列表
     *
     * @return 菜单表格列表
     */
    List<MenuVO> listMenus(MenuQuery queryParams);

    /**
     * 获取菜单下拉列表
     *
     * @return 菜单下拉列表
     */
    List<Option<Long>> listMenuOptions();

    /**
     * 新增菜单
     *
     * @param menu 菜单数据
     * @return 是否成功
     */
    boolean saveMenu(SysMenu menu);

    /**
     * 清理路由缓存
     */
    void cleanCache();

    /**
     * 获取路由列表
     *
     * @return 路由列表
     */
    List<RouteVO> listRoutes();

    /**
     * 资源(菜单+权限)树形列表
     *
     * @return 树形列表
     */
    List<ResourceVO> listResources();

    /**
     * 修改菜单显示状态
     *
     * @param menuId  菜单ID
     * @param visible 是否显示(1->显示；2->隐藏)
     * @return 是否成功
     */
    boolean updateMenuVisible(Long menuId, Integer visible);

    /**
     * 获取角色权限集合
     *
     * @param roles 角色
     * @return 权限集合
     */
    Set<String> listRolePerms(Set<String> roles);

    /**
     * 请求方法下拉列表
     *
     * @return 请求方法下拉列表
     */
    List<Option<String>> requestMethodOption();

    /**
     * 新增菜单及菜单相关的权限信息
     *
     * @param menuForm 菜单表单数据
     * @return 是否成功
     */
    boolean saveMenuOrPermission(MenuForm menuForm);
}
