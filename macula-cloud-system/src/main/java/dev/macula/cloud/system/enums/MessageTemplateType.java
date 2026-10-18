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
    MAIL_BIND("MAIL", "绑定邮箱", verificationMail("您正在绑定邮箱"),
        List.of("client_name", "user_email", "code", "now_time")),
    MAIL_CHANGE_BIND("MAIL", "修改绑定邮箱", verificationMail("您正在修改绑定邮箱"),
        List.of("client_name", "user_email", "code", "now_time")),
    MAIL_FORGOT_PASSWORD("MAIL", "忘记密码", verificationMail("您正在重置密码"),
        List.of("client_name", "user_email", "code", "now_time")),
    MAIL_CHANGE_PASSWORD("MAIL", "修改密码", verificationMail("您正在修改密码"),
        List.of("client_name", "user_email", "code", "now_time")),
    MAIL_RESET_SUCCESS("MAIL", "管理员重置密码成功", noticeMail(
        "您的密码已由管理员重置，如非本人操作，请立即修改账户密码并联系管理员。"),
        List.of("client_name", "user_email", "now_time")),
    MAIL_LOGIN("MAIL", "登录验证", noticeMail(
        "您正在登录 ${client_name}，您的验证码为：<strong>${code}</strong>，该验证码 ${expire_time} 分钟内有效，请勿泄露给他人。"),
        List.of("client_name", "user_email", "code", "expire_time", "now_time")),
    MAIL_PASSWORD_EXPIRY("MAIL", "密码到期提醒", noticeMail(
        "您的密码将于 <strong>${expire_days}</strong> 天后过期，请尽快修改密码。"),
        List.of("client_name", "user_email", "expire_days", "now_time")),
    MAIL_WELCOME("MAIL", "欢迎邮件", noticeMail(
        "感谢您加入 ${client_name}，您的账户已经创建成功。"),
        List.of("client_name", "user_email", "now_time")),
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

    private static String verificationMail(String action) {
        return noticeMail(action + "，您的验证码为：<strong>${code}</strong>，请尽快验证并保管好验证码。");
    }

    private static String noticeMail(String message) {
        return "<p><b>亲爱的用户：${user_email}</b></p><p>" + message
            + "</p><p>${client_name} 官方团队</p><p>${now_time}</p>";
    }
}
