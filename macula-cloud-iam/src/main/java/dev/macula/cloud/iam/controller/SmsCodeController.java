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

import java.util.Map;

import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 登录页同源、CSRF 保护的短信验证码申请端点。
 *
 * @author Rain
 * @since 2026-10-17
 */
@RestController
@Validated
public class SmsCodeController {
    private final VerificationCodeChannelService smsService;

    /** 通道依赖以名称显式注入，避免公共接口存在两个Bean时发生误选。 */
    public SmsCodeController(@Qualifier("smsCodeService") VerificationCodeChannelService smsService) {
        this.smsService = smsService;
    }

    /**
     * 使用安全过滤器已校验的Client上下文发送验证码。
     */
    @PostMapping("/api/v1/sms-code")
    public Map<String, String> send(@RequestParam @Pattern(regexp = "^\\+?[0-9 -]{6,24}$") String phone,
            HttpServletRequest request) {
        try {
            String clientId = HostedLoginClientPolicyService.context(request).clientId();
            smsService.sendCode(phone, request.getRemoteAddr(), VerificationCodeContext.login(clientId));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码暂时无法发送");
        }
        return Map.of("status", "submitted");
    }

}
