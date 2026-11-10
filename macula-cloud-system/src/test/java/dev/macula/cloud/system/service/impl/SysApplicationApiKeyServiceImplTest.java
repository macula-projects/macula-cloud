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

package dev.macula.cloud.system.service.impl;

import dev.macula.boot.mapstruct.DateTimeConverter;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.form.ApplicationApiKeyForm;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import dev.macula.cloud.system.service.ApplicationCredentialCacheService;
import java.time.LocalDateTime;
import java.time.Instant;
import tools.jackson.databind.json.JsonMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 凭据创建、脱敏、配置清空及跨应用/租户拒绝边界。
 * @author Rain
 * @since 6.1.0
 */
class SysApplicationApiKeyServiceImplTest {
    private final SysApplicationApiKeyMapper keys = mock(SysApplicationApiKeyMapper.class);
    private final SysApplicationMapper apps = mock(SysApplicationMapper.class);
    private final ApplicationCredentialCacheService cache = mock(ApplicationCredentialCacheService.class);
    private final dev.macula.cloud.system.service.MenuRouteTenantContext tenantContext =
        mock(dev.macula.cloud.system.service.MenuRouteTenantContext.class);
    private final SysApplicationApiKeyServiceImpl service = new SysApplicationApiKeyServiceImpl(keys, apps, cache, tenantContext, new DateTimeConverter(null));
    private SysApplication app;

    @BeforeEach
    void setup() {
        var trusted = mock(dev.macula.cloud.system.service.MenuRouteTenantContext.TrustedTenant.class);
        when(trusted.tenantId()).thenReturn(12L);
        when(tenantContext.resolve()).thenReturn(trusted);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysApplicationApiKey.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysApplication.class);
        TenantContextHolder.setCurrentTenantId(12L);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("root", "unused", "ROLE_ROOT"));
        app = new SysApplication();
        app.setId(34L);
        app.setTenantId(12L);
        when(keys.lockApplication(34L, 12L)).thenReturn(app);
        when(apps.selectOne(any(Wrapper.class))).thenReturn(app);
        when(keys.insert(any(SysApplicationApiKey.class))).thenAnswer(call -> {
            ((SysApplicationApiKey)call.getArgument(0)).setId(100L);
            return 1;
        });
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clearCurrentTenantId();
        SecurityContextHolder.clearContext();
    }

    @Test
    void forgedTenantWithSameRoleCodeCannotReachForeignKeys() {
        var users = mock(dev.macula.cloud.system.mapper.SysUserMapper.class);
        var memberships = mock(dev.macula.cloud.system.mapper.SysTenantUserMapper.class);
        var tenants = mock(dev.macula.cloud.system.mapper.SysTenantInfoMapper.class);
        var resolver = new dev.macula.cloud.system.service.MenuRouteTenantContext(users, memberships, tenants);
        var guarded = new SysApplicationApiKeyServiceImpl(keys, apps, cache, resolver, new DateTimeConverter(null));
        var user = new dev.macula.cloud.system.pojo.entity.SysUser();
        user.setId(7L);
        when(users.selectOne(any(Wrapper.class))).thenReturn(user);
        when(memberships.selectList(any(Wrapper.class)))
            .thenReturn(List.of(new dev.macula.cloud.system.pojo.entity.SysTenantUser(7L, 1L)));
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic-tenant-boundary")
            .header("alg", "none").subject("manager").claim("tenantId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        TenantContextHolder.setCurrentTenantId(12L);
        when(keys.countManagers(eq(12L), any())).thenReturn(1L);
        assertThatThrownBy(() -> guarded.secret(34L, 99L)).isInstanceOf(ResponseStatusException.class);
        verify(keys, never()).countManagers(anyLong(), any());
        verify(keys, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void multipleKeysAreServerGeneratedAndScopedToLockedApplication() {
        Set<String> generated = new HashSet<>();
        for (int index = 0; index < 10; index++) {
            var secret = service.create(34L, new ApplicationApiKeyForm());
            assertThat(secret.getApiKey()).matches("sk-[A-Za-z0-9_-]{43}");
            generated.add(secret.getApiKey());
            assertThat(secret.toString()).doesNotContain(secret.getApiKey());
        }
        assertThat(generated).hasSize(10);
        ArgumentCaptor<SysApplicationApiKey> captor = ArgumentCaptor.forClass(SysApplicationApiKey.class);
        verify(keys, org.mockito.Mockito.times(10)).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(12L);
        assertThat(captor.getValue().getApplicationId()).isEqualTo(34L);
        assertThat(captor.getValue().getDeleted()).isFalse();
        verify(cache, org.mockito.Mockito.times(10)).afterCommit(12L, 34L, null);
    }

    @Test
    void persistenceFailureDoesNotExposeGeneratedCredential() {
        when(keys.insert(any(SysApplicationApiKey.class))).thenThrow(
            new org.springframework.dao.DuplicateKeyException("sk-synthetic-database-detail"));
        assertThatThrownBy(() -> service.create(34L, new ApplicationApiKeyForm()))
            .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("sk-synthetic-database-detail")
            .hasCause(null);
        verify(cache, never()).afterCommit(anyLong(), anyLong(), any());
    }

    @Test
    void unknownOrForeignApplicationCannotCreateKey() {
        when(keys.lockApplication(34L, 12L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(34L, new ApplicationApiKeyForm()))
            .isInstanceOf(ResponseStatusException.class);
        verify(keys, never()).insert(any(SysApplicationApiKey.class));
        verify(cache, never()).afterCommit(anyLong(), anyLong(), any());
    }

    @Test
    void authenticatedUserWithoutApplicationManagementRoleIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("reader", "unused", "ROLE_READER"));
        assertThatThrownBy(() -> service.page(34L, 1, 20)).isInstanceOf(ResponseStatusException.class);
        verify(apps, never()).selectOne(any(Wrapper.class));
        when(keys.countManagers(eq(12L), any())).thenReturn(1L);
        when(keys.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>(1, 20, 0));
        assertThat(service.page(34L, 1, 20).getTotal()).isZero();
    }

    @Test
    void listIsMaskedAndSecretLookupIncludesAppAndTenantAndActiveStatus() {
        SysApplicationApiKey key = new SysApplicationApiKey();
        key.setId(99L);
        key.setApiKey("sk-abcdefghijklmnopqrstuvwxyz0123456789ABCDxyz");
        Page<SysApplicationApiKey> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(key));
        when(keys.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        var result = service.page(34L, 1, 20).getRecords().get(0);
        assertThat(result.getMaskedKey()).startsWith("sk-abcd").contains("****");
        assertThat(result.toString()).doesNotContain(key.getApiKey());
        when(keys.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> wrapper = call.getArgument(0);
            assertThat(wrapper.getSqlSegment()).contains("application_id", "tenant_id", "deleted", "id");
            return key;
        });
        assertThat(service.secret(34L, 99L).getApiKey()).isEqualTo(key.getApiKey());
    }

    /** 服务显式复用注入的存储时区转换器，Date JSON保留时间点且支持空值。 */
    @Test
    void listCreationTimeUsesDateAndSerializesWithTimezone() {
        SysApplicationApiKey key = new SysApplicationApiKey();
        key.setId(99L);
        key.setApiKey("sk-abcdefghijklmnopqrstuvwxyz0123456789ABCDxyz");
        key.setCreateTime(LocalDateTime.of(2026, 11, 5, 0, 50, 30));
        Page<SysApplicationApiKey> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(key));
        when(keys.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        var utcService = new SysApplicationApiKeyServiceImpl(keys, apps, cache, tenantContext, new DateTimeConverter("UTC"));
        var utc = utcService.page(34L, 1, 20).getRecords().get(0);
        assertThat(utc.getCreateTime().toInstant()).isEqualTo(Instant.parse("2026-11-05T00:50:30Z"));
        JsonMapper json = JsonMapper.builder().build();
        assertThat(json.writeValueAsString(utc)).contains("\"createTime\":\"2026-11-05T00:50:30.000Z\"");
        var shanghaiService = new SysApplicationApiKeyServiceImpl(keys, apps, cache, tenantContext,
            new DateTimeConverter("Asia/Shanghai"));
        var shanghai = shanghaiService.page(34L, 1, 20).getRecords().get(0);
        assertThat(shanghai.getCreateTime().toInstant()).isEqualTo(Instant.parse("2026-11-04T16:50:30Z"));
        key.setCreateTime(null);
        assertThat(shanghaiService.page(34L, 1, 20).getRecords().get(0).getCreateTime()).isNull();
    }

    @Test
    void mismatchedKeyAndInvalidPaginationAreRejected() {
        assertThatThrownBy(() -> service.secret(34L, 999L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.page(34L, 0, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.page(34L, 1, 101)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.page(34L, 1, 0)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void clearingUrlExplicitlyWritesNullAndRefreshesAfterCommit() {
        when(keys.selectOne(any(Wrapper.class))).thenReturn(new SysApplicationApiKey());
        when(keys.update(any(), any(Wrapper.class))).thenAnswer(call -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)call.getArgument(1);
            assertThat(wrapper.getSqlSet()).contains("access_path", "remark");
            assertThat(wrapper.getParamNameValuePairs()).containsValue(null);
            return 1;
        });
        service.update(34L, 99L, new ApplicationApiKeyForm());
        verify(cache).afterCommit(12L, 34L, null);
    }

    @Test
    void deletionRetainsTombstoneAndRefreshesOnlyCurrentApplication() {
        when(keys.selectOne(any(Wrapper.class))).thenReturn(new SysApplicationApiKey());
        when(keys.update(any(), any(Wrapper.class))).thenAnswer(call -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)call.getArgument(1);
            assertThat(wrapper.getSqlSet()).contains("deleted");
            assertThat(wrapper.getSqlSegment()).contains("application_id", "tenant_id");
            return 1;
        });
        service.delete(34L, 99L);
        verify(cache).afterCommit(12L, 34L, null);
    }
    /** 更新 IP 省略保留，显式 null 则写空值，不受 MyBatis 非空更新策略影响。 */
    @Test
    void omittedIpPreservesAndExplicitNullClears() {
        when(keys.selectOne(any(Wrapper.class))).thenReturn(new SysApplicationApiKey());
        when(keys.update(any(), any(Wrapper.class))).thenAnswer(call -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) call.getArgument(1);
            assertThat(update.getSqlSet()).doesNotContain("allow_ips");
            return 1;
        });
        service.update(34L, 99L, new ApplicationApiKeyForm());
        var clear = new ApplicationApiKeyForm();
        clear.setAllowIps(null);
        when(keys.update(any(), any(Wrapper.class))).thenAnswer(call -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) call.getArgument(1);
            assertThat(update.getSqlSet()).contains("allow_ips");
            assertThat(update.getParamNameValuePairs()).containsValue("");
            return 1;
        });
        service.update(34L, 99L, clear);
    }

    /** 非 HTTP 创建仍校验 IP；有效值按多行存储。 */
    @Test
    void createValidatesAndNormalizesBothRuleTypes() {
        var invalid = new ApplicationApiKeyForm();
        invalid.setAllowIps("hostname.invalid");
        assertThatThrownBy(() -> service.create(34L, invalid)).isInstanceOf(ResponseStatusException.class);
        verify(keys, never()).insert(any(SysApplicationApiKey.class));
        var valid = new ApplicationApiKeyForm();
        valid.setAccessPath(" GET:/a, POST:/b ");
        valid.setAllowIps(" 192.0.2.7\r\n2001:db8::/32 ");
        service.create(34L, valid);
        ArgumentCaptor<SysApplicationApiKey> captor = ArgumentCaptor.forClass(SysApplicationApiKey.class);
        verify(keys).insert(captor.capture());
        assertThat(captor.getValue().getAccessPath()).isEqualTo("GET:/a\nPOST:/b");
        assertThat(captor.getValue().getAllowIps()).isEqualTo("192.0.2.7\n2001:db8::/32");
    }

}
