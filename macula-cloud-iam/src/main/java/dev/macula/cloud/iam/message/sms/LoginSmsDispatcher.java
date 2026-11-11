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
package dev.macula.cloud.iam.message.sms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.macula.cloud.iam.message.audit.LoginMessageAudit;
import dev.macula.cloud.iam.message.template.LoginMessageConfigurationService;
import org.springframework.stereotype.Component;

/**
 * 选择受控短信供应商并提交，配置/模板及审计分别委托。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
public class LoginSmsDispatcher {
    private final LoginMessageConfigurationService configurations;
    private final Map<String, SmsSender> senders;
    private final LoginMessageAudit audit;

    /**
     * 显式拒绝重复供应商，配置错误不能依赖Bean顺序决定结果。
     */
    public LoginSmsDispatcher(LoginMessageConfigurationService configurations, List<SmsSender> adapters,
            LoginMessageAudit audit) {
        this.configurations = configurations;
        this.audit = audit;
        Map<String, SmsSender> registry = new HashMap<>();
        for (SmsSender adapter : adapters) {
            if (registry.putIfAbsent(adapter.providerType(), adapter) != null) {
                throw new IllegalStateException("重复短信供应商");
            }
        }
        senders = Map.copyOf(registry);
    }

    /**
     * 只提交配置允许的验证码模板，异常不释放发送额度。
     */
    public void send(String phone, String code) {
        String provider = "UNRESOLVED";
        try {
            var configuration = configurations.sms();
            provider = configuration.provider().getProviderType();
            SmsSender sender = senders.get(provider);
            if (sender == null) {
                throw new IllegalStateException("短信供应商不支持");
            }
            sender.send(configuration.provider(), configuration.template(), phone, code);
            audit.submitted(LoginMessageAudit.Scene.SMS, provider, phone);
        } catch (RuntimeException ex) {
            audit.failed(LoginMessageAudit.Scene.SMS, provider, phone);
            throw ex;
        }
    }
}
