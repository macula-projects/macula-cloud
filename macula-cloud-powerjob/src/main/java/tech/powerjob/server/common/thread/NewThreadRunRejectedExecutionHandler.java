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

package tech.powerjob.server.common.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Echo009
 * @since 2022/10/12
 */
@Slf4j
public class NewThreadRunRejectedExecutionHandler implements RejectedExecutionHandler {

    private static final AtomicLong COUNTER = new AtomicLong();

    private final String source;

    public NewThreadRunRejectedExecutionHandler(String source) {
        this.source = source;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor p) {
        log.error(
            "[{}] ThreadPool[{}] overload, the task[{}] will run by a new thread!, Maybe you need to adjust the ThreadPool config!",
            source, p, r);
        if (!p.isShutdown()) {
            String threadName = source + "-T-" + COUNTER.getAndIncrement();
            log.info("[{}] create new thread[{}] to run job", source, threadName);
            new Thread(r, threadName).start();
        }
    }
}
