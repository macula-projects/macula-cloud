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
package dev.macula.cloud.iam.authentication.aliyun;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.GetMobileRequest;
import com.aliyun.dypnsapi20170525.models.GetMobileResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.tea.TeaUnretryableException;
import com.aliyun.teaopenapi.models.Config;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 使用阿里云号码认证 GetMobile 服务端接口把 App 登录 Token 核验为手机号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunMobileExternalCodeVerifier implements ExternalCodeVerifier {
    private static final String PROVIDER_TYPE = "ALIYUN_MOBILE";
    private static final String ACCESS_KEY_ID = "accessKeyId";
    private static final String ENDPOINT = "dypnsapi.aliyuncs.com";
    private static final String SUCCESS_CODE = "OK";
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;

    private final IdentityProviderConfigService configs;

    @Override
    public Set<ExternalCodeType> supportedTypes() {
        return Set.of(ExternalCodeType.ALIYUN_MOBILE_TOKEN);
    }

    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code) {
        try {
            ProviderConfig provider = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE);
            Config sdkConfig = new Config().setAccessKeyId(provider.require(ACCESS_KEY_ID))
                .setAccessKeySecret(provider.appSecret()).setEndpoint(ENDPOINT)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS).setReadTimeout(READ_TIMEOUT_MILLIS);
            GetMobileResponse response = new Client(sdkConfig).getMobile(
                new GetMobileRequest().setAccessToken(code));
            if (response == null || response.getBody() == null
                || !SUCCESS_CODE.equals(response.getBody().getCode())
                || response.getBody().getGetMobileResultDTO() == null
                || !StringUtils.hasText(response.getBody().getGetMobileResultDTO().getMobile())) {
                throw failed();
            }
            return ExternalCodeVerification.mobile(response.getBody().getGetMobileResultDTO().getMobile());
        } catch (AuthenticationServiceException ex) {
            throw ex;
        } catch (TeaException ex) {
            if (isInfrastructureFailure(ex)) {
                throw unavailable(ex, ex.getStatusCode());
            }
            throw failed();
        } catch (TeaUnretryableException ex) {
            throw unavailable(ex, null);
        } catch (Exception ex) {
            throw unavailable(ex, null);
        }
    }

    static boolean isInfrastructureFailure(TeaException exception) {
        return exception.getStatusCode() != null && exception.getStatusCode() >= 500;
    }

    private ExternalCodeInfrastructureException unavailable(Exception exception, Integer status) {
        log.warn("第三方接口调用失败 provider=ALIYUN_MOBILE stage=get_mobile "
                + "status={} failure=provider_unavailable exception={}", status,
            exception.getClass().getSimpleName());
        return new ExternalCodeInfrastructureException();
    }

    private AuthenticationServiceException failed() {
        return new AuthenticationServiceException("号码认证失败");
    }
}
