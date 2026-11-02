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

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

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
    private final String clientId;
    private String code;

    /**
     * 此构造函数用来初始化未授信凭据.
     *
     * @param principal the principal
     * @param code      the code
     */
    public SmsAuthenticationToken(Object principal, String code) {
        this(principal, code, (String)null);
    }

    /**
     * 创建绑定到 OAuth Client 的未认证短信凭据。
     *
     * @param principal 手机号
     * @param code 短信验证码
     * @param clientId OAuth Client ID
     */
    public SmsAuthenticationToken(Object principal, String code, String clientId) {
        super((Collection<? extends GrantedAuthority>)null);
        this.principal = principal;
        this.code = code;
        this.clientId = clientId;
        setAuthenticated(false);
    }

    /**
     * 此构造函数用来初始化授信凭据.
     *
     * @param principal   the principal
     * @param code        the code
     * @param authorities the authorities
     */
    public SmsAuthenticationToken(Object principal, String code, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.code = code;
        this.clientId = null;
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

    /**
     * 返回验证码绑定的 OAuth Client。
     *
     * @return OAuth Client ID；浏览器登录兼容路径为空
     */
    public String getClientId() {
        return clientId;
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
