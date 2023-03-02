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

package tech.powerjob.server.extension.defaultimpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tech.powerjob.common.utils.CommonUtils;
import tech.powerjob.common.utils.NetUtils;
import tech.powerjob.server.extension.LockService;
import tech.powerjob.server.persistence.remote.model.OmsLockDO;
import tech.powerjob.server.persistence.remote.repository.OmsLockRepository;

/**
 * 基于数据库实现的分布式锁
 *
 * @author tjq
 * @since 2020/4/5
 */
@Slf4j
@Service
public class DatabaseLockService implements LockService {

    private final String ownerIp;

    private final OmsLockRepository omsLockRepository;

    @Autowired
    public DatabaseLockService(OmsLockRepository omsLockRepository) {

        this.ownerIp = NetUtils.getLocalHost();
        this.omsLockRepository = omsLockRepository;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            int num = omsLockRepository.deleteByOwnerIP(ownerIp);
            log.info("[DatabaseLockService] execute shutdown hook, release all lock(owner={},num={})", ownerIp, num);
        }));
    }

    @Override
    public boolean tryLock(String name, long maxLockTime) {

        OmsLockDO newLock = new OmsLockDO(name, ownerIp, maxLockTime);
        try {
            omsLockRepository.saveAndFlush(newLock);
            return true;
        } catch (DataIntegrityViolationException ignore) {
        } catch (Exception e) {
            log.warn("[DatabaseLockService] write lock to database failed, lockName = {}.", name, e);
        }

        OmsLockDO omsLockDO = omsLockRepository.findByLockName(name);
        long lockedMillions = System.currentTimeMillis() - omsLockDO.getGmtCreate().getTime();

        // 锁超时，强制释放锁并重新尝试获取
        if (lockedMillions > omsLockDO.getMaxLockTime()) {

            log.warn("[DatabaseLockService] The lock[{}] already timeout, will be unlocked now.", omsLockDO);
            unlock(name);
            return tryLock(name, maxLockTime);
        }
        return false;
    }

    @Override
    public void unlock(String name) {

        try {
            CommonUtils.executeWithRetry0(() -> omsLockRepository.deleteByLockName(name));
        } catch (Exception e) {
            log.error("[DatabaseLockService] unlock {} failed.", name, e);
        }
    }

}
