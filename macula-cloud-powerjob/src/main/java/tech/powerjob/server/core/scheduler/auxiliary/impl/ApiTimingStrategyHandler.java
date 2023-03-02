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
import tech.powerjob.common.enums.TimeExpressionType;
import tech.powerjob.server.core.scheduler.auxiliary.AbstractTimingStrategyHandler;

/**
 * @author Echo009
 * @since 2022/3/22
 */
@Component
public class ApiTimingStrategyHandler extends AbstractTimingStrategyHandler {
    @Override
    public TimeExpressionType supportType() {
        return TimeExpressionType.API;
    }
}
