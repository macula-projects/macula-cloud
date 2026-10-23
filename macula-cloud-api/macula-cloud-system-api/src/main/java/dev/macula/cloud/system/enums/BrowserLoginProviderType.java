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
package dev.macula.cloud.system.enums;

import java.util.Arrays;

/**
 * OAuth Client 可授权使用的浏览器第三方登录提供商。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum BrowserLoginProviderType {
    GITHUB,
    WECOM,
    FEISHU,
    DINGTALK,
    WECHAT;

    /**
     * 解析持久化或协议中的稳定枚举值。
     *
     * @param value 稳定枚举值
     * @return 浏览器登录提供商
     * @throws IllegalArgumentException 不支持该值时抛出
     */
    public static BrowserLoginProviderType fromValue(String value) {
        return Arrays.stream(values()).filter(type -> type.name().equals(value))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的网页登录提供商"));
    }
}
