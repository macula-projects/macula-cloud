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
 * 单协议版本化配置；应用凭据和身份源不在此复制。
 * @param protocol 稳定协议代码
 * @param schemaVersion 配置结构版本
 * @param enabled 是否用于认证
 * @param settings 类型化载荷
 * @author Rain
 * @since 6.1.0
 */
public record ApplicationProtocolConfiguration(String protocol, int schemaVersion, boolean enabled,
    ApplicationProtocolSettings settings) { }
