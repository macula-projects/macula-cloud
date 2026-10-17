/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.macula.cloud.system.enums;

import java.util.Arrays;

/**
 * 平台消息渠道允许使用的固定供应商及邮件 SMTP 预设。
 *
 * @author Rain
 * @since 2026-10-17
 */
public enum MessageProviderType {
    CUSTOM("MAIL", "CUSTOM", null, 0, "NONE"),
    ALIYUN_MAIL("MAIL", "ALIYUN", "smtp.qiye.aliyun.com", 465, "SSL"),
    TENCENT_MAIL("MAIL", "TENCENT", "smtp.exmail.qq.com", 465, "SSL"),
    NETEASE_MAIL("MAIL", "NETEASE", "smtp.qiye.163.com", 465, "SSL"),
    ALIYUN_SMS("SMS", "ALIYUN", null, 0, null),
    TENCENT_SMS("SMS", "TENCENT", null, 0, null);

    private final String channel;
    private final String code;
    private final String host;
    private final int port;
    private final String securityMode;

    MessageProviderType(String channel, String code, String host, int port, String securityMode) {
        this.channel = channel;
        this.code = code;
        this.host = host;
        this.port = port;
        this.securityMode = securityMode;
    }

    public String channel() { return channel; }
    public String code() { return code; }
    public String host() { return host; }
    public int port() { return port; }
    public String securityMode() { return securityMode; }

    public static MessageProviderType require(String channel, String value) {
        return Arrays.stream(values()).filter(item -> item.channel.equals(channel) && item.code.equals(value))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("供应商类型不支持"));
    }
}
