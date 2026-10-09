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
package dev.macula.cloud.tinyid.security;

import cn.hutool.extra.spring.SpringUtil;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.macula.boot.starter.security.config.ResourceServerConfiguration;
import dev.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.cloud.tinyid.controller.IdContronller;
import dev.macula.cloud.tinyid.service.impl.TinyIdIssuingServiceImpl;
import dev.macula.boot.starter.web.advice.ControllerExceptionAdvice;
import dev.macula.boot.starter.tinyid.base.exception.TinyIdSysException;
import dev.macula.cloud.tinyid.pojo.vo.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用实际资源服务器过滤链验证号段接口 JWT 边界，无数据库或 IAM 依赖。
 * @author Rain
 * @since 6.1.0
 */
class TinyIdSegmentSecurityIT {

    private static final String PATH = "/api/v1/id/nextSegmentIdSimple";
    private static final String SECRET = "tinyid-security-test-only-key-32-bytes";

    @Test
    void protectsEveryIssuingEndpointWithoutTinyIdToken() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "test-key", java.util.Map.of("spring.security.oauth2.resourceserver.jwt.secret", SECRET)));
            context.getEnvironment().getPropertySources().addLast(
                new YamlPropertySourceLoader().load("tinyid", new ClassPathResource("application.yml")).get(0));
            context.register(TestConfiguration.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            SegmentIdService service = context.getBean(SegmentIdService.class);
            when(service.getNextSegmentId("unknown")).thenThrow(
                new TinyIdSysException(ErrorCode.BIZ_TYPE_NOT_FOUND, "发号业务不存在"));

            for (String endpoint : java.util.List.of("nextId", "nextIdSimple", "nextSegmentId", "nextSegmentIdSimple")) {
                mvc.perform(post("/api/v1/id/" + endpoint).param("bizType", "unknown"))
                    .andExpect(status().isUnauthorized());
            }
            verifyNoInteractions(service);
            for (String endpoint : java.util.List.of("/api/v1/admin/data-sources", "/api/v1/unknown")) {
                mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
            }
            mvc.perform(post(PATH).param("bizType", "unknown").header("Authorization", "Bearer "
                + token("another-test-only-secret-32-bytes-long", Instant.now().plusSeconds(60))))
                .andExpect(status().isUnauthorized());
            verifyNoInteractions(service);
            mvc.perform(post(PATH).param("bizType", "unknown").header("Authorization", "Bearer "
                + token(SECRET, Instant.now().minusSeconds(300)))).andExpect(status().isUnauthorized());
            verifyNoInteractions(service);

            mvc.perform(post(PATH).param("bizType", "unknown").header("Authorization", "Bearer "
                + token(SECRET, Instant.now().plusSeconds(60))))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ID503"));
            verify(service).getNextSegmentId("unknown");
            mvc.perform(post(PATH).param("bizType", " ").header("Authorization", "Bearer "
                + token(SECRET, Instant.now().plusSeconds(60)))).andExpect(status().isInternalServerError());
            mvc.perform(post(PATH).header("Authorization", "Bearer "
                + token(SECRET, Instant.now().plusSeconds(60)))).andExpect(status().isInternalServerError());
            mvc.perform(get(PATH).param("bizType", "unknown").header("Authorization", "Bearer "
                + token(SECRET, Instant.now().plusSeconds(60)))).andExpect(status().isInternalServerError());
            var generator = mock(dev.macula.boot.starter.tinyid.base.generator.IdGenerator.class);
            when(generator.nextId(1)).thenReturn(java.util.List.of(101L));
            when(context.getBean(IdGeneratorFactory.class).getIdGenerator("order")).thenReturn(generator);
            var segment = new dev.macula.boot.starter.tinyid.base.entity.SegmentId();
            segment.setCurrentId(new java.util.concurrent.atomic.AtomicLong(100));
            segment.setLoadingId(120);
            segment.setMaxId(200);
            segment.setDelta(1);
            segment.setRemainder(0);
            when(service.getNextSegmentId("order")).thenReturn(segment);
            for (String endpoint : java.util.List.of("nextId", "nextIdSimple", "nextSegmentId", "nextSegmentIdSimple")) {
                mvc.perform(post("/api/v1/id/" + endpoint).param("bizType", "order")
                    .header("Authorization", "Bearer " + token(SECRET, Instant.now().plusSeconds(60))))
                    .andExpect(status().isOk());
            }
        }
    }

    private static String token(String secret, Instant expires) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder().subject("test-app").issuedAt(expires.minusSeconds(60))
            .expiresAt(expires).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    /**
     * 导入生产安全配置，替换数据库边界与签名密钥。
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
    @Import({ResourceServerConfiguration.class, IdContronller.class,
        ControllerExceptionAdvice.class, TinyIdIssuingServiceImpl.class})
    static class TestConfiguration {
        @Bean
        static SpringUtil springUtil() { return new SpringUtil(); }
        @Bean
        SegmentIdService segmentIdService() { return mock(SegmentIdService.class); }
        @Bean
        IdGeneratorFactory idGeneratorFactory() { return mock(IdGeneratorFactory.class); }
    }
}
