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

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用退役前真实Jackson 2/3夹具证明旧邮箱主体可受限恢复且未知类型仍拒绝。
 *
 * @author Macula
 * @since 6.1.0
 */
class LegacyEmailPrincipalReaderTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(),
                    BasicPolymorphicTypeValidator.builder().allowIfSubType(SysUserDetails.class)
                            .allowIfSubType(Long.class)))
            .addModule(new MaculaIamJacksonModule()).build();

    /**
     * 旧格式转换只改变主体载体，保留账号、权限和请求详情。
     */
    @Test
    void restoresBothActualLegacyFormats() throws Exception {
        for (String version : new String[]{"2", "3"}) {
            Map<String, Object> attributes = read(fixture(version));
            var principal = (UsernamePasswordAuthenticationToken) attributes.get(Principal.class.getName());
            assertThat(principal.isAuthenticated()).isTrue();
            assertThat(principal.getCredentials()).isNull();
            assertThat(principal.getName()).isEqualTo("fixture-user");
            assertThat(((SysUserDetails) principal.getPrincipal()).getUserId()).isEqualTo(42L);
            assertThat(principal.getAuthorities()).extracting("authority").containsExactly("USER");
            assertThat(principal.getDetails()).isInstanceOf(
                    WebAuthenticationDetails.class);
        }
    }

    /**
     * 不能把未认证、带凭据或扩展未知字段的载体提升为认证主体。
     */
    @Test
    void rejectsUntrustedLegacyShapes() throws Exception {
        var json = JsonMapper.builder().build();
        List<Consumer<ObjectNode>> mutations = List.of(
                principal -> principal.put("authenticated", false),
                principal -> principal.put("credentials", "synthetic-code"),
                principal -> principal.put("unexpected", true));
        for (var mutate : mutations) {
            var attributes = json.readTree(fixture("3"));
            var principal = (ObjectNode) attributes.get(Principal.class.getName());
            mutate.accept(principal);
            String invalid = json.writeValueAsString(attributes);
            assertThatThrownBy(() -> read(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * 错误位置或任意嵌套type-id仍由原安全Mapper拒绝，不扩白名单。
     */
    @Test
    void refusesUnknownTypesAndMisplacedLegacyPrincipals() throws Exception {
        String json = fixture("3");
        for (String invalid : new String[]{
                json.replace("java.security.Principal", "other"),
                json.replace("dev.macula.cloud.iam.service.userdetails.SysUserDetails", "java.io.File")}) {
            assertThatThrownBy(() -> read(invalid)).isInstanceOf(RuntimeException.class);
        }
    }

    /**
     * 解析前只执行固定旧主体适配，嵌套类型交给既有受限Mapper。
     */
    private Map<String, Object> read(String json) {
        return mapper.readValue(LegacyEmailPrincipalReader.normalize(json), new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 加载修改前序列化器生成的合成夹具，不读取业务Redis。
     */
    private String fixture(String version) throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/authorization/legacy-email-jackson" + version + "-attributes.json")) {
            return new String(Objects.requireNonNull(input).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
