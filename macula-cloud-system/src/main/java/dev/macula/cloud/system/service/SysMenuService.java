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

package dev.macula.cloud.system.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.pojo.bo.MenuBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.query.MenuPageQuery;
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
     * 根菜单id
     */
    Long ROOT_ID = 0L;

    /**
     * 菜单可见
     */
    Integer VISIBLED = 1;

    /**
     * 获取菜单表格列表
     *
     * @return
     */
    List<MenuVO> listMenus(MenuQuery queryParams);


    /**
     * 获取菜单下拉列表
     *
     * @return
     */
    List<Option> listMenuOptions();

    /**
     * 新增菜单
     *
     * @param menu
     * @return
     */
    boolean saveMenu(SysMenu menu);

    /**
     * 清理路由缓存
     */
    void cleanCache();

    /**
     * 获取路由列表
     *
     * @return
     */
    List<RouteVO> listRoutes();

    /**
     * 资源(菜单+权限)树形列表
     *
     * @return
     */
    List<ResourceVO> listResources();

    /**
     * 修改菜单显示状态
     *
     * @param menuId  菜单ID
     * @param visible 是否显示(1->显示；2->隐藏)
     * @return
     */
    boolean updateMenuVisible(Long menuId, Integer visible);

    /**
     * 获取角色权限集合
     *
     * @param roles
     * @return
     */
    Set<String> listRolePerms(Set<String> roles);

    /**
     * 获取我的菜单列表
     *
     * @param menuQuery
     * @return
     */
    JSONObject getMyMenu(MenuQuery menuQuery);

    /**
     * 获取菜单管理列表
     *
     * @param menuPageQuery
     * @return
     */
    IPage<MenuBO> pagesMenus(MenuPageQuery menuPageQuery);

    /**
     * 添加或更新菜单，返回添加或更新的id
     *
     * @param menuDTO
     * @return DTO对象的菜单id
     */
    JSONObject add(MenuDTO menuDTO);

    /**
     * 根据id列表删除指定id菜单
     *
     * @param menuIds 菜单id列表
     * @return
     */
    List<Long> del(List<Long> menuIds);

    /**
     * 请求方法下拉列表
     * @return
     */
    List<Option> requestMethodOption();
}
