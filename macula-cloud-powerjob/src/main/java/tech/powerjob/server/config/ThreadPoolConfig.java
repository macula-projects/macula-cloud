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

package tech.powerjob.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tech.powerjob.server.common.RejectedExecutionHandlerFactory;
import tech.powerjob.server.common.constants.PJThreadPool;
import tech.powerjob.server.common.thread.NewThreadRunRejectedExecutionHandler;

/**
 * 公用线程池配置
 *
 * @author tjq
 * @since 2020/4/28
 */
@Slf4j
@EnableAsync
@Configuration
public class ThreadPoolConfig {

    @Bean(PJThreadPool.TIMING_POOL)
    public TaskExecutor getTimingPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4);
        // use SynchronousQueue
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("PJ-TIMING-");
        executor.setRejectedExecutionHandler(new NewThreadRunRejectedExecutionHandler(PJThreadPool.TIMING_POOL));
        return executor;
    }

    @Bean(PJThreadPool.BACKGROUND_POOL)
    public AsyncTaskExecutor initBackgroundPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 8);
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 16);
        executor.setQueueCapacity(8192);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("PJ-BG-");
        executor.setRejectedExecutionHandler(RejectedExecutionHandlerFactory.newDiscard(PJThreadPool.BACKGROUND_POOL));
        return executor;
    }

    @Bean(PJThreadPool.LOCAL_DB_POOL)
    public TaskExecutor initOmsLocalDbPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int tSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        executor.setCorePoolSize(tSize);
        executor.setMaxPoolSize(tSize);
        executor.setQueueCapacity(2048);
        executor.setThreadNamePrefix("PJ-LOCALDB-");
        executor.setRejectedExecutionHandler(RejectedExecutionHandlerFactory.newAbort(PJThreadPool.LOCAL_DB_POOL));
        return executor;
    }

    /**
     * 引入 WebSocket 支持后需要手动初始化调度线程池
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(Runtime.getRuntime().availableProcessors() * 8, 32));
        scheduler.setThreadNamePrefix("PJ-DEFAULT-");
        scheduler.setDaemon(true);
        return scheduler;
    }

}
