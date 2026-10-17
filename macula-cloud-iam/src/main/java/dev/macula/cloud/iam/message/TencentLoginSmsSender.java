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

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 使用腾讯云已审核模板提交 IAM 登录验证码。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Component
public class TencentLoginSmsSender {
    private final JsonMapper json = JsonMapper.builder().build();

    public void send(IamMessageProvider provider, IamMessageTemplate template, String phone, String code) {
        try {
            Map<?, ?> options = json.readValue(provider.getConfigJson(), Map.class);
            Object appId = options.get("smsSdkAppId");
            if (appId == null || !StringUtils.hasText(String.valueOf(appId)) ||
                !StringUtils.hasText(provider.getRegionCode())) {
                throw new IllegalStateException("登录短信配置不完整");
            }
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumberSet(new String[] {phone});
            request.setSmsSdkAppId(String.valueOf(appId));
            request.setSignName(provider.getSender());
            request.setTemplateId(template.getProviderTemplateCode());
            request.setTemplateParamSet(new String[] {code});
            SendStatus[] statuses = createClient(new Credential(provider.getAccount(), provider.getCredential()),
                provider.getRegionCode()).SendSms(request).getSendStatusSet();
            if (statuses == null || statuses.length != 1 || !"Ok".equals(statuses[0].getCode())) {
                throw new IllegalStateException("短信提交被拒绝");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("短信提交失败", ex);
        }
    }

    SmsClient createClient(Credential credential, String region) {
        return new SmsClient(credential, region);
    }
}
