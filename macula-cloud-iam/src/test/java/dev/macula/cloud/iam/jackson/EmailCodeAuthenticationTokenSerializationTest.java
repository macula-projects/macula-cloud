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
package dev.macula.cloud.iam.jackson;

import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新邮箱认证主体受限往返，不复用退役type-id或保留验证码。
 *
 * @author Macula
 * @since 6.1.0
 */
class EmailCodeAuthenticationTokenSerializationTest {
    /**
     * 使用生产相同的精确允许类型，不能用测试的宽松默认多态掩盖问题。
     */
    @Test
    void restoresAuthenticatedPrincipalAndRejectsCredential() {
        var allowed = BasicPolymorphicTypeValidator.builder().allowIfSubType(SysUserDetails.class)
                .allowIfSubType(EmailCodeAuthenticationToken.class).allowIfSubType(Long.class);
        var json = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), allowed))
                .addModule(new MaculaIamJacksonModule()).build();
        var user = new SysUserDetails();
        user.setUsername("alice");
        user.setUserId(42L);
        user.setTenantId(1L);
        user.setEnabled(true);
        var factor = org.springframework.security.core.authority.FactorGrantedAuthority.fromAuthority(
                org.springframework.security.core.authority.FactorGrantedAuthority.OTT_AUTHORITY);
        var token = new EmailCodeAuthenticationToken(user, null, java.util.List.of(factor));
        String encoded = json.writeValueAsString(token);
        var restored = json.readValue(encoded, EmailCodeAuthenticationToken.class);
        assertThat(restored.isAuthenticated()).isTrue();
        assertThat(restored.getName()).isEqualTo("alice");
        assertThat(restored.getCredentials()).isNull();
        assertThat(restored.getAuthorities()).contains(factor);
        assertThat(encoded).doesNotContain("authentication.email.EmailAuthenticationToken");
        var tree = json.readTree(encoded).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) tree).put("code", "123456");
        assertThatThrownBy(() -> json.readValue(json.writeValueAsString(tree), EmailCodeAuthenticationToken.class))
                .isInstanceOf(RuntimeException.class);
    }
}
