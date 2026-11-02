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
package dev.macula.cloud.iam.service.userdetails;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.pojo.entity.SysUserIdentityBinding;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 在明确的 OAuth Client 用户类型边界内唯一解析本地账号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ClientAwareAccountResolver {
    private final SysUserService users;
    private final SysUserIdentityBindingMapper bindings;
    private final UserIdentifierNormalizer normalizer;

    /**
     * 把同一输入作为用户名、手机号和邮箱候选解析，多个不同账号命中时失败关闭。
     *
     * @param userType OAuth Client 用户类型
     * @param identifier 登录标识输入
     * @return 唯一账号
     */
    public SysUser resolveIdentifier(UserTypeEnum userType, String identifier) {
        requireEmployee(userType);
        Map<Long, SysUser> candidates = new LinkedHashMap<>();
        normalizer.username(identifier).map(users::findUniqueByUsername)
            .ifPresent(user -> addCandidate(candidates, user));
        normalizer.mobile(identifier).map(users::findUniqueByMobile)
            .ifPresent(user -> addCandidate(candidates, user));
        normalizer.email(identifier).map(users::findUniqueByEmail)
            .ifPresent(user -> addCandidate(candidates, user));
        if (candidates.size() != 1) {
            throw denied();
        }
        return candidates.values().iterator().next();
    }

    /**
     * 按服务端统一规则唯一解析手机号。
     *
     * @param userType OAuth Client 用户类型
     * @param mobile 已由 IAM 或受信第三方核验的手机号
     * @return 可能存在的唯一账号
     */
    public Optional<SysUser> findByMobile(UserTypeEnum userType, String mobile) {
        requireEmployee(userType);
        return normalizer.mobile(mobile).map(users::findUniqueByMobile);
    }

    /**
     * 按服务端统一规则唯一解析邮箱。
     *
     * @param userType OAuth Client 用户类型
     * @param email 已由 IAM 核验的邮箱
     * @return 可能存在的唯一账号
     */
    public Optional<SysUser> findByEmail(UserTypeEnum userType, String email) {
        requireEmployee(userType);
        return normalizer.email(email).map(users::findUniqueByEmail);
    }

    /**
     * 按 provider 和稳定主体读取唯一的外部身份绑定。
     *
     * @param userType OAuth Client 用户类型
     * @param providerType 固定提供商类型
     * @param subject 已核验的稳定主体
     * @return 可能存在的唯一账号
     */
    public Optional<SysUser> findByExternalIdentity(UserTypeEnum userType, String providerType, String subject) {
        requireEmployee(userType);
        if (!StringUtils.hasText(providerType) || !StringUtils.hasText(subject)) {
            throw denied();
        }
        List<SysUserIdentityBinding> matches = bindings.selectList(
            new LambdaQueryWrapper<SysUserIdentityBinding>()
                .eq(SysUserIdentityBinding::getProviderType, providerType)
                .eq(SysUserIdentityBinding::getProviderSubject, subject));
        if (matches.size() > 1) {
            throw denied();
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        SysUser account = users.findUniqueByUsername(matches.get(0).getUsername());
        if (account == null) {
            throw denied();
        }
        return Optional.of(account);
    }

    private void addCandidate(Map<Long, SysUser> candidates, SysUser user) {
        if (user.getId() == null) {
            throw denied();
        }
        candidates.put(user.getId(), user);
    }

    private void requireEmployee(UserTypeEnum userType) {
        if (userType != UserTypeEnum.EMPLOYEE) {
            throw denied();
        }
    }

    private BadCredentialsException denied() {
        return new BadCredentialsException("User credentials are invalid.");
    }
}
