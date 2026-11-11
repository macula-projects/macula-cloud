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
 * 可替换的认证完成门禁。实现必须幂等，不能隐式签发Token或创建登录会话。
 * 本轮仅预留拒绝接入点，不实现挑战、因子或MFA成功声明。
 *
 * @author Rain
 * @since 6.1.0
 */
@FunctionalInterface
public interface MfaCompletionPolicy {
    /**
     * 检查已验证身份；尚需额外证明时抛AdditionalAuthenticationRequiredException。
     */
    void check(AuthenticationCompletionContext context);
}
