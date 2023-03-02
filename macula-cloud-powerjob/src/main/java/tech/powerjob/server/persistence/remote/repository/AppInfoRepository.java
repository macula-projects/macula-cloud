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

package tech.powerjob.server.persistence.remote.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.powerjob.server.persistence.remote.model.AppInfoDO;

import java.util.List;
import java.util.Optional;

/**
 * AppInfo 数据访问层
 *
 * @author tjq
 * @since 2020/4/1
 */
public interface AppInfoRepository extends JpaRepository<AppInfoDO, Long> {

    Optional<AppInfoDO> findByAppName(String appName);

    Page<AppInfoDO> findByAppNameLike(String condition, Pageable pageable);

    /**
     * 根据 currentServer 查询 appId 其实只需要 id，处于性能考虑可以直接写SQL只返回ID
     */
    List<AppInfoDO> findAllByCurrentServer(String currentServer);

    @Query(value = "select id from AppInfoDO where currentServer = :currentServer")
    List<Long> listAppIdByCurrentServer(@Param("currentServer") String currentServer);

}
