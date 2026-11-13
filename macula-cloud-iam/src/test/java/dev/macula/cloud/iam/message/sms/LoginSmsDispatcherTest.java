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

import java.util.List;

import dev.macula.cloud.iam.mapper.SysMessageProviderMapper;
import dev.macula.cloud.iam.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.iam.message.audit.LoginMessageAudit;
import dev.macula.cloud.iam.message.audit.MessageAuditHasher;
import dev.macula.cloud.iam.message.template.LoginMessageConfigurationService;
import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import dev.macula.cloud.iam.pojo.entity.SysMessageTemplate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 IAM 只读取唯一启用的短信供应商和登录场景模板。
 *
 * @author Rain
 * @since 2026-10-17
 */
class LoginSmsDispatcherTest {
    private final SysMessageProviderMapper providers = mock(SysMessageProviderMapper.class);
    private final SysMessageTemplateMapper templates = mock(SysMessageTemplateMapper.class);
    private final AliyunLoginSmsSender aliyun = mock(AliyunLoginSmsSender.class);
    private final TencentLoginSmsSender tencent = mock(TencentLoginSmsSender.class);
    private final MessageAuditHasher auditHasher = new MessageAuditHasher("fixture-iam-audit-key-32-characters");
    private LoginSmsDispatcher dispatcher;

    /**
     * 注册真实策略标识，避免Mock默认null掩盖重复供应商。
     */
    @BeforeEach
    void setUp() {
        when(aliyun.providerType()).thenReturn("ALIYUN");
        when(tencent.providerType()).thenReturn("TENCENT");
        dispatcher = new LoginSmsDispatcher(new LoginMessageConfigurationService(providers, templates),
                List.of(aliyun, tencent), new LoginMessageAudit(auditHasher));
    }

    @Test
    void dispatchesAliyunLoginTemplate() {
        SysMessageProvider provider = provider("ALIYUN");
        SysMessageTemplate template = template("ALIYUN");
        when(providers.selectList(any())).thenReturn(List.of(provider));
        when(templates.selectOne(any())).thenReturn(template);

        dispatcher.send("13800000000", "123456");

        verify(aliyun).send(provider, template, "13800000000", "123456");
        verify(tencent, never()).send(any(), any(), any(), any());
    }

    @Test
    void rejectsMissingOrAmbiguousConfiguration() {
        when(providers.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> dispatcher.send("13800000000", "123456"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("未启用");

        SysMessageProvider provider = provider("TENCENT");
        when(providers.selectList(any())).thenReturn(List.of(provider, provider));
        assertThatThrownBy(() -> dispatcher.send("13800000000", "123456"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不唯一");
    }

    @Test
    void rejectsMissingLoginTemplateBeforeSupplierCall() {
        when(providers.selectList(any())).thenReturn(List.of(provider("TENCENT")));
        when(templates.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> dispatcher.send("13800000000", "123456"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("配置不完整");
        verify(tencent, never()).send(any(), any(), any(), any());
    }

    @Test
    void auditTargetHashRequiresTheIndependentKey() {
        String first = auditHasher.hash("13800000000");
        String second = new MessageAuditHasher("another-iam-audit-key-32-characters").hash("13800000000");

        Assertions.assertThat(first).hasSize(64).doesNotContain("13800000000")
                .isNotEqualTo(second);
    }

    private SysMessageProvider provider(String type) {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setChannel("SMS");
        provider.setProviderType(type);
        provider.setCredential("fixture-secret");
        provider.setEnabled(true);
        return provider;
    }

    private SysMessageTemplate template(String type) {
        SysMessageTemplate template = new SysMessageTemplate();
        template.setChannel("SMS");
        template.setTemplateKey("SMS_LOGIN");
        template.setProviderType(type);
        template.setLanguageCode("zh-CN");
        template.setProviderTemplateCode("fixture-template");
        template.setEnabled(true);
        return template;
    }
}
