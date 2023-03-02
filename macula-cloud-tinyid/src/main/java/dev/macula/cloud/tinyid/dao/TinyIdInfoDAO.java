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

package dev.macula.cloud.tinyid.dao;

import dev.macula.cloud.tinyid.dao.entity.TinyIdInfo;

/**
 * @author du_imba
 */
public interface TinyIdInfoDAO {
    /**
     * 根据bizType获取db中的tinyId对象
     *
     * @param bizType
     * @return
     */
    TinyIdInfo queryByBizType(String bizType);

    /**
     * 根据id、oldMaxId、version、bizType更新最新的maxId
     *
     * @param id
     * @param newMaxId
     * @param oldMaxId
     * @param version
     * @param bizType
     * @return
     */
    int updateMaxId(Long id, Long newMaxId, Long oldMaxId, Long version, String bizType);
}
