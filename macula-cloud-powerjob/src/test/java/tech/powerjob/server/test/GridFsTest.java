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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tech.powerjob.server.persistence.mongodb.GridFsManager;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * GridFS 测试
 *
 * @author tjq
 * @since 2020/5/18
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GridFsTest {

    @Resource
    private GridFsManager gridFsManager;

    @Test
    @Disabled
    public void testStore() throws IOException {
        /**
         File file = new File("/Users/tjq/Desktop/DistributeCompute/oms-template-origin.zip");
         gridFsManager.store(file, "test", "test.zip");
         **/
    }

    @Test
    @Disabled
    public void testDownload() throws IOException {
        /**
         File file = new File("/Users/tjq/Desktop/tmp/test-download.zip");
         gridFsManager.download(file, "test", "test.zip");
         **/
    }

    @Test
    @Disabled
    public void testDelete() {
        /**
         gridFsManager.deleteBefore("fs", 0);
         **/
    }

    @Test
    @Disabled
    public void testExists() {
        /**
         System.out.println(gridFsManager.exists("test", "test.zip"));
         System.out.println(gridFsManager.exists("test", "oms-sql.sql"));
         **/
    }

}
