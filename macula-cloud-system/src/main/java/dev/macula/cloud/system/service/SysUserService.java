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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.dto.UserAuthInfo;
import dev.macula.cloud.system.dto.UserImportDTO;
import dev.macula.cloud.system.form.UserForm;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.query.UserPageQuery;
import dev.macula.cloud.system.vo.user.UserExportVO;
import dev.macula.cloud.system.vo.user.UserLoginVO;
import dev.macula.cloud.system.vo.user.UserVO;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 用户业务接口
 *
 * @author haoxr
 * @since 2022/1/14
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据给定的用户名获取登录信息，含角色和按钮权限
     *
     * @param username 用户名
     * @param roles    该用户的角色
     * @return 登录用户信息
     */
    UserLoginVO getLoginUserInfo(String username, Set<String> roles, String tokenId);

    /**
     * 获取当前登录用户的信息
     *
     * @return 返回登录用户基本信息包含角色和按钮权限
     */
    UserLoginVO getLoginUserInfo();

    /**
     * 根据用户名获取认证信息
     *
     * @param username 用户名
     * @return 认证信息
     */
    UserAuthInfo getUserAuthInfo(String username);

    /**
     * 用户分页列表
     *
     * @return 用户信息
     */
    IPage<UserVO> listUserPages(UserPageQuery queryParams);

    /**
     * 获取用户详情
     *
     * @param userId 用户ID
     * @return 用户详情
     */
    UserForm getUserFormData(Long userId);

    /**
     * 新增用户
     *
     * @param userForm 用户表单对象
     * @return 成功标识
     */
    boolean saveUser(UserForm userForm);

    /**
     * 修改用户
     *
     * @param userId   用户ID
     * @param userForm 用户表单对象
     * @return 成功标识
     */
    boolean updateUser(Long userId, UserForm userForm);

    /**
     * 删除用户
     *
     * @param idsStr 用户ID，多个以英文逗号(,)分割
     * @return 成功标识
     */
    boolean deleteUsers(String idsStr);

    /**
     * 修改用户密码
     *
     * @param userId   用户ID
     * @param password 用户密码
     * @return 成功标识
     */
    boolean updatePassword(Long userId, String password);

    /**
     * 导入用户
     *
     * @param userImportDTO 导入用户数据
     * @return 成功标识
     */
    String importUsers(UserImportDTO userImportDTO) throws IOException;

    /**
     * 获取导出用户列表
     *
     * @param queryParams 查询参数
     * @return 用户列表
     */
    List<UserExportVO> listExportUsers(UserPageQuery queryParams);

    /**
     * 根据查询条件获取用户分页列表
     *
     * @param queryParams 查询参数
     * @return 用户列表
     */
    IPage<UserVO> listUserPagesByIds(UserPageQuery queryParams);

    /**
     * 清除指定用户缓存的按钮权限数据
     *
     * @param username 用户名
     * @return 清除成功返回ture，否则返回false
     */
    boolean clearBtnPerms(String username);
}
