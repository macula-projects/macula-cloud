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

import dev.macula.cloud.iam.authentication.sms.SmsService;
import org.springframework.stereotype.Component;

/**
 * {@code SmsServiceImpl} 手机号验证服务
 *
 * @author rain
 * @since 2023/4/12 19:34
 */
public class SmsServiceImpl implements SmsService {
    @Override
    public boolean verifyCode(String phone, String rawCode) {
        // TODO 手机号验证
        return true;
    }

    @Override
    public void sendCode(String phone) {
        // 发送手机验证码

    }
}
