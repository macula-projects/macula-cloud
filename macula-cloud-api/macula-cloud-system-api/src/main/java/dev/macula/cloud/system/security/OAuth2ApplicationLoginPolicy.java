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
package dev.macula.cloud.system.security;

import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.OAuth2ClientApplicationType;
import java.util.HashSet;
import java.util.Map;

/**
 * 应用登录入口的共享类型、数量与注册域约束，不访问数据库或厂商。
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2ApplicationLoginPolicy {
    /** 稳定写入标记仅确认有限转换，不是可信来源证明。 */
    public static final String CONVERT_TO_IN_APP = "convertToInApp";
    /** 只提供无状态的领域校验。 */
    private OAuth2ApplicationLoginPolicy() { }
    /**
     * 校验完整来源引用；小程序双Code属于同一注册，不能按Code数量误判为两应用。
     * @param type 已确认应用类型
     * @param platform 小程序平台，其他类型不使用
     * @param browser 网页能力到固定注册的映射
     * @param external Code类型到固定注册的映射
     * @param enabled 启用端内应用必须恰有一个能力
     */
    public static void validate(OAuth2ClientApplicationType type, String platform, Map<String, Long> browser,
        Map<String, Long> external, boolean enabled) {
        if (type == null || browser == null || external == null) { throw new IllegalArgumentException("登录来源配置无效"); }
        var registrations = new HashSet<Long>();
        browser.forEach((key, id) -> {
            if (!type.supportsBrowserCapability(IdentityLoginCapability.fromValue(key))) {
                throw new IllegalArgumentException("网页登录能力与应用类型不兼容");
            }
            requireRegistration(id);
            registrations.add(id);
        });
        external.forEach((key, id) -> {
            if (!type.supportsExternalCode(ExternalCodeType.fromValue(key), platform)) {
                throw new IllegalArgumentException("平台Code能力与应用类型不兼容");
            }
            requireRegistration(id);
            registrations.add(id);
        });
        if ((type == OAuth2ClientApplicationType.NATIVE || type == OAuth2ClientApplicationType.MINIAPP
            || type == OAuth2ClientApplicationType.IN_APP) && registrations.size() > 1) {
            throw new IllegalArgumentException("当前应用只能选择一个身份源");
        }
        if (type == OAuth2ClientApplicationType.NATIVE && external.size() > 1) {
            throw new IllegalArgumentException("移动客户端只能选择一个SDK或AppLink能力");
        }
        int capabilities = browser.size() + external.size();
        if (type == OAuth2ClientApplicationType.IN_APP && (capabilities > 1 || enabled && capabilities != 1)) {
            throw new IllegalArgumentException("端内应用必须选择一个端内登录能力");
        }
    }
    /** 来源必须为明确正ID，不从0、默认值或请求厂商名择源。 */
    private static void requireRegistration(Long id) {
        if (id == null || id <= 0) { throw new IllegalArgumentException("请选择具体身份源"); }
    }
}
