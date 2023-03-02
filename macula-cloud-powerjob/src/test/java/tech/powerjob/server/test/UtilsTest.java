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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * 工具类测试
 *
 * @author tjq
 * @since 2020/4/3
 */
public class UtilsTest {

    @Test
    public void normalTest() {
        String s = "000000000111010000001100000000010110100110100000000001000000000000";
        System.out.println(s.length());
    }

    @Test
    public void testTZ() {
        System.out.println(TimeZone.getDefault());
    }

    @Test
    public void testStringUtils() {
        String goodAppName = "powerjob-server";
        String appName = "powerjob-server ";
        System.out.println(StringUtils.containsWhitespace(goodAppName));
        System.out.println(StringUtils.containsWhitespace(appName));
    }

    @Test
    public void filterTest() {
        List<String> test = Lists.newArrayList("A", "B", null, "C", null);
        List<String> list = test.stream().filter(Objects::nonNull).collect(Collectors.toList());
        System.out.println(list);
    }
}
