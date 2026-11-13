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

import java.util.List;
import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.system.enums.IdentityBindingMapping;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证映射仅查询所选字段，不能把手机号证明改成用户名证明。
 *
 * @author Rain
 * @since 6.1.0
 */
class IdentityBindingMatcherTest {
    /**
     * 手机号证明即使携带映射也不能绕过本地手机号匹配。
     */
    @Test
    void rejectsPhoneProofToUsernameAndMissingAttributesBeforeQuery() {
        var users = mock(SysUserMapper.class);
        var matcher = new IdentityBindingMatcher(users);
        var proof = new VerifiedExternalIdentity(1L, "WECHAT", "WECHAT_MINIAPP", 0L, "WECHAT_MINIAPP_V1",
                "WECHAT_MINIAPP_PHONE_CODE", "MOBILE_PROOF", "13800000001", Map.of("verified_mobile", "13800000001"));
        assertTrue(matcher.candidates(IdentityBindingMapping.of("mobile", "username"), proof, false).isEmpty());
        assertTrue(matcher.candidates(IdentityBindingMapping.of("email", "email"), proof, false).isEmpty());
        verifyNoInteractions(users);
    }

    /**
     * 企业邮箱只按所选邮箱字段查询，不能回退用户名或手机号。
     */
    @Test
    void normalizesAuthoritativeEmailForTheSelectedFieldOnly() {
        var users = mock(SysUserMapper.class);
        var identity = new VerifiedExternalIdentity(1L, "FEISHU", "FEISHU_WEB", 0L, "FEISHU_OAUTH_V3",
                "BROWSER", "OPEN_ID", "fixture-open-id", Map.of("email", "  Person@Example.Test  "));
        new IdentityBindingMatcher(users).candidates(IdentityBindingMapping.of("email", "email"), identity, false);
        verify(users).findIdentityBindingCandidates(eq("email"), eq("person@example.test"), any(), any(), eq(false));
    }

    /**
     * 单项命中可用，同用户双命中可用；不同用户或单项多账号均不能自动绑定。
     */
    @Test
    void requiresAllMatchingRulesToAgreeOnOneAccount() {
        var users = mock(SysUserMapper.class);
        var first = new SysUser();
        first.setId(1L);
        var second = new SysUser();
        second.setId(2L);
        var identity = new VerifiedExternalIdentity(1L, "FEISHU", "FEISHU_WEB", 0L, "FEISHU_OAUTH_V3",
                "BROWSER", "OPEN_ID", "fixture", Map.of("mobile", "13800000001", "email", "person@example.test"));
        var rules = List.of(IdentityBindingMapping.of("mobile", "mobile"),
                IdentityBindingMapping.of("email", "email"));
        var matcher = new IdentityBindingMatcher(users);
        Mockito.when(users.findIdentityBindingCandidates(eq("mobile"), any(), any(), any(), eq(false)))
                .thenReturn(List.of(first));
        Mockito.when(users.findIdentityBindingCandidates(eq("email"), any(), any(), any(), eq(false)))
                .thenReturn(List.of());
        Assertions.assertEquals(List.of(first),
                matcher.candidates(rules, identity, false));
        Mockito.when(users.findIdentityBindingCandidates(eq("email"), any(), any(), any(), eq(false)))
                .thenReturn(List.of(first));
        Assertions.assertEquals(List.of(first),
                matcher.candidates(rules, identity, false));
        Mockito.when(users.findIdentityBindingCandidates(eq("email"), any(), any(), any(), eq(false)))
                .thenReturn(List.of(second));
        assertTrue(matcher.candidates(rules, identity, false).isEmpty());
        Mockito.when(users.findIdentityBindingCandidates(eq("email"), any(), any(), any(), eq(false)))
                .thenReturn(List.of(first, second));
        assertTrue(matcher.candidates(rules, identity, false).isEmpty());
    }

    /**
     * 锁内使用同一完整集合，不因首次候选唯一而跳过另一条冲突规则。
     */
    @Test
    void repeatsEveryRuleUnderTransactionLocks() {
        var users = mock(SysUserMapper.class);
        var first = new SysUser();
        first.setId(1L);
        var second = new SysUser();
        second.setId(2L);
        Mockito.when(users.identityBindingIsolation()).thenReturn("REPEATABLE-READ");
        Mockito.when(users.findIdentityBindingCandidates(eq("mobile"), any(), any(), any(), eq(true)))
                .thenReturn(List.of(first));
        Mockito.when(users.findIdentityBindingCandidates(eq("email"), any(), any(), any(), eq(true)))
                .thenReturn(List.of(second));
        var identity = new VerifiedExternalIdentity(1L, "FEISHU", "FEISHU_WEB", 0L, "FEISHU_OAUTH_V3",
                "BROWSER", "OPEN_ID", "fixture", Map.of("mobile", "13800000001", "email", "person@example.test"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertTrue(new IdentityBindingMatcher(users).candidates(
                    List.of(IdentityBindingMapping.of("mobile", "mobile"),
                            IdentityBindingMapping.of("email", "email")), identity, true).isEmpty());
            verify(users).findIdentityBindingCandidates(eq("mobile"), any(), any(), any(), eq(true));
            verify(users).findIdentityBindingCandidates(eq("email"), any(), any(), any(), eq(true));
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

}
