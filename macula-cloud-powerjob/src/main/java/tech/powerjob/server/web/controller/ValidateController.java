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

package tech.powerjob.server.web.controller;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.powerjob.common.enums.TimeExpressionType;
import tech.powerjob.common.response.ResultDTO;
import tech.powerjob.server.core.scheduler.TimingStrategyService;

import java.util.List;

/**
 * 校验控制器
 *
 * @author tjq
 * @author Echo009
 * @since 2020/11/28
 */
@RestController
@RequestMapping("/validate")
@RequiredArgsConstructor
public class ValidateController {

    private final TimingStrategyService timingStrategyService;

    @GetMapping("/timeExpression")
    public ResultDTO<List<String>> checkTimeExpression(TimeExpressionType timeExpressionType, String timeExpression,
        @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime) {
        try {
            timingStrategyService.validate(timeExpressionType, timeExpression, startTime, endTime);
            return ResultDTO.success(
                timingStrategyService.calculateNextTriggerTimes(timeExpressionType, timeExpression, startTime,
                    endTime));
        } catch (Exception e) {
            return ResultDTO.success(Lists.newArrayList(ExceptionUtils.getMessage(e)));
        }
    }
}
