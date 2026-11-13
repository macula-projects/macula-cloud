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
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationProtocolConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 单协议存储与完整ETag边界，不依赖Controller或旧共享列。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationProtocolServiceTest {
    /** 所有主/协议/来源/凭据字段均影响版本，普通JSON忽略凭据也不能漏掉Secret变化。 */
    @Test
    void versionIncludesCredentialsAndSourceRelations() {
        var fixture = new IamApplicationServiceImplTest.Fixture();
        var app = new SysIamApplication();
        app.setId(1L);
        app.setApplicationCode("fixture");
        var row = new SysIamApplicationProtocolConfig();
        row.setCredentialHash("synthetic-first");
        var stored = new IamApplicationProtocolService.Stored(row, List.of());
        String first = fixture.protocols.version(app, stored);
        row.setCredentialHash("synthetic-second");
        String second = fixture.protocols.version(app, stored);
        assertThat(first).isNotEqualTo(second);
        assertThrows(ResponseStatusException.class, () -> fixture.protocols.checkVersion(app, stored, first));
        var source = new SysIamApplicationIdentityProvider();
        source.setApplicationId(1L);
        source.setLoginCapability("GITHUB_OAUTH");
        source.setIdentityProviderId(2L);
        assertThat(fixture.protocols.version(app, new IamApplicationProtocolService.Stored(row, List.of(source)))).isNotEqualTo(second);
        app.setWebLoginMode("IN_APP");
        assertThat(fixture.protocols.version(app, stored)).isNotEqualTo(second);
    }

    /** 无协议编辑不能删除不可执行槽；原始凭据只有明确选择协议后才参与新设置。 */
    @Test
    void preservingUnassignedSlotNeverDeletesOrInterpretsShadow() {
        var fixture = new IamApplicationServiceImplTest.Fixture();
        var app = new SysIamApplication();
        app.setId(1L);
        var row = new SysIamApplicationProtocolConfig();
        row.setSchemaVersion(0);
        fixture.protocols.save(app, new IamApplicationProtocolService.Stored(row, List.of()), null, "synthetic-existing");
        verify(fixture.rows, never()).deleteById(any());
        verify(fixture.rows, never()).insert(any(SysIamApplicationProtocolConfig.class));
    }
}
