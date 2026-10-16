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

import dev.macula.cloud.system.form.PasswordPolicyForm;
import dev.macula.cloud.system.mapper.SysPasswordPolicyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 密码策略组合校验测试，不启动外部服务。
 *
 * @author Rain
 * @since 2026-10-13
 */
class PasswordPolicyServiceImplTest {
    private final PasswordPolicyServiceImpl service = new PasswordPolicyServiceImpl(mock(SysPasswordPolicyMapper.class));

    @Test
    void rejectsLengthAndReminderConflicts() {
        assertThrows(ResponseStatusException.class, () -> service.save(new PasswordPolicyForm(20, 8, 2,
            0, 0, 3, true, 0, List.of(), true, List.of())));
        assertThrows(ResponseStatusException.class, () -> service.save(new PasswordPolicyForm(8, 20, 2,
            10, 10, 3, true, 0, List.of(), true, List.of())));
    }

    @Test
    void defaultPolicyDoesNotClaimRuntimeEffect() {
        assertEquals(8, service.get().getMinLength());
    }
}
