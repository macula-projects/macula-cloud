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
package dev.macula.cloud.iam.authentication.github;

import dev.macula.cloud.iam.authentication.thirdparty.AbstractThirdPartyAuthenticationFilter;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;

/**
 * GitHub 登录发起与回调过滤器。
 *
 * @author Rain
 * @since 6.1.0
 */
public class GithubAuthenticationFilter extends AbstractThirdPartyAuthenticationFilter {
    private GithubClient client;

    public GithubAuthenticationFilter() {
        super("GITHUB", "/login/github");
    }

    void githubClient(GithubClient client) {
        this.client = client;
    }

    @Override
    protected String authorizationUri(ProviderConfig config, String state, String redirectUri) {
        return client.authorizationUri(config, state, redirectUri);
    }
}
