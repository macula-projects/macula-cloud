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

package dev.macula.cloud.system.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.boot.result.Option;
import dev.macula.boot.result.Result;
import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.pojo.bo.MenuBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.query.MenuPageQuery;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.vo.menu.MenuVO;
import dev.macula.cloud.system.vo.menu.ResourceVO;
import dev.macula.cloud.system.vo.menu.RouteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
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

    @Operation(summary = "获取当前登录用户的菜单列表")
    @GetMapping("/my")
    public Result getUserMenu(MyMenuQueryDto myMenuQueryDto) {
        JSONObject data = menuService.getMyMenu(myMenuQueryDto);
        return Result.success(data);
    }

    @Operation(summary = "菜单列表")
    @GetMapping("/pages")
    public IPage<MenuBO> pagesMenus(MenuPageQuery menuPageQuery) {
        IPage<MenuBO> data = menuService.pagesMenus(menuPageQuery);
        return data;
    }

    @Operation(summary = "添加更新菜单及权限信息")
    @PostMapping("/add")
    public Result addMenu(@RequestBody MenuDTO menuDTO) {
        log.info("menuDto: {}", menuDTO);
        return Result.success(menuService.add(menuDTO));
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/delete")
    public Result delMenu(@RequestBody List<Long> menuIds) {
        return Result.success(menuService.del(menuIds));
    }

    @Operation(summary = "资源(菜单+权限)列表")
    @GetMapping("/resources")
    public Result<List<ResourceVO>> listResources() {
        List<ResourceVO> resources = menuService.listResources();
        return Result.success(resources);
    }

    @Operation(summary = "菜单列表")
    @GetMapping
    public Result listMenus(MenuQuery queryParams) {
        List<MenuVO> menuList = menuService.listMenus(queryParams);
        return Result.success(menuList);
    }

    @Operation(summary = "菜单下拉列表")
    @GetMapping("/options")
    public Result listMenuOptions() {
        List<Option> menus = menuService.listMenuOptions();
        return Result.success(menus);
    }

    @Operation(summary = "路由列表")
    @GetMapping("/routes")
    public Result listRoutes() {
        List<RouteVO> routeList = menuService.listRoutes();
        return Result.success(routeList);
    }

    @Operation(summary = "菜单详情")
    @Parameter(name = "菜单ID")
    @GetMapping("/{id}")
    public Result detail(
            @PathVariable Long id
    ) {
        SysMenu menu = menuService.getById(id);
        return Result.success(menu);
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public Result addMenu(@RequestBody SysMenu menu) {
        boolean result = menuService.saveMenu(menu);
        return Result.judge(result);
    }

    @Operation(summary = "修改菜单")
    @PutMapping(value = "/{id}")
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public Result updateMenu(
            @RequestBody SysMenu menu
    ) {
        boolean result = menuService.saveMenu(menu);
        return Result.judge(result);
    }

    @Operation(summary = "删除菜单")
    @Parameter(name = "菜单ID", description = "菜单ID，多个以英文(,)分割")
    @DeleteMapping("/{ids}")
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public Result deleteMenus(
            @PathVariable("ids") String ids
    ) {
        boolean result = menuService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.judge(result);
    }

    @Operation(summary = "修改菜单显示状态")
    @Parameter(name = "菜单ID")
    @Parameter(name = "显示状态", description = "显示状态(1:显示;0:隐藏)")
    @PatchMapping("/{menuId}")
    public Result updateMenuVisible(
            @PathVariable Long menuId,
            Integer visible

    ) {
        boolean result = menuService.updateMenuVisible(menuId, visible);
        return Result.judge(result);
    }

    @Schema(description = "查询我的菜单对象")
    @Data
    public static class MyMenuQueryDto {

    }
}

