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

package org.springframework.security.config.annotation.web.builders;

import java.io.IOException;
import java.util.HashMap;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FilterOrderRegUtils} 的内部顺序注册兼容测试。
 *
 * @author rain
 * @since 6.1.0
 */
class HttpSecurityFilterOrderRegistrationUtilsTest {

    private static HttpSecurity httpSecurity() {
        ObjectPostProcessor<Object> objectPostProcessor = new ObjectPostProcessor<>() {
            @Override
            public <O> O postProcess(O object) {
                return object;
            }
        };
        AuthenticationManagerBuilder authenticationBuilder =
                new AuthenticationManagerBuilder(objectPostProcessor);
        return new HttpSecurity(objectPostProcessor, authenticationBuilder, new HashMap<>());
    }

    @Test
    void shouldAllowParentConfigurerToAddFilterAfterOrderRegistration() {
        HttpSecurity http = httpSecurity();
        Filter filter = new TestFilter();

        FilterOrderRegUtils.registryFilterOrder(http, filter,
                UsernamePasswordAuthenticationFilter.class, FilterOrderRegUtils.Position.AFTER);

        assertThatCode(() -> http.addFilter(filter)).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowParentConfigurerToAddFilterBeforeOrderRegistration() {
        HttpSecurity http = httpSecurity();
        Filter filter = new TestFilter();

        FilterOrderRegUtils.registryFilterOrder(http, filter,
                UsernamePasswordAuthenticationFilter.class, FilterOrderRegUtils.Position.BEFORE);

        assertThatCode(() -> http.addFilter(filter)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnknownReferenceFilter() {
        HttpSecurity http = httpSecurity();

        assertThatThrownBy(() -> FilterOrderRegUtils.registryFilterOrder(http,
                new TestFilter(), UnknownFilter.class, FilterOrderRegUtils.Position.AFTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UnknownFilter.class.getName());
    }

    /**
     * 最小过滤器替身，只验证框架的顺序注册能力。
     * @since 6.1.0
     */
    private static class TestFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }

    /**
     * 未注册到框架顺序表的过滤器，必须在引用时被拒绝。
     * @since 6.1.0
     */
    private static final class UnknownFilter extends TestFilter {
    }
}
