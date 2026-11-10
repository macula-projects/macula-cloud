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

import dev.macula.cloud.system.service.impl.AdminClientProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 显式开启的一次性本地初始化任务；正常服务不创建此 Bean，无 HTTP 入口。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminClientProvisioningProperties.class)
@ConditionalOnProperty(prefix = "macula.iam.admin-client-provisioning", name = "enabled", havingValue = "true")
public class AdminClientProvisioningRunner implements ApplicationRunner {
    private final AdminClientProvisioningService service;
    private final AdminClientProvisioningProperties properties;
    private final ConfigurableApplicationContext context;

    /** Flyway 完成后运行，提交成功关闭进程；不传播可能含数据库材料的底层异常。 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            service.provision(properties);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Admin client provisioning failed; verify protected recovery evidence before retrying");
        }
        SpringApplication.exit(context, () -> 0);
    }
}
