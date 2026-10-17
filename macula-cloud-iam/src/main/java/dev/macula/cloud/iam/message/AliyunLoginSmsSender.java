/*
 * Copyright (c) 2026 Macula
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
package dev.macula.cloud.iam.message;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 使用阿里云已审核模板提交 IAM 登录验证码。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Component
public class AliyunLoginSmsSender {
    private final JsonMapper json = JsonMapper.builder().build();

    public void send(IamMessageProvider provider, IamMessageTemplate template, String phone, String code) {
        try {
            Config config = new Config().setAccessKeyId(provider.getAccount())
                .setAccessKeySecret(provider.getCredential()).setEndpoint("dysmsapi.aliyuncs.com");
            var response = createClient(config).sendSms(new SendSmsRequest()
                .setPhoneNumbers(phone).setSignName(provider.getSender())
                .setTemplateCode(template.getProviderTemplateCode())
                .setTemplateParam(json.writeValueAsString(Map.of("code", code))));
            if (response.getBody() == null || !"OK".equals(response.getBody().getCode())) {
                throw new IllegalStateException("短信提交被拒绝");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("短信提交失败", ex);
        }
    }

    Client createClient(Config config) throws Exception {
        return new Client(config);
    }
}
