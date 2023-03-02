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

package tech.powerjob.server.core.uid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.powerjob.server.remote.server.self.ServerInfoService;

/**
 * 唯一ID生成服务，使用 Twitter snowflake 算法 机房ID：固定为0，占用2位 机器ID：由 ServerIdProvider 提供
 *
 * @author tjq
 * @since 2020/4/6
 */
@Slf4j
@Service
public class IdGenerateService {

    private final SnowFlakeIdGenerator snowFlakeIdGenerator;

    private static final int DATA_CENTER_ID = 0;

    public IdGenerateService(ServerInfoService serverInfoService) {
        long id = serverInfoService.fetchServiceInfo().getId();
        snowFlakeIdGenerator = new SnowFlakeIdGenerator(DATA_CENTER_ID, id);
        log.info("[IdGenerateService] initialize IdGenerateService successfully, ID:{}", id);
    }

    /**
     * 分配分布式唯一ID
     *
     * @return 分布式唯一ID
     */
    public long allocate() {
        return snowFlakeIdGenerator.nextId();
    }

}
