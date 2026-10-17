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
package dev.macula.cloud.system.enums;

import java.util.List;

/**
 * 邮件与短信模板的代码内默认值；数据库仅保存启用或修改后的覆盖记录。
 *
 * @author Rain
 * @since 2026-10-13
 */
public enum MessageTemplateType {
    MAIL_BIND("MAIL", "绑定邮箱", "绑定邮箱的验证码：${code}。", List.of("code")),
    MAIL_CHANGE_BIND("MAIL", "变更绑定邮箱", "变更邮箱的验证码：${code}。", List.of("code")),
    MAIL_FORGOT_PASSWORD("MAIL", "找回密码", "找回密码的验证码：${code}。", List.of("code")),
    MAIL_CHANGE_PASSWORD("MAIL", "修改密码", "修改密码的验证码：${code}。", List.of("code")),
    MAIL_RESET_SUCCESS("MAIL", "密码重置成功", "您的密码已重置。如非本人操作，请立即联系管理员。", List.of()),
    MAIL_LOGIN("MAIL", "登录验证", "登录验证码：${code}，请勿转发。", List.of("code")),
    MAIL_PASSWORD_EXPIRY("MAIL", "密码到期提醒", "您的密码将在 ${days} 天后到期，请及时更新。", List.of("days")),
    MAIL_WELCOME("MAIL", "欢迎使用", "欢迎使用 ${platform}。", List.of("platform")),
    SMS_BIND("SMS", "", "绑定手机号验证码 ${code}。", List.of("code")),
    SMS_BIND_SUCCESS("SMS", "", "手机号绑定已完成。", List.of()),
    SMS_CHANGE_PHONE("SMS", "", "更换手机号验证码 ${code}。", List.of("code")),
    SMS_FORGOT_PASSWORD("SMS", "", "找回密码验证码 ${code}。", List.of("code")),
    SMS_CHANGE_PASSWORD("SMS", "", "修改密码验证码 ${code}。", List.of("code")),
    SMS_RESET_SUCCESS("SMS", "", "密码已重置。如非本人操作，请联系管理员。", List.of()),
    SMS_LOGIN("SMS", "", "登录验证码 ${code}，请勿泄露。", List.of("code")),
    SMS_PASSWORD_EXPIRY("SMS", "", "密码将在 ${days} 天后到期。", List.of("days")),
    SMS_WELCOME("SMS", "", "欢迎使用 ${platform}。", List.of("platform"));

    private final String channel;
    private final String title;
    private final String content;
    private final List<String> variables;

    MessageTemplateType(String channel, String title, String content, List<String> variables) {
        this.channel = channel;
        this.title = title;
        this.content = content;
        this.variables = variables;
    }

    public String channel() { return channel; }
    public String title() { return title; }
    public String content() { return content; }
    public List<String> variables() { return variables; }
}
