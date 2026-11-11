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
package dev.macula.cloud.iam.authentication.verification;

/**
 * 验证码状态端口；投递与账号加载不进入存储实现。
 *
 * @author Macula
 * @since 6.1.0
 */
public interface VerificationCodeStore {
    /**
     * 占用发送额度并生成凭据，尚不可用于认证。
     */
    String issue(VerificationCodeScope scope, String target, String ip, VerificationCodeSettings settings);

    /**
     * 仅投递提交成功后激活有时效、有尝试上限的验证码。
     */
    void activate(VerificationCodeScope scope, String target, String code, VerificationCodeSettings settings);

    /**
     * 原子消费成功凭据，错误尝试不延长有效期。
     */
    boolean consume(VerificationCodeScope scope, String target, String code, VerificationCodeSettings settings);
}
