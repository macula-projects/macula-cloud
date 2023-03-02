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

package tech.powerjob.server.test;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.Test;
import tech.powerjob.common.OmsConstant;

import java.util.Date;
import java.util.TimeZone;

/**
 * 时区问题测试
 *
 * @author tjq
 * @since 2020/6/24
 */
public class TimezoneTest {

    @Test
    public void testTimeZone() {
        Date now = new Date();
        System.out.println(now.toString());

        System.out.println("timestamp before GMT: " + System.currentTimeMillis());

        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        TimeZone timeZone = TimeZone.getDefault();
        System.out.println(timeZone.getDisplayName());
        System.out.println(new Date());
        System.out.println(DateFormatUtils.format(new Date(), OmsConstant.TIME_PATTERN));

        System.out.println("timestamp after GMT: " + System.currentTimeMillis());
    }

}
