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
package dev.macula.cloud.iam.authentication.verification;

import dev.macula.cloud.iam.authentication.email.EmailCodeService;
import dev.macula.cloud.iam.authentication.sms.SmsCodeService;
import dev.macula.cloud.iam.config.DefaultSecurityConfiguration;
import dev.macula.cloud.iam.message.dispatch.LoginMailDispatcher;
import dev.macula.cloud.iam.message.dispatch.LoginSmsDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证双通道适配、具名装配及失败关闭，不连接真实投递设施。
 *
 * @author Macula
 * @since 6.1.0
 */
class VerificationCodeChannelServicesTest {
    /** 两通道缺少任一设施都必须采用同一拒绝策略。 */
    @ParameterizedTest
    @EnumSource(VerificationChannel.class)
    void missingFacilitiesFailClosed(VerificationChannel channel) {
        for (boolean enginePresent : new boolean[] {false, true}) {
            for (boolean deliveryPresent : new boolean[] {false, true}) {
                var beans = new DefaultListableBeanFactory();
                var store = mock(VerificationCodeStore.class);
                var engine = new VerificationCodeService(store);
                var sms = mock(LoginSmsDispatcher.class);
                var mail = mock(LoginMailDispatcher.class);
                if (enginePresent) {
                    beans.registerSingleton("engine", engine);
                }
                if (deliveryPresent) {
                    beans.registerSingleton("sms", sms);
                    beans.registerSingleton("mail", mail);
                }
                var config = new DefaultSecurityConfiguration();
                var normalizer = new UserIdentifierNormalizer();
                var port = channel == VerificationChannel.SMS
                        ? config.smsCodeService(beans.getBeanProvider(VerificationCodeService.class),
                                beans.getBeanProvider(LoginSmsDispatcher.class), normalizer, new SmsVerificationProperties())
                        : config.emailCodeService(beans.getBeanProvider(VerificationCodeService.class),
                                beans.getBeanProvider(LoginMailDispatcher.class), normalizer, new EmailVerificationProperties());
                assertThat(port.isAvailable()).isEqualTo(enginePresent && deliveryPresent);
                if (!port.isAvailable()) {
                    assertThat(port).isInstanceOf(UnavailableVerificationCodeService.class);
                    assertThat(port.verifyCode("target", "123456", VerificationCodeContext.login("client"))).isFalse();
                    assertThat(port.verifyCode("target", "123456", VerificationCodeContext.binding("client"))).isFalse();
                    assertThatThrownBy(() -> port.sendCode("target", "127.0.0.1", VerificationCodeContext.login("client")))
                            .isInstanceOf(IllegalStateException.class);
                }
                verifyNoInteractions(store, sms, mail);
            }
        }
    }

    /** 多个公共端口不能按类型猜测；具名注入始终固定通道。 */
    @Test
    void channelNamesResolveDistinctPorts() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("smsCodeService", VerificationCodeChannelService.class, UnavailableVerificationCodeService::new);
            context.registerBean("emailCodeService", VerificationCodeChannelService.class, UnavailableVerificationCodeService::new);
            context.refresh();
            assertThat(context.getBean("smsCodeService", VerificationCodeChannelService.class))
                    .isNotSameAs(context.getBean("emailCodeService", VerificationCodeChannelService.class));
            assertThatThrownBy(() -> context.getBean(VerificationCodeChannelService.class))
                    .isInstanceOf(NoUniqueBeanDefinitionException.class);
        }
    }

    /** 邮件显示名来自受信上下文；HTML转义仍由邮件投递器负责。 */
    @Test
    void emailActivatesOnlyAfterDeliveryAndRejectsBindingBeforeStore() {
        var store = mock(VerificationCodeStore.class);
        var mail = mock(LoginMailDispatcher.class);
        var settings = new EmailVerificationProperties();
        var service = new EmailCodeService(new VerificationCodeService(store), mail, new UserIdentifierNormalizer(), settings);
        var scope = new VerificationCodeScope(VerificationChannel.EMAIL, VerificationCodePurpose.LOGIN, "a");
        var login = VerificationCodeContext.login("a", "Application <A>");
        when(store.issue(scope, "alice@example.test", "127.0.0.1", settings)).thenReturn("123456");
        service.sendCode(" Alice@Example.test ", "127.0.0.1", login);
        var order = inOrder(store, mail);
        order.verify(store).issue(scope, "alice@example.test", "127.0.0.1", settings);
        order.verify(mail).send("alice@example.test", "123456", "Application <A>", settings.getTtl());
        order.verify(store).activate(scope, "alice@example.test", "123456", settings);
        doThrow(new IllegalStateException("delivery rejected")).when(mail)
                .send("alice@example.test", "123456", "Application <A>", settings.getTtl());
        assertThatThrownBy(() -> service.sendCode("alice@example.test", "127.0.0.1", login))
                .isInstanceOf(IllegalStateException.class);
        verify(store).activate(scope, "alice@example.test", "123456", settings);
        var binding = VerificationCodeContext.binding("a");
        assertThatThrownBy(() -> service.sendCode("alice@example.test", "127.0.0.1", binding))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.verifyCode("alice@example.test", "123456", binding)).isFalse();
        verify(store, never()).issue(new VerificationCodeScope(VerificationChannel.EMAIL,
                VerificationCodePurpose.IDENTITY_BINDING, "a"), "alice@example.test", "127.0.0.1", settings);
        verify(store, never()).consume(new VerificationCodeScope(VerificationChannel.EMAIL,
                VerificationCodePurpose.IDENTITY_BINDING, "a"), "alice@example.test", "123456", settings);
    }

    /** 短信继续区分browser、Client与绑定用途，不修改原Scope算法。 */
    @Test
    void smsScopesDoNotFallBackAcrossPurposeOrClient() {
        var store = mock(VerificationCodeStore.class);
        var settings = new SmsVerificationProperties();
        var service = new SmsCodeService(new VerificationCodeService(store), mock(LoginSmsDispatcher.class),
                new UserIdentifierNormalizer(), settings);
        var binding = new VerificationCodeScope(VerificationChannel.SMS, VerificationCodePurpose.IDENTITY_BINDING, "a");
        when(store.consume(binding, "13800000000", "123456", settings)).thenReturn(true);
        assertThat(service.verifyCode("138-0000-0000", "123456", VerificationCodeContext.binding("a"))).isTrue();
        assertThat(service.verifyCode("13800000000", "123456", VerificationCodeContext.login("a"))).isFalse();
        assertThat(service.verifyCode("13800000000", "123456", VerificationCodeContext.binding("b"))).isFalse();
        assertThat(service.verifyCode("13800000000", "123456", VerificationCodeContext.login(null))).isFalse();
        assertThatThrownBy(() -> VerificationCodeContext.binding(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VerificationCodeContext.binding(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new VerificationCodeScope(VerificationChannel.SMS, VerificationCodePurpose.LOGIN, null).context())
                .isEqualTo("login\0browser");
    }
}
