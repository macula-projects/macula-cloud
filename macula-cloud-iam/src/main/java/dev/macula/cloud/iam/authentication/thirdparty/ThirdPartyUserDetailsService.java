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

import dev.macula.boot.enums.StatusEnum;
import dev.macula.boot.enums.DataScopeEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 仅按全局用户名加载本地身份，不用租户或 OAuth2 client 决定账号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyUserDetailsService {
    private final SysUserService users;

    public UserDetails load(String username) {
        SysUser user = users.findUniqueByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (!StatusEnum.ENABLE.getValue().equals(user.getStatus())) {
            throw new DisabledException("该账户已被禁用");
        }
        UserAuthInfo info = new UserAuthInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setPassword(user.getPassword());
        info.setStatus(user.getStatus());
        info.setDeptId(user.getDeptId());
        info.setDataScope(DataScopeEnum.DEFAULT.getValue());
        return new SysUserDetails(info);
    }
}
