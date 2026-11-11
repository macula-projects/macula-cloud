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
package dev.macula.cloud.iam.config;

import dev.macula.cloud.iam.authentication.mfa.MfaCompletionPolicy;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 默认不增加因子要求；后续MFA模块只需替换策略Bean，不能自报acr/amr。
 *
 * @author Rain
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
public class MfaExtensionConfiguration {
    /**
     * 默认策略直接继续，不能声称已经实现真实多因素认证。
     */
    @Bean
    @ConditionalOnMissingBean(MfaCompletionPolicy.class)
    MfaCompletionPolicy mfaCompletionPolicy() {
        return context -> {
        };
    }

    /**
     * 配置读取可延迟缺失；实际出现来源证明时服务仍会闭合拒绝。
     */
    @Bean
    MfaCompletionService mfaCompletionService(MfaCompletionPolicy policy,
            ObjectProvider<IdentityProviderConfigService> sources) {
        return new MfaCompletionService(policy, sources.getIfAvailable());
    }
}
