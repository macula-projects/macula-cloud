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

/**
 * 两端共享的应用配置版本；只隔离配置视图，不影响授权/Token/Session存储。
 * @author Rain
 * @since 6.1.0
 */
public final class IamApplicationCacheNames {
    public static final String CONFIGURATION_VERSION = "v7-iam-application";
    /** 仅提供稳定常量。 */
    private IamApplicationCacheNames() { }
}
