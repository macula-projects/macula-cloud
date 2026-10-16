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
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.mapper.SysMessageProviderMapper;
import dev.macula.cloud.system.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.system.message.*;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import dev.macula.cloud.system.pojo.entity.SysMessageTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 枚举默认模板与消息服务凭据脱敏测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class MessageSettingsServiceImplTest {
    private final SysMessageProviderMapper providerMapper = mock(SysMessageProviderMapper.class);
    private final SysMessageTemplateMapper templateMapper = mock(SysMessageTemplateMapper.class);
    private final MailTestSender mailSender = mock(MailTestSender.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final MessageSettingsServiceImpl service = new MessageSettingsServiceImpl(providerMapper, templateMapper,
        new TemplateRenderer(), mailSender, mock(AliyunSmsTestSender.class),
        mock(TencentSmsTestSender.class), mock(CustomSmsTestSender.class), redis);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enumDefaultsAreAvailableWithoutRows() {
        assertTrue(service.templates("MAIL", "").stream().anyMatch(template ->
            template.getTemplateKey().equals("MAIL_RESET_SUCCESS") &&
                !template.getContent().contains("${password}") && !template.isOverridden()));
    }

    @Test
    void providerReadsDoNotReturnCredential() {
        SysMessageProvider entity = new SysMessageProvider();
        entity.setCredential("stored-secret");
        entity.setChannel("MAIL");
        entity.setProviderType("EXCHANGE");
        when(providerMapper.selectById(1L)).thenReturn(entity);
        assertTrue(service.provider(1L).isCredentialConfigured());
        assertFalse(java.util.Arrays.stream(dev.macula.cloud.system.vo.MessageProviderVO.class.getDeclaredFields())
            .anyMatch(field -> "credential".equals(field.getName())));
    }

    @Test
    void rejectsPlaintextCredentialsInExtraJson() {
        MessageProviderForm form = new MessageProviderForm();
        form.setChannel("SMS");
        form.setProviderType("CUSTOM");
        form.setName("custom");
        form.setEndpoint("https://sms.example.test/send");
        form.setAccount("account");
        form.setCredential("secret");
        form.setSender("sender");
        form.setConfigJson("{\"apiKey\":\"plaintext\"}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        verify(providerMapper, never()).insert(any(SysMessageProvider.class));
    }

    @Test
    void rejectsNestedApiKeyAndCredentialInCustomSmsOptions() {
        MessageProviderForm form = new MessageProviderForm();
        form.setChannel("SMS");
        form.setProviderType("CUSTOM");
        form.setName("custom-secret-header");
        form.setEndpoint("https://sms.example.test/send");
        form.setCredential("encrypted-by-entity");
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\"," +
            "\"authHeader\":\"Authorization\",\"headers\":{\"X-API-KEY\":\"plaintext\"}}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\"," +
            "\"authHeader\":\"Authorization\",\"fixedParameters\":{\"access_key\":\"plaintext\"}}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\"," +
            "\"authHeader\":\"Authorization\",\"headers\":{\"X-Auth\":\"plaintext\"}}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\"," +
            "\"authHeader\":\"Authorization\",\"headers\":{\"authHeader\":\"plaintext\"}}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        verify(providerMapper, never()).insert(any(SysMessageProvider.class));
    }

    @Test
    void rejectsEmbeddedCredentialInAuthPrefix() {
        MessageProviderForm form = new MessageProviderForm();
        form.setChannel("SMS");
        form.setProviderType("CUSTOM");
        form.setName("custom-secret-prefix");
        form.setEndpoint("https://sms.example.test/send");
        form.setCredential("encrypted-by-entity");
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\"," +
            "\"authHeader\":\"Authorization\",\"authPrefix\":\"Bearer plaintext \"}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        verify(providerMapper, never()).insert(any(SysMessageProvider.class));
    }

    @Test
    void customSmsRequiresAuthHeaderAndCredential() {
        MessageProviderForm form = new MessageProviderForm();
        form.setChannel("SMS");
        form.setProviderType("CUSTOM");
        form.setName("custom-no-auth");
        form.setEndpoint("https://sms.example.test/send");
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\"}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        verify(providerMapper, never()).insert(any(SysMessageProvider.class));
    }

    @Test
    void customSmsAuthHeaderRequiresEncryptedCredentialField() {
        MessageProviderForm form = new MessageProviderForm();
        form.setChannel("SMS");
        form.setProviderType("CUSTOM");
        form.setName("custom-auth");
        form.setEndpoint("https://sms.example.test/send");
        form.setConfigJson("{\"phoneParameter\":\"phone\",\"contentParameter\":\"message\",\"authHeader\":\"Authorization\"}");
        assertThrows(ResponseStatusException.class, () -> service.saveProvider(null, form));
        verify(providerMapper, never()).insert(any(SysMessageProvider.class));
    }

    @Test
    void savesTemplateOverrideAndUsesItForPreview() {
        MessageTemplateForm form = new MessageTemplateForm("MAIL_TEST", "EXCHANGE", "zh-CN",
            "覆盖标题", "您好，${platform}", null, true);
        assertTrue(service.saveTemplate("MAIL", form).isOverridden());
        verify(templateMapper).insert(argThat((SysMessageTemplate entity) ->
            "EXCHANGE".equals(entity.getProviderType()) && "您好，${platform}".equals(entity.getContent())));
        SysMessageTemplate stored = new SysMessageTemplate();
        stored.setChannel("MAIL");
        stored.setTemplateKey("MAIL_TEST");
        stored.setProviderType("EXCHANGE");
        stored.setLanguageCode("zh-CN");
        stored.setTitle("覆盖标题");
        stored.setContent("您好，${platform}");
        stored.setEnabled(true);
        when(templateMapper.selectOne(any())).thenReturn(stored);
        assertEquals("您好，Macula", service.preview("MAIL", "MAIL_TEST", "EXCHANGE",
            java.util.Map.of("platform", "Macula")));
        stored.setEnabled(false);
        assertThrows(ResponseStatusException.class, () -> service.preview("MAIL", "MAIL_TEST", "EXCHANGE",
            java.util.Map.of("platform", "Macula")));
    }

    @Test
    void testMailUsesTemporaryRecipientAndRateLimit() {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setId(7L);
        provider.setChannel("MAIL");
        provider.setProviderType("EXCHANGE");
        provider.setEnabled(true);
        when(providerMapper.selectById(7L)).thenReturn(provider);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(java.time.Duration.class))).thenReturn(true, false);
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("root", "unused", "ROLE_ROOT"));
        MessageTestForm form = new MessageTestForm(7L, "MAIL_TEST", "temporary@example.test",
            java.util.Map.of("platform", "Macula"));
        assertDoesNotThrow(() -> service.test(form));
        verify(mailSender).send(eq(provider), eq("temporary@example.test"), eq("测试邮件"), contains("Macula"));
        assertThrows(ResponseStatusException.class, () -> service.test(form));
        verify(mailSender, times(1)).send(any(), anyString(), anyString(), anyString());
    }
}
