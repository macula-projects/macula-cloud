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

package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.iam.authentication.weapp.WeappClient;
import dev.macula.cloud.iam.authentication.weapp.WeappClientService;

/**
 * {@code WeappClientServiceImpl} 获取微信Client配置信息
 *
 * @author rain
 * @since 2023/4/12 19:49
 */
public class WeappClientServiceImpl implements WeappClientService {
    @Override
    public WeappClient get(String clientId) {
        // TODO 获取微信CLIENT信息
        return null;
    }
}
