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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.macula.cloud.system.enums.PasswordComplexityRule;
import dev.macula.cloud.system.form.PasswordPolicyConfigForm;
import dev.macula.cloud.system.form.SecurityBasicConfigForm;
import dev.macula.cloud.system.form.SecurityDefensePolicyConfigForm;
import dev.macula.cloud.system.mapper.SysDictItemMapper;
import dev.macula.cloud.system.pojo.entity.SysDictItem;
import dev.macula.cloud.system.service.SecurityPolicyService;
import dev.macula.cloud.system.vo.PasswordPolicyConfigResult;
import dev.macula.cloud.system.vo.SecurityBasicConfigResult;
import dev.macula.cloud.system.vo.SecurityDefensePolicyConfigResult;
import dev.macula.cloud.system.vo.SecurityPolicyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用租户字典项逐 key 持久化安全策略，租户范围由 MyBatis 租户插件限定。
 *
 * @author Rain
 * @since 2026-10-16
 */
@Service
@RequiredArgsConstructor
public class SecurityPolicyServiceImpl implements SecurityPolicyService {
    static final String TYPE_CODE = "system_security_policy";

    private final SysDictItemMapper mapper;

    @Override
    public SecurityPolicyResult get() {
        Map<String, String> values = loadValues();
        return new SecurityPolicyResult(toBasic(values), toDefense(values), toPassword(values));
    }

    @Override
    @Transactional
    public SecurityBasicConfigResult saveBasic(SecurityBasicConfigForm form) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("sessionValidTime", integer(form.getSessionValidTime()));
        values.put("verifyCodeValidTime", integer(form.getVerifyCodeValidTime()));
        values.put("rememberMeValidTime", integer(form.getRememberMeValidTime()));
        values.put("sessionMaximum", integer(form.getSessionMaximum()));
        saveValues(values, 0);
        return toBasic(loadValues());
    }

    @Override
    @Transactional
    public SecurityDefensePolicyConfigResult saveDefense(SecurityDefensePolicyConfigForm form) {
        String csp = string(form.getContentSecurityPolicy());
        if (csp.indexOf('\r') >= 0 || csp.indexOf('\n') >= 0) {
            bad("内容安全策略不可包含换行");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("loginFailureDuration", integer(form.getLoginFailureDuration()));
        values.put("loginFailureCount", integer(form.getLoginFailureCount()));
        values.put("autoUnlockTime", integer(form.getAutoUnlockTime()));
        values.put("contentSecurityPolicy", csp);
        saveValues(values, 10);
        return toDefense(loadValues());
    }

    @Override
    @Transactional
    public PasswordPolicyConfigResult savePassword(PasswordPolicyConfigForm form) {
        validatePassword(form);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("passwordBiggestLength", integer(form.getPasswordBiggestLength()));
        values.put("passwordLeastLength", integer(form.getPasswordLeastLength()));
        values.put("passwordComplexity", integer(form.getPasswordComplexity().code()));
        values.put("customWeakPassword", string(form.getCustomWeakPassword()));
        values.put("weakPasswordCheck", bool(form.getWeakPasswordCheck()));
        values.put("includeAccountCheck", bool(form.getIncludeAccountCheck()));
        values.put("notSameChars", integer(form.getNotSameChars()));
        values.put("historyPasswordCheck", bool(form.getHistoryPasswordCheck()));
        values.put("historyPasswordCheckCount", integer(form.getHistoryPasswordCheckCount()));
        values.put("illegalSequenceCheck", bool(form.getIllegalSequenceCheck()));
        values.put("passwordValidDays", integer(form.getPasswordValidDays()));
        values.put("passwordValidWarnBeforeDays", integer(form.getPasswordValidWarnBeforeDays()));
        saveValues(values, 20);
        return toPassword(loadValues());
    }

    private Map<String, String> loadValues() {
        List<SysDictItem> items = mapper.selectList(new QueryWrapper<SysDictItem>().eq("type_code", TYPE_CODE));
        Map<String, String> values = new LinkedHashMap<>();
        for (SysDictItem item : items) {
            if (values.putIfAbsent(item.getName(), item.getValue()) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "安全策略存在重复配置键：" + item.getName());
            }
        }
        return values;
    }

    private void saveValues(LinkedHashMap<String, String> values, int sortOffset) {
        Map<String, SysDictItem> existing = new LinkedHashMap<>();
        for (SysDictItem item : mapper.selectList(new QueryWrapper<SysDictItem>()
            .eq("type_code", TYPE_CODE).in("name", values.keySet()))) {
            if (existing.putIfAbsent(item.getName(), item) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "安全策略存在重复配置键：" + item.getName());
            }
        }
        int sort = sortOffset;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SysDictItem item = existing.get(entry.getKey());
            if (item == null) {
                item = new SysDictItem().setTypeCode(TYPE_CODE).setName(entry.getKey());
                item.setStatus(1);
                item.setDefaulted(0);
                item.setRemark("系统安全策略");
                item.setSort(++sort);
                item.setValue(entry.getValue());
                mapper.insert(item);
            } else {
                item.setValue(entry.getValue());
                item.setSort(++sort);
                item.setStatus(1);
                mapper.updateById(item);
            }
        }
    }

    private SecurityBasicConfigResult toBasic(Map<String, String> values) {
        return new SecurityBasicConfigResult(
            intValue(values, "sessionValidTime", 1800),
            intValue(values, "verifyCodeValidTime", 300),
            intValue(values, "rememberMeValidTime", 0),
            intValue(values, "sessionMaximum", 1));
    }

    private SecurityDefensePolicyConfigResult toDefense(Map<String, String> values) {
        return new SecurityDefensePolicyConfigResult(
            intValue(values, "loginFailureDuration", 15),
            intValue(values, "loginFailureCount", 5),
            intValue(values, "autoUnlockTime", 30),
            values.getOrDefault("contentSecurityPolicy", ""));
    }

    private PasswordPolicyConfigResult toPassword(Map<String, String> values) {
        return new PasswordPolicyConfigResult(
            intValue(values, "passwordBiggestLength", 128),
            intValue(values, "passwordLeastLength", 8),
            complexity(values, "passwordComplexity", PasswordComplexityRule.MUST_NUMBERS_AND_CAPITAL_LETTERS),
            values.getOrDefault("customWeakPassword", ""),
            booleanValue(values, "weakPasswordCheck", true),
            booleanValue(values, "includeAccountCheck", true),
            intValue(values, "notSameChars", 3),
            booleanValue(values, "historyPasswordCheck", false),
            intValue(values, "historyPasswordCheckCount", 0),
            booleanValue(values, "illegalSequenceCheck", true),
            intValue(values, "passwordValidDays", 0),
            intValue(values, "passwordValidWarnBeforeDays", 0));
    }

    private static void validatePassword(PasswordPolicyConfigForm form) {
        if (form.getPasswordLeastLength() > form.getPasswordBiggestLength()) {
            bad("密码最小长度不得大于最大长度");
        }
        if (Boolean.TRUE.equals(form.getHistoryPasswordCheck()) && form.getHistoryPasswordCheckCount() == 0 ||
            Boolean.FALSE.equals(form.getHistoryPasswordCheck()) && form.getHistoryPasswordCheckCount() != 0) {
            bad("历史密码检查次数与开关不匹配");
        }
        if (form.getPasswordValidDays() == 0 && form.getPasswordValidWarnBeforeDays() != 0 ||
            form.getPasswordValidDays() > 0 &&
                form.getPasswordValidWarnBeforeDays() >= form.getPasswordValidDays()) {
            bad("密码有效期提醒天数无效");
        }
        if (string(form.getCustomWeakPassword()).indexOf('\0') >= 0) {
            bad("自定义弱密码不可包含空字符");
        }
    }

    private static int intValue(Map<String, String> values, String key, int defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw corrupt(key);
        }
    }

    private static boolean booleanValue(Map<String, String> values, String key, boolean defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            throw corrupt(key);
        }
        return Boolean.parseBoolean(value);
    }

    private static PasswordComplexityRule complexity(Map<String, String> values, String key,
        PasswordComplexityRule defaultValue) {
        if (!values.containsKey(key)) {
            return defaultValue;
        }
        try {
            return PasswordComplexityRule.fromCode(intValue(values, key, defaultValue.code()));
        } catch (IllegalArgumentException exception) {
            throw corrupt(key);
        }
    }

    private static String integer(int value) {
        return Integer.toString(value);
    }

    private static String bool(boolean value) {
        return Boolean.toString(value);
    }

    private static String string(String value) {
        return value == null ? "" : value;
    }

    private static ResponseStatusException corrupt(String key) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "安全策略配置值格式错误：" + key);
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
