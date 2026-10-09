/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import dev.macula.cloud.iam.config.CorsFilter;
import static org.assertj.core.api.Assertions.*;

/**
 * 验证环境否决、固定 issuer 和演示路径的全局过滤器边界。
 * @author Rain
 * @since 6.1.0
 */
class PlaygroundAccessPolicyTest {
    @Test void requiresBothExplicitFlagAndAllowedActiveProfile() {
        var properties = new PlaygroundProperties();
        var environment = new MockEnvironment();
        var policy = new PlaygroundAccessPolicy(properties, environment);
        assertThat(policy.isEnabled()).isFalse();
        properties.setEnabled(true);
        environment.setActiveProfiles("local");
        assertThat(policy.isEnabled()).isFalse();
        properties.setAllowedProfiles(Set.of("local"));
        assertThat(policy.isEnabled()).isTrue();
        environment.setActiveProfiles("dev");
        assertThat(policy.isEnabled()).isFalse();
        environment.setActiveProfiles();
        assertThat(policy.isEnabled()).isFalse();
    }

    @Test void productionAlwaysVetoesEvenWhenExplicitlyAllowlisted() {
        var properties = new PlaygroundProperties();
        properties.setEnabled(true);
        properties.setAllowedProfiles(Set.of("local", "prd", "production"));
        var environment = new MockEnvironment();
        var policy = new PlaygroundAccessPolicy(properties, environment);
        for (String[] profiles : new String[][]{{"prd"}, {"production"}, {"local", "prd"}, {"production", "local"}}) {
            environment.setActiveProfiles(profiles);
            assertThat(policy.isEnabled()).isFalse();
        }
    }

    @Test void issuerCannotSmuggleDestinationOrInsecureRemoteHost() {
        var properties = new PlaygroundProperties();
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var policy = new PlaygroundAccessPolicy(properties, environment);
        assertThat(policy.issuer("https://iam.example/").toString()).isEqualTo("https://iam.example");
        for (String issuer : new String[]{"http://iam.example", "http://127.0.0.1:9010", "https://user@iam.example",
            "https://iam.example/path", "https://iam.example?target=x", "https://iam.example#x", "//iam.example"}) {
            assertThatThrownBy(() -> policy.issuer(issuer)).isInstanceOf(IllegalArgumentException.class);
        }
        properties.setAllowLoopbackHttp(true);
        assertThat(policy.issuer("http://127.0.0.1:9010").getHost()).isEqualTo("127.0.0.1");
        environment.setActiveProfiles("dev");
        assertThatThrownBy(() -> policy.issuer("http://127.0.0.1:9010")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void disabledGateReturns404AndNoStoreEvenForPreflight() throws Exception {
        var properties = new PlaygroundProperties();
        var policy = new PlaygroundAccessPolicy(properties, new MockEnvironment());
        var filter = new PlaygroundConfiguration.PlaygroundGateFilter(policy, "https://iam.example");
        for (String method : new String[]{"GET", "POST", "OPTIONS"}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(method, "/playground"), response,
                (request, result) -> { throw new AssertionError("Disabled request reached controller"); });
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }
    }

    @Test void mutationRequiresExactConfiguredOrigin() throws Exception {
        var properties = new PlaygroundProperties();
        properties.setEnabled(true);
        properties.setAllowedProfiles(Set.of("local"));
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var filter = new PlaygroundConfiguration.PlaygroundGateFilter(
            new PlaygroundAccessPolicy(properties, environment), "https://iam.example");
        for (String origin : new String[]{"", "null", "https://evil.example", "https://iam.example.evil", "https://iam.example"}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/iam-playground/flows");
            if (!origin.isEmpty()) request.addHeader("Origin", origin);
            var response = new MockHttpServletResponse();
            AtomicBoolean forwarded = new AtomicBoolean();
            filter.doFilter(request, response, (req, res) -> forwarded.set(true));
            assertThat(forwarded.get()).isEqualTo(origin.equals("https://iam.example"));
            if (!forwarded.get()) assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Test void legacyCorsDoesNotBypassPlaygroundGateOrAddWildcard() throws Exception {
        for (String path : new String[]{"/playground", "/playground/assets/playground.js", "/api/v1/iam-playground/flows"}) {
            var request = new MockHttpServletRequest("OPTIONS", path);
            var response = new MockHttpServletResponse();
            AtomicBoolean forwarded = new AtomicBoolean();
            new CorsFilter().doFilter(request, response, (req, res) -> forwarded.set(true));
            assertThat(forwarded.get()).isTrue();
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        }
        var response = new MockHttpServletResponse();
        new CorsFilter().doFilter(new MockHttpServletRequest("OPTIONS", "/business"), response,
            (req, res) -> { throw new AssertionError("Legacy OPTIONS should be unchanged"); });
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
    }
}
