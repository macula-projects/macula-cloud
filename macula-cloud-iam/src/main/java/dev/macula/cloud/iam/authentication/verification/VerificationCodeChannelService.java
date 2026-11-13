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
 * 验证码通道策略端口；状态算法与发送适配分离，调用方不能选择另一通道。
 *
 * @author Macula
 * @since 6.1.0
 */
public interface VerificationCodeChannelService {
    /** 表示设施已装配，不代表供应商渠道已配置或消息已送达。 */
    boolean isAvailable();
    /** 在固定通道与受信上下文中发送，提交失败不得激活验证码。 */
    void sendCode(String target, String remoteAddress, VerificationCodeContext context);

    /** 原子消费验证码，不能把不可用设施当作核验成功。 */
    boolean verifyCode(String target, String rawCode, VerificationCodeContext context);
}
