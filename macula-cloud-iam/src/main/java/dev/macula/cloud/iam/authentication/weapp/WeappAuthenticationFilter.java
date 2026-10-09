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

package dev.macula.cloud.iam.authentication.weapp;

import tools.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.Assert;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * 解析小程序登录请求并提交认证。
 * @author rain
 * @since 6.1.0
 */
public class WeappAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private static final PathPatternRequestMatcher DEFAULT_REQUEST_MATCHER =
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login/weapp");
    private final ObjectMapper om = tools.jackson.databind.json.JsonMapper.builder().build();
    private Converter<HttpServletRequest, WeappAuthenticationToken> weappAuthenticationTokenConverter;
    private boolean postOnly = true;

    public WeappAuthenticationFilter() {
        super(DEFAULT_REQUEST_MATCHER);
        this.weappAuthenticationTokenConverter = defaultConverter();
    }

    public WeappAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(DEFAULT_REQUEST_MATCHER, authenticationManager);
        this.weappAuthenticationTokenConverter = defaultConverter();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException, IOException, ServletException {
        if (this.postOnly && !HttpMethod.POST.matches(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }
        WeappAuthenticationToken authRequest = weappAuthenticationTokenConverter.convert(request);
        if (authRequest == null) {
            throw new BadCredentialsException("fail to extract miniapp authentication request params");
        }
        setDetails(request, authRequest);
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    protected void setDetails(HttpServletRequest request, WeappAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }

    public void setConverter(Converter<HttpServletRequest, WeappAuthenticationToken> converter) {
        Assert.notNull(converter, "Converter must not be null");
        this.weappAuthenticationTokenConverter = converter;
    }

    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    private Converter<HttpServletRequest, WeappAuthenticationToken> defaultConverter() {
        return request -> {
            try (BufferedReader reader = request.getReader()) {
                WeappRequest weappRequest = this.om.readValue(reader, WeappRequest.class);
                return new WeappAuthenticationToken(weappRequest);
            } catch (IOException | tools.jackson.core.JacksonException e) {
                return null;
            }
        };
    }
}
