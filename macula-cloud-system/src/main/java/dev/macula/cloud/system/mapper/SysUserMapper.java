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

package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.starter.mp.annotation.DataPermission;
import dev.macula.cloud.system.dto.UserAuthInfo;
import dev.macula.cloud.system.pojo.bo.UserBO;
import dev.macula.cloud.system.pojo.bo.UserFormBO;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.query.UserPageQuery;
import dev.macula.cloud.system.vo.user.UserExportVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户持久层
 *
 * @author haoxr
 * @since 2022/1/14
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 获取用户分页列表（过滤数据权限）
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return 用户列表
     */
    @DataPermission(deptAlias = "u", userAlias = "u")
    @InterceptorIgnore(tenantLine = "true")
    Page<UserBO> listUserPages(Page<UserBO> page, UserPageQuery queryParams);

    /**
     * 根据用户id获取用户分页列表
     *
     * @param page 分页对象
     * @param queryParams 查询条件
     * @return 用户列表
     */
    @InterceptorIgnore(tenantLine = "true")
    Page<UserBO> listUserPagesByIds(Page<UserBO> page, UserPageQuery queryParams);

    /**
     * 获取用户表单详情
     *
     * @param userId 用户ID
     * @return 用户详情
     */
    UserFormBO getUserDetail(Long userId);

    /**
     * 根据用户名获取认证信息
     *
     * @param username 用户名
     * @return 用户认证信息
     */
    @InterceptorIgnore(tenantLine = "true")
    UserAuthInfo getUserAuthInfo(Long tenantId, String username);

    /**
     * 获取导出用户列表
     *
     * @param queryParams 查询条件
     * @return 用户导出列表
     */
    @DataPermission(deptAlias = "u")
    List<UserExportVO> listExportUsers(UserPageQuery queryParams);
}
