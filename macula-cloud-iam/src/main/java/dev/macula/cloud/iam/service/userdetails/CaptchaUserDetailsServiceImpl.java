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

package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.iam.authentication.captcha.CaptchaUserDetailsService;
import dev.macula.cloud.iam.service.support.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * {@code CaptchaUserDetailsServiceImpl} 通过手机号获取用户
 *
 * @author rain
 * @since 2023/4/12 19:28
 */
@RequiredArgsConstructor
public class CaptchaUserDetailsServiceImpl implements CaptchaUserDetailsService {

    private final UserDetailsService userDetailsService;

    @Override
    public UserDetails loadUserByPhone(String phone) throws UsernameNotFoundException {
        // TODO 从手机号获取用户名
        String username = "";
        return userDetailsService.loadUserByUsername(username);
    }
}
