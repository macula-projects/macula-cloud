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
package dev.macula.cloud.iam.controller;

import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 邮箱验证码申请端点，CSRF与可信Client策略由登录安全链先执行。
 *
 * @author Macula
 * @since 6.1.0
 */
@RestController
@Validated
public class EmailCodeController {
    public static final String PATH = "/api/v1/email-code";
    private final VerificationCodeChannelService codes;

    /** 通道依赖以名称显式注入，避免公共接口存在两个Bean时发生误选。 */
    public EmailCodeController(@Qualifier("emailCodeService") VerificationCodeChannelService codes) {
        this.codes = codes;
    }

    /**
     * 合法地址按SMS同样提交，不通过响应暴露账号是否存在。
     */
    @PostMapping(PATH)
    public Map<String, String> send(@RequestParam @Email @Size(min = 3, max = 128) String email,
            HttpServletRequest request) {
        var context = HostedLoginClientPolicyService.context(request);
        try {
            codes.sendCode(email, request.getRemoteAddr(), VerificationCodeContext.login(context.clientId(),
                    context.client() == null ? "Macula" : context.client().applicationName()));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码暂时无法发送");
        }
        return Map.of("status", "submitted");
    }
}
