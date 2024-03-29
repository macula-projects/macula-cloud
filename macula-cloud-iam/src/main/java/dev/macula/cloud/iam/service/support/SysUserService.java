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

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysUser;

import java.util.Set;

/**
 * 用户业务接口
 *
 * @author haoxr
 * @since 2022/1/14
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名获取认证信息(给oauth2调用的，带了密码，不要给其他应用访问）
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @param groupIds 人群包ID集合
     * @return UserAuthInfo
     */
    UserAuthInfo getUserAuthInfo(Long tenantId, String username, Set<Long> groupIds);

}
