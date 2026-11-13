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
package dev.macula.cloud.system.protocol;

/**
 * 已实现协议的策略端口；类型解析、校验和凭据需求由协议自身提供。
 * 用户载荷不能注册Java类，也不能选择未经实现的协议。
 * @param <T> 协议专有的非秘密设置类型
 * @author Rain
 * @since 6.1.0
 */
public interface ApplicationProtocolDefinition<T> {
    /** 返回稳定协议代码。 */
    String protocol();

    /** 返回目录中的协议名称。 */
    String label();

    /** 返回此策略支持的持久化版本。 */
    int schemaVersion();

    /** 返回类型边界，通用注册表不推断具体协议类。 */
    Class<T> settingsType();

    /** 严格解析有界非秘密JSON；未知字段、重复字段和错误结构均拒绝。 */
    T parse(String configuration);

    /** 输出可再次严格解析的非秘密配置。 */
    String serialize(T settings);

    /** 校验用途、应用架构和启用条件，不以草稿为理由放过未知类型。 */
    void validate(T settings, String applicationType, String purpose, boolean enabled);

    /** 判断本协议设置是否要求共享密钥，不读取或返回凭据。 */
    boolean requiresSecret(T settings);
}
