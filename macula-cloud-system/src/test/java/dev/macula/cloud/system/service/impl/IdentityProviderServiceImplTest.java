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

import dev.macula.cloud.system.converter.IdentityProviderConverter;
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.vo.IdentityProviderVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证六类身份源字段、分类及敏感配置的管理边界。
 *
 * @author Rain
 * @since 2026-10-14
 */
class IdentityProviderServiceImplTest {
    private final SysIdentityProviderMapper mapper = mock(SysIdentityProviderMapper.class);
    private final IdentityProviderServiceImpl service = new IdentityProviderServiceImpl(mapper,
        Mappers.getMapper(IdentityProviderConverter.class));

    @Test
    void catalogIsTheSourceOfProviderCodesAndValidationFields() {
        var catalog = service.catalog();
        assertEquals(java.util.List.of("social", "enterprise"),
            catalog.getCategories().stream().map(category -> category.getCode()).toList());
        assertEquals(java.util.List.of("GITHUB", "ALIPAY", "WECHAT", "WECOM", "FEISHU", "DINGTALK"),
            catalog.getProviders().stream().map(provider -> provider.getCode()).toList());
        assertTrue(catalog.getProviders().stream().allMatch(provider ->
            provider.getCreateUrl().startsWith("https://") && !provider.getCreateHint().isBlank() &&
                provider.getFields().stream().allMatch(field -> !field.getHelp().isBlank())));
        var alipay = catalog.getProviders().stream().filter(provider -> provider.getCode().equals("ALIPAY"))
            .findFirst().orElseThrow();
        assertEquals("social", alipay.getCategory());
        assertEquals("/login/alipay", alipay.getCallbackUrl());
        assertTrue(alipay.getFields().stream().anyMatch(field -> field.getKey().equals("appPrivateKey")
            && field.isSecret() && field.isMultiline() && field.isRequired()
            && field.getHelp().contains("本地生成")));
        var dingtalk = catalog.getProviders().stream().filter(provider -> provider.getCode().equals("DINGTALK"))
            .findFirst().orElseThrow();
        assertTrue(dingtalk.getFields().stream().anyMatch(field -> field.getKey().equals("appKey")
            && field.getHelp().contains("Client ID")));
        assertFalse(alipay.getFields().stream().anyMatch(field -> field.getKey().equals("callbackUrl")));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsUnsupportedProviderAndMismatchedCategory() {
        IdentityProviderForm form = form("QQ", "social", Map.of());
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setProviderType("GITHUB");
        form.setCategory("enterprise");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        verifyNoInteractions(mapper);
    }

    @Test
    void savesEachProviderWithItsOwnFields() {
        when(mapper.selectCount(any())).thenReturn(0L);
        assertProvider("GITHUB", "social", Map.of("clientId", "github-id", "clientSecret", "secret"));
        assertProvider("ALIPAY", "social", Map.of("appId", "ali-id", "appPrivateKey", "private-key",
            "alipayPublicKey", "public-key"));
        assertProvider("WECHAT", "social", Map.of("appId", "wechat-id", "appSecret", "secret"));
        assertProvider("WECOM", "enterprise", Map.of("corpId", "corp-id", "agentId", "agent-id",
            "appSecret", "secret"));
        assertProvider("FEISHU", "enterprise", Map.of("appId", "feishu-id", "appSecret", "secret"));
        assertProvider("DINGTALK", "enterprise", Map.of("appKey", "ding-key", "appSecret", "secret"));
    }

    @Test
    void rejectsMissingUnknownAndNonStringConfigValues() {
        IdentityProviderForm form = form("WECOM", "enterprise", Map.of("corpId", "corp", "appSecret", "secret"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setConfig(Map.of("corpId", "corp", "agentId", "agent", "appSecret", "secret", "clientId", "other"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setConfig(Map.of("corpId", "corp", "agentId", 123, "appSecret", "secret"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setConfig(Map.of("corpId", "x".repeat(256), "agentId", "agent", "appSecret", "secret"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        verifyNoInteractions(mapper);
    }

    @Test
    void keepsCredentialOutOfJsonAndResponse() {
        when(mapper.selectCount(any())).thenReturn(0L);
        IdentityProviderVO result = service.save(null, form("ALIPAY", "social",
            Map.of("appId", "ali-id", "appPrivateKey", "private-key", "alipayPublicKey", "public-key")));
        verify(mapper).insert((SysIdentityProvider)argThat((SysIdentityProvider entity) ->
            "private-key".equals(entity.getAppSecret()) && !entity.getConfigJson().contains("private-key") &&
                entity.getConfigJson().contains("public-key")));
        assertFalse(result.getConfig().containsKey("appPrivateKey"));
        assertEquals(Boolean.TRUE, result.getConfiguredSecrets().get("appPrivateKey"));
        assertEquals("/login/alipay", result.getCallbackUrl());
    }

    @Test
    void normalizesNameAndUnsetFlagsOutsideConverter() {
        when(mapper.selectCount(any())).thenReturn(0L);
        IdentityProviderForm form = form("GITHUB", "social",
            Map.of("clientId", "github-id", "clientSecret", "secret"));
        form.setName("  GitHub Login  ");
        form.setEnabled(null);
        form.setVisible(null);

        IdentityProviderVO result = service.save(null, form);

        assertEquals("GitHub Login", result.getName());
        assertFalse(result.isEnabled());
        assertFalse(result.isVisible());
        verify(mapper).insert(argThat((SysIdentityProvider entity) ->
            "GitHub Login".equals(entity.getName()) && Boolean.FALSE.equals(entity.getEnabled()) &&
                Boolean.FALSE.equals(entity.getVisible())));
    }

    @Test
    void blankCredentialRetainsExistingValueAndTypeCannotChange() {
        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(7L);
        existing.setName("github");
        existing.setProviderType("GITHUB");
        existing.setAppId("old-id");
        existing.setAppSecret("old-secret");
        existing.setConfigJson("{\"clientId\":\"old-id\"}");
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);
        IdentityProviderForm form = form("GITHUB", "social", Map.of("clientId", "new-id", "clientSecret", ""));
        service.save(7L, form);
        assertEquals("old-secret", existing.getAppSecret());
        assertEquals("/login/github", service.get(7L).getCallbackUrl());
        form.setProviderType("WECHAT");
        assertThrows(ResponseStatusException.class, () -> service.save(7L, form));
    }

    @Test
    void readsLegacyColumnsButRejectsUnknownJsonFields() {
        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(8L);
        existing.setName("github");
        existing.setProviderType("GITHUB");
        existing.setAppId("legacy-id");
        existing.setAppSecret("legacy-secret");
        existing.setConfigJson("{}");
        when(mapper.selectById(8L)).thenReturn(existing);
        IdentityProviderVO result = service.get(8L);
        assertEquals("github", result.getName());
        assertEquals("legacy-id", result.getConfig().get("clientId"));
        assertFalse(result.getConfig().containsKey("clientSecret"));
        assertEquals(Boolean.TRUE, result.getConfiguredSecrets().get("clientSecret"));
        assertFalse(result.isEnabled());
        assertFalse(result.isVisible());
        existing.setConfigJson("{\"clientSecret\":\"plaintext\"}");
        assertThrows(ResponseStatusException.class, () -> service.get(8L));
        assertThrows(ResponseStatusException.class, () -> service.save(8L,
            form("GITHUB", "social", Map.of("clientId", "legacy-id", "clientSecret", ""))));
        verify(mapper, never()).updateById(any(SysIdentityProvider.class));
    }

    private void assertProvider(String type, String category, Map<String, Object> config) {
        IdentityProviderVO result = service.save(null, form(type, category, config));
        assertEquals(category, result.getCategory());
        assertEquals(type, result.getProviderType());
    }

    private IdentityProviderForm form(String type, String category, Map<String, Object> config) {
        IdentityProviderForm form = new IdentityProviderForm();
        form.setName(type + " Login");
        form.setProviderType(type);
        form.setCategory(category);
        form.setConfig(new HashMap<>(config));
        form.setEnabled(true);
        form.setVisible(true);
        return form;
    }
}
