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

package dev.macula.cloud.iam.service.support;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;

/**
 * {@code UserAuthInfoService} 根据用户类型和用户名获取用户认证信息，用于多身份体系，比如会员、行政员工、外包等多个来源
 *
 * @author rain
 * @since 2023/4/15 21:46
 */

public interface UserAuthInfoService {
    /**
     * 获取用户信息，上层覆盖该方法
     *
     * @param userType 用户类型
     * @param username 用户名
     * @return 返回指定用户类型和用户名的用户信息
     */
    UserAuthInfo getUserAuthInfo(UserTypeEnum userType, String username);
}
