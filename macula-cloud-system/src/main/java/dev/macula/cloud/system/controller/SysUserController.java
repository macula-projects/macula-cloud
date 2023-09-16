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

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.dto.UserImportDTO;
import dev.macula.cloud.system.dto.UserTokenRolesDTO;
import dev.macula.cloud.system.form.UserForm;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.query.UserPageQuery;
import dev.macula.cloud.system.service.SysUserService;
import dev.macula.cloud.system.vo.user.UserExportVO;
import dev.macula.cloud.system.vo.user.UserLoginVO;
import dev.macula.cloud.system.vo.user.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;

/**
 * 用户控制器
 *
 * @author haoxr
 * @since 2022/1/15
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "用户分页列表")
    @GetMapping
    public IPage<UserVO> listUserPages(UserPageQuery queryParams) {
        return userService.listUserPages(queryParams);
    }

    @Operation(summary = "用户表单数据")
    @Parameter(name = "用户ID")
    @GetMapping("/{userId}/form")
    public UserForm getUserDetail(@PathVariable Long userId) {
        return userService.getUserFormData(userId);
    }

    @Operation(summary = "新增用户")
    @AuditLog(title = "新增用户")
    @PostMapping
    public boolean saveUser(@Validated @RequestBody UserForm userForm) {
        return userService.saveUser(userForm);
    }

    @Operation(summary = "修改用户")
    @AuditLog(title = "修改用户")
    @Parameter(name = "用户ID")
    @PutMapping(value = "/{userId}")
    public boolean updateUser(@PathVariable Long userId, @RequestBody @Validated UserForm userForm) {
        return userService.updateUser(userId, userForm);
    }

    @Operation(summary = "删除用户")
    @AuditLog(title = "删除用户")
    @Parameter(name = "用户ID", description = "用户ID，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deleteUsers(@PathVariable String ids) {
        return userService.deleteUsers(ids);
    }

    @Operation(summary = "修改用户密码")
    @AuditLog(title = "修改用户密码")
    @Parameter(name = "用户ID")
    @PatchMapping(value = "/{userId}/password")
    public boolean updatePassword(@PathVariable Long userId, @RequestParam String password) {
        return userService.updatePassword(userId, password);
    }

    @Operation(summary = "修改用户状态")
    @Parameter(name = "用户ID")
    @AuditLog(title = "修改用户状态")
    @Parameter(name = "用户状态", description = "用户状态(1:启用;0:禁用)")
    @PatchMapping(value = "/{userId}/status")
    public boolean updateStatus(@PathVariable Long userId, @RequestParam Integer status) {
        return userService.update(
            new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId).set(SysUser::getStatus, status));
    }

    @Operation(summary = "用户导入模板下载")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        String fileName = "用户导入模板.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));

        String fileClassPath = "excel-templates" + File.separator + fileName;
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileClassPath);

        ServletOutputStream outputStream = response.getOutputStream();
        ExcelWriter excelWriter = EasyExcel.write(outputStream).withTemplate(inputStream).build();

        excelWriter.finish();
    }

    @Operation(summary = "导入用户")
    @AuditLog(title = "导入用户")
    @PostMapping("/_import")
    public String importUsers(UserImportDTO userImportDTO) throws IOException {
        return userService.importUsers(userImportDTO);
    }

    @Operation(summary = "导出用户")
    @GetMapping("/export")
    public void exportUsers(UserPageQuery queryParams, HttpServletResponse response) throws IOException {
        String fileName = "用户列表.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));

        List<UserExportVO> exportUserList = userService.listExportUsers(queryParams);
        EasyExcel.write(response.getOutputStream(), UserExportVO.class).sheet("用户列表").doWrite(exportUserList);
    }

    @Operation(summary = "获取登录后用户信息",
        description = "根据用户名获取认证信息，给starter-system用，角色前端添加，已废弃")
    @Parameter(name = "用户名")
    @GetMapping("/{username}/loginUserinfo")
    @Deprecated
    public UserLoginVO getLoginUserInfoWithoutRoles(@PathVariable String username,
        @RequestParam(value = GlobalConstants.TOKEN_ID_NAME, required = false) String tokenId) {
        return userService.getLoginUserInfo(username, null, tokenId);
    }

    @Operation(summary = "获取登录后用户信息", description = "根据用户名获取认证信息，给starter-system用")
    @Parameter(name = "用户名")
    @Parameter(name = "角色和TokenID", description = "包含TokenID和角色列表")
    @PostMapping("/{username}/loginUserinfo")
    public UserLoginVO getLoginUserInfoForRemote(@PathVariable String username, UserTokenRolesDTO roles) {
        return userService.getLoginUserInfo(username, roles.getRoles(), roles.getTokenId());
    }

    @Operation(summary = "获取当前登录用户信息", description = "获取登录用户信息，给前端登录后用")
    @GetMapping("/me")
    public UserLoginVO getLoginUserInfo() {
        return userService.getLoginUserInfo();
    }

    @Operation(summary = "根据id查询单个/多个用户", hidden = true)
    @GetMapping("/getUsers")
    public IPage<UserVO> getUsers(UserPageQuery queryParams) {
        return userService.listUserPagesByIds(queryParams);
    }

    @PostMapping("/{username}/btnperms/clear")
    public boolean clearBtnPerms(@PathVariable String username) {
        return userService.clearBtnPerms(username);
    }
}
