package dev.macula.cloud.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Joiner;
import dev.macula.boot.result.Option;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.converter.MenuTenantConverter;
import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.dto.PermDTO;
import dev.macula.cloud.system.enums.MenuTypeEnum;
import dev.macula.cloud.system.mapper.SysMenuTenantMapper;

import dev.macula.cloud.system.pojo.bo.MenuBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.pojo.entity.SysRoleMenu;
import dev.macula.cloud.system.query.MenuPageQuery;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.service.SysMenuTenantService;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRoleMenuService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SysMenuTenantServiceImpl extends ServiceImpl<SysMenuTenantMapper, SysMenuTenant> implements SysMenuTenantService {

    private final MenuTenantConverter menuTenantConverter;

    private final SysPermissionService permissionService;

    private final SysRoleMenuService  roleMenuService;

    @Override
    public JSONObject getMyMenu(MenuQuery menuQuery) {
        JSONObject data = new JSONObject();
        Set<Long> buttonParentIds = new HashSet<>();
        List<MenuBO> menus = new ArrayList<>();
        String roleMenuIdSql = "select rm.menu_id from sys_role r left join sys_role_menu rm on r.id = rm.role_id where find_in_set( r.code, '"
                + Joiner.on(",").join(SecurityUtils.getRoles())
                + "')";
        loopLoadMyMenu(CollectionUtil.newHashSet(), menus, buttonParentIds, menuQuery, new HashMap<>(), roleMenuIdSql);
        data.put("menu", menus);
        if (!buttonParentIds.isEmpty()) {
            List<SysMenuTenant> subButtonPerm = list(new LambdaQueryWrapper<SysMenuTenant>()
                    .inSql(SysMenuTenant::getId, roleMenuIdSql)
                    .in(SysMenuTenant::getParentId, buttonParentIds)
                    .eq(SysMenuTenant::getType, MenuTypeEnum.BUTTON).orderByAsc(SysMenuTenant::getSort));
            data.put("permissions", subButtonPerm.stream().map(SysMenuTenant::getPerm).collect(Collectors.toList()));
        }
        return data;
    }

    @Override
    public IPage<MenuBO> pagesMenus(MenuPageQuery menuPageQuery) {
        List<MenuBO> menuBOS = new ArrayList<>();
        Set<Long> containKeyWorkShowMenuParentId = new HashSet<>();
        if (StringUtils.isNotBlank(menuPageQuery.getKeywords())) {
            containKeyWorkShowMenuParentId = getBaseMapper().listShowMenuParentIdByName(menuPageQuery.getKeywords());
        }
        loopLoadListMenu(containKeyWorkShowMenuParentId, menuBOS, menuPageQuery, new HashMap<Long, MenuBO>());
        IPage<MenuBO> data = new Page<>(menuPageQuery.getPageNum(), menuPageQuery.getPageSize(), menuBOS.size());
        int startIndex = (menuPageQuery.getPageNum() - 1) * menuPageQuery.getPageSize();
        int endIndex = (menuPageQuery.getPageNum()) * menuPageQuery.getPageSize();
        data.setRecords(menuBOS.subList(startIndex, Math.min(endIndex, menuBOS.size())));
        return data;
    }

    @Override
    @Transactional
    public JSONObject add(MenuDTO menuDTO) {
        JSONObject jsonObject = new JSONObject();
        SysMenuTenant menu = menuTenantConverter.MenuDTO2Entity(menuDTO);
        Long menuId = addOrUpdateSysMenu(menu);
        Assert.notNull(menuId, "保存菜单失败，无法获取保存的菜单id，稍后重试！");
        jsonObject.put("menuId", menuId);
        Map<String, PermDTO> permDTOMap = permissionService.handlerAddMenuPerms(menuDTO.getApiList(), menuId);
        // 没有添加菜单权限直接返回
        if (permDTOMap.isEmpty()) {
            return jsonObject;
        }
        jsonObject.put("apiList", permDTOMap);
        return jsonObject;
    }

    @Override
    @Transactional
    public List<Long> del(List<Long> menuIds) {
        List<Long> idList = new ArrayList<>();
        for (Long id : menuIds) {
            idList.add(id);
            loopDelMenu(id);
        }
        return idList;
    }

    /**
     * 添加或更新菜单信息
     *
     * @param menu
     * @return
     */
    private Long addOrUpdateSysMenu(SysMenuTenant menu) {
        if (Objects.nonNull(menu.getId())) {
            getBaseMapper().updateById(menu);
        } else {
            getBaseMapper().insert(menu);
        }
        return menu.getId();
    }

    /**
     * 根据菜单id递归删除菜单
     *
     * @param menuId
     */
    private void loopDelMenu(Long menuId) {
        try {
            List<SysMenuTenant> subMenu = getBaseMapper().selectList(new LambdaQueryWrapper<SysMenuTenant>().eq(SysMenu::getParentId, menuId));
            if (subMenu.isEmpty()) {
                return;
            }
            for (SysMenu sysMenu : subMenu) {
                loopDelMenu(sysMenu.getId());
            }
        } finally {
            permissionService.deleteByMenuId(menuId);
            getBaseMapper().deleteById(menuId);
            roleMenuService.remove(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
        }
    }

    /**
     * 使用递归，查询父菜单id下的所有菜单信息
     *
     * @param showParentIds 符合显示条件的顶层菜单id
     * @param menuBOS
     * @param menuPageQuery 查询对象
     * @param handlerMenu   暂存处理后的sysMenu的BO对象
     */
    private void loopLoadListMenu(Set<Long> showParentIds, List<MenuBO> menuBOS, MenuPageQuery menuPageQuery,
                                  Map<Long, MenuBO> handlerMenu) {
        Wrapper queryWrapper = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSort);
        List<SysMenuTenant> entities = list(queryWrapper);
        Set<Long> handlerMenuIds = innerHandlerLoopMenu(entities, showParentIds, menuBOS, handlerMenu, null);
        List<SysPermission> sysPermissionList = permissionService.list(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getMenuId, handlerMenuIds));
        sysPermissionList.forEach(sysPerm -> handlerMenu.get(sysPerm.getMenuId()).getApiList().add(permissionService.toDTO(sysPerm)));
    }

    /**
     * 使用递归，获取父菜单id下的所有我的菜单，用于我的菜单列表显示
     *
     * @param showParentIds   符合显示条件的顶层菜单id
     * @param buttonParentIds 按钮的上一层菜单id集合
     * @param menus
     * @param menuQuery
     * @param handlerMenu     暂存处理后的sysMenu的BO对象
     * @param roleMenuIdSql   角色菜单id子查询sql
     */
    private void loopLoadMyMenu(Set<Long> showParentIds, List<MenuBO> menus, Set<Long> buttonParentIds,
                                MenuQuery menuQuery, Map<Long, MenuBO> handlerMenu, String roleMenuIdSql) {
        Wrapper queryWrapper = new LambdaQueryWrapper<SysMenu>()
                .inSql(SysMenu::getId, roleMenuIdSql)
                .in(SysMenu::getType, MenuTypeEnum.MENU, MenuTypeEnum.CATALOG, MenuTypeEnum.EXTLINK, MenuTypeEnum.IFRAME)
                .ne(SysMenu::getPath, "")
                .eq(SysMenu::getVisible, Objects.nonNull(menuQuery.getStatus()) ? menuQuery.getStatus() : VISIBLED)
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSort);
        List<SysMenuTenant> entities = list(queryWrapper);
        innerHandlerLoopMenu(entities, showParentIds, menus, handlerMenu, buttonParentIds);
    }

    /**
     * 我的菜单列表及菜单管理列表遍历菜单中的内部实现2BO
     *
     * @param entities    菜单实体对象
     * @param showParentIds   菜单查询中的parentId,及下次遍历查询的parentId集合
     * @param menuBOS         查询出来并转换后的BO对象集合
     * @param handlerMenu     暂存处理后的sysMenu的BO对象
     * @param buttonParentIds 该参数来自我的菜单接口， 需要获取按钮的权限信息；菜单管理列表遍历无该参数传null
     */
    private Set<Long> innerHandlerLoopMenu(List<SysMenuTenant> entities, Set<Long> showParentIds, List<MenuBO> menuBOS, Map<Long, MenuBO> handlerMenu, Set<Long> buttonParentIds) {
        Set<Long> handlerMenuIds = new HashSet<>();
        if (entities.isEmpty()) {
            return handlerMenuIds;
        }
        Long lastParentId = null;
        List<MenuBO> lastParentMenus = new ArrayList<>();
        for (SysMenuTenant entity : entities) {
            handlerMenuIds.add(entity.getId());
            if (Objects.isNull(lastParentId)) {
                lastParentId = entity.getParentId();
            }
            MenuBO menuBO = menuTenantConverter.entity2BO(entity);
            handlerMenu.put(entity.getId(), menuBO);
            menuBO.setApiList(new ArrayList<>());
            if (Objects.nonNull(buttonParentIds) && MenuTypeEnum.MENU.equals(entity.getType())) {
                buttonParentIds.add(entity.getId());
            }
            if (ROOT_ID.equals(entity.getParentId()) && (showParentIds.isEmpty() || showParentIds.contains(entity.getId()))) {
                menuBOS.add(menuBO);
                continue;
            }
            if (!lastParentId.equals(entity.getParentId())) {
                if (!ROOT_ID.equals(lastParentId)) {
                    handlerMenu.get(lastParentId).setChildren(lastParentMenus);
                }
                lastParentId = entity.getParentId();
                lastParentMenus = new ArrayList<>();
            }
            lastParentMenus.add(menuBO);
        }
        if (!ROOT_ID.equals(lastParentId)) {
            // 防止配置错误，没有父菜单子结果集报错兼容， 这个default的BO对象不会返回前端
            handlerMenu.getOrDefault(lastParentId, new MenuBO()).setChildren(lastParentMenus);
        }
        return handlerMenuIds;
    }
}
