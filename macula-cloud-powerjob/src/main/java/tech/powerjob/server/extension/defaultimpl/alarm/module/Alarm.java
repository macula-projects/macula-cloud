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

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import tech.powerjob.common.OmsConstant;
import tech.powerjob.common.PowerSerializable;
import tech.powerjob.common.utils.CommonUtils;

/**
 * 报警内容
 *
 * @author tjq
 * @since 2020/8/1
 */
public interface Alarm extends PowerSerializable {

    String fetchTitle();

    default String fetchContent() {
        StringBuilder sb = new StringBuilder();
        JSONObject content = JSONObject.parseObject(JSONObject.toJSONString(this));
        content.forEach((key, originWord) -> {
            sb.append(key).append(": ");
            String word = String.valueOf(originWord);
            if (StringUtils.endsWithIgnoreCase(key, "time") || StringUtils.endsWithIgnoreCase(key, "date")) {
                try {
                    if (originWord instanceof Long) {
                        word = CommonUtils.formatTime((Long)originWord);
                    }
                } catch (Exception ignore) {
                }
            }
            sb.append(word).append(OmsConstant.LINE_SEPARATOR);
        });
        return sb.toString();
    }
}
