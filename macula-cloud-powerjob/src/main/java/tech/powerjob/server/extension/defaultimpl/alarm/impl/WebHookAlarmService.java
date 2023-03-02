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

package tech.powerjob.server.extension.defaultimpl.alarm.impl;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import tech.powerjob.common.OmsConstant;
import tech.powerjob.common.utils.HttpUtils;
import tech.powerjob.server.extension.Alarmable;
import tech.powerjob.server.extension.defaultimpl.alarm.module.Alarm;
import tech.powerjob.server.persistence.remote.model.UserInfoDO;

import java.util.List;

/**
 * http 回调报警
 *
 * @author tjq
 * @since 11/14/20
 */
@Slf4j
@Service
public class WebHookAlarmService implements Alarmable {

    private static final String HTTP_PROTOCOL_PREFIX = "http://";
    private static final String HTTPS_PROTOCOL_PREFIX = "https://";

    @Override
    public void onFailed(Alarm alarm, List<UserInfoDO> targetUserList) {
        if (CollectionUtils.isEmpty(targetUserList)) {
            return;
        }
        targetUserList.forEach(user -> {
            String webHook = user.getWebHook();
            if (StringUtils.isEmpty(webHook)) {
                return;
            }

            // 自动添加协议头
            if (!webHook.startsWith(HTTP_PROTOCOL_PREFIX) && !webHook.startsWith(HTTPS_PROTOCOL_PREFIX)) {
                webHook = HTTP_PROTOCOL_PREFIX + webHook;
            }

            MediaType jsonType = MediaType.parse(OmsConstant.JSON_MEDIA_TYPE);
            RequestBody requestBody = RequestBody.create(jsonType, JSONObject.toJSONString(alarm));

            try {
                String response = HttpUtils.post(webHook, requestBody);
                log.info("[WebHookAlarmService] invoke webhook[url={}] successfully, response is {}", webHook,
                    response);
            } catch (Exception e) {
                log.warn("[WebHookAlarmService] invoke webhook[url={}] failed!", webHook, e);
            }
        });
    }
}
