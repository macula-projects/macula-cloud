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
 * Web/SPA 的互斥登录入口；仅历史缺省值解释为普通浏览器模式。
 * @author Rain
 * @since 6.1.0
 */
public enum IamWebLoginMode {
    BROWSER("BROWSER"), IN_APP("IN_APP");
    private final String value;
    /** 保持持久化标识与枚举实现分离。 */
    IamWebLoginMode(String value) { this.value = value; }
    /** 返回稳定接口及数据库标识。 */
    public String value() { return value; }
    /** 只接受精确协议值，不修剪或降级非法输入。 */
    public static IamWebLoginMode fromValue(String value) {
        for (var mode : values()) {
            if (mode.value.equals(value)) { return mode; }
        }
        throw new IllegalArgumentException("网页登录模式无效");
    }
    /** Web/SPA 缺省为 BROWSER，其他架构不接受此配置。未知架构必须失败关闭。 */
    public static IamWebLoginMode resolve(String applicationType, String value) {
        var type = IamApplicationType.valueOf(applicationType);
        if (type.webApplication()) {
            return value == null ? BROWSER : fromValue(value);
        }
        if (value != null) { throw new IllegalArgumentException("仅Web/SPA可配置网页登录模式"); }
        return null;
    }
    /** 基于服务端当前Client判定端内策略，不信任请求环境自报。 */
    public static boolean isInApp(String applicationType, String value) {
        return resolve(applicationType, value) == IN_APP;
    }
}
