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

package tech.powerjob.server.extension.defaultimpl.alarm;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.powerjob.server.extension.Alarmable;
import tech.powerjob.server.extension.defaultimpl.alarm.module.Alarm;
import tech.powerjob.server.persistence.remote.model.UserInfoDO;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 报警服务
 *
 * @author tjq
 * @since 2020/4/19
 */
@Slf4j
@Component
public class AlarmCenter {

    private final ExecutorService POOL;

    private final List<Alarmable> BEANS = Lists.newLinkedList();

    public AlarmCenter(List<Alarmable> alarmables) {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("AlarmPool-%d").build();
        POOL = new ThreadPoolExecutor(cores, cores, 5, TimeUnit.MINUTES, Queues.newLinkedBlockingQueue(), factory);

        alarmables.forEach(bean -> {
            BEANS.add(bean);
            log.info("[AlarmCenter] bean(className={},obj={}) register to AlarmCenter successfully!",
                bean.getClass().getName(), bean);
        });
    }

    public void alarmFailed(Alarm alarm, List<UserInfoDO> targetUserList) {
        POOL.execute(() -> BEANS.forEach(alarmable -> {
            try {
                alarmable.onFailed(alarm, targetUserList);
            } catch (Exception e) {
                log.warn("[AlarmCenter] alarm failed.", e);
            }
        }));
    }
}
