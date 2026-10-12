package dev.macula.cloud.iam.authentication.sms;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;

import java.io.Serial;
import java.util.Collection;

/**
 * 承载手机号和短信验证码的认证令牌。
 *
 * @author n1
 * @since 6.1.0
 */
public class SmsAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Object principal;
    private String code;

    /**
     * 此构造函数用来初始化未授信凭据.
     *
     * @param principal the principal
     * @param code   the code
     */
    public SmsAuthenticationToken(Object principal, String code) {
        super((Collection<? extends GrantedAuthority>)null);
        this.principal = principal;
        this.code = code;
        setAuthenticated(false);
    }

    /**
     * 此构造函数用来初始化授信凭据.
     *
     * @param principal   the principal
     * @param code     the code
     * @param authorities the authorities
     */
    public SmsAuthenticationToken(Object principal, String code,
        Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.code = code;
        // must use super, as we override
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.code;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
        }

        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        code = null;
    }
}
