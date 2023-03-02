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

package tech.powerjob.server.core.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Echo009
 * @since 2022/10/12
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CoreScheduleTaskManager implements InitializingBean, DisposableBean {

    private final PowerScheduleService powerScheduleService;

    private final InstanceStatusCheckService instanceStatusCheckService;

    private final List<Thread> coreThreadContainer = new ArrayList<>();

    @SuppressWarnings("AlibabaAvoidManuallyCreateThread")
    @Override
    public void afterPropertiesSet() {
        // 定时调度
        coreThreadContainer.add(new Thread(new LoopRunnable("ScheduleCronJob", PowerScheduleService.SCHEDULE_RATE,
            powerScheduleService::scheduleCronJob), "Thread-ScheduleCronJob"));
        coreThreadContainer.add(new Thread(new LoopRunnable("ScheduleCronWorkflow", PowerScheduleService.SCHEDULE_RATE,
            powerScheduleService::scheduleCronWorkflow), "Thread-ScheduleCronWorkflow"));
        coreThreadContainer.add(new Thread(new LoopRunnable("ScheduleFrequentJob", PowerScheduleService.SCHEDULE_RATE,
            powerScheduleService::scheduleFrequentJob), "Thread-ScheduleFrequentJob"));
        // 数据清理
        coreThreadContainer.add(new Thread(
            new LoopRunnable("CleanWorkerData", PowerScheduleService.SCHEDULE_RATE, powerScheduleService::cleanData),
            "Thread-CleanWorkerData"));
        // 状态检查
        coreThreadContainer.add(new Thread(
            new LoopRunnable("CheckRunningInstance", InstanceStatusCheckService.CHECK_INTERVAL,
                instanceStatusCheckService::checkRunningInstance), "Thread-CheckRunningInstance"));
        coreThreadContainer.add(new Thread(
            new LoopRunnable("CheckWaitingDispatchInstance", InstanceStatusCheckService.CHECK_INTERVAL,
                instanceStatusCheckService::checkWaitingDispatchInstance), "Thread-CheckWaitingDispatchInstance"));
        coreThreadContainer.add(new Thread(
            new LoopRunnable("CheckWaitingWorkerReceiveInstance", InstanceStatusCheckService.CHECK_INTERVAL,
                instanceStatusCheckService::checkWaitingWorkerReceiveInstance),
            "Thread-CheckWaitingWorkerReceiveInstance"));
        coreThreadContainer.add(new Thread(
            new LoopRunnable("CheckWorkflowInstance", InstanceStatusCheckService.CHECK_INTERVAL,
                instanceStatusCheckService::checkWorkflowInstance), "Thread-CheckWorkflowInstance"));

        coreThreadContainer.forEach(Thread::start);
    }

    @Override
    public void destroy() {
        coreThreadContainer.forEach(Thread::interrupt);
    }

    @RequiredArgsConstructor
    private static class LoopRunnable implements Runnable {

        private final String taskName;

        private final Long runningInterval;

        private final Runnable innerRunnable;

        @SuppressWarnings("BusyWait")
        @Override
        public void run() {
            log.info("start task : {}.", taskName);
            while (true) {
                try {
                    innerRunnable.run();
                    Thread.sleep(runningInterval);
                } catch (InterruptedException e) {
                    log.warn("[{}] task has been interrupted!", taskName, e);
                    break;
                } catch (Exception e) {
                    log.error("[{}] task failed!", taskName, e);
                }
            }
        }
    }

}
