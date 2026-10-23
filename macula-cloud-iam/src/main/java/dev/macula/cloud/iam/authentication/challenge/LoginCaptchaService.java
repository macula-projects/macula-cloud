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
package dev.macula.cloud.iam.authentication.challenge;

/**
 * 颁发并一次性消费绑定 Client、Origin 和来源地址的登录 CAPTCHA。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface LoginCaptchaService {
    CaptchaChallenge issue(String clientId, String origin, String remoteAddress);

    boolean consume(String challengeId, String answer, String clientId, String origin);

    /**
     * 返回给端侧的不可预测 challenge ID 与人机题目。
     *
     * @param id challenge ID
     * @param prompt 人机题目
     * @since 6.1.0
     */
    record CaptchaChallenge(String id, String prompt) {
    }
}
