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

import com.mongodb.client.gridfs.model.GridFSFile;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import tech.powerjob.server.common.utils.OmsFileUtils;
import tech.powerjob.server.core.scheduler.CleanService;
import tech.powerjob.server.persistence.mongodb.GridFsManager;

import javax.annotation.Resource;
import java.util.Date;
import java.util.function.Consumer;

/**
 * 在线日志测试
 *
 * @author tjq
 * @since 2020/5/11
 */

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled
public class OmsLogTest {

    @Resource
    private CleanService cleanService;
    @Resource
    private GridFsTemplate gridFsTemplate;

    @Test
    public void testLocalLogCleaner() {
        cleanService.cleanLocal(OmsFileUtils.genLogDirPath(), 0);
    }

    @Test
    public void testRemoteLogCleaner() {
        cleanService.cleanRemote(GridFsManager.LOG_BUCKET, 0);
    }

    @Test
    public void testGridFsQuery() {
        Query mongoQuery = Query.query(Criteria.where("uploadDate").gt(new Date()));
        gridFsTemplate.find(mongoQuery).forEach(new Consumer<GridFSFile>() {
            @Override
            public void accept(GridFSFile gridFSFile) {
                System.out.println(gridFSFile.getFilename());
            }
        });
    }
}
