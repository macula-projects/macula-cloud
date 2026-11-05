/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.mapstruct.config.MapStructAutoConfiguration;
import dev.macula.boot.starter.web.config.JacksonConfiguration;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.converter.AuditLogConverter;
import dev.macula.cloud.system.converter.DeptConverter;
import dev.macula.cloud.system.converter.MenuConverter;
import dev.macula.cloud.system.converter.PermissionConverter;
import dev.macula.cloud.system.converter.RoleConverter;
import dev.macula.cloud.system.converter.UserConverter;
import dev.macula.cloud.system.form.MenuForm;
import dev.macula.cloud.system.form.PermissionForm;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.bo.AuditLogBO;
import dev.macula.cloud.system.pojo.bo.UserBO;
import dev.macula.cloud.system.pojo.bo.UserExportBO;
import dev.macula.cloud.system.pojo.entity.SysDept;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.pojo.entity.SysRole;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.impl.SysDeptServiceImpl;
import dev.macula.cloud.system.query.DeptQuery;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRoleService;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import dev.macula.cloud.system.vo.app.ApplicationApiKeyVO;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import dev.macula.cloud.system.vo.dept.DeptVO;
import dev.macula.cloud.system.vo.log.AuditLogVO;
import dev.macula.cloud.system.vo.menu.MenuDetailVO;
import dev.macula.cloud.system.vo.menu.MenuVO;
import dev.macula.cloud.system.vo.perm.PermissionVO;
import dev.macula.cloud.system.vo.role.RoleDetailVO;
import dev.macula.cloud.system.vo.role.RolePageVO;
import dev.macula.cloud.system.vo.user.UserExportVO;
import dev.macula.cloud.system.vo.user.UserVO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证接口Date/Instant时间点类型、带时区JSON、真实生成的Mapper及不暴露Entity的HTTP边界。
 * 不启动业务应用或连接数据库、Redis、IAM。
 *
 * @author Rain
 * @since 6.1.0
 */
class InterfaceDateTimeContractTest {
    private static final LocalDateTime STORED_TIME = LocalDateTime.of(2026, 11, 5, 8, 50, 30);
    private static final Instant INSTANT = Instant.parse("2026-11-05T00:50:30Z");
    private static final String JSON_TIME = "2026-11-05T00:50:30.000Z";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, MapStructAutoConfiguration.class))
        .withUserConfiguration(JacksonConfiguration.class, MappingConfiguration.class)
        .withPropertyValues("macula.time.storage-zone=Asia/Shanghai", "spring.jackson.time-zone=UTC");

    /** JSON接口模型不得直接携带数据库日期时间类型或无时区格式覆盖。 */
    @Test
    void interfaceModelsUseTimepointsWithoutTimezoneStrippingAnnotations() {
        for (Class<?> type : List.of(ApplicationVO.class, ApplicationApiKeyVO.class, UserVO.class,
            AuditLogVO.class, DeptVO.class, MenuVO.class, RolePageVO.class, OAuth2ClientVO.class,
            RoleDetailVO.class, MenuDetailVO.class, PermissionVO.class, UserExportVO.class)) {
            for (var field : type.getDeclaredFields()) {
                assertThat(TemporalAccessor.class.isAssignableFrom(field.getType()) && field.getType() != Instant.class).as(type.getSimpleName() + "." + field.getName())
                    .isFalse();
                assertThat(field.getAnnotation(com.fasterxml.jackson.annotation.JsonFormat.class)).isNull();
            }
        }
    }

    /** 使用真实Spring JsonMapper验证带偏移输入、UTC输出和空值，不依赖字段注解。 */
    @Test
    void configuredJsonRoundTripsOffsetTimeAsDate() {
        runner.run(context -> {
            JsonMapper json = context.getBean(JsonMapper.class);
            RoleDetailVO view = json.readValue("{\"createTime\":\"2026-11-05T08:50:30.000+08:00\"}", RoleDetailVO.class);
            assertThat(view.getCreateTime().toInstant()).isEqualTo(INSTANT);
            assertThat(json.readTree(json.writeValueAsString(view)).get("createTime").asString()).isEqualTo(JSON_TIME);
            assertThat(json.readValue("{\"createTime\":null}", RoleDetailVO.class).getCreateTime()).isNull();
            assertThatThrownBy(() -> json.readValue("{\"createTime\":\"2026-11-05 08:50:30\"}", RoleDetailVO.class))
                .isInstanceOf(tools.jackson.databind.exc.InvalidFormatException.class);
        });
    }

    /** Date与Instant可通过ISO互通，纳秒输入只截断毫秒精度，不能被解析成超大毫秒数。 */
    @Test
    void instantAndDateJsonInteroperateWithoutFractionRollover() {
        runner.run(context -> {
            JsonMapper json = context.getBean(JsonMapper.class);
            Instant nanos = Instant.parse("2026-11-05T00:50:30.123456789Z");
            String instantJson = json.writeValueAsString(nanos);
            assertThat(instantJson).isEqualTo("\"2026-11-05T00:50:30.123456789Z\"");
            Date date = json.readValue(instantJson, Date.class);
            assertThat(date.toInstant()).isEqualTo(Instant.parse("2026-11-05T00:50:30.123Z"));
            assertThat(json.readValue(json.writeValueAsString(date), Instant.class)).isEqualTo(date.toInstant());
            assertThat(json.readValue("\"2026-11-05T08:50:30+08:00\"", Date.class).toInstant()).isEqualTo(INSTANT);
            assertThat(json.readValue("\"2026-11-05T08:50:30.123456789+08:00\"", Date.class).toInstant())
                .isEqualTo(Instant.parse("2026-11-05T00:50:30.123Z"));
        });
    }

    /** 实际生成的各类Mapper按外部存储时区转换，不退回MapStruct的UTC内置规则。 */
    @Test
    void generatedMappersHonorStorageTimezoneAcrossViews() {
        runner.run(context -> {
            SysRole role = new SysRole();
            role.setCreateTime(STORED_TIME);
            role.setLastUpdateTime(STORED_TIME);
            RoleConverter roles = context.getBean(RoleConverter.class);
            assertThat(roles.entity2Detail(role).getCreateTime().toInstant()).isEqualTo(INSTANT);
            Page<SysRole> rolePage = new Page<>();
            rolePage.setRecords(List.of(role));
            assertThat(roles.entity2Page(rolePage).getRecords().get(0).getLastUpdateTime().toInstant()).isEqualTo(INSTANT);
            SysMenu menu = new SysMenu();
            menu.setCreateTime(STORED_TIME);
            assertThat(context.getBean(MenuConverter.class).entity2VO(menu).getCreateTime().toInstant()).isEqualTo(INSTANT);
            SysDept dept = new SysDept();
            dept.setCreateTime(STORED_TIME);
            assertThat(context.getBean(DeptConverter.class).entity2Vo(dept).getCreateTime().toInstant()).isEqualTo(INSTANT);
            ApplicationBO app = new ApplicationBO();
            app.setCreateTime(STORED_TIME);
            Page<ApplicationBO> apps = new Page<>();
            apps.setRecords(List.of(app));
            assertThat(context.getBean(ApplicationConverter.class).bo2Vo(apps).getRecords().get(0).getCreateTime().toInstant())
                .isEqualTo(INSTANT);
            UserBO user = new UserBO();
            user.setCreateTime(STORED_TIME);
            user.setGender(1);
            assertThat(context.getBean(UserConverter.class).po2Vo(user).getCreateTime().toInstant()).isEqualTo(INSTANT);
            UserExportBO export = new UserExportBO();
            export.setCreateTime(STORED_TIME);
            assertThat(context.getBean(UserConverter.class).toExportVOs(List.of(export)).get(0).getCreateTime().toInstant())
                .isEqualTo(INSTANT);
            AuditLogBO log = new AuditLogBO();
            log.setCreateTime(STORED_TIME);
            Page<AuditLogBO> logs = new Page<>();
            logs.setRecords(List.of(log));
            assertThat(context.getBean(AuditLogConverter.class).bo2Vo(logs).getRecords().get(0).getCreateTime().toInstant())
                .isEqualTo(INSTANT);
        });
    }

    /** 部门非树形兜底分支不能通过Bean复制绕过配置的存储时区。 */
    @Test
    void departmentFallbackUsesConfiguredTimezone() {
        runner.run(context -> {
            SysDept dept = new SysDept();
            dept.setId(1L);
            dept.setParentId(1L);
            dept.setCreateTime(STORED_TIME);
            var service = spy(new SysDeptServiceImpl(context.getBean(DeptConverter.class)));
            doReturn(List.of(dept)).when(service).list(org.mockito.ArgumentMatchers.<Wrapper<SysDept>>any());
            assertThat(service.listDepartments(new DeptQuery()).get(0).getCreateTime().toInstant()).isEqualTo(INSTANT);
        });
    }

    /** 角色、菜单和权限详情保留业务字段，但HTTP响应不再泄漏LocalDateTime。 */
    @Test
    void detailEndpointsReturnIsoTimesAndExistingBusinessFields() {
        runner.run(context -> {
            var roleService = mock(SysRoleService.class);
            var menuService = mock(SysMenuService.class);
            var permissions = mock(SysPermissionService.class);
            SysRole role = new SysRole();
            role.setId(1L);
            role.setCode("operator");
            role.setCreateTime(STORED_TIME);
            when(roleService.getById(1L)).thenReturn(role);
            SysMenu menu = new SysMenu();
            menu.setId(2L);
            menu.setPath("/system/example");
            menu.setCreateTime(STORED_TIME);
            when(menuService.getById(2L)).thenReturn(menu);
            SysPermission permission = new SysPermission().setName("example").setMenuId(2L).setUrlPerm("GET:/example");
            permission.setId(3L);
            permission.setCreateTime(STORED_TIME);
            when(permissions.getById(3L)).thenReturn(permission);
            var mvc = MockMvcBuilders.standaloneSetup(
                new SysRoleController(roleService, context.getBean(RoleConverter.class), permissions),
                new SysMenuController(menuService, context.getBean(MenuConverter.class), permissions),
                new SysPermissionController(permissions, context.getBean(PermissionConverter.class)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(context.getBean(JsonMapper.class))).build();
            mvc.perform(get("/api/v1/roles/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("operator")).andExpect(jsonPath("$.createTime").value(JSON_TIME));
            mvc.perform(get("/api/v1/menus/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/system/example")).andExpect(jsonPath("$.createTime").value(JSON_TIME));
            mvc.perform(get("/api/v1/permissions/3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPerm").value("GET:/example")).andExpect(jsonPath("$.createTime").value(JSON_TIME));
        });
    }

    /** 写接口仅接收业务表单，审计字段不能覆盖实体，权限更新按路径ID操作。 */
    @Test
    void writeFormsDoNotBindAuditTimeAndKeepPathIdentity() {
        runner.run(context -> {
            var menus = mock(SysMenuService.class);
            var permissions = mock(SysPermissionService.class);
            when(menus.saveMenu(any())).thenReturn(true);
            when(permissions.updateById(any(SysPermission.class))).thenReturn(true);
            var mvc = MockMvcBuilders.standaloneSetup(
                new SysMenuController(menus, context.getBean(MenuConverter.class), permissions),
                new SysPermissionController(permissions, context.getBean(PermissionConverter.class)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(context.getBean(JsonMapper.class))).build();
            mvc.perform(post("/api/v1/menus").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"菜单\",\"path\":\"/example\",\"createTime\":\"2026-11-05T00:50:30Z\"}"))
                .andExpect(status().isOk());
            var menu = ArgumentCaptor.forClass(SysMenu.class);
            verify(menus).saveMenu(menu.capture());
            assertThat(menu.getValue().getCreateTime()).isNull();
            mvc.perform(put("/api/v1/permissions/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":99,\"name\":\"权限\",\"urlPerm\":\"GET:/example\",\"lastUpdateTime\":\"2026-11-05T00:50:30Z\"}"))
                .andExpect(status().isOk());
            var permission = ArgumentCaptor.forClass(SysPermission.class);
            verify(permissions).updateById(permission.capture());
            assertThat(permission.getValue().getId()).isEqualTo(3L);
            assertThat(permission.getValue().getLastUpdateTime()).isNull();
            assertThat(MenuForm.class.getDeclaredFields()).extracting("name").doesNotContain("createTime", "lastUpdateTime", "tenantId");
            assertThat(PermissionForm.class.getDeclaredFields()).extracting("name").doesNotContain("createTime", "lastUpdateTime");
        });
    }

    /** Excel沿用人类可读格式，Date类型调整不应把导出日期变成ISO文本或丢失时间。 */
    @Test
    void excelExportRetainsReadableDateTime() {
        UserExportVO user = new UserExportVO();
        user.setUsername("fixture");
        user.setCreateTime(Date.from(STORED_TIME.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output, UserExportVO.class).sheet("users").doWrite(List.of(user));
        List<Map<Integer, String>> rows = EasyExcel.read(new ByteArrayInputStream(output.toByteArray()))
            .headRowNumber(1).sheet().doReadSync();
        assertThat(rows.get(0).get(6)).isEqualTo("2026/11/05 08:50:30");
    }

    /** 只扫描生成的转换器，不扫描服务或连接外部环境。
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = RoleConverter.class)
    static class MappingConfiguration {
    }
}
