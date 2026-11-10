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

/**
 * 接入应用的协议语义，持久化值与枚举名称分开定义。
 * @author Rain
 * @since 6.1.0
 */
public enum OAuth2ProtocolMode {
    OIDC("OIDC"), OAUTH2_1("OAUTH2_1");
    private final String value;
    /** 保存稳定协议值。 */
    OAuth2ProtocolMode(String value) { this.value = value; }
    /** 返回持久化协议值。 */
    public String value() { return value; }
    /** 解析显式协议；缺失或未知值不能降级成历史模式。 */
    public static OAuth2ProtocolMode fromValue(String value) {
        for (OAuth2ProtocolMode mode : values()) {
            if (mode.value.equals(value)) { return mode; }
        }
        throw new IllegalArgumentException("请选择接入协议");
    }
}
