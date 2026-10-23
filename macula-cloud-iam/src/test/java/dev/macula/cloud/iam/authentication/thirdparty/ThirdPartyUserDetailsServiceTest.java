/*
 * Copyright (c) 2026 Macula
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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.support.UserAuthInfoService;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.userdetails.SysUserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证第三方回调按全局系统账号登录，不依赖 OAuth client。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyUserDetailsServiceTest {
    @Test
    void loadsGlobalAccountWithoutClient() {
        SysUserService users = mock(SysUserService.class);
        SysUser account = new SysUser();
        account.setId(1L);
        account.setUsername("alice");
        account.setStatus(1);
        when(users.findUniqueByUsername("alice")).thenReturn(account);

        var service = new ThirdPartyUserDetailsService(users);
        assertEquals("alice", service.load("alice").getUsername());
        assertTrue(service.load("alice").getAuthorities().isEmpty());
        verify(users, times(2)).findUniqueByUsername("alice");
    }

    @Test
    void rejectsMissingGlobalAccount() {
        SysUserService systemUsers = mock(SysUserService.class);
        var service = new ThirdPartyUserDetailsService(systemUsers);
        assertThrows(UsernameNotFoundException.class, () -> service.load("alice"));
    }

    @Test
    void oauthClientSelectsEachSupportedUserTypeAndTenant() {
        SysUserService systemUsers = mock(SysUserService.class);
        SysOAuth2ClientService clients = mock(SysOAuth2ClientService.class);
        UserAuthInfoService routedUsers = mock(UserAuthInfoService.class);
        UserAuthInfo user = new UserAuthInfo();
        user.setUserId(1L);
        user.setUsername("same-name");
        user.setStatus(1);
        user.setDataScope(1);
        user.setRoles(java.util.Set.of("USER"));
        when(routedUsers.getUserAuthInfo(any(), eq(99L), eq("same-name"))).thenReturn(user);
        SysUserDetailsServiceImpl service = new SysUserDetailsServiceImpl(systemUsers, clients,
            mock(ClientAwareAccountResolver.class), routedUsers);

        for (UserTypeEnum type : UserTypeEnum.values()) {
            SysOAuth2Client client = new SysOAuth2Client();
            client.setTenantId(99L);
            client.setUserType(type);
            when(clients.getClientByClientId(type.getType())).thenReturn(client);
            assertEquals("same-name",
                service.loadUserByClient(type.getType(), 99L, type, "same-name").getUsername());
            verify(routedUsers).getUserAuthInfo(type, 99L, "same-name");
        }
        verifyNoInteractions(systemUsers);
    }

    @Test
    void nonEmployeeClientNeverFallsBackToSameNamedSystemUser() {
        SysUserService systemUsers = mock(SysUserService.class);
        SysOAuth2ClientService clients = mock(SysOAuth2ClientService.class);
        UserAuthInfoService routedUsers = mock(UserAuthInfoService.class);
        SysOAuth2Client client = new SysOAuth2Client();
        client.setTenantId(99L);
        client.setUserType(UserTypeEnum.MEMBER);
        when(clients.getClientByClientId("member-client")).thenReturn(client);

        SysUserDetailsServiceImpl service = new SysUserDetailsServiceImpl(systemUsers, clients,
            mock(ClientAwareAccountResolver.class), routedUsers);

        assertThrows(UsernameNotFoundException.class,
            () -> service.loadUserByClient("member-client", 99L, UserTypeEnum.MEMBER, "same-name"));
        verify(routedUsers).getUserAuthInfo(UserTypeEnum.MEMBER, 99L, "same-name");
        verifyNoInteractions(systemUsers);
    }

    @Test
    void employeeClientMayUseTheExistingSystemUserSource() {
        SysUserService systemUsers = mock(SysUserService.class);
        SysOAuth2ClientService clients = mock(SysOAuth2ClientService.class);
        SysOAuth2Client client = new SysOAuth2Client();
        client.setTenantId(99L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        when(clients.getClientByClientId("employee-client")).thenReturn(client);
        UserAuthInfo user = new UserAuthInfo();
        user.setUserId(1L);
        user.setUsername("employee");
        user.setStatus(1);
        user.setDataScope(1);
        user.setRoles(java.util.Set.of("USER"));
        when(systemUsers.getUserAuthInfo(99L, "employee", null)).thenReturn(user);

        SysUserDetailsServiceImpl service = new SysUserDetailsServiceImpl(systemUsers, clients,
            mock(ClientAwareAccountResolver.class), null);

        assertEquals("employee",
            service.loadUserByClient("employee-client", 99L, UserTypeEnum.EMPLOYEE, "employee").getUsername());
        verify(clients, times(1)).getClientByClientId("employee-client");
    }
}
