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

/**
 * MFA策略的最小可信投影；不包含密码、平台凭据、原始请求或自报声明。
 * clientId为空表示独立门户尚无可信应用上下文。
 *
 * @author Rain
 * @since 6.1.0
 */
public record AuthenticationCompletionContext(String clientId, Long tenantId, Long userId,
                                              String entryPoint, AuthenticationMethod method, Long registrationId,
                                              AuthenticationCheckpoint checkpoint) {
}
