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

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 验证码投递生命周期：先限流，投递提交后激活，失败保留滥用额度。
 *
 * @author Macula
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    private final VerificationCodeStore store;

    /**
     * 回调只接收本次验证码；投递失败不激活也不释放限流。
     */
    public void send(VerificationCodeScope scope, String target, String remote, VerificationCodeSettings settings,
            Consumer<String> delivery) {
        String code = store.issue(scope, target, remote == null ? "unknown" : remote, settings);
        delivery.accept(code);
        store.activate(scope, target, code, settings);
    }

    /**
     * 输入缺失不进入存储；具体标识的规范化由通道适配器处理。
     */
    public boolean verify(VerificationCodeScope scope, String target, String code, VerificationCodeSettings settings) {
        return target != null && code != null && store.consume(scope, target, code, settings);
    }
}
