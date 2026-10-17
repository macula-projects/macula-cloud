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
package dev.macula.cloud.iam.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 使用共享平台配置提交 IAM 登录验证码，不暴露通用发送入口。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginSmsDispatcher {
    private final IamMessageProviderMapper providerMapper;
    private final IamMessageTemplateMapper templateMapper;
    private final AliyunLoginSmsSender aliyunSender;
    private final TencentLoginSmsSender tencentSender;
    private final MessageAuditHasher auditHasher;

    public void send(String phone, String code) {
        String providerType = "UNRESOLVED";
        try {
            List<IamMessageProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<IamMessageProvider>()
                .eq(IamMessageProvider::getChannel, "SMS").eq(IamMessageProvider::getEnabled, true));
            if (providers.size() != 1) {
                throw new IllegalStateException("短信渠道未启用或配置不唯一");
            }
            IamMessageProvider provider = providers.get(0);
            providerType = provider.getProviderType();
            IamMessageTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<IamMessageTemplate>()
                .eq(IamMessageTemplate::getChannel, "SMS").eq(IamMessageTemplate::getTemplateKey, "SMS_LOGIN")
                .eq(IamMessageTemplate::getProviderType, providerType)
                .eq(IamMessageTemplate::getLanguageCode, "zh-CN").eq(IamMessageTemplate::getEnabled, true));
            if (template == null || !StringUtils.hasText(template.getProviderTemplateCode()) ||
                !StringUtils.hasText(provider.getCredential())) {
                throw new IllegalStateException("登录短信配置不完整");
            }
            switch (providerType) {
                case "ALIYUN" -> aliyunSender.send(provider, template, phone, code);
                case "TENCENT" -> tencentSender.send(provider, template, phone, code);
                default -> throw new IllegalStateException("短信供应商不支持");
            }
            audit(providerType, phone, "SUBMITTED");
        } catch (RuntimeException ex) {
            audit(providerType, phone, "FAILED");
            throw ex;
        }
    }

    private void audit(String providerType, String phone, String result) {
        log.info("消息发送审计 caller=iam scene=SMS_LOGIN provider={} result={} targetHash={}",
            safe(providerType), result, auditHasher.hash(phone));
    }

    private String safe(String value) {
        return value == null ? "UNRESOLVED" : value.replaceAll("[\\p{Cntrl}]", "_");
    }
}
