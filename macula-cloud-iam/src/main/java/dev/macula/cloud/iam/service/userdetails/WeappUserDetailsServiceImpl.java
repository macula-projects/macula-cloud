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

import dev.macula.cloud.iam.authentication.weapp.WeappRequest;
import dev.macula.cloud.iam.authentication.weapp.WeappUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@code WeappUserDetailsServiceImpl} 微信用户加载或者注册绑定服务
 *
 * @author rain
 * @since 2023/4/12 19:50
 */
public class WeappUserDetailsServiceImpl implements WeappUserDetailsService {
    @Override
    public UserDetails register(WeappRequest request) {
        // TODO 微信注册服务
        return null;
    }

    @Override
    public UserDetails loadByOpenId(String clientId, String openId) {
        return null;
    }
}
