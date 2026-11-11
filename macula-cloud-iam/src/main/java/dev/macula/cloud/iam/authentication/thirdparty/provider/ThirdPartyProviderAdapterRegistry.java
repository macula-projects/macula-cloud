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
package dev.macula.cloud.iam.authentication.thirdparty.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;

/**
 * 为每个厂商选择唯一策略；配置错误在启动时暴露，不按注册顺序试探其他平台。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
public final class ThirdPartyProviderAdapterRegistry {
    private final Map<String, ThirdPartyProviderAdapter> adapters;

    /**
     * 拒绝重复厂商，防止装配顺序改变可信凭据来源。
     */
    public ThirdPartyProviderAdapterRegistry(List<ThirdPartyProviderAdapter> candidates) {
        Map<String, ThirdPartyProviderAdapter> entries = new HashMap<>();
        for (ThirdPartyProviderAdapter adapter : candidates) {
            if (entries.putIfAbsent(adapter.providerType(), adapter) != null) {
                throw new IllegalStateException("第三方厂商存在重复适配器");
            }
        }
        adapters = Map.copyOf(entries);
    }

    /**
     * 只选择精确厂商，不回退到默认来源。
     */
    public ThirdPartyProviderAdapter require(String providerType) {
        ThirdPartyProviderAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new AuthenticationServiceException("第三方认证方式不受支持");
        }
        return adapter;
    }
}
