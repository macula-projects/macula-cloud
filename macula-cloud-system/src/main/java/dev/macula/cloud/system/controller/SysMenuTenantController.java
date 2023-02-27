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
import dev.macula.cloud.system.annotation.AuditLog;
import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.pojo.bo.MenuBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.query.MenuPageQuery;
import dev.macula.cloud.system.query.MenuQuery;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.SysMenuTenantService;
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
//@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Slf4j
public class SysMenuTenantController {
    private final SysMenuTenantService menuService;

    @Operation(summary = "获取当前登录用户的菜单列表")
    //@GetMapping("/my")
    public JSONObject getUserMenu(MenuQuery menuQuery) {
        JSONObject data = menuService.getMyMenu(menuQuery);
        return data;
    }

    @Operation(summary = "菜单列表")
    //@GetMapping("/pages")
    public IPage<MenuBO> pagesMenus(MenuPageQuery menuPageQuery) {
        IPage<MenuBO> data = menuService.pagesMenus(menuPageQuery);
        return data;
    }

    @Operation(summary = "添加更新菜单及权限信息")
    @AuditLog(title = "添加更新菜单及权限信息")
    //@PostMapping("/add")
    public JSONObject addMenu(@RequestBody MenuDTO menuDTO) {
        return menuService.add(menuDTO);
    }

    @Operation(summary = "删除菜单")
    @AuditLog(title = "删除菜单")
    //@DeleteMapping("/delete")
    public List<Long> delMenu(@RequestBody List<Long> menuIds) {
        return menuService.del(menuIds);
    }

}

