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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 显式策略注册表；只调度启动时提供的实现，不绑定某个协议的Settings。
 * 未实现的协议没有目录项，解析失败不能降级到其他协议。
 * @author Rain
 * @since 6.1.0
 */
public final class IamApplicationProtocolRegistry {
    public static final int MAX_CONFIG_BYTES = 32 * 1024;
    private final Map<String, ApplicationProtocolDefinition<?>> definitions;

    /** 注册表由服务器组装；重复代码、空实现和无效版本均在启动时拒绝。 */
    public IamApplicationProtocolRegistry(Collection<? extends ApplicationProtocolDefinition<?>> supplied) {
        Objects.requireNonNull(supplied, "协议实现集合不能为空");
        Map<String, ApplicationProtocolDefinition<?>> entries = new LinkedHashMap<>();
        for (ApplicationProtocolDefinition<?> definition : supplied) {
            if (definition == null || definition.protocol() == null || definition.protocol().isBlank()
                || definition.schemaVersion() < 1 || definition.settingsType() == null
                || entries.putIfAbsent(definition.protocol(), definition) != null) {
                throw new IllegalArgumentException("协议实现注册无效或重复");
            }
        }
        definitions = Collections.unmodifiableMap(entries);
    }

    /** 返回已实现策略的稳定目录；不能从请求中追加策略。 */
    public List<ApplicationProtocolDefinition<?>> definitions() {
        return List.copyOf(definitions.values());
    }

    /** 未知协议一律失败，不按前缀、默认类型或Java类名猜测。 */
    public ApplicationProtocolDefinition<?> require(String protocol) {
        ApplicationProtocolDefinition<?> definition = definitions.get(protocol);
        if (definition == null) {
            throw new IllegalArgumentException("协议尚未实现");
        }
        return definition;
    }

    /** 从持久化构造登记；未分配槽只投影空协议，不解释历史配置。 */
    public IamApplicationProtocolRegistration<?> read(String protocol, int schemaVersion, boolean enabled,
        String identifier, Instant issuedAt, String configuration, String applicationType, String purpose) {
        requireBounded(configuration);
        if (protocol == null) {
            return new IamApplicationProtocolRegistration<>(null, schemaVersion, enabled, identifier, issuedAt, null);
        }
        return parse(require(protocol), schemaVersion, enabled, identifier, issuedAt, configuration, applicationType, purpose);
    }

    /** 写入前再次执行类型及业务校验，禁止对象绕过严格JSON读取边界。 */
    public String serialize(IamApplicationProtocolRegistration<?> registration, String applicationType, String purpose) {
        if (!registration.assigned()) {
            throw new IllegalArgumentException("未分配槽不能作为运行协议写入");
        }
        return serialize(require(registration.protocol()), registration, applicationType, purpose);
    }

    /** 凭据需求由具体策略判断，未分配槽不能用于认证。 */
    public boolean requiresSecret(IamApplicationProtocolRegistration<?> registration) {
        if (!registration.assigned()) {
            throw new IllegalArgumentException("未分配槽不能用于认证");
        }
        return requiresSecret(require(registration.protocol()), registration);
    }

    /** 在任何协议解析之前限制UTF-8字节数，不能仅限制字符数量。 */
    private static void requireBounded(String configuration) {
        if (configuration == null || configuration.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("协议配置载荷无效或超出限制");
        }
    }

    /** 捕获策略泛型后解析，不在通用层列举协议类型。 */
    private static <T> IamApplicationProtocolRegistration<T> parse(ApplicationProtocolDefinition<T> definition,
        int version, boolean enabled, String identifier, Instant issuedAt, String configuration,
        String applicationType, String purpose) {
        requireVersion(definition, version);
        T settings = definition.parse(configuration);
        definition.validate(settings, applicationType, purpose, enabled);
        return new IamApplicationProtocolRegistration<>(definition.protocol(), version, enabled, identifier, issuedAt, settings);
    }

    /** 类型转换只按已注册Class执行，不能使用来自JSON的类型标识。 */
    private static <T> String serialize(ApplicationProtocolDefinition<T> definition,
        IamApplicationProtocolRegistration<?> registration, String applicationType, String purpose) {
        requireVersion(definition, registration.schemaVersion());
        T settings = definition.settingsType().cast(registration.settings());
        definition.validate(settings, applicationType, purpose, registration.enabled());
        String configuration = definition.serialize(settings);
        requireBounded(configuration);
        return configuration;
    }

    /** 凭据判断也必须检查登记版本与类型。 */
    private static <T> boolean requiresSecret(ApplicationProtocolDefinition<T> definition,
        IamApplicationProtocolRegistration<?> registration) {
        requireVersion(definition, registration.schemaVersion());
        return definition.requiresSecret(definition.settingsType().cast(registration.settings()));
    }

    /** 不自动升级或降级未知结构版本。 */
    private static void requireVersion(ApplicationProtocolDefinition<?> definition, int version) {
        if (definition.schemaVersion() != version) {
            throw new IllegalArgumentException("协议配置版本不受支持");
        }
    }
}
