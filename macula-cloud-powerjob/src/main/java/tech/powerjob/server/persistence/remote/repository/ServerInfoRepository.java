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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.powerjob.server.persistence.remote.model.ServerInfoDO;

import javax.transaction.Transactional;
import java.util.Date;

/**
 * 服务器信息 数据操作层
 *
 * @author tjq
 * @since 2020/4/15
 */
public interface ServerInfoRepository extends JpaRepository<ServerInfoDO, Long> {

    ServerInfoDO findByIp(String ip);

    @Transactional(rollbackOn = Exception.class)
    @Modifying
    @CanIgnoreReturnValue
    @Query(value = "update ServerInfoDO set gmtModified = :gmtModified where ip = :ip")
    int updateGmtModifiedByIp(@Param("ip") String ip, @Param("gmtModified") Date gmtModified);

    @Transactional(rollbackOn = Exception.class)
    @Modifying
    @CanIgnoreReturnValue
    @Query(value = "update ServerInfoDO set id = :id where ip = :ip")
    int updateIdByIp(@Param("id") long id, @Param("ip") String ip);

    @Transactional(rollbackOn = Exception.class)
    @Modifying
    @Query(value = "delete from ServerInfoDO where gmtModified < ?1")
    int deleteByGmtModifiedBefore(Date threshold);
}
