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
package dev.macula.cloud.system.message.sms;

import dev.macula.cloud.system.enums.MessageTemplateType;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import java.util.Map;

/**
 * 短信测试投递策略，保留各供应商的模板变量契约。
 * @author Rain
 * @since 6.1.0
 */
public interface SmsSender {
    /** 返回用于注册和选择策略的稳定供应商标识。 */
    String providerType();

    /** 根据服务端选定的模板和语言提交短信，不接受浏览器指定发送地址。 */
    void send(SysMessageProvider provider, String target, String templateCode,
            MessageTemplateType type, String languageCode, Map<String, String> variables);
}
