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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.pojo.entity.SysUserIdentityBinding;
import dev.macula.cloud.iam.service.support.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 核验已有本地账号归属后原子创建第三方身份绑定，不负责注册新账号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyBindingService {
    private final PendingThirdPartyBindingRepository pending;
    private final IdentityProviderConfigService configs;
    private final SysUserService users;
    private final ThirdPartyUserDetailsService details;
    private final SysUserIdentityBindingMapper bindings;
    private final PasswordEncoder passwords;
    private final SmsService sms;

    @Transactional
    public BoundAccount bind(HttpServletRequest request, String username, String method, String password,
        String phone, String code, Authentication currentAuthentication) {
        PendingThirdPartyBindingRepository.Pending external = pending.current(request);
        try {
            configs.requireEnabled(external.providerType());
            SysUser account = users.findUniqueByUsername(username);
            if (account == null) {
                throw denied();
            }
            UserDetails user = details.load(account.getUsername());
            verifyProof(account, method, password, phone, code, currentAuthentication);
            if (configs.findBoundUsername(external.providerType(), external.subject()).isPresent()) {
                throw denied();
            }
            List<SysUserIdentityBinding> existing = bindings.selectList(new LambdaQueryWrapper<SysUserIdentityBinding>()
                .eq(SysUserIdentityBinding::getProviderType, external.providerType())
                .eq(SysUserIdentityBinding::getUsername, account.getUsername()));
            if (!existing.isEmpty()) {
                throw denied();
            }
            SysUserIdentityBinding binding = new SysUserIdentityBinding();
            binding.setProviderType(external.providerType());
            binding.setProviderSubject(external.subject());
            binding.setUsername(account.getUsername());
            bindings.insert(binding);
            pending.consume(request, external);
            return new BoundAccount(user, external.context().savedRequestTarget());
        } catch (RuntimeException ex) {
            pending.failedAttempt(request);
            throw ex;
        }
    }

    private void verifyProof(SysUser account, String method, String password, String phone, String code,
        Authentication current) {
        switch (proofMethod(method)) {
            case PASSWORD -> {
                if (!StringUtils.hasText(password) || !passwords.matches(password, account.getPassword())) {
                    throw denied();
                }
            }
            case SMS -> {
                String registered = normalize(account.getMobile());
                if (!StringUtils.hasText(registered) || !registered.equals(normalize(phone))) {
                    throw denied();
                }
                SysUser owner = users.findUniqueByMobile(account.getMobile());
                if (owner == null || !Objects.equals(owner.getId(), account.getId())
                    || !sms.verifyCode(phone, code)) {
                    throw denied();
                }
            }
            case SESSION -> {
                if (current == null || !current.isAuthenticated()
                    || !account.getUsername().equals(current.getName())) {
                    throw denied();
                }
            }
        }
    }

    private ProofMethod proofMethod(String value) {
        for (ProofMethod method : ProofMethod.values()) {
            if (method.value().equals(value)) {
                return method;
            }
        }
        throw denied();
    }

    private String normalize(String phone) {
        return phone == null ? null : phone.replace(" ", "").replace("-", "");
    }

    private BadCredentialsException denied() {
        return new BadCredentialsException("账号绑定验证失败");
    }

    /**
     * 本地账号归属证明方式。
     *
     * @since 6.1.0
     */
    private enum ProofMethod {
        PASSWORD("password"),
        SMS("sms"),
        SESSION("session");

        private final String value;

        ProofMethod(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    /**
     * 已认证的本地账号及服务端保存的续转目标。
     *
     * @since 6.1.0
     */
    public record BoundAccount(UserDetails user, String target) { }
}
