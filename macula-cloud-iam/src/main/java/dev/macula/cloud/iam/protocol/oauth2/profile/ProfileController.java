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
package dev.macula.cloud.iam.protocol.oauth2.profile;

import dev.macula.cloud.iam.vo.ProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 平台OAuth资料端点，独立于标准OIDC UserInfo。
 *
 * @author Rain
 * @since 6.1.0
 */
@RestController
public final class ProfileController {
    public static final String PATH = "/api/v1/profile";
    private final ProfileService profiles;

    /**
     * 资料权限由统一服务验证。
     */
    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    /**
     * 仅接受单个Bearer头，禁止Query或重复凭据。
     */
    @GetMapping(PATH)
    public ProfileVO profile(HttpServletRequest request) {
        var headers = java.util.Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1 || !headers.get(0).regionMatches(true, 0, "Bearer ", 0, 7)
                || headers.get(0).substring(7).isBlank() || request.getParameter("access_token") != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return profiles.profile(headers.get(0).substring(7));
    }
}
