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
 * 接入应用的可配置草稿与允许认证状态。
 * @author Rain
 * @since 6.1.0
 */
public enum OAuth2ClientConfigurationStatus {
    DRAFT("DRAFT"), ENABLED("ENABLED");
    private final String value;
    /** 保存独立于Java名称的数据库状态值。 */
    OAuth2ClientConfigurationStatus(String value) { this.value = value; }
    /** 返回明确的持久化状态值。 */
    public String value() { return value; }
    /** 仅接受明确状态，未知或缺失值不能启用认证。 */
    public static OAuth2ClientConfigurationStatus fromValue(String value) {
        for (OAuth2ClientConfigurationStatus status : values()) {
            if (status.value.equals(value)) { return status; }
        }
        throw new IllegalArgumentException("请选择配置状态");
    }
}
