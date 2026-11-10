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
package dev.macula.cloud.system.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 独立、无凭据且不跟随重定向的元数据客户端。
 *
 * @author Rain
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuth2IssuerMetadataProperties.class)
public class OAuth2IssuerMetadataConfiguration {
    /** 不使用携带业务认证拦截器的共享Builder。 */
    @Bean
    @Qualifier("issuerMetadataClient")
    public RestClient issuerMetadataClient(OAuth2IssuerMetadataProperties properties) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
