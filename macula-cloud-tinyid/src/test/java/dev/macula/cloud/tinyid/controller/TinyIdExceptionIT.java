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
package dev.macula.cloud.tinyid.controller;

import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.boot.starter.tinyid.base.exception.TinyIdSysException;
import dev.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import dev.macula.boot.starter.tinyid.base.generator.IdGenerator;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.boot.starter.web.config.WebAutoConfiguration;
import dev.macula.cloud.tinyid.pojo.vo.ErrorCode;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdDataSourceVO;
import dev.macula.cloud.tinyid.service.TinyIdManagementService;
import dev.macula.cloud.tinyid.service.impl.TinyIdIssuingServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 使用生产 Web 自动配置验证异常 JSON、文本协议及管理响应形状；安全链由独立 IT 覆盖。
 * @author Rain
 * @since 6.1.0
 */
class TinyIdExceptionIT {

    @Test
    void preservesProtocolsWithProductionWebAdvice() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addLast(
                new YamlPropertySourceLoader().load("tinyid", new ClassPathResource("application.yml")).get(0));
            context.register(TestConfiguration.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var service = context.getBean(SegmentIdService.class);
            when(service.getNextSegmentId("missing"))
                .thenThrow(new TinyIdSysException(ErrorCode.BIZ_TYPE_NOT_FOUND, "发号业务不存在"));
            when(service.getNextSegmentId("conflict"))
                .thenThrow(new TinyIdSysException(ErrorCode.SEGMENT_CONFLICT, "号段更新冲突，请稍后重试"));
            SegmentId segment = new SegmentId();
            segment.setCurrentId(new AtomicLong(100));
            segment.setLoadingId(120);
            segment.setMaxId(200);
            segment.setDelta(1);
            segment.setRemainder(0);
            when(service.getNextSegmentId("order")).thenReturn(segment);
            when(context.getBean(IdGeneratorFactory.class).getIdGenerator("missing"))
                .thenThrow(new TinyIdSysException(ErrorCode.BIZ_TYPE_NOT_FOUND, "发号业务不存在"));
            var generator = mock(IdGenerator.class);
            when(generator.nextId()).thenReturn(101L);
            when(generator.nextId(1)).thenReturn(List.of(101L));
            when(context.getBean(IdGeneratorFactory.class).getIdGenerator("order")).thenReturn(generator);
            when(context.getBean(TinyIdManagementService.class).listDataSources())
                .thenReturn(List.of(new TinyIdDataSourceVO("primary", 0, true)));
            ReflectionTestUtils.setField(context.getBean(TinyIdIssuingServiceImpl.class), "batchSizeMax", 100);

            for (boolean feign : List.of(false, true)) {
                for (String business : List.of("missing", "conflict")) {
                    var request = post("/api/v1/id/nextSegmentIdSimple").param("bizType", business)
                        .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
                    if (feign) {
                        request.header(GlobalConstants.FEIGN_REQ_ID, "test");
                    }
                    mvc.perform(request).andExpect(status().isInternalServerError())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(business.equals("missing") ? "ID503" : "ID504"))
                        .andExpect(jsonPath("$.msg").value(business.equals("missing")
                            ? "发号业务不存在" : "号段更新冲突，请稍后重试"));
                }
                var success = post("/api/v1/id/nextSegmentIdSimple").param("bizType", "order")
                    .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
                var textIds = post("/api/v1/id/nextIdSimple").param("bizType", "order");
                var admin = get("/api/v1/admin/data-sources");
                if (feign) {
                    success.header(GlobalConstants.FEIGN_REQ_ID, "test");
                    textIds.header(GlobalConstants.FEIGN_REQ_ID, "test");
                    admin.header(GlobalConstants.FEIGN_REQ_ID, "test");
                }
                mvc.perform(success).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("100,120,200,1,0"));
                mvc.perform(textIds).andExpect(status().isOk()).andExpect(content().string("101"));
                mvc.perform(admin).andExpect(status().isOk())
                    .andExpect(jsonPath(feign ? "$[0].key" : "$.data[0].key").value("primary"));
                if (!feign) {
                    mvc.perform(get("/api/v1/admin/data-sources")).andExpect(jsonPath("$.success").value(true));
                }
            }
            mvc.perform(post("/api/v1/id/nextSegmentIdSimple")).andExpect(status().isInternalServerError());
            mvc.perform(post("/api/v1/id/nextSegmentIdSimple").param("bizType", " "))
                .andExpect(status().isInternalServerError());
            mvc.perform(get("/api/v1/id/nextSegmentIdSimple")).andExpect(status().isInternalServerError());
            mvc.perform(post("/api/v1/id/nextIdSimple").param("bizType", "missing"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ID503"));
            mvc.perform(post("/api/v1/id/nextId").param("bizType", "order"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(101))
                .andExpect(jsonPath("$.data[0]").isNumber());
            mvc.perform(post("/api/v1/id/nextSegmentId").param("bizType", "order"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.maxId").value(200));
            mvc.perform(post("/api/v1/id/nextSegmentId").param("bizType", "missing"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ID503"));
            mvc.perform(post("/api/v1/id/nextId").param("bizType", "missing"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ID503"));
            mvc.perform(post("/api/v1/id/nextId").param("bizType", "order")
                .header(GlobalConstants.FEIGN_REQ_ID, "test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(101));
        }
    }

    /**
     * 仅替换业务存储边界，导入生产 Web 配置和 Controller。
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({WebAutoConfiguration.class,
        IdContronller.class, TinyIdAdminController.class, TinyIdIssuingServiceImpl.class})
    static class TestConfiguration {
        @Bean
        JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
        @Bean
        SegmentIdService segmentIdService() { return mock(SegmentIdService.class); }
        @Bean
        IdGeneratorFactory idGeneratorFactory() { return mock(IdGeneratorFactory.class); }
        @Bean
        TinyIdManagementService managementService() { return mock(TinyIdManagementService.class); }
    }
}
