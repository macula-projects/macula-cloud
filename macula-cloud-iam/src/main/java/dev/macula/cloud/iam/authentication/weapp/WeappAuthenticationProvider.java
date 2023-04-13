package dev.macula.cloud.iam.authentication.weapp;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Objects;

/**
 * Miniapp authentication provider.
 *
 * @author felord.cn
 * @since 1.0.8.RELEASE
 */
public class WeappAuthenticationProvider implements AuthenticationProvider, MessageSourceAware {

    private final GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();
    private final WeappUserDetailsService weappUserDetailsService;
    private MessageSourceAccessor messages = SpringSecurityMessageSource.getAccessor();

    public WeappAuthenticationProvider(WeappUserDetailsService weappUserDetailsService) {
        this.weappUserDetailsService = weappUserDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Assert.isInstanceOf(WeappAuthenticationToken.class, authentication,
            () -> messages.getMessage("MiniAppAuthenticationProvider.onlySupports",
                "Only MiniAppAuthenticationToken is supported"));

        WeappAuthenticationToken unAuthenticationToken = (WeappAuthenticationToken)authentication;
        WeappRequest credentials = (WeappRequest)unAuthenticationToken.getCredentials();
        UserDetails userDetails =
            weappUserDetailsService.loadByOpenId(credentials.getClientId(), credentials.getOpenId());
        if (Objects.isNull(userDetails)) {
            userDetails = weappUserDetailsService.register(credentials);
        }
        return createSuccessAuthentication(authentication, userDetails);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WeappAuthenticationToken.class.isAssignableFrom(authentication);
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
        WeappAuthenticationToken authenticationToken = new WeappAuthenticationToken(user, authorities);
        authenticationToken.setDetails(authentication.getPrincipal());

        return authenticationToken;
    }
}
