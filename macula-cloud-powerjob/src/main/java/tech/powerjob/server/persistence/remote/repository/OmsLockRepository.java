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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tech.powerjob.server.persistence.remote.model.OmsLockDO;

import javax.transaction.Transactional;

/**
 * 利用唯一性约束作为数据库锁
 *
 * @author tjq
 * @since 2020/4/2
 */
public interface OmsLockRepository extends JpaRepository<OmsLockDO, Long> {

    @Modifying
    @Transactional(rollbackOn = Exception.class)
    @Query(value = "delete from OmsLockDO where lockName = ?1")
    int deleteByLockName(String lockName);

    OmsLockDO findByLockName(String lockName);

    @Modifying
    @Transactional(rollbackOn = Exception.class)
    int deleteByOwnerIP(String ip);
}
