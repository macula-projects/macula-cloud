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

import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HTTP兼容边界的冲突拒绝、非秘密视图和资源管理内部登记投影。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationWireAdapterTest {
    /** 不接受两个相互冲突的协议权威或隐藏的第二份配置。 */
    @Test
    void rejectsConflictingLegacyProtocolFields() {
        var fixture = new IamApplicationServiceImplTest.Fixture();
        var form = fixture.form();
        var app = new SysIamApplication();
        app.setPurpose("APPLICATION");
        var conflicting = new OAuth2ProtocolSettings();
        form.setProtocolConfigurations(List.of(new ApplicationProtocolConfiguration("OAUTH2_1", 1, false, conflicting)));
        assertThrows(ResponseStatusException.class, () -> fixture.wire.selection(form, app, new IamApplicationProtocolService.Stored(null, List.of())));
    }

    /** 明确能力输入不能被旧厂商JSON或旧允许列表静默覆盖。 */
    @Test
    void rejectsConflictingBrowserSourceInputs() {
        var fixture = new IamApplicationServiceImplTest.Fixture();
        var form = fixture.form();
        form.setBrowserCapabilityBindings(Map.of("GITHUB_OAUTH", 11L));
        form.setBrowserProviderBindings(Map.of("GITHUB", 12L));
        assertThrows(ResponseStatusException.class, () -> fixture.wire.routes(form, new IamApplicationProtocolService.Stored(null, List.of())));
        var existing = new SysIamApplicationIdentityProvider();
        existing.setLoginCapability("GITHUB_OAUTH");
        existing.setIdentityProviderId(11L);
        var absent = fixture.form();
        assertThrows(ResponseStatusException.class, () -> fixture.wire.routes(absent, new IamApplicationProtocolService.Stored(null, List.of(existing))));
    }

    /** 资源认证内部存OAUTH2_1，公开页面仍然没有业务协议、Grant或Secret。 */
    @Test
    void resourceViewKeepsBusinessProtocolAbsent() {
        var fixture = new IamApplicationServiceImplTest.Fixture();
        TransactionSynchronizationManager.initSynchronization();
        try {
            var form = fixture.form();
            form.setPurpose("RESOURCE_SERVER");
            form.setApplicationType("SERVICE");
            form.setWebLoginMode(null);
            form.setProtocol(null);
            form.setHostedLoginMethods(Set.of());
            form.getSettings().setAuthorizationGrantTypes("");
            form.getSettings().setScopes("");
            form.getSettings().setRedirectUris("");
            form.setClientSecret("synthetic-resource-secret");
            var view = fixture.service.save(null, form);
            assertThat(view.getProtocol()).isNull();
            assertThat(view.getProtocolConfigurations()).isEmpty();
            assertThat(view.getSettings().getAuthorizationGrantTypes()).isEmpty();
            assertThat(view.isSecretConfigured()).isTrue();
            assertThat(fixture.protocolRows.get(view.getId()).getProtocol()).isEqualTo("OAUTH2_1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
