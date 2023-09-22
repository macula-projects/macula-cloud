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

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.boot.enums.MenuTypeEnum;
import dev.macula.boot.enums.StatusEnum;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.converter.MenuConverter;
import dev.macula.cloud.system.form.MenuForm;
import dev.macula.cloud.system.mapper.SysMenuMapper;
import dev.macula.cloud.system.pojo.bo.RouteBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.vo.menu.MenuVO;
import dev.macula.cloud.system.vo.menu.ResourceVO;
import dev.macula.cloud.system.vo.menu.RouteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单业务实现类
 *
 * @author haoxr
 * @since 2020/11/06
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
    private final MenuConverter menuConverter;

    private final SysPermissionService permissionService;

    /**
     * 递归生成资源（菜单+权限）树形列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return
     */
    private static List<ResourceVO> recurResources(Long parentId, List<SysMenu> menuList) {
        if (CollectionUtil.isEmpty(menuList)) {
            return Collections.EMPTY_LIST;
        }

        List<ResourceVO> menus = menuList.stream().filter(menu -> menu.getParentId().equals(parentId)).map(menu -> {
            ResourceVO resourceVO = new ResourceVO();
            resourceVO.setValue(menu.getId());
            resourceVO.setLabel(menu.getName());

            List<ResourceVO> children = recurResources(menu.getId(), menuList);
            resourceVO.setChildren(children);

            return resourceVO;
        }).collect(Collectors.toList());
        return menus;
    }

    /**
     * 递归生成菜单下拉层级列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return
     */
    private static List<Option> recurMenuOptions(Long parentId, List<SysMenu> menuList) {
        if (CollectionUtil.isEmpty(menuList)) {
            return Collections.EMPTY_LIST;
        }

        List<Option> menus = menuList.stream().filter(menu -> menu.getParentId().equals(parentId))
            .map(menu -> new Option(menu.getId(), menu.getName(), recurMenuOptions(menu.getId(), menuList)))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return menus;
    }

    /**
     * 保存菜单
     */
    @Override
    public boolean saveMenu(SysMenu menu) {
        String path = menu.getPath();

        MenuTypeEnum menuType = menu.getType();  // 菜单类型
        switch (menuType) {
            case CATALOG: // 目录
                Assert.isTrue(path.startsWith("/"), "目录路由路径格式错误，必须以/开始");
                menu.setComponent("Layout");
                break;
            case LINK: // 外链
                menu.setComponent(null);
                break;
        }

        boolean result = this.saveOrUpdate(menu);
        return result;
    }

    /**
     * 菜单表格树形列表
     */
    @Override
    public List<MenuVO> listMenus(MenuQuery queryParams) {
        List<SysMenu> menus = this.list(
            new LambdaQueryWrapper<SysMenu>().like(StrUtil.isNotBlank(queryParams.getKeywords()), SysMenu::getName,
                queryParams.getKeywords()).orderByAsc(SysMenu::getSort));

        Set<Long> cacheMenuIds = menus.stream().map(menu -> menu.getId()).collect(Collectors.toSet());

        List<MenuVO> list = menus.stream().map(menu -> {
            Long parentId = menu.getParentId();
            // parentId不在当前菜单ID的列表，说明为顶级菜单ID，根据此ID作为递归的开始条件节点
            if (!cacheMenuIds.contains(parentId)) {
                cacheMenuIds.add(parentId);
                return recurMenus(parentId, menus);
            }
            return new LinkedList<MenuVO>();
        }).collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
        return list;
    }

    /**
     * 菜单下拉数据
     */
    @Override
    public List<Option> listMenuOptions() {
        List<SysMenu> menuList = this.list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        List<Option> menus = recurMenuOptions(SecurityConstants.ROOT_NODE_ID, menuList);
        return menus;
    }

    /**
     * 路由列表
     */
    @Override
    @Cacheable(cacheNames = "system", key = "'routes'")
    public List<RouteVO> listRoutes() {
        List<RouteBO> menuList = this.baseMapper.listRoutes();
        List<RouteVO> routeList = recurRoutes(SecurityConstants.ROOT_NODE_ID, menuList);
        return routeList;
    }

    /**
     * 递归生成菜单路由层级列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return
     */
    private List<RouteVO> recurRoutes(Long parentId, List<RouteBO> menuList) {
        List<RouteVO> list = new ArrayList<>();
        Optional.ofNullable(menuList)
            .ifPresent(menus -> menus.stream().filter(menu -> menu.getParentId().equals(parentId)).forEach(menu -> {
                RouteVO routeVO = new RouteVO();

                MenuTypeEnum menuTypeEnum = menu.getType();

                if (MenuTypeEnum.MENU.equals(menuTypeEnum)) {
                    routeVO.setName(menu.getPath()); //  根据name路由跳转 this.$router.push({name:xxx})
                }
                routeVO.setPath(menu.getPath()); // 根据path路由跳转 this.$router.push({path:xxx})
                routeVO.setRedirect(menu.getRedirect());
                routeVO.setComponent(menu.getComponent());
                routeVO.setId(menu.getId());
                routeVO.setParentId(menu.getParentId());
                routeVO.setSort(menu.getSort());

                RouteVO.Meta meta = new RouteVO.Meta();
                meta.setTitle(menu.getName());
                meta.setIcon(menu.getIcon());
                meta.setRoles(menu.getRoles());
                meta.setHidden(StatusEnum.DISABLE.getValue().equals(menu.getVisible()));
                meta.setKeepAlive(true);
                meta.setType(menu.getType());
                meta.setVisible(menu.getVisible() == 1);

                routeVO.setMeta(meta);
                List<RouteVO> children = recurRoutes(menu.getId(), menuList);
                // 含有子节点的目录设置为可见
                boolean alwaysShow = CollectionUtil.isNotEmpty(children) && children.stream()
                    .anyMatch(item -> item.getMeta().getHidden().equals(false));
                meta.setAlwaysShow(alwaysShow);
                routeVO.setChildren(children);

                list.add(routeVO);
            }));
        return list;
    }

    /**
     * 获取菜单资源树形列表
     *
     * @return
     */
    @Override
    public List<ResourceVO> listResources() {
        List<SysMenu> menuList = this.list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        List<ResourceVO> resources = recurResources(SecurityConstants.ROOT_NODE_ID, menuList);
        return resources;
    }

    /**
     * 递归生成菜单列表
     *
     * @param parentId 父级ID
     * @param menuList 菜单列表
     * @return List<MenuVO>
     */
    private List<MenuVO> recurMenus(Long parentId, List<SysMenu> menuList) {
        if (CollectionUtil.isEmpty(menuList)) {
            return new ArrayList<>();
        }

        return menuList.stream().filter(menu -> menu.getParentId().equals(parentId)).map(entity -> {
            MenuVO menuVO = menuConverter.entity2VO(entity);
            List<MenuVO> children = recurMenus(entity.getId(), menuList);
            menuVO.setChildren(children);
            return menuVO;
        }).collect(Collectors.toList());
    }

    /**
     * 修改菜单显示状态
     *
     * @param menuId  菜单ID
     * @param visible 是否显示(1->显示；2->隐藏)
     * @return
     */
    @Override
    public boolean updateMenuVisible(Long menuId, Integer visible) {
        boolean result = this.update(
            new LambdaUpdateWrapper<SysMenu>().eq(SysMenu::getId, menuId).set(SysMenu::getVisible, visible));
        return result;
    }

    @Override
    public Set<String> listRolePerms(Set<String> roles) {
        // 要处理反向角色，正向角色-反向角色
        Set<String> positiveRoles =
            roles.stream().filter(role -> !role.startsWith(SecurityConstants.NEGATED_ROLE_PREFIX))
                .collect(Collectors.toSet());

        Set<String> negativeRoles =
            roles.stream().filter(role -> role.startsWith(SecurityConstants.NEGATED_ROLE_PREFIX))
                .collect(Collectors.toSet());

        Set<String> perms = new HashSet<>();

        if (CollectionUtil.isNotEmpty(positiveRoles)) {
            Set<String> positivePerms = this.baseMapper.listRolePerms(positiveRoles);
            perms.addAll(positivePerms);
            if (CollectionUtil.isNotEmpty(positivePerms) && CollectionUtil.isNotEmpty(negativeRoles)) {
                Set<String> negativePerms = this.baseMapper.listRolePerms(negativeRoles);
                if (CollectionUtil.isNotEmpty(negativePerms)) {
                    perms = perms.stream().filter(item -> !negativePerms.contains(item)).collect(Collectors.toSet());
                }
            }
        }

        return perms;
    }

    @Override
    public List<Option> requestMethodOption() {
        return Arrays.asList(RequestMethod.values()).stream()
            .map(method -> new Option(method.toString(), method.toString())).collect(Collectors.toList());
    }

    @Override
    public boolean saveMenuOrPermission(MenuForm menuForm) {
        SysMenu sysMenu = menuConverter.form2Entity(menuForm);
        boolean result = saveMenu(sysMenu);
        Assert.isTrue(result, "菜单信息保存失败！");
        // 菜单结构问题会找不到当前新增的菜单，前端已做菜单新增处理，理论上不存在新增菜单会保存权限情况
        if(sysMenu.getId() == null){
            return true;
        }
        return permissionService.saveOrUpdate(sysMenu.getId(), menuForm.getApiList());
    }

    /**
     * 清理路由缓存
     */
    @Override
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public void cleanCache() {
    }

}
