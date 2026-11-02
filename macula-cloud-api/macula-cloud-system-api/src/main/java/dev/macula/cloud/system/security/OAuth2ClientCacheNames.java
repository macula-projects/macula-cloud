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
 * IAM 与 System 共享的 OAuth2 Client 衍生缓存名称。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2ClientCacheNames {
    /** Token CORS 全局 Origin 索引，独立于按 Client ID 缓存的 RegisteredClient。 */
    public static final String TOKEN_ORIGIN_INDEX = "cloud:iam:oauth2:origin-index";

    private OAuth2ClientCacheNames() {
    }
}
