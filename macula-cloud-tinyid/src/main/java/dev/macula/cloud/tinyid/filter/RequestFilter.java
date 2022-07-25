/*
 * Copyright (c) 2022 Macula
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

package dev.macula.cloud.tinyid.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @Author du_imba
 */
@WebFilter(urlPatterns = "/*", filterName = "requestFilter")
public class RequestFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String params = "";
        Map<String, String[]> paramsMap = request.getParameterMap();
        if (paramsMap != null && !paramsMap.isEmpty()) {
            for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                params += entry.getKey() + ":" + StringUtils.arrayToDelimitedString(entry.getValue(), ",") + ";";
            }
        }
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable e) {
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            logger.debug("request filter path={}, cost={}, params={}", request.getServletPath(), cost, params);
        }
    }

    @Override
    public void destroy() {

    }
}
