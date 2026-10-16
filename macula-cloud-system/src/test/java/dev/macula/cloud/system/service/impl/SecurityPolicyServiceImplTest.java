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
package dev.macula.cloud.system.service.impl;

import dev.macula.cloud.system.enums.PasswordComplexityRule;
import dev.macula.cloud.system.form.PasswordPolicyConfigForm;
import dev.macula.cloud.system.form.SecurityBasicConfigForm;
import dev.macula.cloud.system.mapper.SysDictItemMapper;
import dev.macula.cloud.system.pojo.entity.SysDictItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户安全策略逐 key 持久化、默认值和组合校验测试。
 *
 * @author Rain
 * @since 2026-10-16
 */
class SecurityPolicyServiceImplTest {
    private final SysDictItemMapper mapper = mock(SysDictItemMapper.class);
    private final SecurityPolicyServiceImpl service = new SecurityPolicyServiceImpl(mapper);

    @Test
    void returnsDocumentedDefaultsWhenTenantHasNoValues() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertEquals(1800, service.get().getBasic().getSessionValidTime());
        assertEquals(15, service.get().getDefense().getLoginFailureDuration());
        assertEquals(PasswordComplexityRule.MUST_NUMBERS_AND_CAPITAL_LETTERS,
            service.get().getPassword().getPasswordComplexity());
    }

    @Test
    void savesBasicAsFourNameValueRows() {
        when(mapper.selectList(any())).thenReturn(List.of());
        SecurityBasicConfigForm form = new SecurityBasicConfigForm();
        form.setSessionValidTime(3600);
        form.setVerifyCodeValidTime(180);
        form.setRememberMeValidTime(86400);
        form.setSessionMaximum(3);

        service.saveBasic(form);

        ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
        verify(mapper, times(4)).insert(captor.capture());
        assertEquals(List.of("sessionValidTime", "verifyCodeValidTime", "rememberMeValidTime", "sessionMaximum"),
            captor.getAllValues().stream().map(SysDictItem::getName).toList());
        assertEquals(List.of("3600", "180", "86400", "3"),
            captor.getAllValues().stream().map(SysDictItem::getValue).toList());
        captor.getAllValues().forEach(item -> assertEquals(SecurityPolicyServiceImpl.TYPE_CODE, item.getTypeCode()));
    }

    @Test
    void rejectsDuplicateAndMalformedStoredValues() {
        SysDictItem first = item("sessionValidTime", "1800");
        SysDictItem duplicate = item("sessionValidTime", "3600");
        when(mapper.selectList(any())).thenReturn(List.of(first, duplicate));
        assertThrows(ResponseStatusException.class, service::get);

        when(mapper.selectList(any())).thenReturn(List.of(item("weakPasswordCheck", "yes")));
        assertThrows(ResponseStatusException.class, service::get);
    }

    @Test
    void rejectsInvalidPasswordCombinations() {
        PasswordPolicyConfigForm invalidLength = validPassword();
        invalidLength.setPasswordLeastLength(20);
        invalidLength.setPasswordBiggestLength(8);
        assertThrows(ResponseStatusException.class, () -> service.savePassword(invalidLength));

        PasswordPolicyConfigForm form = validPassword();
        form.setHistoryPasswordCheck(true);
        form.setHistoryPasswordCheckCount(0);
        PasswordPolicyConfigForm invalidHistory = form;
        assertThrows(ResponseStatusException.class, () -> service.savePassword(invalidHistory));

        form = validPassword();
        form.setPasswordValidDays(30);
        form.setPasswordValidWarnBeforeDays(30);
        PasswordPolicyConfigForm invalidWarning = form;
        assertThrows(ResponseStatusException.class, () -> service.savePassword(invalidWarning));
    }

    private static PasswordPolicyConfigForm validPassword() {
        PasswordPolicyConfigForm form = new PasswordPolicyConfigForm();
        form.setPasswordBiggestLength(128);
        form.setPasswordLeastLength(8);
        form.setPasswordComplexity(PasswordComplexityRule.MUST_NUMBERS_AND_CAPITAL_LETTERS);
        form.setCustomWeakPassword("");
        form.setWeakPasswordCheck(true);
        form.setIncludeAccountCheck(true);
        form.setNotSameChars(3);
        form.setHistoryPasswordCheck(false);
        form.setHistoryPasswordCheckCount(0);
        form.setIllegalSequenceCheck(true);
        form.setPasswordValidDays(0);
        form.setPasswordValidWarnBeforeDays(0);
        return form;
    }

    private static SysDictItem item(String name, String value) {
        return new SysDictItem().setTypeCode(SecurityPolicyServiceImpl.TYPE_CODE).setName(name).setValue(value);
    }
}
