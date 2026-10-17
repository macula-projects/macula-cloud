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

import dev.macula.cloud.iam.authentication.sms.SmsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 登录页同源、CSRF 保护的短信验证码申请端点。
 *
 * @author Rain
 * @since 2026-10-17
 */
@RestController
@RequiredArgsConstructor
@Validated
public class SmsCodeController {
    private final SmsService smsService;

    @PostMapping("/api/v1/sms-code")
    public Map<String, String> send(@RequestParam @Pattern(regexp = "^\\+?[0-9 -]{6,24}$") String phone,
        HttpServletRequest request) {
        try {
            smsService.sendCode(phone, request.getRemoteAddr());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码暂时无法发送");
        }
        return Map.of("status", "submitted");
    }

}
