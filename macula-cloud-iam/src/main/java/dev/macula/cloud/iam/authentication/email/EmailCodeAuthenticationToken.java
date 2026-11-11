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
package dev.macula.cloud.iam.authentication.email;

import java.io.Serial;
import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * 承载邮箱和邮件验证码的认证令牌。
 *
 * @author Macula
 * @since 6.1.0
 */
public class EmailCodeAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Object principal;
    private final String clientId;
    private String code;

    /**
     * 此构造函数用来初始化未授信凭据.
     *
     * @param principal the principal
     * @param code the code
     */
    public EmailCodeAuthenticationToken(Object principal, String code) {
        this(principal, code, (String) null);
    }

    /**
     * 创建绑定到 OAuth Client 的未认证邮箱凭据。
     *
     * @param principal 邮箱
     * @param code 短信验证码
     * @param clientId OAuth Client ID
     */
    public EmailCodeAuthenticationToken(Object principal, String code, String clientId) {
        super((Collection<? extends GrantedAuthority>) null);
        this.principal = principal;
        this.code = code;
        this.clientId = clientId;
        setAuthenticated(false);
    }

    /**
     * 此构造函数用来初始化授信凭据.
     *
     * @param principal the principal
     * @param code the code
     * @param authorities the authorities
     */
    public EmailCodeAuthenticationToken(Object principal, String code,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.code = code;
        this.clientId = null;
        // must use super, as we override
        super.setAuthenticated(true);
    }

    /**
     * 返回短期验证码，认证成功前后均不可记录。
     */
    @Override
    public Object getCredentials() {
        return this.code;
    }

    /**
     * 返回邮箱标识或已验证本地用户。
     */
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

    /**
     * 只有成功构造器可以建立可信认证。
     */
    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                    "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
        }

        super.setAuthenticated(false);
    }

    /**
     * 认证完成即清除明文验证码。
     */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        code = null;
    }
}
