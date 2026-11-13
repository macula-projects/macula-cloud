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
import dev.macula.cloud.system.mapper.SysMessageProviderMapper;
import dev.macula.cloud.system.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.system.message.MessageAuditHasher;
import dev.macula.cloud.system.message.TemplateRenderer;
import dev.macula.cloud.system.message.MailHtmlSanitizer;
import dev.macula.cloud.system.message.mail.impl.MailTestSender;
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
        List.of(), mock(StringRedisTemplate.class),
        auditHasher);

    /** 保留既有邮件和短信场景目录。 */
    @Test void exposesExactSceneCatalogs() {
        when(templates.selectOne(any())).thenReturn(null);
        var mailTemplates = service.templates("MAIL", "", "zh-CN");
        assertEquals(8, mailTemplates.size());
        assertTrue(mailTemplates.stream().allMatch(template -> template.getContent().contains("亲爱的用户")));
        assertTrue(mailTemplates.stream().allMatch(template -> template.getContent().contains("官方团队")));
        assertTrue(mailTemplates.stream().anyMatch(template -> template.getContent().contains("expire_time")));
        assertEquals(9, service.templates("SMS", "ALIYUN", "en-US").size());
    }

    /** 拒绝未知供应商和不完整配置。 */
    @Test void rejectsRemovedProviders() {
        when(providers.selectList(any())).thenReturn(List.of());
        MessageProviderForm form = new MessageProviderForm();
        form.setProviderType("QINIU");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider("MAIL", form));
        form.setProviderType("CUSTOM");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider("SMS", form));
        verify(providers, never()).insert(any(SysMessageProvider.class));
    }

    /** 空白凭据保留且响应不包含原密钥。 */
    @Test void blankCredentialKeepsStoredSecretAndResponseNeverContainsIt() {
        SysMessageProvider entity = new SysMessageProvider();
        entity.setId(1L);
        entity.setChannel("MAIL");
        entity.setProviderType("CUSTOM");
        entity.setCredential("stored-secret");
        entity.setConfigJson("{\"port\":465,\"securityMode\":\"SSL\"}");
        when(providers.selectList(any())).thenReturn(List.of(entity));
        MessageProviderForm form = new MessageProviderForm();
        form.setProviderType("CUSTOM");
        form.setEndpoint("smtp.example.test");
        form.setPort(465);
        form.setSecurityMode("SSL");
        form.setAccount("user");
        form.setSender("sender@example.test");
        assertTrue(service.saveProvider("MAIL", form).isCredentialConfigured());
        assertEquals("stored-secret", entity.getCredential());
        assertFalse(java.util.Arrays.stream(dev.macula.cloud.system.vo.MessageProviderVO.class.getDeclaredFields())
            .anyMatch(field -> "credential".equals(field.getName())));
    }

    /** 切换供应商必须重新提供凭据。 */
    @Test void switchingProviderRequiresAReplacementCredential() {
        SysMessageProvider entity = new SysMessageProvider();
        entity.setId(1L);
        entity.setChannel("MAIL");
        entity.setProviderType("CUSTOM");
        entity.setCredential("stored-secret");
        when(providers.selectList(any())).thenReturn(List.of(entity));
        MessageProviderForm form = new MessageProviderForm();
        form.setProviderType("ALIYUN");
        form.setAccount("user@example.test");
        form.setSender("sender@example.test");

        assertThrows(ResponseStatusException.class, () -> service.saveProvider("MAIL", form));
        verify(providers, never()).updateById(any(SysMessageProvider.class));
    }

    /** 邮件预览不能执行变量中的活动内容。 */
    @Test void previewEscapesVariablesAndRemovesActiveHtml() {
        when(templates.selectOne(any())).thenReturn(null);
        String html = service.preview("MAIL_LOGIN", java.util.Map.of(
            "client_name", "Macula Cloud", "user_email", "user@example.com",
            "code", "<script>alert(1)</script>", "expire_time", "5", "now_time", "2026-10-18 10:00"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    /** 禁用模板不能触发测试投递。 */
    @Test void rejectsTestSendWhenSmsTemplateIsDisabled() {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setChannel("SMS");
        provider.setProviderType("ALIYUN");
        provider.setEnabled(true);
        SysMessageTemplate template = new SysMessageTemplate();
        template.setChannel("SMS");
        template.setTemplateKey("SMS_LOGIN");
        template.setProviderType("ALIYUN");
        template.setLanguageCode("zh-CN");
        template.setContent("验证码 ${code}");
        template.setProviderTemplateCode("SMS_001");
        template.setEnabled(false);
        when(providers.selectList(any())).thenReturn(List.of(provider));
        when(templates.selectOne(any())).thenReturn(template);

        MessageTestForm form = new MessageTestForm("SMS_LOGIN", "13800000000", "zh-CN",
            java.util.Map.of("code", "123456"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.test(form));

        assertEquals("短信模板未启用或配置不完整", error.getReason());
    }

    /** 审计目标使用独立密钥生成摘要。 */
    @Test void auditTargetHashRequiresTheIndependentKey() {
        String first = auditHasher.hash("user@example.test");
        String second = new MessageAuditHasher("another-system-audit-key-32-chars").hash("user@example.test");

        assertEquals(64, first.length());
        assertFalse(first.contains("user@example.test"));
        assertNotEquals(first, second);
    }

    /** 新预设独立于企业邮箱，Exchange 默认值允许管理员覆盖。 */
    @Test
    void savesPersonalMailAndSelfHostedExchangeDefaults() {
        when(providers.selectList(any())).thenReturn(List.of());
        MessageProviderForm form = new MessageProviderForm();
        form.setEnabled(true);
        form.setAccount("user@example.test");
        form.setSender("user@example.test");
        form.setCredential("fixture-password");
        for (String type : List.of("NETEASE_163", "QQ", "EXCHANGE")) {
            form.setProviderType(type);
            form.setEndpoint("mail.corp.example.test");
            var result = service.saveProvider("MAIL", form);
            assertEquals(type, result.getProviderType());
            assertEquals(type.equals("EXCHANGE") ? 587 : 465, result.getPort());
            assertEquals(type.equals("EXCHANGE") ? "STARTTLS" : "SSL", result.getSecurityMode());
            assertEquals(type.equals("EXCHANGE") ? "mail.corp.example.test"
                    : type.equals("QQ") ? "smtp.qq.com" : "smtp.163.com", result.getEndpoint());
        }
        form.setProviderType("EXCHANGE");
        form.setPort(2525);
        form.setSecurityMode("SSL");
        assertEquals(2525, service.saveProvider("MAIL", form).getPort());
    }

    /** 认证 Header 不回显，留空保留，填写后整体替换。 */
    @Test
    void customSmsHeadersAreWriteOnlyAndCanBeReplaced() {
        var entity = new SysMessageProvider();
        entity.setId(1L);
        entity.setChannel("SMS");
        entity.setProviderType("CUSTOM_HTTP");
        entity.setCredential("{\"Authorization\":\"Bearer fixture-old\"}");
        when(providers.selectList(any())).thenReturn(List.of(entity));
        var form = new MessageProviderForm();
        form.setProviderType("CUSTOM_HTTP");
        form.setEndpoint("https://sms.example.test/send");
        form.setSender("fixture-sign");
        form.setEnabled(true);
        var result = service.saveProvider("SMS", form);
        assertTrue(result.isAuthHeadersConfigured());
        assertEquals("{\"Authorization\":\"Bearer fixture-old\"}", entity.getCredential());
        form.setAuthHeaders(java.util.Map.of("X-Api-Key", "fixture-new"));
        service.saveProvider("SMS", form);
        assertTrue(entity.getCredential().contains("fixture-new"));
        assertFalse(entity.getCredential().contains("fixture-old"));
        String response = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result);
        assertFalse(response.contains("Authorization"));
        assertFalse(response.contains("fixture-old"));
        form.setAccount("not-applicable");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider("SMS", form));
    }

    /** 切换短信供应商时必须提供新用途的凭据。 */
    @Test
    void rejectsReuseOfCloudCredentialsForCustomSms() {
        var entity = new SysMessageProvider();
        entity.setId(1L);
        entity.setChannel("SMS");
        entity.setProviderType("ALIYUN");
        entity.setCredential("fixture-cloud-secret");
        when(providers.selectList(any())).thenReturn(List.of(entity));
        var form = new MessageProviderForm();
        form.setProviderType("CUSTOM_HTTP");
        form.setEndpoint("https://sms.example.test/send");
        form.setSender("fixture-sign");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider("SMS", form));
        assertEquals("ALIYUN", entity.getProviderType());
        verify(providers, never()).updateById(any(SysMessageProvider.class));
    }

    /** 重复供应商在构造时拒绝，不依赖 Spring Bean 顺序选择。 */
    @Test
    void rejectsDuplicateSmsStrategies() {
        var first = mock(dev.macula.cloud.system.message.sms.SmsSender.class);
        var second = mock(dev.macula.cloud.system.message.sms.SmsSender.class);
        when(first.providerType()).thenReturn("CUSTOM_HTTP");
        when(second.providerType()).thenReturn("CUSTOM_HTTP");
        assertThrows(IllegalStateException.class, () -> new MessageSettingsServiceImpl(providers, templates,
                new TemplateRenderer(), new MailHtmlSanitizer(), mock(MailTestSender.class),
                List.of(first, second), mock(StringRedisTemplate.class), auditHasher));
    }
}
