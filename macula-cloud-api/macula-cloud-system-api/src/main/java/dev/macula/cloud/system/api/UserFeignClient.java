/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.system.api;

import dev.macula.cloud.system.api.fallback.AbstractUserFeignFallbackFactory;
import dev.macula.cloud.system.dto.UserAuthInfo;
import dev.macula.cloud.system.vo.user.UserLoginVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "macula-cloud-system", contextId = "userFeignClient",
    fallbackFactory = AbstractUserFeignFallbackFactory.class)
public interface UserFeignClient {

    @GetMapping("/api/v1/users/{username}/authinfo")
    UserAuthInfo getUserAuthInfo(@PathVariable String username);

    @GetMapping("/api/v1/users/{username}/userinfo")
    UserLoginVO getUserInfo(@PathVariable String username);
}
