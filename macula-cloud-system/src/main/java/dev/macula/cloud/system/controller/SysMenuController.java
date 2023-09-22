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

package dev.macula.cloud.system.controller;

import dev.macula.boot.result.Option;
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.MenuForm;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.vo.menu.MenuVO;
import dev.macula.cloud.system.vo.menu.ResourceVO;
import dev.macula.cloud.system.vo.menu.RouteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 菜单控制器
 *
 * @author haoxr
 * @since 2020/11/06
 */
@Tag(name = "菜单接口")
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Slf4j
public class SysMenuController {
    private final SysMenuService menuService;

    @Operation(summary = "获取请求方法下拉列表")
    @GetMapping("/methodOption")
    public List<Option> requestMethodOption() {
        return menuService.requestMethodOption();
    }

    @Operation(summary = "资源(菜单+权限)列表")
    @GetMapping("/resources")
    public List<ResourceVO> listResources() {
        List<ResourceVO> resources = menuService.listResources();
        return resources;
    }

    @Operation(summary = "菜单列表")
    @GetMapping
    public List<MenuVO> listMenus(MenuQuery queryParams) {
        List<MenuVO> menuList = menuService.listMenus(queryParams);
        return menuList;
    }

    @Operation(summary = "菜单下拉列表")
    @GetMapping("/options")
    public List<Option> listMenuOptions() {
        List<Option> menus = menuService.listMenuOptions();
        return menus;
    }

    @Operation(summary = "路由列表")
    @GetMapping("/routes")
    public List<RouteVO> listRoutes() {
        List<RouteVO> routeList = menuService.listRoutes();
        return routeList;
    }

    @Operation(summary = "菜单详情")
    @Parameter(name = "菜单ID")
    @GetMapping("/{id}")
    public SysMenu detail(@PathVariable Long id) {
        SysMenu menu = menuService.getById(id);
        return menu;
    }

    @Operation(summary = "新增菜单")
    @AuditLog(title = "新增菜单")
    @PostMapping
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public boolean addMenu(@RequestBody MenuForm menuForm) {
        boolean result = menuService.saveMenuOrPermission(menuForm);
        return result;
    }

    @Operation(summary = "修改菜单")
    @AuditLog(title = "修改菜单")
    @PutMapping(value = "/{id}")
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public boolean updateMenu(@RequestBody SysMenu menu) {
        boolean result = menuService.saveMenu(menu);
        return result;
    }

    @Operation(summary = "删除菜单")
    @AuditLog(title = "删除菜单")
    @Parameter(name = "菜单ID", description = "菜单ID，多个以英文(,)分割")
    @DeleteMapping("/{ids}")
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public boolean deleteMenus(@PathVariable("ids") String ids) {
        boolean result = menuService.removeByIds(Arrays.asList(ids.split(",")));
        return result;
    }

    @Operation(summary = "修改菜单显示状态")
    @AuditLog(title = "修改菜单显示状态")
    @Parameter(name = "菜单ID")
    @Parameter(name = "显示状态", description = "显示状态(1:显示;0:隐藏)")
    @PatchMapping("/{menuId}")
    public boolean updateMenuVisible(@PathVariable Long menuId, Integer visible

    ) {
        boolean result = menuService.updateMenuVisible(menuId, visible);
        return result;
    }
}

