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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证身份源字段、分类及敏感配置的管理边界。
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
        assertEquals(java.util.List.of("social", "enterprise", "mobile"),
            catalog.getCategories().stream().map(category -> category.getCode()).toList());
        assertEquals(java.util.List.of("GITHUB", "ALIPAY", "WECHAT", "WECOM", "FEISHU", "DINGTALK",
                "ALIYUN_MOBILE"),
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
        assertTrue(dingtalk.getFields().stream().anyMatch(field -> field.getKey().equals("corpId")
            && field.isRequired() && !field.isSecret()));
        assertFalse(dingtalk.getFields().stream().anyMatch(field -> field.getKey().equals("webviewBridgeUri")));
        var feishu = catalog.getProviders().stream().filter(provider -> provider.getCode().equals("FEISHU"))
            .findFirst().orElseThrow();
        assertFalse(feishu.getFields().stream().anyMatch(field -> field.getKey().equals("webviewBridgeUri")));
        var wechat = catalog.getProviders().stream().filter(provider -> provider.getCode().equals("WECHAT"))
            .findFirst().orElseThrow();
        assertTrue(wechat.getFields().stream().anyMatch(field -> field.getKey().equals("officialAccountAppId")
            && field.isRequired() && !field.isSecret()));
        assertTrue(wechat.getFields().stream().anyMatch(field -> field.getKey().equals("officialAccountAppSecret")
            && field.isRequired() && field.isSecret()));
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
        assertProvider("WECHAT", "social", Map.of("appId", "wechat-id", "appSecret", "secret",
            "officialAccountAppId", "official-id", "officialAccountAppSecret", "official-secret"));
        assertProvider("WECOM", "enterprise", Map.of("corpId", "corp-id", "agentId", "agent-id",
            "appSecret", "secret"));
        assertProvider("FEISHU", "enterprise", Map.of("appId", "feishu-id", "appSecret", "secret"));
        assertProvider("DINGTALK", "enterprise", Map.of("appKey", "ding-key", "appSecret", "secret",
            "corpId", "ding-corp"));
        assertProvider("ALIYUN_MOBILE", "mobile", Map.of("accessKeyId", "aliyun-key",
            "accessKeySecret", "secret"));
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
    void storesAdditionalSecretsSeparatelyAndRetainsBlankValuesOnUpdate() {
        when(mapper.selectCount(any())).thenReturn(0L);
        IdentityProviderVO result = service.save(null, form("WECHAT", "social", Map.of(
            "appId", "website-id", "appSecret", "website-secret",
            "officialAccountAppId", "official-id", "officialAccountAppSecret", "official-secret")));

        verify(mapper).insert((SysIdentityProvider)argThat((SysIdentityProvider entity) ->
            "website-secret".equals(entity.getAppSecret()) &&
                "{\"officialAccountAppSecret\":\"official-secret\"}".equals(entity.getSecretConfigJson()) &&
                !entity.getConfigJson().contains("official-secret")));
        assertEquals(Map.of("appSecret", true, "officialAccountAppSecret", true,
            "mobileAppSecret", false, "miniAppSecret", false), result.getConfiguredSecrets());
        assertFalse(result.getConfig().containsKey("officialAccountAppSecret"));

        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(9L);
        existing.setName("微信登录");
        existing.setProviderType("WECHAT");
        existing.setAppId("website-id");
        existing.setAppSecret("website-secret");
        existing.setSecretConfigJson("{\"officialAccountAppSecret\":\"old-official-secret\"}");
        existing.setConfigJson("{\"appId\":\"website-id\",\"officialAccountAppId\":\"official-id\"}");
        when(mapper.selectById(9L)).thenReturn(existing);

        service.save(9L, form("WECHAT", "social", Map.of(
            "appId", "website-id", "appSecret", "",
            "officialAccountAppId", "official-id", "officialAccountAppSecret", "")));

        assertEquals("website-secret", existing.getAppSecret());
        assertEquals("{\"officialAccountAppSecret\":\"old-official-secret\"}", existing.getSecretConfigJson());
    }

    @Test
    void rejectsUnknownOrMalformedAdditionalSecretJson() {
        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(10L);
        existing.setName("微信登录");
        existing.setProviderType("WECHAT");
        existing.setAppId("website-id");
        existing.setAppSecret("website-secret");
        existing.setConfigJson("{\"appId\":\"website-id\",\"officialAccountAppId\":\"official-id\"}");
        when(mapper.selectById(10L)).thenReturn(existing);

        existing.setSecretConfigJson("{\"unknownSecret\":\"value\"}");
        assertThrows(ResponseStatusException.class, () -> service.get(10L));
        existing.setSecretConfigJson("[]");
        assertThrows(ResponseStatusException.class, () -> service.get(10L));
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
    void rejectsSecondEnabledProviderOfTheSameType() {
        when(mapper.selectCount(any())).thenReturn(0L, 1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.save(null, form("GITHUB", "social",
                Map.of("clientId", "github-id", "clientSecret", "secret"))));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("同类型仅允许一个启用配置", error.getReason());
        verify(mapper, never()).insert(any(SysIdentityProvider.class));
    }

    @Test
    void updatingTheEnabledProviderDoesNotConflictWithItself() {
        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(7L);
        existing.setName("GitHub Login");
        existing.setProviderType("GITHUB");
        existing.setAppId("github-id");
        existing.setAppSecret("old-secret");
        existing.setConfigJson("{\"clientId\":\"github-id\"}");
        existing.setEnabled(true);
        existing.setVisible(true);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);

        service.save(7L, form("GITHUB", "social",
            Map.of("clientId", "github-id", "clientSecret", "")));

        verify(mapper).updateById(existing);
    }

    @Test
    void mapsDatabaseUniquenessRaceToConflict() {
        when(mapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("generated enabled-provider key"))
            .when(mapper).insert(any(SysIdentityProvider.class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.save(null, form("GITHUB", "social",
                Map.of("clientId", "github-id", "clientSecret", "secret"))));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("身份提供商配置冲突", error.getReason());
    }

    @Test
    void disabledProviderMayCoexist() {
        when(mapper.selectCount(any())).thenReturn(0L);
        IdentityProviderForm form = form("GITHUB", "social",
            Map.of("clientId", "github-id", "clientSecret", "secret"));
        form.setEnabled(false);

        service.save(null, form);

        verify(mapper, times(1)).selectCount(any());
        verify(mapper).insert(any(SysIdentityProvider.class));
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
