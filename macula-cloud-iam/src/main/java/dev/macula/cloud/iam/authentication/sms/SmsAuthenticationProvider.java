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
package dev.macula.cloud.iam.authentication.sms;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.Assert;

import java.util.Collection;

/**
 * 校验短信验证码并建立用户认证状态。
 *
 * @author n1
 * @since 6.1.0
 */
public class SmsAuthenticationProvider implements AuthenticationProvider, InitializingBean, MessageSourceAware {

    private final GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();
    private final SmsUserDetailsService smsUserDetailsService;
    private final SmsService smsService;
    private MessageSourceAccessor messages = SpringSecurityMessageSource.getAccessor();

    public SmsAuthenticationProvider(SmsUserDetailsService smsUserDetailsService,
        SmsService smsService) {
        this.smsUserDetailsService = smsUserDetailsService;
        this.smsService = smsService;
    }

    @Override
    public Authentication authenticate(@NonNull Authentication authentication) throws AuthenticationException {
        Assert.isInstanceOf(SmsAuthenticationToken.class, authentication,
            () -> messages.getMessage("SmsAuthenticationProvider.onlySupports",
                "Only SmsAuthenticationToken is supported"));

        SmsAuthenticationToken unAuthenticationToken = (SmsAuthenticationToken)authentication;

        String phone = unAuthenticationToken.getName();
        String rawCode = (String)unAuthenticationToken.getCredentials();
        // 验证码校验
        boolean verified = unAuthenticationToken.getClientId() == null
            ? smsService.verifyCode(phone, rawCode)
            : smsService.verifyCode(phone, rawCode, unAuthenticationToken.getClientId());
        if (verified) {
            UserDetails userDetails = smsUserDetailsService.loadUserByPhone(phone);
            return createSuccessAuthentication(authentication, userDetails);
        } else {
            throw new BadCredentialsException("code is not matched");
        }
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {
        return SmsAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(smsUserDetailsService, "smsUserDetailsService must not be null");
        Assert.notNull(smsService, "smsService must not be null");
    }

    @Override
    public void setMessageSource(@NonNull MessageSource messageSource) {
        this.messages = new MessageSourceAccessor(messageSource);
    }

    /**
     * 认证成功将非授信凭据转为授信凭据. 封装用户信息 角色信息。
     *
     * @param authentication the authentication
     * @param user           the user
     * @return the authentication
     */
    protected Authentication createSuccessAuthentication(Authentication authentication, UserDetails user) {

        Collection<? extends GrantedAuthority> authorities = authoritiesMapper.mapAuthorities(user.getAuthorities());
        SmsAuthenticationToken authenticationToken = new SmsAuthenticationToken(user, null, authorities);
        authenticationToken.setDetails(authentication.getDetails());

        return authenticationToken;
    }

}
