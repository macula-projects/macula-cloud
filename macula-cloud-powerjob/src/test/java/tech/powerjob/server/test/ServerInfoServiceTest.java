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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import tech.powerjob.server.persistence.remote.model.ServerInfoDO;
import tech.powerjob.server.persistence.remote.repository.ServerInfoRepository;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * test server info
 *
 * @author tjq
 * @since 2021/2/21
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServerInfoServiceTest {

    @Resource
    private ServerInfoRepository serverInfoRepository;

    @Test
    @Transactional
    @Rollback
    public void generateInvalidRecord2Test() {

        List<ServerInfoDO> records = Lists.newLinkedList();
        for (int i = 0; i < 11; i++) {

            // invalid ip to test
            String ip = "T-192.168.1." + i;

            Date gmtModified = DateUtils.addHours(new Date(), -ThreadLocalRandom.current().nextInt(1, 48));

            ServerInfoDO serverInfoDO = new ServerInfoDO(ip);
            serverInfoDO.setGmtModified(gmtModified);

            records.add(serverInfoDO);
        }

        serverInfoRepository.saveAll(records);
        serverInfoRepository.flush();
    }

}