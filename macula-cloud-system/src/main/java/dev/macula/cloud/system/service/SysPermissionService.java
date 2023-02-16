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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.dto.PermDTO;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.vo.perm.PermPageVO;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

/**
 * 权限业务接口
 *
 * @author haoxr
 * @since 2022/1/22
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 权限<->有权限的角色集合
     *
     * @return
     */
    List<SysPermission> listPermRoles();

    /**
     * 获取权限分页列表
     *
     * @param permPageQuery
     * @return 权限列表
     */
    Page<PermPageVO> listPermPages(PermPageQuery permPageQuery);

    /**
     * 刷新Redis缓存中角色菜单的权限规则，角色和菜单信息变更调用
     */
    void refreshPermRolesRules();

    /**
     * 处理添加菜单的权限信息更新
     *
     * @param apiList 权限信息DTO对象
     * @param menuId  菜单id
     * @return
     */
    Map<String, PermDTO> handlerAddMenuPerms(List<PermDTO> apiList, Long menuId);

    /**
     * 根据菜单id删除该菜单下的权限信息
     *
     * @param menuId
     */
    void deleteByMenuId(Long menuId);

    /**
     * 查询权限列表，然后转换成权限信息DTO对象
     *
     * @param queryWrapper
     * @return
     */
    List<PermDTO> listDTO(Wrapper queryWrapper);

    /**
     * 实体类转换成DTO对象
     * @param entity
     * @return
     */
    PermDTO toDTO(SysPermission entity);

    /**
     * 接口权限路径验证器
     *
     * @param id
     * @param url
     * @param requestMethod
     * @return
     */
    boolean validtorUrlPerm(Long id, String url, RequestMethod requestMethod);
}
