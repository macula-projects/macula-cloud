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
package dev.macula.cloud.system.message;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 通过阿里云短信 SDK 按审核通过的模板编码提交测试短信。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Component
public class AliyunSmsTestSender {
    private final JsonMapper json = JsonMapper.builder().build();

    public void send(SysMessageProvider provider, String target, String templateCode, Map<String, String> variables) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(provider.getAccount()) ||
            !StringUtils.hasText(provider.getCredential()) || !StringUtils.hasText(provider.getSender())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "阿里短信配置或模板编码不完整");
        }
        try {
            Config config = new Config().setAccessKeyId(provider.getAccount())
                .setAccessKeySecret(provider.getCredential()).setEndpoint("dysmsapi.aliyuncs.com");
            Client client = createClient(config);
            var response = client.sendSms(new SendSmsRequest().setPhoneNumbers(target)
                .setSignName(provider.getSender()).setTemplateCode(templateCode)
                .setTemplateParam(json.writeValueAsString(variables)));
            if (response.getBody() == null || !"OK".equals(response.getBody().getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里短信提交失败");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里短信连接失败");
        }
    }

    Client createClient(Config config) throws Exception {
        return new Client(config);
    }
}
