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

import dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 有界类型化持久化及完整快照的并发保护。
 * @author Rain
 * @since 6.1.0
 */
class ApplicationProtocolConfigurationServiceTest {
    private final SysApplicationProtocolConfigMapper mapper = mock(SysApplicationProtocolConfigMapper.class);
    private final SysOAuth2ClientExternalProviderMapper sources = mock(SysOAuth2ClientExternalProviderMapper.class);
    private final ApplicationProtocolConfigurationService service = new ApplicationProtocolConfigurationService(mapper, sources);

    /** 关闭草稿也不能携带重复、未知或超大配置。 */
    @Test
    void validatesAllEnvelopesBeforePersistence() {
        var profile = new ApplicationProtocolConfiguration("OAUTH2_1", 1, false, new OAuth2ProtocolSettings());
        assertThrows(IllegalArgumentException.class, () -> service.validate(List.of(profile, profile), "WEB_SERVER", false));
        var settings = OAuth2ProtocolConfiguration.settings(profile);
         settings.setRedirectUris("x".repeat(32769));
        assertThrows(IllegalArgumentException.class, () -> service.validate(List.of(profile), "WEB_SERVER", false));
        verifyNoInteractions(mapper);
    }

    /** 子协议、共享密钥和来源变化均使旧快照失效，不泄露字段内容。 */
    @Test
    void fullSnapshotRejectsChangesOutsideCurrentProtocolTab() {
        var app = new SysOAuth2ClientConfig();
         app.setId(1L);
         app.setClientId("client");
         app.setClientSecret("synthetic-hash");
        var row = new SysApplicationProtocolConfig();
         row.setProtocol("OIDC");
         row.setSchemaVersion(1);
         row.setConfiguration("{}");
        when(mapper.selectList(any())).thenReturn(List.of(row));
         when(sources.selectList(any())).thenReturn(List.of());
        String version = service.version(app);
         assertFalse(version.contains("synthetic"));
        assertDoesNotThrow(() -> service.checkVersion(app, version));
        row.setConfiguration("{\"scopes\":\"openid\"}");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.checkVersion(app, version));
        String next = service.version(app);
         app.setClientSecret("rotated-synthetic-hash");
        assertNotEquals(next, service.version(app));
        next = service.version(app);
         var binding = new SysOAuth2ClientExternalProvider();
         binding.setCodeType("WECOM_APP_CODE");
         binding.setIdentityProviderId(7L);
        when(sources.selectList(any())).thenReturn(List.of(binding));
         assertNotEquals(next, service.version(app));
    }

    /** 数据库存储的未知字段不得被全局宽松ObjectMapper忽略。 */
    @Test
    void rejectsUnknownPersistedSettings() {
        var row = new SysApplicationProtocolConfig();
         row.setProtocol("OIDC");
         row.setSchemaVersion(1);
        row.setConfiguration("{\"kind\":\"oauth2\",\"unregisteredSetting\":true}");
         when(mapper.selectList(any())).thenReturn(List.of(row));
        assertThrows(RuntimeException.class, () -> service.list(1L));
    }
}
