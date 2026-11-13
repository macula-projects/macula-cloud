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
package dev.macula.cloud.iam.message.mail;

import dev.macula.cloud.iam.message.dispatch.LoginMailDispatcher;

import java.time.Duration;
import java.util.List;

import dev.macula.cloud.iam.mapper.SysMessageProviderMapper;
import dev.macula.cloud.iam.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.iam.message.audit.LoginMessageAudit;
import dev.macula.cloud.iam.message.template.LoginMessageConfigurationService;
import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件默认模板、HTML转义及提交审计保留原合同。
 *
 * @author Macula
 * @since 6.1.0
 */
class LoginMailDispatcherTest {
    /**
     * 邮件模板缺失仍用原默认值，客户端名称只能作为转义文本。
     */
    @Test
    void defaultTemplateEscapesClientName() {
        var providers = mock(SysMessageProviderMapper.class);
        var templates = mock(SysMessageTemplateMapper.class);
        var provider = new SysMessageProvider();
        when(providers.selectList(any())).thenReturn(List.of(provider));
        var sender = mock(MailSender.class);
        var audit = mock(LoginMessageAudit.class);
        var dispatcher = new LoginMailDispatcher(new LoginMessageConfigurationService(providers, templates), sender,
                audit);
        dispatcher.send("alice@example.test", "123456", "<b>Client</b>", Duration.ofMinutes(5));
        verify(sender).send(eq(provider), eq("alice@example.test"), eq("Macula Cloud"), eq("登录验证"),
                contains("&lt;b&gt;Client&lt;/b&gt;"));
        verify(audit).submitted(LoginMessageAudit.Scene.MAIL, "SMTP", "alice@example.test");
    }
}
