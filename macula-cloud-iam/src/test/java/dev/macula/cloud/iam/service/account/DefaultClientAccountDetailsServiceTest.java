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

package dev.macula.cloud.iam.service.account;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 公共账号加载必须校验可信Client快照，且不依赖当前Servlet请求。
 *
 * @author Rain
 * @since 6.1.0
 */
class DefaultClientAccountDetailsServiceTest {
    /**
     * Client租户发生变化时不得读取任何目录或返回主体。
     */
    @Test
    void rejectsChangedTenantBeforeLoadingDirectory() {
        var users = mock(SysUserService.class);
        var clients = mock(SysOAuth2ClientService.class);
        var client = client();
        client.setTenantId(2L);
        when(clients.getClientByClientId("app")).thenReturn(client);
        var directory = mock(UserAuthInfoService.class);
        var service = new DefaultClientAccountDetailsService(users, clients, directory,
                new ClientAwareAccountResolver(users, new dev.macula.cloud.system.security.UserIdentifierNormalizer()));
        assertThatThrownBy(() -> service.loadUserByClient("app", 1L, UserTypeEnum.EMPLOYEE, "alice"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users, directory);
    }

    /**
     * 扩展无结果时员工仍可使用本地目录，返回的租户来自当前可信Client。
     */
    @Test
    void loadsLocalEmployeeWithoutRequestContext() {
        var users = mock(SysUserService.class);
        var clients = mock(SysOAuth2ClientService.class);
        when(clients.getClientByClientId("app")).thenReturn(client());
        var info = new UserAuthInfo();
        info.setUsername("alice");
        info.setStatus(1);
        info.setDataScope(1);
        when(users.getUserAuthInfo(1L, "alice", null)).thenReturn(info);
        var service = new DefaultClientAccountDetailsService(users, clients, null,
                new ClientAwareAccountResolver(users, new dev.macula.cloud.system.security.UserIdentifierNormalizer()));
        assertThat((SysUserDetails) service.loadUserByClient("app", 1L, UserTypeEnum.EMPLOYEE, "alice"))
                .extracting(SysUserDetails::getUsername, SysUserDetails::getTenantId).containsExactly("alice", 1L);
        info.setStatus(0);
        assertThatThrownBy(() -> service.loadUserByClient("app", 1L, UserTypeEnum.EMPLOYEE, "alice"))
                .isInstanceOf(DisabledException.class);
    }

    /**
     * 两条既有入口有意保留不同目录优先级，抽取不能悄悄改变登录账号。
     */
    @Test
    void hostedEmployeeUsesLocalWhileExplicitClientUsesExtension() {
        var users = mock(SysUserService.class);
        var clients = mock(SysOAuth2ClientService.class);
        var directory = mock(UserAuthInfoService.class);
        var identifiers = mock(ClientAwareAccountResolver.class);
        when(clients.getClientByClientId("app")).thenReturn(client());
        var account = new dev.macula.cloud.iam.pojo.entity.SysUser();
        account.setUsername("alice");
        when(identifiers.resolveIdentifier(UserTypeEnum.EMPLOYEE, "alice")).thenReturn(account);
        when(users.getUserAuthInfo(1L, "alice", null)).thenReturn(info(10L));
        when(directory.getUserAuthInfo(UserTypeEnum.EMPLOYEE, 1L, "alice")).thenReturn(info(20L));
        var service = new DefaultClientAccountDetailsService(users, clients, directory, identifiers);
        assertThat((SysUserDetails) service.loadForHostedLogin("app", 1L, UserTypeEnum.EMPLOYEE, "alice",
                ClientAccountDetailsService.IdentifierType.USERNAME)).extracting(SysUserDetails::getUserId)
                .isEqualTo(10L);
        verifyNoInteractions(directory);
        assertThat((SysUserDetails) service.loadUserByClient("app", 1L, UserTypeEnum.EMPLOYEE, "alice"))
                .extracting(SysUserDetails::getUserId).isEqualTo(20L);
    }

    /**
     * 已核实邮箱不能因扩展目录返回另一个用户而串号。
     */
    @Test
    void rejectsEmailCandidateWhoseLoadedPrincipalChanged() {
        var users = mock(SysUserService.class);
        var clients = mock(SysOAuth2ClientService.class);
        var directory = mock(UserAuthInfoService.class);
        var identifiers = mock(ClientAwareAccountResolver.class);
        when(clients.getClientByClientId("app")).thenReturn(client());
        var account = new dev.macula.cloud.iam.pojo.entity.SysUser();
        account.setId(10L);
        account.setUsername("alice");
        when(identifiers.findByEmail(UserTypeEnum.EMPLOYEE, "alice@example.test")).thenReturn(
                java.util.Optional.of(account));
        when(directory.getUserAuthInfo(UserTypeEnum.EMPLOYEE, 1L, "alice")).thenReturn(info(20L));
        var service = new DefaultClientAccountDetailsService(users, clients, directory, identifiers);
        assertThatThrownBy(() -> service.loadForHostedLogin("app", 1L, UserTypeEnum.EMPLOYEE, "alice@example.test",
                ClientAccountDetailsService.IdentifierType.EMAIL)).isInstanceOf(BadCredentialsException.class);
    }

    /**
     * 合成已启用的目录投影。
     */
    private UserAuthInfo info(Long id) {
        var info = new UserAuthInfo();
        info.setUserId(id);
        info.setUsername("alice");
        info.setStatus(1);
        info.setDataScope(1);
        return info;
    }

    /**
     * 构造完整的合成Client路由，不读取真实配置。
     */
    private SysOAuth2Client client() {
        var client = new SysOAuth2Client();
        client.setTenantId(1L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        return client;
    }
}
