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

package dev.macula.cloud.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证文档服务的最小 Spring Boot 应用装配。
 *
 * @author Rain
 * @since 6.1.0
 */
@SpringBootTest(classes = MaculaDocsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MaculaDocsApplicationTest {

    @Test
    void contextLoads() {
    }
}
