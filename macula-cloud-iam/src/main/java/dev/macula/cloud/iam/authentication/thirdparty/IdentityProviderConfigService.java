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
package dev.macula.cloud.iam.authentication.thirdparty;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysUserIdentityBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 从数据库读取唯一启用的身份源配置及全局账号绑定。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class IdentityProviderConfigService {
    private final SysIdentityProviderMapper providerMapper;
    private final SysUserIdentityBindingMapper bindingMapper;
    private final JsonMapper json = JsonMapper.builder().build();

    public boolean isVisible(String providerType) {
        try {
            return providerMapper.selectCount(new LambdaQueryWrapper<SysIdentityProvider>()
                .eq(SysIdentityProvider::getProviderType, providerType)
                .eq(SysIdentityProvider::getEnabled, true)
                .eq(SysIdentityProvider::getVisible, true)) == 1;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public ProviderConfig requireEnabled(String providerType) {
        try {
            List<SysIdentityProvider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<SysIdentityProvider>()
                    .eq(SysIdentityProvider::getProviderType, providerType)
                    .eq(SysIdentityProvider::getEnabled, true));
            if (providers.size() != 1) throw unavailable();
            SysIdentityProvider provider = providers.get(0);
            if (!StringUtils.hasText(provider.getAppSecret())) throw unavailable();
            Map<String, String> config = StringUtils.hasText(provider.getConfigJson())
                ? json.readValue(provider.getConfigJson(), new TypeReference<>() { }) : Map.of();
            return new ProviderConfig(providerType, provider.getAppId(), provider.getAppSecret(), config);
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    public String requireBoundUsername(String providerType, String providerSubject) {
        List<SysUserIdentityBinding> bindings = bindingMapper.selectList(
            new LambdaQueryWrapper<SysUserIdentityBinding>()
                .eq(SysUserIdentityBinding::getProviderType, providerType)
                .eq(SysUserIdentityBinding::getProviderSubject, providerSubject));
        if (bindings.size() != 1 || !StringUtils.hasText(bindings.get(0).getUsername())) {
            throw new AuthenticationServiceException("第三方账号尚未绑定");
        }
        return bindings.get(0).getUsername();
    }

    private AuthenticationServiceException unavailable() {
        return new AuthenticationServiceException("第三方登录暂不可用");
    }

    public record ProviderConfig(String providerType, String appId, String appSecret,
                                 Map<String, String> values) {
        public String require(String key) {
            String value = values.get(key);
            if (!StringUtils.hasText(value)) throw new AuthenticationServiceException("第三方登录暂不可用");
            return value;
        }
    }
}
