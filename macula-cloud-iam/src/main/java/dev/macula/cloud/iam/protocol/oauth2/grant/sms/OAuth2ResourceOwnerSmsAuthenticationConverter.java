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

package dev.macula.cloud.iam.protocol.oauth2.grant.sms;

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.authentication.captcha.CaptchaAuthenticationFilter;
import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2ResourceOwnerBaseAuthenticationConverter;
import dev.macula.cloud.iam.utils.OAuth2EndpointUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

/**
 * @author lengleng
 * @date 2022-05-31
 *
 *     短信登录转换器
 */
public class OAuth2ResourceOwnerSmsAuthenticationConverter
    extends OAuth2ResourceOwnerBaseAuthenticationConverter<OAuth2ResourceOwnerSmsAuthenticationToken> {

    /**
     * 是否支持此convert
     *
     * @param grantType 授权类型
     * @return boolean 是否支持当前的grant type
     */
    @Override
    public boolean support(String grantType) {
        return SecurityConstants.GRANT_TYPE_SMS.equals(grantType);
    }

    @Override
    public OAuth2ResourceOwnerSmsAuthenticationToken buildToken(Authentication clientPrincipal,
        Set<String> requestedScopes, Map<String, Object> additionalParameters) {
        return new OAuth2ResourceOwnerSmsAuthenticationToken(
            new AuthorizationGrantType(SecurityConstants.GRANT_TYPE_SMS), clientPrincipal, requestedScopes,
            additionalParameters);
    }

    /**
     * 校验扩展参数 密码模式密码必须不为空
     *
     * @param request 参数列表
     */
    @Override
    public void checkParams(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
        // PHONE (REQUIRED)
        String phone = parameters.getFirst(CaptchaAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY);
        if (!StringUtils.hasText(phone) || parameters.get(CaptchaAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY)
            .size() != 1) {
            OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST,
                CaptchaAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY,
                OAuth2EndpointUtils.ACCESS_TOKEN_REQUEST_ERROR_URI);
        }

        // CAPTCHA (REQUIRED)
        String captcha = parameters.getFirst(CaptchaAuthenticationFilter.SPRING_SECURITY_FORM_CAPTCHA_KEY);
        if (!StringUtils.hasText(captcha) || parameters.get(
            CaptchaAuthenticationFilter.SPRING_SECURITY_FORM_CAPTCHA_KEY).size() != 1) {
            OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST,
                CaptchaAuthenticationFilter.SPRING_SECURITY_FORM_CAPTCHA_KEY,
                OAuth2EndpointUtils.ACCESS_TOKEN_REQUEST_ERROR_URI);
        }
    }
}
