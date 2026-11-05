/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

package dev.macula.cloud.system.migration;

import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式租户 SQL 及创建/删除共用应用行锁的静态契约，不替代真实数据库迁移验证。
 * @author Rain
 * @since 6.1.0
 */
class ApplicationApiKeyMigrationTest {
    @Test
    void applicationLockAlwaysIncludesExplicitTenantAndForUpdate() throws Exception {
        var method = SysApplicationApiKeyMapper.class.getMethod("lockApplication", Long.class, Long.class);
        assertThat(String.join(" ", method.getAnnotation(Select.class).value()))
            .contains("tenant_id = #{tenantId}", "id = #{appId}", "FOR UPDATE");
        assertThat(method.getAnnotation(InterceptorIgnore.class).tenantLine()).isEqualTo("true");
    }

    @Test
    void managerQueryRestrictsBothMenuAndRoleToCurrentTenant() throws Exception {
        var method = SysApplicationApiKeyMapper.class.getMethod("countManagers", Long.class, java.util.Set.class);
        assertThat(String.join(" ", method.getAnnotation(Select.class).value()))
            .contains("r.tenant_id = #{tenantId}", "m.tenant_id = #{tenantId}", "r.deleted = 0", "r.status = 1");
    }
}
