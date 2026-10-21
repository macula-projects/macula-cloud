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
package dev.macula.cloud.iam.authentication.thirdparty;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 第三方授权码认证令牌；认证成功后不再保留授权码。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ThirdPartyAuthenticationToken extends AbstractAuthenticationToken {
    private final String providerType;
    private final Object principal;
    private String code;
    private final ThirdPartyLoginContext loginContext;
    private final String redirectUri;

    private ThirdPartyAuthenticationToken(String providerType, Object principal, String code,
        ThirdPartyLoginContext loginContext, String redirectUri,
        Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.providerType = providerType;
        this.principal = principal;
        this.code = code;
        this.loginContext = loginContext;
        this.redirectUri = redirectUri;
        setAuthenticated(principal instanceof UserDetails);
    }

    public static ThirdPartyAuthenticationToken unauthenticated(String providerType, String code,
        ThirdPartyLoginContext context, String redirectUri) {
        return new ThirdPartyAuthenticationToken(providerType, providerType, code, context,
            redirectUri, java.util.List.of());
    }

    public static ThirdPartyAuthenticationToken authenticated(String providerType, UserDetails user,
        ThirdPartyLoginContext context) {
        return new ThirdPartyAuthenticationToken(providerType, user, null, context, null,
            user.getAuthorities());
    }

    @Override
    public Object getCredentials() {
        return code == null ? "" : code;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getProviderType() {
        return providerType;
    }

    public ThirdPartyLoginContext getLoginContext() {
        return loginContext;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        code = null;
    }
}
