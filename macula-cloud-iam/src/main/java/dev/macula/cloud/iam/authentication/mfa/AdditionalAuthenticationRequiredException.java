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
package dev.macula.cloud.iam.authentication.mfa;

import org.springframework.security.web.authentication.session.SessionAuthenticationException;

/**
 * 尚无挑战协议时安全阻止发布认证成果；不携带平台错误或敏感参数。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class AdditionalAuthenticationRequiredException extends SessionAuthenticationException {
    /**
     * 使用固定安全消息，避免策略实现泄漏内部状态。
     */
    public AdditionalAuthenticationRequiredException() {
        super("Additional authentication is unavailable.");
    }
}
