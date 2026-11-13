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
package dev.macula.cloud.system.message.sms.impl;

import dev.macula.cloud.system.message.sms.SmsSender;
import dev.macula.cloud.system.message.sms.support.CustomSmsHttpClient;
import dev.macula.cloud.system.message.sms.support.CustomSmsOptions;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import dev.macula.cloud.system.enums.MessageTemplateType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 通过管理员配置的固定 POST 服务提交模板短信。
 * @author Rain
 * @since 6.1.0
 */
@Component
public class CustomHttpSmsSender implements SmsSender {
    private final CustomSmsHttpClient client = new CustomSmsHttpClient();

    /** 返回配置中使用的稳定标识。 */
    @Override
    public String providerType() {
        return CustomSmsOptions.PROVIDER_TYPE;
    }

    /** 从服务端配置构造固定契约，不向客户端开放投递地址或 Header。 */
    @Override
    public void send(SysMessageProvider provider, String target, String templateCode,
            MessageTemplateType type, String languageCode, Map<String, String> variables) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String key : type.variables()) {
            if (variables.get(key) == null || variables.get(key).isBlank()) {
                throw new IllegalStateException("短信模板变量缺失");
            }
            params.put(key, variables.get(key));
        }
        if (templateCode == null || templateCode.isBlank() || provider.getSender() == null
                || provider.getSender().isBlank()) {
            throw new IllegalStateException("自定义短信模板配置不完整");
        }
        client.send(CustomSmsOptions.parse(provider.getEndpoint(), provider.getCredential()), Map.of(
                "phone", target, "signName", provider.getSender(), "templateCode", templateCode,
                "templateParams", params, "scene", type.name(), "languageCode", languageCode));
    }
}
