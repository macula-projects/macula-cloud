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

package dev.macula.cloud.tinyid.converter;

import dev.macula.boot.mapstruct.config.MapStructAutoConfiguration;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdAuditLogBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessBO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdAuditLogVO;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdBusinessVO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证TinyID数据库BO保留LocalDateTime，对外Date字段遵循公共时区并序列化为ISO。
 * @author Rain
 * @since 6.1.0
 */
class TinyIdDateTimeMappingTest {
    /** 两类VO的审计时间均为Date，生成Mapper遵循显式存储时区。 */
    @Test
    void mapsDatabaseTimesToIsoInterfaceDates() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MapStructAutoConfiguration.class, JacksonAutoConfiguration.class))
            .withUserConfiguration(MappingConfiguration.class)
            .withPropertyValues("macula.time.storage-zone=Asia/Shanghai", "spring.jackson.time-zone=UTC")
            .run(context -> {
                for (Class<?> type : List.of(TinyIdBusinessVO.class, TinyIdAuditLogVO.class)) {
                    for (var field : type.getDeclaredFields()) {
                        assertThat(TemporalAccessor.class.isAssignableFrom(field.getType()) && field.getType() != Instant.class).isFalse();
                    }
                }
                var converter = context.getBean(TinyIdManagementConverter.class);
                var stored = LocalDateTime.of(2026, 11, 5, 8, 50, 30);
                var business = new TinyIdBusinessBO();
                business.setCreateTime(stored);
                business.setUpdateTime(stored);
                var view = converter.toBusinessVO(business);
                assertThat(view.getCreateTime().toInstant()).isEqualTo(Instant.parse("2026-11-05T00:50:30Z"));
                assertThat(view.getUpdateTime()).isEqualTo(view.getCreateTime());
                var log = new TinyIdAuditLogBO();
                log.setCreateTime(stored);
                var audit = converter.toAuditLogVO(log);
                JsonMapper json = context.getBean(JsonMapper.class);
                assertThat(json.readTree(json.writeValueAsString(audit)).get("createTime").asString())
                    .isEqualTo("2026-11-05T00:50:30.000Z");
                business.setCreateTime(null);
                assertThat(converter.toBusinessVO(business).getCreateTime()).isNull();
            });
    }

    /** 仅扫描生成的TinyID Mapper。
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = TinyIdManagementConverter.class)
    static class MappingConfiguration {
    }
}
