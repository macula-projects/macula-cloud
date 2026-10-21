/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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
package dev.macula.cloud.iam.handler;

import java.util.Arrays;

import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * 浏览器认证失败的公开原因，只暴露受控错误码和固定提示，避免泄露内部异常与账号状态。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum AuthenticationFailureReason {

    AUTHENTICATION_REJECTED("authentication_rejected", "认证信息无效或账号不可用。"),
    AUTHENTICATION_UNAVAILABLE("authentication_unavailable", "认证暂不可用，请稍后重试。");

    private final String code;
    private final String message;

    AuthenticationFailureReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public static AuthenticationFailureReason from(AuthenticationException exception) {
        if (exception instanceof BadCredentialsException || exception instanceof AccountStatusException
            || exception instanceof UsernameNotFoundException) {
            return AUTHENTICATION_REJECTED;
        }
        return AUTHENTICATION_UNAVAILABLE;
    }

    public static AuthenticationFailureReason fromCode(String code) {
        return Arrays.stream(values()).filter(reason -> reason.code.equals(code)).findFirst()
            .orElse(AUTHENTICATION_UNAVAILABLE);
    }
}
