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

import org.junit.jupiter.api.Test;

import java.util.List;

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
    private final IamMessageProviderMapper providers = mock(IamMessageProviderMapper.class);
    private final IamMessageTemplateMapper templates = mock(IamMessageTemplateMapper.class);
    private final AliyunLoginSmsSender aliyun = mock(AliyunLoginSmsSender.class);
    private final TencentLoginSmsSender tencent = mock(TencentLoginSmsSender.class);
    private final MessageAuditHasher auditHasher = new MessageAuditHasher("fixture-iam-audit-key-32-characters");
    private final LoginSmsDispatcher dispatcher = new LoginSmsDispatcher(
        providers, templates, aliyun, tencent, auditHasher);

    @Test
    void dispatchesAliyunLoginTemplate() {
        IamMessageProvider provider = provider("ALIYUN");
        IamMessageTemplate template = template("ALIYUN");
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

        IamMessageProvider provider = provider("TENCENT");
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

        org.assertj.core.api.Assertions.assertThat(first).hasSize(64).doesNotContain("13800000000")
            .isNotEqualTo(second);
    }

    private IamMessageProvider provider(String type) {
        IamMessageProvider provider = new IamMessageProvider();
        provider.setChannel("SMS");
        provider.setProviderType(type);
        provider.setCredential("fixture-secret");
        provider.setEnabled(true);
        return provider;
    }

    private IamMessageTemplate template(String type) {
        IamMessageTemplate template = new IamMessageTemplate();
        template.setChannel("SMS");
        template.setTemplateKey("SMS_LOGIN");
        template.setProviderType(type);
        template.setLanguageCode("zh-CN");
        template.setProviderTemplateCode("fixture-template");
        template.setEnabled(true);
        return template;
    }
}
