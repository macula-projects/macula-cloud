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

import dev.macula.cloud.system.form.SecuritySettingsForm;
import dev.macula.cloud.system.mapper.SysSecuritySettingsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 全局安全配置默认值及 CSP 保存边界测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class SecuritySettingsServiceImplTest {
    private final SecuritySettingsServiceImpl service = new SecuritySettingsServiceImpl(mock(SysSecuritySettingsMapper.class));

    @Test
    void defaultSettingsAreNotYetEffective() {
        assertEquals(30, service.get().getSessionTimeoutMinutes());
        assertFalse(service.get().isEffective());
    }

    @Test
    void rejectsHeaderInjectionInFutureCspValue() {
        assertThrows(ResponseStatusException.class, () -> service.save(new SecuritySettingsForm(1, 30, 0,
            300, 15, 5, 30, "default-src 'self'\r\nX-Test: injected")));
    }
}
