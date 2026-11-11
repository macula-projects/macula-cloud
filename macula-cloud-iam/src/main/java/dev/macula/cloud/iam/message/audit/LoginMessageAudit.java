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
package dev.macula.cloud.iam.message.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录消息提交审计，共用脱敏目标和固定结果分类。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginMessageAudit {
    private final MessageAuditHasher hasher;

    /**
     * 仅表示供应商接受提交，不宣称送达。
     */
    public void submitted(Scene scene, String provider, String target) {
        write(scene, provider, target, "SUBMITTED");
    }

    /**
     * 失败审计只包含安全分类及目标摘要。
     */
    public void failed(Scene scene, String provider, String target) {
        write(scene, provider, target, "FAILED");
    }

    /**
     * 清除供应商标识的控制字符，个人信息只保留HMAC。
     */
    private void write(Scene scene, String provider, String target, String result) {
        String safe = provider == null ? "UNRESOLVED" : provider.replaceAll("[\\p{Cntrl}]", "_");
        log.info("消息发送审计 caller=iam scene={} provider={} result={} targetHash={}",
                scene.value, safe, result, hasher.hash(target));
    }

    /**
     * 审计场景使用明确协议值，不从调用方输入推导。
     *
     * @since 6.1.0
     */
    public enum Scene {
        SMS("SMS_LOGIN"), MAIL("MAIL_LOGIN");
        private final String value;

        /**
         * 保存既有日志字段值。
         */
        Scene(String value) {
            this.value = value;
        }
    }
}
