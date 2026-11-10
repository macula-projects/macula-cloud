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

import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.authentication.sms.SmsCodePurpose;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import java.util.Objects;

/**
 * 有效外部核验pending内证明本地账号归属，所有来源复用同一绑定事务。
 * @author Rain
 * @since 6.1.0
 */
@Service
public class ThirdPartyBindingService {
    private final PendingThirdPartyBindingRepository pending;
    private final RegistrationIdentityBindingService bindings;
    private final SysUserService users;
    private final PasswordEncoder passwords;
    private final SmsService sms;
    private final UserIdentifierNormalizer normalizer;
    private ProviderDirectoryPolicyService directory;
    private org.springframework.transaction.support.TransactionTemplate transactions;

    /** 既有单元测试显式构造，不启动数据库或网络资源。 */
    public ThirdPartyBindingService(PendingThirdPartyBindingRepository pending, RegistrationIdentityBindingService bindings,
        SysUserService users, PasswordEncoder passwords, SmsService sms, UserIdentifierNormalizer normalizer) {
        this.pending = pending;
        this.bindings = bindings;
        this.users = users;
        this.passwords = passwords;
        this.sms = sms;
        this.normalizer = normalizer;
    }
    /** 生产使用显式事务，避免持有数据库锁等待平台目录请求。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ThirdPartyBindingService(PendingThirdPartyBindingRepository pending, RegistrationIdentityBindingService bindings,
        SysUserService users, PasswordEncoder passwords, SmsService sms, UserIdentifierNormalizer normalizer,
        ProviderDirectoryPolicyService directory, org.springframework.transaction.PlatformTransactionManager manager) {
        this(pending, bindings, users, passwords, sms, normalizer);
        this.directory = java.util.Objects.requireNonNull(directory);
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
    }


    /** 页面仅显示已配置短信能力，不以Client托管开关代替绑定证明策略。 */
    public boolean isSmsAvailable() { return sms.isAvailable(); }

    /** 绑定证明独立于托管按钮开关，只在有效pending和目标Client范围内可用。 */
    public BoundAccount bind(HttpServletRequest request, String username, String method, String password,
        String phone, String code, Authentication current) {
        PendingThirdPartyBindingRepository.Pending external = pending.current(request);
        var admission = directory == null ? null : directory.check(external.identity());
        if (transactions == null) {
            return bindVerified(request, username, method, password, phone, code, current, external, admission);
        }
        return transactions.execute(status -> bindVerified(request, username, method, password, phone, code,
            current, external, admission));
    }

    /** 账号归属证明、绑定锁和票据提交在同一事务中；此处没有远程目录调用。 */
    private BoundAccount bindVerified(HttpServletRequest request, String username, String method, String password,
        String phone, String code, Authentication current, PendingThirdPartyBindingRepository.Pending external,
        ProviderDirectoryPolicyService.Admission admission) {
        try {
            SysUser account = account(external, username);
            switch (method) {
                case "password" -> {
                    if (!StringUtils.hasText(password) || !StringUtils.hasText(account.getPassword())
                        || !passwords.matches(password, account.getPassword())) { throw denied(); }
                }
                case "sms" -> {
                    String registered = normalizer.mobile(account.getMobile()).orElseThrow(this::denied);
                    SysUser owner = users.findUniqueByMobile(registered);
                    if (owner == null || !Objects.equals(owner.getId(), account.getId())
                        || !normalizer.mobile(phone).filter(registered::equals).isPresent()
                        || !sms.verifyCode(registered, code, external.context().clientId(), SmsCodePurpose.IDENTITY_BINDING)) { throw denied(); }
                }
                case "session" -> {
                    if (current == null || !current.isAuthenticated() || !(current.getPrincipal() instanceof SysUserDetails details)
                        || !Objects.equals(details.getUserId(), account.getId())
                        || !Objects.equals(details.getTenantId(), external.context().tenantId())) { throw denied(); }
                }
                default -> throw denied();
            }
            UserDetails principal = external.context().clientAuthorizationVersion() != null
                ? bindings.bind(external.context().clientId(), external.context().tenantId(), external.context().userType(), external.identity(),
                    account.getId(), false, admission, external.context().clientAuthorizationVersion())
                : directory == null
                ? bindings.bind(external.context().clientId(), external.context().tenantId(),
                    external.context().userType(), external.identity(), account.getId(), false)
                : bindings.bind(external.context().clientId(), external.context().tenantId(),
                    external.context().userType(), external.identity(), account.getId(), false, admission);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 数据提交后才消费pending/Redis票据，回滚保留原有效期供重试。 */
                @Override
                public void afterCommit() {
                    pending.consume(request, external);
                    if (directory != null) { directory.bound(external.identity()); }
                }
            });
            return new BoundAccount(principal, external.context().savedRequestTarget(), external.claimed() != null, external.context(), method);
        } catch (AuthenticationException | DuplicateKeyException ex) {
            pending.failedAttempt(request);
            throw denied();
        }
    }

    /** 使用pending中的Client发送专用证明码，不依赖SDK不存在的SavedRequest。 */
    public void sendSms(HttpServletRequest request, String username, String phone) {
        PendingThirdPartyBindingRepository.Pending external = pending.current(request);
        try {
            SysUser account = account(external, username);
            String registered = normalizer.mobile(account.getMobile()).orElseThrow(this::denied);
            if (!normalizer.mobile(phone).filter(registered::equals).isPresent()) { throw denied(); }
            SysUser owner = users.findUniqueByMobile(registered);
            if (owner == null || !Objects.equals(owner.getId(), account.getId())) { throw denied(); }
            sms.sendCode(registered, request.getRemoteAddr(), external.context().clientId(), SmsCodePurpose.IDENTITY_BINDING);
        } catch (AuthenticationException ex) {
            pending.failedAttempt(request);
            // 与成功相同的响应，不通过发码接口暴露账号是否存在。
        }
    }

    /** 在证明前校验当前来源/路由/用户状态与认证范围。 */
    private SysUser account(PendingThirdPartyBindingRepository.Pending external, String username) {
        SysOAuth2Client client = bindings.requireClient(external.context().clientId(), external.identity(), false,
            external.context().tenantId(), external.context().userType());
        if ("IN_APP".equals(client.getApplicationType()) && external.claimed() == null
            && !Objects.equals(client.getAuthorizationVersion(), external.context().clientAuthorizationVersion())) { throw denied(); }
        SysUser account = users.findUniqueByUsername(username);
        bindings.validateAccount(client, external.identity(), account);
        return account;
    }
    /** 不暴露具体匹配身份或失败字段。 */
    private BadCredentialsException denied() { return new BadCredentialsException("账号绑定验证失败"); }
    /**
     * 浏览器与SDK完成方式分离，SDK不创建SSO会话。
     * @since 6.1.0
     */
    public record BoundAccount(UserDetails user, String target, boolean sdk, ThirdPartyLoginContext context,
        String proofMethod) {
        /** 无来源的Controller单测可显式构造；运行时bind总是携带原上下文。 */
        public BoundAccount(UserDetails user, String target, boolean sdk) {
            this(user, target, sdk, null, null);
        }
    }
}
