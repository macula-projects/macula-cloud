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

package tech.powerjob.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.powerjob.server.common.utils.PropertyUtils;

/**
 * powerjob-server entry
 *
 * @author tjq
 * @since 2020/3/29
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class PowerJobServerApplication {

    private static final String TIPS =
        "\n\n" + "******************* PowerJob Tips *******************\n" + "如果应用无法启动，我们建议您仔细阅读以下文档来解决:\n" + "if server can't startup, we recommend that you read the documentation to find a solution:\n" + "https://www.yuque.com/powerjob/guidence/problem\n" + "******************* PowerJob Tips *******************\n\n";

    public static void main(String[] args) {

        // pre();

        // Start SpringBoot application.
        try {
            SpringApplication.run(PowerJobServerApplication.class, args);
        } catch (Throwable t) {
            log.error(TIPS);
            throw t;
        }
    }

    private static void pre() {
        log.info(TIPS);
        PropertyUtils.init();
    }

}
