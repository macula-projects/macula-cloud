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

import dev.macula.cloud.iam.service.userdetails.OAuthClientUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * 回调时重新校验 OAuth client 安全边界并按其用户类型加载账号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyUserDetailsService {
    private final OAuthClientUserDetailsService userDetailsService;

    public UserDetails load(ThirdPartyLoginContext context, String username) {
        return userDetailsService.loadUserByClient(context.clientId(), context.tenantId(), context.userType(),
            username);
    }
}
