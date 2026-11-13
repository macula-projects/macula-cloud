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
    EXCHANGE("MAIL", "EXCHANGE", null, 587, "STARTTLS"),
    NETEASE_163("MAIL", "NETEASE_163", "smtp.163.com", 465, "SSL"),
    QQ_MAIL("MAIL", "QQ", "smtp.qq.com", 465, "SSL"),
    CUSTOM_HTTP_SMS("SMS", "CUSTOM_HTTP", null, 0, null),
    ALIYUN_SMS("SMS", "ALIYUN", null, 0, null),
    TENCENT_SMS("SMS", "TENCENT", null, 0, null);

    private final String channel;
    private final String code;
    private final String host;
    private final int port;
    private final String securityMode;

    /** 定义稳定配置标识及 SMTP 缺省参数。 */
    MessageProviderType(String channel, String code, String host, int port, String securityMode) {
        this.channel = channel;
        this.code = code;
        this.host = host;
        this.port = port;
        this.securityMode = securityMode;
    }

    /** 返回所属渠道。 */
    public String channel() { return channel; }
    /** 返回持久化及接口使用的稳定标识。 */
    public String code() { return code; }
    /** 返回固定 SMTP 主机，自定义主机时为空。 */
    public String host() { return host; }
    /** 返回缺省端口。 */
    public int port() { return port; }
    /** 返回缺省传输安全模式。 */
    public String securityMode() { return securityMode; }

    /** 按渠道和稳定标识查找供应商，不匹配时拒绝。 */
    public static MessageProviderType require(String channel, String value) {
        return Arrays.stream(values()).filter(item -> item.channel.equals(channel) && item.code.equals(value))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("供应商类型不支持"));
    }
}
