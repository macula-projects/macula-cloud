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

package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/**
 * API Key 持久化及显式租户约束的应用行锁、管理授权查询。
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysApplicationApiKeyMapper extends BaseMapper<SysApplicationApiKey> {
    /** 锁定同一应用行，串行化创建凭据与应用删除。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_application_tenant WHERE id = #{appId} AND tenant_id = #{tenantId} FOR UPDATE")
    SysApplication lockApplication(@Param("appId") Long appId, @Param("tenantId") Long tenantId);

    /** 由当前租户的有效角色与应用菜单授权决定能否管理凭据。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
        "SELECT COUNT(*) FROM sys_role_tenant r JOIN sys_role_menu rm ON r.id = rm.role_id",
        "JOIN sys_menu_tenant m ON m.id = rm.menu_id",
        "WHERE r.tenant_id = #{tenantId} AND m.tenant_id = #{tenantId}",
        "AND r.deleted = 0 AND r.status = 1 AND m.path = '/system/application'",
        "AND r.code IN <foreach collection='roles' item='role' open='(' separator=',' close=')'>#{role}</foreach>",
        "</script>"})
    long countManagers(@Param("tenantId") Long tenantId, @Param("roles") Set<String> roles);

    /** 提交后强制从数据库读取应用，不复用原 SqlSession 的查询缓存。 */
    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM sys_application_tenant WHERE id = #{appId} AND tenant_id = #{tenantId}")
    SysApplication cacheApplication(@Param("appId") Long appId, @Param("tenantId") Long tenantId);

    /** 缓存发布同时读取有效与撤销记录，强制刷新会话缓存。 */
    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM sys_application_apikey_tenant WHERE application_id = #{appId} AND tenant_id = #{tenantId}")
    List<SysApplicationApiKey> cacheKeys(@Param("appId") Long appId, @Param("tenantId") Long tenantId);
}
