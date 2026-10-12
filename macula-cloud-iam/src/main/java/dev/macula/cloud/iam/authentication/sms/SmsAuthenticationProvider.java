package dev.macula.cloud.iam.authentication.sms;

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
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Assert.isInstanceOf(SmsAuthenticationToken.class, authentication,
            () -> messages.getMessage("SmsAuthenticationProvider.onlySupports",
                "Only SmsAuthenticationToken is supported"));

        SmsAuthenticationToken unAuthenticationToken = (SmsAuthenticationToken)authentication;

        String phone = unAuthenticationToken.getName();
        String rawCode = (String)unAuthenticationToken.getCredentials();
        // 验证码校验
        if (smsService.verifyCode(phone, rawCode)) {
            UserDetails userDetails = smsUserDetailsService.loadUserByPhone(phone);
            //TODO 此处省略对UserDetails 的可用性 是否过期  是否锁定 是否失效的检验  建议根据实际情况添加  或者在 UserDetailsService 的实现中处理
            return createSuccessAuthentication(authentication, userDetails);
        } else {
            throw new BadCredentialsException("code is not matched");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return SmsAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(smsUserDetailsService, "smsUserDetailsService must not be null");
        Assert.notNull(smsService, "smsService must not be null");
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
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
