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

package tech.powerjob.server.extension.defaultimpl.alarm.module;

import lombok.Data;

/**
 * 任务执行失败告警对象
 *
 * @author tjq
 * @since 2020/4/30
 */
@Data
public class JobInstanceAlarm implements Alarm {
    /**
     * 应用ID
     */
    private long appId;
    /**
     * 任务ID
     */
    private long jobId;
    /**
     * 任务实例ID
     */
    private long instanceId;
    /**
     * 任务名称
     */
    private String jobName;
    /**
     * 任务自带的参数
     */
    private String jobParams;
    /**
     * 时间表达式类型（CRON/API/FIX_RATE/FIX_DELAY）
     */
    private Integer timeExpressionType;
    /**
     * 时间表达式，CRON/NULL/LONG/LONG
     */
    private String timeExpression;
    /**
     * 执行类型，单机/广播/MR
     */
    private Integer executeType;
    /**
     * 执行器类型，Java/Shell
     */
    private Integer processorType;
    /**
     * 执行器信息
     */
    private String processorInfo;

    /**
     * 任务实例参数
     */
    private String instanceParams;
    /**
     * 执行结果
     */
    private String result;
    /**
     * 预计触发时间
     */
    private Long expectedTriggerTime;
    /**
     * 实际触发时间
     */
    private Long actualTriggerTime;
    /**
     * 结束时间
     */
    private Long finishedTime;
    /**
     *
     */
    private String taskTrackerAddress;

    @Override
    public String fetchTitle() {
        return "PowerJob AlarmService: Job Running Failed";
    }
}
