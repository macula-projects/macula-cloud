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
import cn.hutool.core.text.CharPool;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.PermissionValidtorForm;
import dev.macula.cloud.system.mapper.SysPermissionMapper;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.vo.perm.PermPageVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission>
    implements SysPermissionService {

    private static final String VALIDATOR_PERM_ID_SPLIT = "::";
    private static final String VALIDATOR_PERM_JOIN = ":";
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String EMPTY_STR = "";

    /**
     * 获取权限分页列表
     *
     * @param queryParams 查询条件
     * @return URL权限列表
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
        redisTemplate.delete(CacheConstants.SECURITY_URL_PERM_ROLES_KEY);
        List<SysPermission> permissions = this.listPermRoles();
        if (CollectionUtil.isNotEmpty(permissions)) {
            // 初始化URL【权限->角色(集合)】规则
            List<SysPermission> urlPermList =
                permissions.stream().filter(item -> StrUtil.isNotBlank(item.getUrlPerm())).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(urlPermList)) {
                Map<String, List<String>> urlPermRoles = new HashMap<>();
                urlPermList.forEach(item -> {
                    String perm = item.getUrlPerm();
                    List<String> roles = item.getRoles();
                    urlPermRoles.put(perm, roles);
                });
                redisTemplate.opsForHash().putAll(CacheConstants.SECURITY_URL_PERM_ROLES_KEY, urlPermRoles);
            }
        }
    }

    @Override
    public List<Option<Boolean>> validatorUrlPerm(List<PermissionValidtorForm> validatorForms) {
        if (validatorForms.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> validtorFormMap = validatorForms.stream().collect(Collectors.toMap(form -> {
            StringBuffer sb = new StringBuffer();
            sb.append(Objects.isNull(form.getId()) ? EMPTY_STR : form.getId()).append(VALIDATOR_PERM_ID_SPLIT)
                .append(form.getCode()).append(VALIDATOR_PERM_JOIN).append(form.getUrl()).append(VALIDATOR_PERM_JOIN)
                .append(form.getMethod().toString());
            return sb.toString();
        }, form -> {
            StringBuffer sb = new StringBuffer();
            sb.append(form.getMethod().toString()).append(VALIDATOR_PERM_JOIN).append(form.getUrl());
            return sb.toString();
        }, (oldValue, newValue) -> newValue));

        return validtorFormMap.keySet().stream().map(item -> {
            String[] keyIdArr = item.split(VALIDATOR_PERM_ID_SPLIT);
            long count = count(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getUrlPerm, validtorFormMap.get(item))
                    .and(StringUtils.isNotBlank(keyIdArr[0]),
                        wrapper -> wrapper.ne(SysPermission::getId, keyIdArr[0])));
            return new Option<>(count == 0, item);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean saveOrUpdate(Long menuId, List<PermissionValidtorForm> apiList) {
        if (apiList == null || apiList.isEmpty()) {
            return true;
        }
        //获取当前的菜单对应的权限，做多删处理
        List<SysPermission> perms = list(new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getMenuId, menuId));
        Map<Long, SysPermission> mPerm = perms.stream().collect(Collectors.toMap(SysPermission::getId, item -> item));
        List<SysPermission> savePerms = new ArrayList<>();
        apiList.forEach(item -> {
            SysPermission dbPerm = null;
            if (item.getId() != null) {
                dbPerm = mPerm.remove(item.getId());
            }
            String urlPerm = item.getMethod().name() + CharPool.COLON + item.getUrl();
            // 相同不处理
            if (dbPerm != null && StringUtils.equals(dbPerm.getUrlPerm(), urlPerm) && StringUtils.equals(item.getCode(),
                dbPerm.getName())) {
                return;
            }
            SysPermission sysPermission = new SysPermission();
            if (dbPerm != null) {
                sysPermission.setId(dbPerm.getId());
            }
            sysPermission.setName(item.getCode());
            sysPermission.setUrlPerm(urlPerm);
            sysPermission.setMenuId(menuId);
            savePerms.add(sysPermission);
        });
        if (!mPerm.isEmpty()) {
            removeByIds(mPerm.keySet());
        }
        return saveOrUpdateBatch(savePerms);
    }
}
