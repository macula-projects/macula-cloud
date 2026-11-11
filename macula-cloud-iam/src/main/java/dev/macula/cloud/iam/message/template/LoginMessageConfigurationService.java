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
package dev.macula.cloud.iam.message.template;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.iam.mapper.SysMessageProviderMapper;
import dev.macula.cloud.iam.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import dev.macula.cloud.iam.pojo.entity.SysMessageTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 读取唯一启用渠道及登录模板，不能由浏览器指定发送配置。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class LoginMessageConfigurationService {
    private static final String DEFAULT_SENDER_NAME = "Macula Cloud";
    private static final String DEFAULT_TITLE = "登录验证";
    private static final String DEFAULT_CONTENT = "您正在登录 ${client_name}，您的验证码为："
            + "<strong>${code}</strong>，该验证码 ${expire_time} 分钟内有效，请勿泄露给他人。";

    private final SysMessageProviderMapper providers;
    private final SysMessageTemplateMapper templates;

    /**
     * 保留邮件未自定义时的原默认模板。
     */
    public Configuration mail() {
        SysMessageProvider provider = provider("MAIL");
        SysMessageTemplate template = template("MAIL", "MAIL_LOGIN", "");
        if (template == null) {
            template = new SysMessageTemplate();
            template.setSenderName(DEFAULT_SENDER_NAME);
            template.setTitle(DEFAULT_TITLE);
            template.setContent(DEFAULT_CONTENT);
        }
        if (!StringUtils.hasText(template.getTitle()) || !StringUtils.hasText(template.getContent())) {
            throw new IllegalStateException("登录邮件模板未配置");
        }
        return new Configuration(provider, template);
    }

    /**
     * 短信必须显式配置供应商模板号及凭据，不使用邮件模板回退。
     */
    public Configuration sms() {
        SysMessageProvider provider = provider("SMS");
        SysMessageTemplate template = template("SMS", "SMS_LOGIN", provider.getProviderType());
        if (template == null || !StringUtils.hasText(template.getProviderTemplateCode())
                || !StringUtils.hasText(provider.getCredential())) {
            throw new IllegalStateException("登录短信配置不完整");
        }
        return new Configuration(provider, template);
    }

    /**
     * 配置不唯一时失败关闭，不随机挑选渠道。
     */
    private SysMessageProvider provider(String channel) {
        List<SysMessageProvider> values = providers.selectList(new LambdaQueryWrapper<SysMessageProvider>()
                .eq(SysMessageProvider::getChannel, channel).eq(SysMessageProvider::getEnabled, true));
        if (values.size() != 1) {
            throw new IllegalStateException("消息渠道未启用或配置不唯一");
        }
        return values.get(0);
    }

    /**
     * 模板只能来自服务端固定场景和语言。
     */
    private SysMessageTemplate template(String channel, String key, String provider) {
        return templates.selectOne(new LambdaQueryWrapper<SysMessageTemplate>()
                .eq(SysMessageTemplate::getChannel, channel).eq(SysMessageTemplate::getTemplateKey, key)
                .eq(SysMessageTemplate::getProviderType, provider).eq(SysMessageTemplate::getLanguageCode, "zh-CN")
                .eq(SysMessageTemplate::getEnabled, true));
    }

    /**
     * 内部投递配置，不向Controller或日志暴露其中凭据。
     *
     * @since 6.1.0
     */
    public record Configuration(SysMessageProvider provider, SysMessageTemplate template) {
        /**
         * 配置包含敏感数据，禁止记录自动生成的对象内容。
         */
        @Override
        public String toString() {
            return "Configuration[redacted]";
        }
    }
}
