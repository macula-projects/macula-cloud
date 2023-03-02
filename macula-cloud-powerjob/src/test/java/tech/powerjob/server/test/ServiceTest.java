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

package tech.powerjob.server.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tech.powerjob.server.core.scheduler.CleanService;
import tech.powerjob.server.core.uid.IdGenerateService;
import tech.powerjob.server.extension.LockService;

import javax.annotation.Resource;

/**
 * 服务测试
 *
 * @author tjq
 * @since 2020/4/2
 */
//@ActiveProfiles("daily")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServiceTest {

    @Resource
    private LockService lockService;
    @Resource
    private IdGenerateService idGenerateService;
    @Resource
    private CleanService cleanService;

    @Test
    public void testLockService() {
        String lockName = "myLock";

        lockService.tryLock(lockName, 10000);
        lockService.tryLock(lockName, 10000);

        Assertions.assertDoesNotThrow(() -> lockService.unlock(lockName));
    }

    @Test
    public void testIdGenerator() {
        Assertions.assertDoesNotThrow(() -> idGenerateService.allocate());
    }

    @Test
    public void testCleanInstanceInfo() {
        Assertions.assertDoesNotThrow(() -> cleanService.cleanInstanceLog());
    }

    @Test
    public void testCleanWorkflowNodeInfo() {
        Assertions.assertDoesNotThrow(() -> cleanService.cleanWorkflowNodeInfo());
    }

}
