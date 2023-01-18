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

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.enums.StatusEnum;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.controller.SysMenuController;
import dev.macula.cloud.system.converter.MenuConverter;
import dev.macula.cloud.system.converter.PermissionConverter;
import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.dto.PermDTO;
import dev.macula.cloud.system.enums.MenuTypeEnum;
import dev.macula.cloud.system.mapper.SysMenuMapper;
import dev.macula.cloud.system.mapper.SysPermissionMapper;
import dev.macula.cloud.system.pojo.bo.MenuListBO;
import dev.macula.cloud.system.pojo.bo.MyMenuBO;
import dev.macula.cloud.system.pojo.bo.RouteBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.vo.menu.MenuVO;
import dev.macula.cloud.system.vo.menu.ResourceVO;
import dev.macula.cloud.system.vo.menu.RouteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Slf4j
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
  private final MenuConverter menuConverter;
  private final SysPermissionMapper sysPermissionMapper;
  private final PermissionConverter permissionConverter;

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

    List<Option> menus = menuList.stream()
      .filter(menu -> menu.getParentId().equals(parentId))
      .map(menu -> new Option(menu.getId(), menu.getName(), recurMenuOptions(menu.getId(), menuList)))
      .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    return menus;
  }

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

    List<ResourceVO> menus = menuList.stream()
      .filter(menu -> menu.getParentId().equals(parentId))
      .map(menu -> {
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
   * 菜单表格树形列表
   */
  @Override
  public List<MenuVO> listMenus(MenuQuery queryParams) {
    List<SysMenu> menus = this.list(new LambdaQueryWrapper<SysMenu>()
      .like(StrUtil.isNotBlank(queryParams.getKeywords()), SysMenu::getName, queryParams.getKeywords())
      .orderByAsc(SysMenu::getSort)
    );

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
      case EXTLINK: // 外链
        menu.setComponent(null);
        break;
    }

    boolean result = this.saveOrUpdate(menu);
    return result;
  }

  /**
   * 菜单下拉数据
   */
  @Override
  public List<Option> listMenuOptions() {
    List<SysMenu> menuList = this.list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
    List<Option> menus = recurMenuOptions(GlobalConstants.ROOT_NODE_ID, menuList);
    return menus;
  }

  /**
   * 路由列表
   */
  @Override
  @Cacheable(cacheNames = "system", key = "'routes'")
  public List<RouteVO> listRoutes() {
    List<RouteBO> menuList = this.baseMapper.listRoutes();
    List<RouteVO> routeList = recurRoutes(GlobalConstants.ROOT_NODE_ID, menuList);
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
    Optional.ofNullable(menuList).ifPresent(menus -> menus.stream()
      .filter(menu -> menu.getParentId().equals(parentId))
      .forEach(menu -> {
        RouteVO routeVO = new RouteVO();

        MenuTypeEnum menuTypeEnum = menu.getType();

        if (MenuTypeEnum.MENU.equals(menuTypeEnum)) {
          routeVO.setName(menu.getPath()); //  根据name路由跳转 this.$router.push({name:xxx})
        }
        routeVO.setPath(menu.getPath()); // 根据path路由跳转 this.$router.push({path:xxx})
        routeVO.setRedirect(menu.getRedirect());
        routeVO.setComponent(menu.getComponent());

        RouteVO.Meta meta = new RouteVO.Meta();
        meta.setTitle(menu.getName());
        meta.setIcon(menu.getIcon());
        meta.setRoles(menu.getRoles());
        meta.setHidden(StatusEnum.DISABLE.getValue().equals(menu.getVisible()));
        meta.setKeepAlive(true);

        routeVO.setMeta(meta);
        List<RouteVO> children = recurRoutes(menu.getId(), menuList);
        // 含有子节点的目录设置为可见
        boolean alwaysShow = CollectionUtil.isNotEmpty(children) && children.stream().anyMatch(item -> item.getMeta().getHidden().equals(false));
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
    List<SysMenu> menuList = this.list(new LambdaQueryWrapper<SysMenu>()
      .orderByAsc(SysMenu::getSort));
    List<ResourceVO> resources = recurResources(GlobalConstants.ROOT_NODE_ID, menuList);
    return resources;
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
    boolean result = this.update(new LambdaUpdateWrapper<SysMenu>()
      .eq(SysMenu::getId, menuId)
      .set(SysMenu::getVisible, visible)
    );
    return result;
  }

  @Override
  public Set<String> listRolePerms(Set<String> roles) {
    Set<String> perms = this.baseMapper.listRolePerms(roles);
    return perms;
  }

  @Override
  public JSONObject getMyMenu(SysMenuController.MyMenuQueryDto myMenuQueryDto) {
    JSONObject data = new JSONObject();
    List<String> permissions = new ArrayList<>();
    List<MyMenuBO> menus = new ArrayList<>();
    loopLoadMenu(0L, 2, menus, permissions);
    data.put("permissions", permissions);
    data.put("menu", menus);
    return data;
  }

  @Override
  public JSONArray listMenus(SysMenuController.MenuListQueryDto menuListQueryDto) {
    JSONArray array = new JSONArray();
    List<MenuListBO> menuListBOS = new ArrayList<>();
    loopLoadMenu(0L, menuListBOS);
    array.addAll(menuListBOS);
    return array;
  }

  @Override
  @Transactional
  public JSONObject add(MenuDTO menuDTO) {
    JSONObject jsonObject = new JSONObject();
    SysMenu menu = menuConverter.MenuDTO2Entity(menuDTO);
    if (Objects.nonNull(menu.getId())) {
      this.baseMapper.updateById(menu);
    } else {
      this.baseMapper.insert(menu);
    }
    Assert.notNull(menu.getId(), "保存菜单失败，稍后重试！");
    jsonObject.put("menuId", menu.getId());
    if (CollectionUtil.isEmpty(menuDTO.getApiList())) {
      sysPermissionMapper.delete(new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getMenuId, menu.getId()));
      return jsonObject;
    }
    List<Long> updatePermIds = new ArrayList<>();
    Map<String, PermDTO> permDTOMap = new HashMap<>();
    for(PermDTO permDTO: menuDTO.getApiList()) {
      SysPermission permission = permissionConverter.permDTO2Entity(permDTO);
      permission.setMenuId(menu.getId());
      if (Objects.nonNull(permission.getId())) {
        sysPermissionMapper.updateById(permission);
      } else {
        sysPermissionMapper.insert(permission);
      }
      updatePermIds.add(permission.getId());
      permDTO.setId(permission.getId());
      permDTOMap.put(permDTO.getCode()+"_"+permDTO.getUrl(), permDTO);
    }
    jsonObject.put("apiList", permDTOMap);
    List<SysPermission> permissions =sysPermissionMapper.selectList(
      new LambdaQueryWrapper<SysPermission>()
        .eq(SysPermission::getMenuId, menu.getId())
        .notIn(SysPermission::getId, updatePermIds));
    if(!permissions.isEmpty()) {
      List<Long> deleteIds = permissions.stream().map(SysPermission::getId).collect(Collectors.toList());
      sysPermissionMapper.delete(new LambdaQueryWrapper<SysPermission>().in(SysPermission::getId, deleteIds));
    }
    return jsonObject;
  }

  @Override
  @Transactional
  public List<Long> del(List<Long> menuIds) {
    List<Long> idList = new ArrayList<>();
    for(Long id: menuIds) {
      idList.add(id);
      loopDelMenu(id);
    }
    return idList;
  }

  private void loopDelMenu(Long menuId){
    try {
      List<SysMenu> subMenu = this.baseMapper.selectList(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
      if (subMenu.isEmpty()) {
        return;
      }
      for (SysMenu sysMenu : subMenu) {
        loopDelMenu(sysMenu.getId());
      }
    } finally {
      sysPermissionMapper.delete(new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getMenuId, menuId));
      this.baseMapper.deleteById(menuId);
    }
  }

  private void loopLoadMenu(Long parentId, List<MenuListBO> menuListBOS) {
    List<MenuListBO> dbDatas = this.baseMapper.getMenuList(parentId);
    if (dbDatas.isEmpty()) {
      return;
    }
    menuListBOS.addAll(dbDatas);
    for (MenuListBO dbData : dbDatas) {
      // 按钮直接过滤掉，不增加sql压力
      if (MenuTypeEnum.BUTTON.equals(dbData.getMeta().getType())) {
        continue;
      }
      List<MenuListBO> subDbDatas = new ArrayList<>();
      dbData.setChildren(subDbDatas);
      loopLoadMenu(dbData.getId(), subDbDatas);
    }
  }

  private void loopLoadMenu(Long parentId, int menuType, List<MyMenuBO> menus, List<String> permissions) {
    List<MyMenuBO> dbMenus = this.baseMapper.getMyMenuList(parentId, menuType);
    if (dbMenus.isEmpty()) {
      return;
    }
    menus.addAll(dbMenus);
    for (MyMenuBO parentMenu : dbMenus) {
      if (Objects.nonNull(parentMenu.getMeta().getType())
        && MenuTypeEnum.MENU.equals(parentMenu.getMeta())) {
        permissions.addAll(this.baseMapper.listButtonPermsByParentId(parentMenu.getId()));
      } else {
        List<MyMenuBO> subMenus = new ArrayList<>();
        parentMenu.setChildren(subMenus);
        loopLoadMenu(parentMenu.getId(), 1, subMenus, permissions);
        loopLoadMenu(parentMenu.getId(), 2, subMenus, permissions);
      }
    }
  }

  /**
   * 递归生成菜单列表
   *
   * @param parentId 父级ID
   * @param menuList 菜单列表
   * @return
   */
  private List<MenuVO> recurMenus(Long parentId, List<SysMenu> menuList) {
    if (CollectionUtil.isEmpty(menuList)) {
      return Collections.EMPTY_LIST;
    }

    List<MenuVO> menus = menuList.stream()
      .filter(menu -> menu.getParentId().equals(parentId))
      .map(entity -> {
        MenuVO menuVO = menuConverter.entity2VO(entity);
        List<MenuVO> children = recurMenus(entity.getId(), menuList);
        menuVO.setChildren(children);
        return menuVO;
      }).collect(Collectors.toList());
    return menus;
  }

  /**
   * 清理路由缓存
   */
  @Override
  @CacheEvict(cacheNames = "system", key = "'routes'")
  public void cleanCache() {
  }

}
