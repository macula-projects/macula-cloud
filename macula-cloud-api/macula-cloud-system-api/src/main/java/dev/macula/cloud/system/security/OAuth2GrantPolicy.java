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
import java.util.Set;

/**
 * 当前授权执行与历史授权读取的边界；退役标识只用于识别，不授予执行许可。
 * @author Macula
 * @since 6.1.0
 */
public final class OAuth2GrantPolicy {
    private static final Set<String> SUPPORTED = Set.of("authorization_code", "refresh_token",
        "client_credentials", ExternalCodeType.GRANT_TYPE);
    private static final Set<String> RETIRED = Set.of("password", "sms", "email");

    /** 仅提供纯规则。 */
    private OAuth2GrantPolicy() { }

    /** 返回当前允许配置和执行的授权标识，不包含历史兼容值。 */
    public static Set<String> supported() { return SUPPORTED; }

    /** 判断原授权是否已退役，用于阻止旧 Refresh 继续发布结果。 */
    public static boolean retired(String value) { return RETIRED.contains(value); }

    /** 新写入必须整改历史授权；读取旧值不因此获得新签发许可。 */
    public static void requireSupported(Set<String> grants) {
        if (!SUPPORTED.containsAll(grants)) {
            throw new IllegalArgumentException("授权方式已退役或不受支持，请显式整改配置");
        }
    }
}
