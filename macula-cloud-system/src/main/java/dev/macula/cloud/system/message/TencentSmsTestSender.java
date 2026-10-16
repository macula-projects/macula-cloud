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

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import dev.macula.cloud.system.enums.MessageTemplateType;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 通过腾讯云短信 SDK 按审核通过的模板编码提交测试短信。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Component
public class TencentSmsTestSender {
    private final JsonMapper json = JsonMapper.builder().build();

    public void send(SysMessageProvider provider, String target, String templateCode,
        MessageTemplateType type, Map<String, String> variables) {
        TencentOptions options = StringUtils.hasText(provider.getConfigJson())
            ? json.readValue(provider.getConfigJson(), TencentOptions.class) : new TencentOptions(null);
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(options.getSmsSdkAppId()) ||
            !StringUtils.hasText(provider.getAccount()) || !StringUtils.hasText(provider.getCredential()) ||
            !StringUtils.hasText(provider.getSender())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "腾讯短信配置或模板编码不完整");
        }
        try {
            SmsClient client = createClient(new Credential(provider.getAccount(), provider.getCredential()),
                StringUtils.hasText(provider.getRegionCode()) ? provider.getRegionCode() : "ap-guangzhou");
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumberSet(new String[] {target});
            request.setSmsSdkAppId(options.getSmsSdkAppId());
            request.setSignName(provider.getSender());
            request.setTemplateId(templateCode);
            request.setTemplateParamSet(type.variables().stream().map(variables::get).toArray(String[]::new));
            SendStatus[] statuses = client.SendSms(request).getSendStatusSet();
            if (statuses == null || statuses.length != 1 || !"Ok".equals(statuses[0].getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "腾讯短信提交失败");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "腾讯短信连接失败");
        }
    }

    SmsClient createClient(Credential credential, String region) {
        return new SmsClient(credential, region);
    }

    /** 腾讯短信应用 ID。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TencentOptions {
        private String smsSdkAppId;
    }
}
