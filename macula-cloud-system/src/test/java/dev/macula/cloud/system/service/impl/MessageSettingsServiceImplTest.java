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
package dev.macula.cloud.system.service.impl;

import dev.macula.cloud.system.form.MessageProviderForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.mapper.*;
import dev.macula.cloud.system.message.*;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import dev.macula.cloud.system.pojo.entity.SysMessageTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 固定目录和单渠道配置边界测试。
 *
 * @author Rain
 * @since 2026-10-17
 */
class MessageSettingsServiceImplTest {
    private final SysMessageProviderMapper providers = mock(SysMessageProviderMapper.class);
    private final SysMessageTemplateMapper templates = mock(SysMessageTemplateMapper.class);
    private final MessageAuditHasher auditHasher = new MessageAuditHasher("fixture-system-audit-key-32-chars");
    private final MessageSettingsServiceImpl service = new MessageSettingsServiceImpl(providers, templates,
        new TemplateRenderer(), new MailHtmlSanitizer(), mock(MailTestSender.class),
        mock(AliyunSmsTestSender.class), mock(TencentSmsTestSender.class), mock(StringRedisTemplate.class),
        auditHasher);

    @Test void exposesExactSceneCatalogs() {
        when(templates.selectOne(any())).thenReturn(null);
        var mailTemplates = service.templates("MAIL", "", "zh-CN");
        assertEquals(8, mailTemplates.size());
        assertTrue(mailTemplates.stream().allMatch(template -> template.getContent().contains("亲爱的用户")));
        assertTrue(mailTemplates.stream().allMatch(template -> template.getContent().contains("官方团队")));
        assertTrue(mailTemplates.stream().anyMatch(template -> template.getContent().contains("expire_time")));
        assertEquals(9, service.templates("SMS", "ALIYUN", "en-US").size());
    }

    @Test void rejectsRemovedProviders() {
        when(providers.selectList(any())).thenReturn(List.of());
        MessageProviderForm form = new MessageProviderForm();
        form.setProviderType("EXCHANGE");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider("MAIL", form));
        form.setProviderType("CUSTOM");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider("SMS", form));
        verify(providers, never()).insert(any(SysMessageProvider.class));
    }

    @Test void blankCredentialKeepsStoredSecretAndResponseNeverContainsIt() {
        SysMessageProvider entity = new SysMessageProvider();
        entity.setId(1L); entity.setChannel("MAIL"); entity.setProviderType("CUSTOM");
        entity.setCredential("stored-secret"); entity.setConfigJson("{\"port\":465,\"securityMode\":\"SSL\"}");
        when(providers.selectList(any())).thenReturn(List.of(entity));
        MessageProviderForm form = new MessageProviderForm();
        form.setProviderType("CUSTOM"); form.setEndpoint("smtp.example.test"); form.setPort(465);
        form.setSecurityMode("SSL"); form.setAccount("user"); form.setSender("sender@example.test");
        assertTrue(service.saveProvider("MAIL", form).isCredentialConfigured());
        assertEquals("stored-secret", entity.getCredential());
        assertFalse(java.util.Arrays.stream(dev.macula.cloud.system.vo.MessageProviderVO.class.getDeclaredFields())
            .anyMatch(field -> "credential".equals(field.getName())));
    }

    @Test void switchingProviderRequiresAReplacementCredential() {
        SysMessageProvider entity = new SysMessageProvider();
        entity.setId(1L); entity.setChannel("MAIL"); entity.setProviderType("CUSTOM");
        entity.setCredential("stored-secret");
        when(providers.selectList(any())).thenReturn(List.of(entity));
        MessageProviderForm form = new MessageProviderForm();
        form.setProviderType("ALIYUN"); form.setAccount("user@example.test");
        form.setSender("sender@example.test");

        assertThrows(ResponseStatusException.class, () -> service.saveProvider("MAIL", form));
        verify(providers, never()).updateById(any(SysMessageProvider.class));
    }

    @Test void previewEscapesVariablesAndRemovesActiveHtml() {
        when(templates.selectOne(any())).thenReturn(null);
        String html = service.preview("MAIL_LOGIN", java.util.Map.of(
            "client_name", "Macula Cloud", "user_email", "user@example.com",
            "code", "<script>alert(1)</script>", "expire_time", "5", "now_time", "2026-10-18 10:00"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test void rejectsTestSendWhenSmsTemplateIsDisabled() {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setChannel("SMS"); provider.setProviderType("ALIYUN"); provider.setEnabled(true);
        SysMessageTemplate template = new SysMessageTemplate();
        template.setChannel("SMS"); template.setTemplateKey("SMS_LOGIN");
        template.setProviderType("ALIYUN"); template.setLanguageCode("zh-CN");
        template.setContent("验证码 ${code}"); template.setProviderTemplateCode("SMS_001");
        template.setEnabled(false);
        when(providers.selectList(any())).thenReturn(List.of(provider));
        when(templates.selectOne(any())).thenReturn(template);

        MessageTestForm form = new MessageTestForm("SMS_LOGIN", "13800000000", "zh-CN",
            java.util.Map.of("code", "123456"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.test(form));

        assertEquals("短信模板未启用或配置不完整", error.getReason());
    }

    @Test void auditTargetHashRequiresTheIndependentKey() {
        String first = auditHasher.hash("user@example.test");
        String second = new MessageAuditHasher("another-system-audit-key-32-chars").hash("user@example.test");

        assertEquals(64, first.length());
        assertFalse(first.contains("user@example.test"));
        assertNotEquals(first, second);
    }
}
