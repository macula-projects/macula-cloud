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
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Joiner;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.converter.PermissionConverter;
import dev.macula.cloud.system.dto.PermDTO;
import dev.macula.cloud.system.mapper.SysPermissionMapper;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.pojo.entity.SysRole;
import dev.macula.cloud.system.pojo.entity.SysRolePermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRolePermissionService;
import dev.macula.cloud.system.vo.perm.PermPageVO;
import dev.macula.cloud.system.vo.perm.ResourcePermPageVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限业务实现类
 *
 * @author haoxr
 * @since 2022/1/22
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {

    private final RedisTemplate redisTemplate;

    private final PermissionConverter permissionConverter;

    private final SysRolePermissionService rolePermissionService;

    /**
     * 获取权限分页列表
     *
     * @param queryParams
     * @return
     */
    @Override
    public Page<PermPageVO> listPermPages(PermPageQuery queryParams) {
        Page<PermPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        List<PermPageVO> list = this.baseMapper.listPermPages(page, queryParams);
        page.setRecords(list);
        return page;
    }

    /**
     * 权限<->有权限的角色集合
     *
     * @return
     */
    @Override
    public List<SysPermission> listPermRoles() {
        return this.baseMapper.listPermRoles();
    }

    /**
     * 刷新权限角色缓存
     *
     * @return
     */
    @Override
    @Async
    public void refreshPermRolesRules() {
        redisTemplate.delete(GlobalConstants.URL_PERM_ROLES_KEY);
        List<SysPermission> permissions = this.listPermRoles();
        if (CollectionUtil.isNotEmpty(permissions)) {
            // 初始化URL【权限->角色(集合)】规则
            List<SysPermission> urlPermList = permissions.stream()
                    .filter(item -> StrUtil.isNotBlank(item.getUrlPerm()))
                    .collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(urlPermList)) {
                Map<String, List<String>> urlPermRoles = new HashMap<>();
                urlPermList.stream().forEach(item -> {
                    String perm = item.getUrlPerm();
                    List<String> roles = item.getRoles();
                    urlPermRoles.put(perm, roles);
                });
                redisTemplate.opsForHash().putAll(GlobalConstants.URL_PERM_ROLES_KEY, urlPermRoles);
            }
        }
    }

    @Override
    @Transactional
    public Map<String, PermDTO> handlerAddMenuPerms(List<PermDTO> apiList, Long menuId) {
        // 获取更新和添加的菜单权限id,用来删除其余该菜单下的其它菜单权限信息
        List<Long> updatePermIds = new ArrayList<>();
        Map<String, PermDTO> permDTOMap = handlerAddOrUpdateMenuPerms(apiList, menuId, updatePermIds);
        if (!updatePermIds.isEmpty()) {
            deleteOtherMenuPerms(menuId, updatePermIds);
        }
        return permDTOMap;
    }

    @Override
    public void deleteByMenuId(Long menuId) {
        Assert.notNull(menuId, "根据菜单id删除权限失败，菜单id为空，稍后重试！");
        List<SysPermission> sysPermissions = list(new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getMenuId, menuId));
        if(sysPermissions.isEmpty()){
            return;
        }
        Set<Long> batchIds = sysPermissions.stream().map(SysPermission::getId).collect(Collectors.toSet());
        getBaseMapper().deleteBatchIds(batchIds);
        rolePermissionService.remove(new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getPermissionId, batchIds));
    }

    @Override
    public List<PermDTO> listDTO(Wrapper queryWrapper) {
        List<SysPermission> sysPermissions = list(queryWrapper);
        return permissionConverter.listEntities2DTO(sysPermissions);
    }

    @Override
    public PermDTO toDTO(SysPermission entity) {
        return permissionConverter.entity2DTO(entity);
    }

    @Override
    public boolean validtorUrlPerm(Long id, String url, RequestMethod requestMethod) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(requestMethod.toString()).append(":").append(url);
        long count = count(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getUrlPerm, stringBuffer.toString())
                .and(Objects.nonNull(id), wrapper->wrapper.ne(SysPermission::getId, id)));
        return count == 0;
    }

    private Map<String, PermDTO> handlerAddOrUpdateMenuPerms(List<PermDTO> permDTOList, Long menuId, List<Long> updatePermIds) {
        Map<String, PermDTO> permDTOMap = new HashMap<>();
        // 没有更新或添加的菜单权限直接清空权限列表
        if (CollectionUtil.isEmpty(permDTOList)) {
            getBaseMapper().delete(new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getMenuId, menuId));
            return permDTOMap;
        }
        for (PermDTO permDTO : permDTOList) {
            Long permId = addOrUpdateMenuPermission(permDTO, menuId);
            Assert.notNull(permId, "保存菜单失败，权限添加失败，稍后重试！");
            updatePermIds.add(permId);
            permDTO.setId(permId);
            permDTOMap.put(permDTO.getCode() + "_" + permDTO.getUrl(), permDTO);
        }
        return permDTOMap;
    }

    private Long addOrUpdateMenuPermission(PermDTO permDTO, Long menuId) {
        SysPermission permission = permissionConverter.permDTO2Entity(permDTO);
        permission.setMenuId(menuId);
        if (Objects.nonNull(permission.getId())) {
            getBaseMapper().updateById(permission);
        } else {
            getBaseMapper().insert(permission);
        }
        return permission.getId();
    }

    private void deleteOtherMenuPerms(Long menuId, List<Long> updatePermIds) {
        List<SysPermission> permissions = getBaseMapper().selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getMenuId, menuId)
                        .notIn(SysPermission::getId, updatePermIds));
        if (!permissions.isEmpty()) {
            List<Long> deleteIds = permissions.stream().map(SysPermission::getId).collect(Collectors.toList());
            getBaseMapper().delete(new LambdaQueryWrapper<SysPermission>().in(SysPermission::getId, deleteIds));
        }
    }
}
