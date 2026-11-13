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
 * 缺少验证码设施时共用的失败关闭策略，不调用存储或投递通道。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class UnavailableVerificationCodeService implements VerificationCodeChannelService {
    /** 不把未装配设施显示为可用证明。 */
    @Override
    public boolean isAvailable() {
        return false;
    }

    /** 明确拒绝投递，不能返回伪造的提交成功。 */
    @Override
    public void sendCode(String target, String remoteAddress, VerificationCodeContext context) {
        throw new IllegalStateException("验证码服务未配置");
    }

    /** 任何通道与用途均不能绕过设施缺失。 */
    @Override
    public boolean verifyCode(String target, String rawCode, VerificationCodeContext context) {
        return false;
    }
}
