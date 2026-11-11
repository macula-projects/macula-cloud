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
package dev.macula.cloud.iam.service.identity;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 新绑定通过稳定用户ID解析，缺失/禁用及路由不匹配时失败关闭。
 *
 * @author Rain
 * @since 6.1.0
 */
class RegistrationIdentityBindingServiceTest {
    /**
     * 账户改名不需要重写外部身份绑定。
     */
    @Test
    void loadsCurrentUsernameFromStableUserId() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("renamed");
        var service = IdentitySourceTestFixtures.registrationService(user);
        var identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "external-member").identity();
        assertEquals("renamed",
                service.findBound("app", 1L, UserTypeEnum.EMPLOYEE, identity).orElseThrow().getUsername());
    }

    /**
     * OIDC的SDK许可必须经过真实绑定服务，不能仅在Token层放行。
     */
    @Test
    void oidcSdkLoadsBoundUserThroughCurrentProtocol() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("oidc-member");
        var service = IdentitySourceTestFixtures.registrationService(user, "OIDC");
        var identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "external-member").identity();
        assertEquals("oidc-member", service.findBound("app", 1L, UserTypeEnum.EMPLOYEE, identity)
                .orElseThrow().getUsername());
        assertThrows(BadCredentialsException.class,
                () -> service.findBound("app", 2L, UserTypeEnum.EMPLOYEE, identity));
    }

    /**
     * 孤儿绑定不能按用户名或旧表查另一个账号。
     */
    @Test
    void missingLocalAccountIsRejected() {
        var service = IdentitySourceTestFixtures.registrationService(null);
        var identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "external-member").identity();
        assertThrows(BadCredentialsException.class,
                () -> service.findBound("app", 1L, UserTypeEnum.EMPLOYEE, identity));
    }

    /**
     * 已有绑定也不能登录被禁用账号。
     */
    @Test
    void disabledBoundAccountIsRejected() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("disabled");
        var service = IdentitySourceTestFixtures.registrationService(user);
        user.setStatus(0);
        var identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "external-member").identity();
        assertThrows(BadCredentialsException.class,
                () -> service.findBound("app", 1L, UserTypeEnum.EMPLOYEE, identity));
    }

    /**
     * 调用方不能替换Client快照内的租户或用户目录。
     */
    @Test
    void clientRouteMismatchIsRejected() {
        var service = IdentitySourceTestFixtures.registrationService(null);
        var identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "external-member").identity();
        assertThrows(BadCredentialsException.class,
                () -> service.findBound("app", 2L, UserTypeEnum.EMPLOYEE, identity));
        assertThrows(BadCredentialsException.class, () -> service.findBound("app", 1L, UserTypeEnum.MEMBER, identity));
    }

    /**
     * 核验结果类别必须匹配来源真实能力，不允许伪造号码主体。
     */
    @Test
    void verifiedResultRejectsWrongKindOrSourceCapability() {
        assertThrows(IllegalArgumentException.class, () -> new VerifiedExternalIdentity(1L, "WECOM", "WECOM_SDK", 0L,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow("WECOM_SDK"),
                "WECOM_APP_CODE", "MOBILE_PROOF", "13800000000", java.util.Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new VerifiedExternalIdentity(1L, "WECHAT", "WECHAT_WEBSITE", 0L,
                        dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow(
                                "WECHAT_WEBSITE"), "WECHAT_MINIAPP_PHONE_CODE", "MOBILE_PROOF", "13800000000",
                        java.util.Map.of()));
    }

    /**
     * Client行锁取得的新版本与原pending不符时，拒绝后续来源/账号写入。
     */
    @Test
    void rejectsStaleInAppVersionInsideBindingLock() {
        var service = org.mockito.Mockito.spy(IdentitySourceTestFixtures.registrationService(null));
        var client = new dev.macula.cloud.iam.pojo.entity.SysOAuth2Client();
        client.setApplicationType("IN_APP");
        client.setAuthorizationVersion(8L);
        var context = IdentitySourceTestFixtures.context("WECOM", "WEBVIEW", "app", java.time.Instant.now(), "/");
        var identity = IdentitySourceTestFixtures.browserIdentity(context, "fixture-member");
        org.mockito.Mockito.doReturn(client).when(service)
                .requireClient("app", identity, true, 1L, UserTypeEnum.EMPLOYEE);
        assertThrows(BadCredentialsException.class,
                () -> service.bind("app", 1L, UserTypeEnum.EMPLOYEE, identity, 9L, false, null, 7L));
        var sources = org.springframework.test.util.ReflectionTestUtils.getField(service, "sources");
        org.mockito.Mockito.verifyNoInteractions(sources);
    }

}
