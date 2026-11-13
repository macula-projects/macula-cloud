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

import dev.macula.cloud.system.enums.IdentityBindingMappings;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import java.util.List;
import java.util.Set;
import org.mockito.ArgumentCaptor;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        Mappers.getMapper(IdentityProviderConverter.class),
        mock(SysIamApplicationIdentityProviderMapper.class));

    @Test
    void catalogIsTheSourceOfProviderCodesAndValidationFields() {
        var catalog = service.catalog();
        assertEquals(List.of("social", "enterprise", "mobile"),
            catalog.getCategories().stream().map(category -> category.getCode()).toList());
        assertEquals(List.of("GITHUB", "ALIPAY", "WECHAT", "WECOM", "FEISHU", "DINGTALK",
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
        assertProvider("WECHAT", "social", Map.of("appId", "wechat-id", "appSecret", "secret"));
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
        assertNull(result.getCallbackUrl());
    }

    @Test
    void storesOnlySpecificSourceSecretAndRetainsBlankValueOnUpdate() {
        when(mapper.selectCount(any())).thenReturn(0L);
        IdentityProviderVO result = service.save(null, form("WECHAT", "social", Map.of(
            "appId", "website-id", "appSecret", "website-secret")));

        verify(mapper).insert((SysIdentityProvider)argThat((SysIdentityProvider entity) ->
            "website-secret".equals(entity.getAppSecret()) &&
                "{}".equals(entity.getSecretConfigJson()) &&
                !entity.getConfigJson().contains("official-secret")));
        assertEquals(Map.of("appSecret", true), result.getConfiguredSecrets());
        assertFalse(result.getConfig().containsKey("officialAccountAppSecret"));

        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(9L);
        existing.setName("微信登录");
        existing.setProviderType("WECHAT");
        existing.setSourceType("WECHAT_WEBSITE");
        existing.setVersion(0L);
        existing.setAppId("website-id");
        existing.setAppSecret("website-secret");

        existing.setConfigJson("{\"appId\":\"website-id\"}");
        when(mapper.selectById(9L)).thenReturn(existing);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);

        service.save(9L, form("WECHAT", "social", Map.of(
            "appId", "website-id", "appSecret", "")));

        assertEquals("website-secret", existing.getAppSecret());
        assertEquals("{}", existing.getSecretConfigJson());
    }

    /** 验证持久化加密空值在详情及分页投影中都保持无附加凭据。 */
    @Test
    void readsLegacyEncryptedNullAndEmptyAdditionalSecrets() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "identity-source-test"),
            SysIdentityProvider.class);
        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(10L);
        existing.setProviderType("GITHUB");
        existing.setSourceType("GITHUB_OAUTH");
        existing.setAppId("github-id");
        existing.setAppSecret("decrypted-secret");
        existing.setConfigJson("{\"clientId\":\"github-id\"}");
        when(mapper.selectById(10L)).thenReturn(existing);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysIdentityProvider>(1, 20);
        page.setRecords(List.of(existing));
        page.setTotal(1);
        when(mapper.selectPage(any(), any())).thenReturn(page);
        for (String empty : new String[] {null, "null", "{}"}) {
            existing.setSecretConfigJson(empty);
            page.setRecords(List.of(existing));
            assertEquals(Map.of("clientSecret", true), service.get(10L).getConfiguredSecrets());
            assertEquals(1, service.page(1, 20, null, null, "social").getRecords().size());
        }
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
    void permitsSeparateEnabledSourcesOfTheSameProvider() {
        when(mapper.selectCount(any())).thenReturn(0L);
        service.save(null, form("GITHUB", "social", Map.of("clientId", "github-id", "clientSecret", "secret")));
        service.save(null, form("GITHUB", "social", Map.of("clientId", "other-id", "clientSecret", "other-secret")));
        verify(mapper, times(2)).insert(any(SysIdentityProvider.class));
    }

    @Test
    void updatingTheEnabledProviderDoesNotConflictWithItself() {
        SysIdentityProvider existing = new SysIdentityProvider();
        existing.setId(7L);
        existing.setName("GitHub Login");
        existing.setProviderType("GITHUB");
        existing.setSourceType("GITHUB_OAUTH");
        existing.setVersion(0L);
        existing.setAppId("github-id");
        existing.setAppSecret("old-secret");
        existing.setConfigJson("{\"clientId\":\"github-id\"}");
        existing.setEnabled(true);
        existing.setVisible(true);
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);

        service.save(7L, form("GITHUB", "social",
            Map.of("clientId", "github-id", "clientSecret", "")));

        verify(mapper).update(eq(existing), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
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
        existing.setSourceType("GITHUB_OAUTH");
        existing.setVersion(0L);
        existing.setAppId("old-id");
        existing.setAppSecret("old-secret");
        existing.setConfigJson("{\"clientId\":\"old-id\"}");
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        IdentityProviderForm form = form("GITHUB", "social", Map.of("clientId", "old-id", "clientSecret", ""));
        service.save(7L, form);
        assertEquals("old-secret", existing.getAppSecret());
        assertEquals("/login/github", service.get(7L).getCallbackUrl());
        form.setProviderType("WECHAT");
        assertThrows(ResponseStatusException.class, () -> service.save(7L, form));
    }

    @Test
    void untypedSourceRequiresExplicitReconfigurationAndRejectsPublicSecrets() {
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
        assertTrue(result.getConfig().isEmpty());
        assertFalse(result.getConfig().containsKey("clientSecret"));
        assertTrue(result.getConfiguredSecrets().isEmpty());
        assertNull(result.getSourceType());
        assertFalse(result.isEnabled());
        assertFalse(result.isVisible());
        existing.setConfigJson("{\"clientSecret\":\"plaintext\"}");
        assertThrows(ResponseStatusException.class, () -> service.get(8L));
        assertThrows(ResponseStatusException.class, () -> service.save(8L,
            form("GITHUB", "social", Map.of("clientId", "legacy-id", "clientSecret", ""))));
        verify(mapper, never()).updateById(any(SysIdentityProvider.class));
    }

    /** 企业来源没有默认自动绑定；开启必须声明该来源支持的规则。 */
    @Test
    void autoBindingRequiresExplicitSupportedRule() {
        when(mapper.selectCount(any())).thenReturn(0L);
        var form = form("WECOM", "enterprise", Map.of("corpId", "corp", "agentId", "agent", "appSecret", "secret"));
        assertFalse(service.save(null, form).getAutoBindEnabled());
        form.setAutoBindEnabled(true);
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setBindingRule("EMPLOYEE_NO_TO_USERNAME");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setBindingRule("MEMBER_ID_TO_USERNAME");
        assertEquals("MEMBER_ID_TO_USERNAME", service.save(null, form).getBindingRule());
        form.setAutoBindEnabled(false);
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        var social = form("GITHUB", "social", Map.of("clientId", "github", "clientSecret", "secret"));
        social.setAutoBindEnabled(true);
        social.setBindingRule("MEMBER_ID_TO_USERNAME");
        assertThrows(ResponseStatusException.class, () -> service.save(null, social));
    }

    /** 过期版本不能覆盖策略；更新竞争失败由409明确返回。 */
    @Test
    void staleVersionAndCompareAndSetFailureRejectPolicyUpdate() {
        var existing = new SysIdentityProvider();
        existing.setId(7L);
        existing.setProviderType("WECOM");
        existing.setSourceType("WECOM_WEB");
        existing.setAppId("corp");
        existing.setAppSecret("secret");
        existing.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"agent\"}");
        existing.setVersion(2L);
        when(mapper.selectById(7L)).thenReturn(existing);
        var form = form("WECOM", "enterprise", Map.of("corpId", "corp", "agentId", "agent", "appSecret", ""));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.save(7L, form)).getStatusCode().value());
        form.setVersion(2L);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.save(7L, form)).getStatusCode().value());
    }

    /** 验证各来源独立字段；企业资料自动补充必须声明固定组织。 */
    private void assertProvider(String type, String category, Map<String, Object> config) {
        var input = form(type, category, config);
        if ("FEISHU".equals(type)) {
            input.setIdentityFlow("FEISHU_OAUTH_V3");
            input.setOrganizationId("fixture-tenant");
        }
        IdentityProviderVO result = service.save(null, input);
        assertEquals(category, result.getCategory());
        assertEquals(type, result.getProviderType());
    }

    private IdentityProviderForm form(String type, String category, Map<String, Object> config) {
        IdentityProviderForm form = new IdentityProviderForm();
        form.setName(type + " Login");
        form.setProviderType(type);
        form.setSourceType(switch (type) {
            case "GITHUB" -> "GITHUB_OAUTH";
            case "WECHAT" -> "WECHAT_WEBSITE";
            case "WECOM" -> "WECOM_WEB";
            case "FEISHU" -> "FEISHU_WEB";
            case "DINGTALK" -> "DINGTALK_WEB";
            case "ALIYUN_MOBILE" -> "ALIYUN_MOBILE";
            default -> "ALIPAY_APPLICATION";
        });
        form.setAutoBindEnabled(false);
        form.setVersion(0L);
        form.setCategory(category);
        form.setConfig(new HashMap<>(config));
        form.setEnabled(true);
        form.setVisible(true);
        return form;
    }
    /** 新来源不要求界面提供Flow，默认采用已核实版本；新映射可保持及显式清除。 */
    @Test
    void createsAndPreservesExplicitMappingWithInternalDefaults() {
        when(mapper.selectCount(any())).thenReturn(0L);
        var form = form("FEISHU", "enterprise", Map.of("appId", "app", "appSecret", "secret"));
        form.setSourceType("FEISHU_WEB");
        form.setIdentityFlow(null);
        form.setDirectoryPolicy(null);
        form.setOrganizationId("tenant-fixed");
        form.setAutoBindEnabled(true);
        form.setBindingSourceField("email");
        form.setBindingTargetField("email");
        var saved = service.save(null, form);
        assertEquals("FEISHU_OAUTH_V3", saved.getIdentityFlow());
        assertEquals("AUTO", saved.getDirectoryPolicy());
        assertEquals("email", saved.getBindingSourceField());
        assertNull(saved.getBindingRule());
        var captured = ArgumentCaptor.forClass(SysIdentityProvider.class);
        verify(mapper).insert(captured.capture());
        var stored = captured.getValue();
        stored.setId(7L);
        when(mapper.selectById(7L)).thenReturn(stored);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var update = form("FEISHU", "enterprise", Map.of("appId", "app", "appSecret", ""));
        update.setAutoBindEnabled(true);
        update.setVersion(stored.getVersion());
        var retained = service.save(7L, update);
        assertEquals("email", retained.getBindingTargetField());
        assertEquals("AUTO", retained.getDirectoryPolicy());
        update.setVersion(stored.getVersion());
        update.setBindingRule(null);
        assertThrows(ResponseStatusException.class, () -> service.save(7L, update));
        update.setAutoBindEnabled(false);
        update.setBindingSourceField(null);
        update.setBindingTargetField(null);
        var cleared = service.save(7L, update);
        assertNull(cleared.getBindingSourceField());
        assertNull(cleared.getBindingTargetField());
    }

    /** 新旧客户端不能丢掉新规则或用半组更新绕过校验。 */
    @Test
    void rejectsPartialAndConflictingMappingWrites() {
        var form = form("WECOM", "enterprise", Map.of("corpId", "corp", "agentId", "1001", "appSecret", "secret"));
        form.setAutoBindEnabled(true);
        form.setBindingSourceField("member_id");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setBindingTargetField("username");
        form.setBindingRule("VERIFIED_MOBILE_TO_MOBILE");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setBindingSourceField(null);
        form.setBindingTargetField(null);
        form.setBindingRule("MEMBER_ID_TO_USERNAME");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
    }

    /** 隐藏组织策略后，企微固定企业来自同一来源已规范化配置，不能因首尾空白生成不同身份域。 */
    @Test
    void derivesFixedWecomOrganizationFromNormalizedApplicationConfig() {
        when(mapper.selectCount(any())).thenReturn(0L);
        var input = form("WECOM","enterprise",Map.of("corpId"," corp-fixed ","agentId","1001","appSecret","secret"));
        var saved = service.save(null,input);
        assertEquals("corp-fixed",saved.getOrganizationId());
        assertEquals("AUTO",saved.getDirectoryPolicy());
    }

    /** 同一企微注册一次填写凭据，显式多选能力；旧PUT不能把新增能力清空。 */
    @Test
    void oneWecomRegistrationSharesCredentialsAndPreservesCapabilitiesForOldPut() {
        when(mapper.selectCount(any())).thenReturn(0L);
        var input = form("WECOM", "enterprise", Map.of("corpId", "corp", "agentId", "1001", "appSecret", "secret"));
        input.setCredentialType("WECOM_APPLICATION");
        input.setLoginCapabilities(Set.of("WECOM_BROWSER", "WECOM_WEBVIEW", "WECOM_MINIAPP"));
        var saved = service.save(null, input);
        assertEquals("WECOM_APPLICATION", saved.getCredentialType());
        assertEquals(input.getLoginCapabilities(), saved.getLoginCapabilities());
        var capture = ArgumentCaptor.forClass(SysIdentityProvider.class);
        verify(mapper).insert(capture.capture());
        var stored = capture.getValue();
         stored.setId(7L);
        assertEquals("secret", stored.getAppSecret());
        when(mapper.selectById(7L)).thenReturn(stored);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var oldPut = form("WECOM", "enterprise", Map.of("corpId", "corp", "agentId", "1001", "appSecret", ""));
        oldPut.setVersion(stored.getVersion());
        assertEquals(input.getLoginCapabilities(), service.save(7L, oldPut).getLoginCapabilities());
        var disabled = form("WECOM", "enterprise", Map.of("corpId", "corp", "agentId", "1001", "appSecret", ""));
        disabled.setVersion(stored.getVersion());
         disabled.setLoginCapabilities(Set.of());
        assertTrue(service.save(7L, disabled).getLoginCapabilities().isEmpty());
    }

    /** 微信各平台注册独立，钉钉同一应用三种能力共用一套凭据。 */
    @Test
    void rejectsCrossWechatRegistrationAndSharesDingtalkSdkCredentials() {
        var website = form("WECHAT", "social", Map.of("appId", "site", "appSecret", "secret"));
        website.setSourceType("WECHAT_WEBSITE");
        website.setLoginCapabilities(Set.of("WECHAT_MOBILE_APP"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, website));
        var ding = form("DINGTALK", "enterprise", Map.of("appKey", "ding", "corpId", "corp", "appSecret", "secret"));
        when(mapper.selectCount(any())).thenReturn(0L);
        ding.setLoginCapabilities(Set.of("DINGTALK_WEB", "DINGTALK_SDK", "DINGTALK_WEBVIEW"));
        var saved = service.save(null, ding);
        assertEquals("DINGTALK_APPLICATION", saved.getCredentialType());
        assertEquals(ding.getLoginCapabilities(), saved.getLoginCapabilities());
        var captured = ArgumentCaptor.forClass(SysIdentityProvider.class);
        verify(mapper).insert(captured.capture());
        assertEquals("ding", captured.getValue().getAppId());
        assertEquals("secret", captured.getValue().getAppSecret());
        assertEquals("ding", saved.getConfig().get("appKey"));
        assertFalse(saved.getConfig().containsKey("appSecret"));
    }

    /** 真实Jackson输入、持久化与VO回显保留多条规则，旧写入不得截断。 */
    @Test
    void savesCompleteMappingArrayAndRejectsLossyLegacyUpdates() {
        when(mapper.selectCount(any())).thenReturn(0L);
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var input = form("FEISHU", "enterprise", Map.of("appId", "app", "appSecret", "secret"));
        input.setSourceType("FEISHU_WEB");
        input.setOrganizationId("tenant-fixed");
        input.setAutoBindEnabled(true);
        var wire = json.readValue("{\"bindingMappings\":[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\"},{\"sourceField\":\"email\",\"targetField\":\"email\"}]}", IdentityProviderForm.class);
        input.setBindingMappings(wire.getBindingMappings());
        var saved = service.save(null, input);
        assertEquals(2, saved.getBindingMappings().size());
        assertNull(saved.getBindingSourceField());
        var captured = ArgumentCaptor.forClass(SysIdentityProvider.class);
        verify(mapper).insert(captured.capture());
        var stored = captured.getValue();
        assertEquals(saved.getBindingMappings(), IdentityBindingMappings.toWire(
            IdentityBindingMappings.decode(stored.getBindingMappingsJson(), null, null, null)));
        stored.setId(7L);
        when(mapper.selectById(7L)).thenReturn(stored);
        when(mapper.update(any(SysIdentityProvider.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var update = form("FEISHU", "enterprise", Map.of("appId", "app", "appSecret", ""));
        update.setAutoBindEnabled(null);
        update.setVersion(stored.getVersion());
        var retained = service.save(7L, update);
        assertEquals(saved.getBindingMappings(), retained.getBindingMappings());
        update.setVersion(stored.getVersion());
        update.setBindingSourceField("email");
        update.setBindingTargetField("email");
        assertThrows(ResponseStatusException.class, () -> service.save(7L, update));
        update.setBindingMappings(List.of());
        update.setBindingSourceField(null);
        update.setBindingTargetField(null);
        update.setAutoBindEnabled(false);
        var cleared = service.save(7L, update);
        assertTrue(cleared.getBindingMappings().isEmpty());
    }
    /** AppLink能力不需要身份源回调，历史坏地址也不参与保存校验。 */
    @Test
    void enablesAppLinkWithoutSourceCallback() {
        when(mapper.selectCount(any())).thenReturn(0L);
        var input = form("FEISHU", "enterprise", Map.of("appId", "app", "appSecret", "secret"));
        input.setSourceType("FEISHU_WEB");
        input.setOrganizationId("tenant-fixed");
        input.setLoginCapabilities(Set.of("FEISHU_WEB", "FEISHU_APPLINK", "FEISHU_WEBVIEW"));
        var saved = service.save(null, input);
        assertTrue(saved.getLoginCapabilities().contains("FEISHU_APPLINK"));
        assertNull(saved.getOauthRedirectUri());
    }

}
