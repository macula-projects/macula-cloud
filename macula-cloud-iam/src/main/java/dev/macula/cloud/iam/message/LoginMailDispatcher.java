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
import dev.macula.cloud.iam.mapper.SysMessageProviderMapper;
import dev.macula.cloud.iam.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import dev.macula.cloud.iam.pojo.entity.SysMessageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 读取共享 MAIL_LOGIN 配置并以固定变量渲染 IAM 登录邮件。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginMailDispatcher {
    private static final String CHANNEL = "MAIL";
    private static final String TEMPLATE_KEY = "MAIL_LOGIN";
    private static final String LANGUAGE_CODE = "zh-CN";
    private static final String DEFAULT_SENDER_NAME = "Macula Cloud";
    private static final String DEFAULT_TITLE = "登录验证";
    private static final String DEFAULT_CONTENT = "您正在登录 ${client_name}，您的验证码为："
        + "<strong>${code}</strong>，该验证码 ${expire_time} 分钟内有效，请勿泄露给他人。";

    private final SysMessageProviderMapper providerMapper;
    private final SysMessageTemplateMapper templateMapper;
    private final LoginMailSender sender;
    private final MessageAuditHasher auditHasher;

    public void send(String email, String code, String clientName, Duration ttl) {
        try {
            SysMessageProvider provider = provider();
            SysMessageTemplate template = template();
            String subject = render(template.getTitle(), email, code, clientName, ttl);
            String content = render(template.getContent(), email, code, clientName, ttl);
            sender.send(provider, email, template.getSenderName(), subject, content);
            audit(email, "SUBMITTED");
        } catch (RuntimeException ex) {
            audit(email, "FAILED");
            throw ex;
        }
    }

    private SysMessageProvider provider() {
        List<SysMessageProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<SysMessageProvider>()
            .eq(SysMessageProvider::getChannel, CHANNEL).eq(SysMessageProvider::getEnabled, true));
        if (providers.size() != 1) {
            throw new IllegalStateException("邮件渠道未启用或配置不唯一");
        }
        return providers.get(0);
    }

    private SysMessageTemplate template() {
        SysMessageTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<SysMessageTemplate>()
            .eq(SysMessageTemplate::getChannel, CHANNEL)
            .eq(SysMessageTemplate::getTemplateKey, TEMPLATE_KEY)
            .eq(SysMessageTemplate::getProviderType, "")
            .eq(SysMessageTemplate::getLanguageCode, LANGUAGE_CODE)
            .eq(SysMessageTemplate::getEnabled, true));
        if (template == null) {
            template = new SysMessageTemplate();
            template.setSenderName(DEFAULT_SENDER_NAME);
            template.setTitle(DEFAULT_TITLE);
            template.setContent(DEFAULT_CONTENT);
            return template;
        }
        if (!StringUtils.hasText(template.getTitle()) || !StringUtils.hasText(template.getContent())) {
            throw new IllegalStateException("登录邮件模板未配置");
        }
        return template;
    }

    private String render(String source, String email, String code, String clientName, Duration ttl) {
        return source
            .replace("${client_name}", escape(clientName))
            .replace("${user_email}", escape(email))
            .replace("${code}", escape(code))
            .replace("${expire_time}", String.valueOf(Math.max(1L, ttl.toMinutes())))
            .replace("${now_time}", escape(OffsetDateTime.now().toString()));
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private void audit(String email, String result) {
        log.info("消息发送审计 caller=iam scene=MAIL_LOGIN provider=SMTP result={} targetHash={}",
            result, auditHasher.hash(email));
    }
}
