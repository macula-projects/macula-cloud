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

import org.springframework.security.core.userdetails.UserDetails;

/**
 * 邮箱认证的用户加载端口，不持有验证码或投递设施。
 *
 * @author Macula
 * @since 6.1.0
 */
public interface EmailUserDetailsService {
    /**
     * 在过滤器已固定的Client内加载唯一的邮箱账号，失败统一拒绝。
     */
    UserDetails loadUserByEmail(String email);
}
