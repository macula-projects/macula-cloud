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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

/**
 * {@code RegisteredClient} 中由服务端持有的外部凭证策略键。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ExternalCodeClientSettings {
    public static final String CODE_TYPES = "macula.external-code.types";
    public static final String PROVIDER_BINDINGS = "macula.external-code.provider-bindings";
    public static final String ALLOWED_ORIGINS = "macula.login-code.allowed-origins";
    public static final String TENANT_ID = "macula.user-route.tenant-id";
    public static final String USER_TYPE = "macula.user-route.user-type";

    private ExternalCodeClientSettings() {
    }
}
