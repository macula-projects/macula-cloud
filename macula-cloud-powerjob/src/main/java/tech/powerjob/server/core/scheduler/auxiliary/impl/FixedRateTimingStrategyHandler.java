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

package tech.powerjob.server.core.scheduler.auxiliary.impl;

import org.springframework.stereotype.Component;
import tech.powerjob.common.PowerJobDKey;
import tech.powerjob.common.enums.TimeExpressionType;
import tech.powerjob.common.exception.PowerJobException;
import tech.powerjob.server.core.scheduler.auxiliary.AbstractTimingStrategyHandler;

/**
 * @author Echo009
 * @since 2022/3/22
 */
@Component
public class FixedRateTimingStrategyHandler extends AbstractTimingStrategyHandler {

    @Override
    public void validate(String timeExpression) {
        long delay;
        try {
            delay = Long.parseLong(timeExpression);
        } catch (Exception e) {
            throw new PowerJobException("invalid timeExpression!");
        }
        // 默认 120s ，超过这个限制应该使用考虑使用其他类型以减少资源占用
        int maxInterval = Integer.parseInt(System.getProperty(PowerJobDKey.FREQUENCY_JOB_MAX_INTERVAL, "120000"));
        if (delay > maxInterval) {
            throw new PowerJobException("the rate must be less than " + maxInterval + "ms");
        }
        if (delay <= 0) {
            throw new PowerJobException("the rate must be greater than 0 ms");
        }
    }

    @Override
    public Long calculateNextTriggerTime(Long preTriggerTime, String timeExpression, Long startTime, Long endTime) {
        long r = startTime != null && startTime > preTriggerTime ? startTime
            : preTriggerTime + Long.parseLong(timeExpression);
        return endTime != null && endTime < r ? null : r;
    }

    @Override
    public TimeExpressionType supportType() {
        return TimeExpressionType.FIXED_RATE;
    }
}
