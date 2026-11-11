# Plan: IAM 托管邮箱验证码登录与认证规则收敛 (from spec.md 2026-11-11)

Status: accepted。已接受[spec.md](spec.md)，用户2026-11-11以OK确认规格，Plan审查已完成（1个实际轮次，NO_FINDINGS），用户以OK明确批准，进入Phase B。作者署名待确认，不推断署名；无提交/推送/业务库操作授权。

使用现有Java17、Spring Security、Spring Authorization Server、Jackson3、Redis/MySQL、JUnit/Mockito、Vue/Vitest/Cypress与本地SVG；不新增或升级运行库。现有邮件依赖用于隔离SMTP测试。当前DefaultSecurityConfiguration已由用户放回config根包，以实际工作区为准，不再次移动。未找到CLAUDE.md，遵循Cloud AGENTS.md及相关规则。

REVIEW.md未指定Stage3审查器，工具检索未发现codex-plan-review；按sdlc-build回退sdlc-reviewer子代理，只运行Plan pass，最多3轮，每轮记录review-findings.md；后续只审变更，不重复已记录问题。

## Files that change

- `.sdlc/iam-architecture-simplification/verification.md`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysRoleMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysUser.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/CustomeOAuth2AccessTokenGenerator.java`
- `macula-cloud-iam/src/main/java/org/springframework/security/config/annotation/web/builders/FilterOrderRegUtils.java`
- `macula-cloud-iam/src/test/java/org/springframework/security/config/annotation/web/builders/HttpSecurityFilterOrderRegistrationUtilsTest.java`

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/DefaultAccessTokenClaimsServiceTest.java`

- `macula-cloud-admin/cypress/e2e/iam-hosted-login.cy.js`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/cache/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/userdetails/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/client/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/package-info.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/AuthenticationMethod.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaSessionAuthenticationStrategy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/DefaultAccessTokenClaimsService.java`

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryRefreshServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeRateLimiterTest.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationService.java`

- `.sdlc/iam-hosted-email-login/build-notes.md`
- `.sdlc/iam-hosted-email-login/intent.md`
- `.sdlc/iam-hosted-email-login/plan.md`
- `.sdlc/iam-hosted-email-login/review-findings.md`
- `.sdlc/iam-hosted-email-login/spec.md`
- `.sdlc/iam-hosted-email-login/verification.md`
- `macula-cloud-admin/README.md`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-api/macula-cloud-system-api/README.md`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2HostedLoginMethod.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/OAuth2HostedLoginMethodTest.java`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/docs/architecture.md`
- `macula-cloud-iam/docs/architecture.png`
- `macula-cloud-iam/docs/architecture.svg`
- `macula-cloud-iam/docs/configuration.md`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailCodeAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailCodeAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailCodeAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailCodeService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailUserDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginPolicyFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/LoginRequestMatchers.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsCodePurpose.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/UnavailableSmsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderCacheCipher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderCacheSecurityProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/userdetails/HostedUserDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/EmailVerificationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/RedisVerificationCodeStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/SmsVerificationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/VerificationChannel.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/VerificationCodePurpose.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeScope.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeSettings.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/EmailCodeController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/jackson/EmailCodeAuthenticationTokenDeserializer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/jackson/EmailCodeAuthenticationTokenMixin.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/jackson/MaculaIamJacksonModule.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/AliyunLoginSmsSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/LoginMailDispatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/LoginMailSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/LoginSmsDispatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/MessageAuditHasher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/SmsVerificationCodeStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/SmsVerificationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/TencentLoginSmsSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/audit/LoginMessageAudit.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/audit/MessageAuditHasher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/mail/LoginMailDispatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/mail/MailSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/mail/SmtpMailSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/sms/AliyunLoginSmsSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/sms/LoginSmsDispatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/sms/SmsSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/sms/TencentLoginSmsSender.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/template/LoginMessageConfigurationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/ClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/ClientAwareAccountResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/directory/IdentityDirectoryService.java`
- `macula-cloud-iam/src/main/resources/application.yml`
- `macula-cloud-iam/src/main/resources/static/iam/auth.css`
- `macula-cloud-iam/src/main/resources/static/iam/auth.js`
- `macula-cloud-iam/src/main/resources/static/iam/icons/README.md`
- `macula-cloud-iam/src/main/resources/static/iam/icons/dingtalk.svg`
- `macula-cloud-iam/src/main/resources/static/iam/icons/feishu.svg`
- `macula-cloud-iam/src/main/resources/static/iam/icons/github.svg`
- `macula-cloud-iam/src/main/resources/static/iam/icons/wechat.svg`
- `macula-cloud-iam/src/main/resources/static/iam/icons/wecom.svg`
- `macula-cloud-iam/src/main/resources/templates/login.html`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/email/EmailCodeAuthenticationFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/email/EmailCodeAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/email/EmailCodeServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginPolicyFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/LoginRequestMatchersTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyAuthenticationFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderApplicationTokenCacheIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderCacheCipherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/userdetails/HostedUserDetailsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/RedisVerificationCodeStoreTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/MfaProtocolIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedEmailLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ThirdPartyBindingControllerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/jackson/EmailCodeAuthenticationTokenSerializationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/jackson/LegacyEmailPrincipalReaderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/LoginSmsDispatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/SmsVerificationCodeStoreTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/mail/LoginMailDispatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/mail/SmtpMailSenderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/sms/LoginSmsDispatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/support/FakeSmtpServer.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/template/LoginMessageConfigurationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/ClientAwareAccountResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/userdetails/SmsServiceImplTest.java`
- `macula-cloud-system/README.md`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/resources/db/migration/V25__iam_hosted_email_login.sql`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/HostedEmailLoginMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`

## Order of work

1. **保护基线和明确契约**：以当前工作区为基线，现有架构精简及V24应用URL/IP改动均保留。计划列出精确旧/新路径，搬包同步现有调用与测试；本地基线状态和相关文件摘要留在临时目录。实现前再核对迁移版本。新增Java类型/方法依仓库版权头和Javadoc要求。
2. **验证码共用内核**：将SmsVerificationCodeStore中的算法收敛为VerificationCodeStore及Redis实现，VerificationCodeService复用申请/发送回调/激活/消费流程，通过有类型Scope(Channel, Purpose, Client)和Settings表达约束。原SmsCodePurpose内部引用迁到VerificationCodePurpose，显式wire值仍为login/identity-binding。SMS所有旧Redis key、browser上下文、HMAC输入、限流作用域、Lua及成功/失败时序不变，EMAIL独立命名空间。SmsVerificationProperties保留原配置键；EmailVerificationProperties独立键且相同默认数值；敏感配置不生成toString。发送回调用有类型参数/Consumer组合，不做万能Message Map或抽象父类。
3. **消息职责与投递端口**：将MailSender/SmsSender作为实际投递端口，现有SMTP、Aliyun、Tencent实现迁入mail/sms。LoginMessageConfigurationService集中受控渠道/模板读取，分别保留邮件默认模板、HTML转义与短信供应商模板参数合同。LoginMessageAudit复用目标摘要与安全分类，MessageAuditHasher保持原算法/密钥/日志字段；投递只标记SUBMITTED，不声称实际送达。短信供应商选择拒绝重复/未知键。不新增通用发送Controller，不调用真实用户邮箱。
4. **共同账号规则与准入**：ClientAccountDetailsService新增显式托管标识加载用例，HostedUserDetailsService只适配可信上下文；保留托管员工本地优先与原显式用户名加载扩展优先的差异。明确无Client旧门户路径，缺少上下文不增加请求参数/header/cookie信任。手机号/邮箱唯一候选与最终加载主体userId/租户必须一致。准入快照集中完整谓词，保留锁内身份键/来源/flow/组织/时效复核，删除未用弱requireCurrent，事务与提交后消费边界不动。ProviderCacheSecurityProperties/装配映射原external-code配置键，移除ProviderCacheCipher对协议类的依赖，不改密钥和派生输入。
5. **邮箱登录与序列化**：增加薄EmailCodeAuthenticationToken/Filter/Provider/Configurer与EmailCodeService/EmailUserDetailsService，全部仅装配到登录链。Token使用新全限定名，区别退役EmailAuthenticationToken；补严格Jackson mixin/deserializer及Session/Redis往返。授权存储的BasicPolymorphicTypeValidator仅新增允许新的具体EmailCodeAuthenticationToken，保留其余精确类型与旧Principal读取；不放开包前缀或任意Authentication子类。Provider只处理凭据和调用已有账号端口，验证码状态进入共用内核。原短信Token/Filter/Configurer和序列化类型保持；不为两种薄框架适配器制造额外继承层。
6. **Client策略与API**：API枚举新增EMAIL；HostedLoginClientPolicyService按枚举明确处理三个全局开关，EMAIL默认true且可显式false；HostedLoginPolicyFilter在POST发码/认证前固定EMAIL方法，拒绝在副作用前发生。新增POST/login/email及POST/api/v1/email-code，字段email/code，CSRF及原失败/成功续转保持；无Client门户对齐SMS现有受控分支，不借header/cookie降级。发码与SMS一样，对合法输入受限发送，不查账号存在性生成不同响应；认证才查唯一账号。MAIL_LOGIN/SMTP未配置时失败关闭，发送失败不激活。旧/api/v1/oauth2/email-codes仍410，所有退役grant仍拒绝。
7. **匿名入口去重**：LoginRequestMatchers分组已有资源和登录/绑定入口，health单独保留；第三方Configurer保存同一份不可变精确paths描述，公开白名单和Filter都由它派生。Filter仍只匹配既有GET；旧匿名规则的HTTP方法语义保持，不把所有枚举或UNKNOWN/空路径匿名公开。主配置压到具名三组，删除无H2依赖的控制台放行，CSRF/策略/框架Filter顺序和成功处理不变。以路径/方法矩阵对比，不使用/login/**或/api/**。
8. **存量一次性迁移和管理端**：当前下一版本V25，新增数据迁移只对purpose=APPLICATION、支持托管的应用类型、合法非空且尚无EMAIL的显式集合追加EMAIL；NULL默认继承，空全关、非法、SERVICE/IN_APP/RESOURCE_SERVER保持。幂等、不改主键/Secret/grant/协议/来源/绑定/authorizationVersion；完整配置哈希自然使旧表单409。不在每次read时补值。Admin新增EMAIL复选框，新增默认沿用PASSWORD/SMS/EMAIL，不支持托管的类型仍[]；用户取消EMAIL后保存、重载、重启和再次迁移都不重开。保留原校验与CAS。仅在隔离MySQL执行原迁移链及新迁移，不连接业务库。
9. **页面与图标**：邮箱表单保持SMS风格，补展示/发码/倒计时/错误/重复提交。五家平台图标使用现有或许可明确的本地SVG并记录来源，单行居中、可访问名称/焦点/提示，320/390px及桌面实测；图标显示条件/原href/回调保持。修改static/iam样式，不引入UI库或CDN，不改第三方合同。
10. **文档与验证收尾**：README维持模块介绍，configuration明确EMAIL策略、全局键和一次性迁移；删除OIDC email Scope已开放的暗示，保留邮箱属性说明；更新真实类图。先最小测试，再隔离Redis/SMTP/MySQL、安全链、跨模块全仓及Admin回归；记录真实命令、失败修正与未验证边界。实际路径或方案偏差先更新本计划，影响规格合同则重新请求工程师裁定；不提交推送部署。

## Risks

- 当前工作区已有大量架构重构及用户其他变更，不能恢复HEAD或整体覆盖；本轮只修改列明路径内对应职责。V24不在本计划写入范围，新的版本如被占用则先更新计划。
- SMS验证码和缓存摘要兼容是硬约束；抽取代码前用固定输入/真实隔离Redis证明原键/HMAC及TTL，不能以“旧验证码重新获取即可”隐藏破坏性变化。
- 新邮箱认证不能使用退役EmailAuthenticationToken全限定名，也不能扩大任意Jackson类型白名单。新旧载体在两种记录中的读取分别验证。
- 一次性开启认证面是用户明确要求；只向已允许托管且非空的列表追加，空保持。回滚旧版本须同步处理EMAIL值；真实环境迁移/账号质量/邮件渠道就绪仍需负责人单独执行和确认，不在本地操作范围。
- 账号加载两个既有目录优先级有意不同。规范化、停用、歧义、跨租户、加载主体一致性和旧门户路由必须分用例测试，不用通用fallback吞异常。
- 主体匹配及绑定锁内准入不能减弱，较弱的requireCurrent不得取代原完整校验；外部网络仍在事务外，票据在提交后消费。
- 图标许可和SMTP环境不可假设：只用可用许可的本地资源，测试收件人为合成地址并由隔离SMTP捕获。真实送达/真机不声称已验证。
- sdlc技能要求接受后commit与用户无提交约束相冲突，遵循用户，只维护本地accepted状态；作者署名仍待确认，不影响实现，无额外组织品牌/合规技能可用。

## Proof

| 规格 | 必须证明的行为与测试 |
| --- | --- |
| R1/R2/R3 | HostedLoginClientPolicyServiceTest/FilterTest、EmailCodeAuthenticationProviderTest、HostedUserDetailsServiceTest与HostedEmailLoginFlowTest覆盖EMAIL启用/关闭、全局优先、Client路由、唯一/歧义/停用/跨租户、缺上下文/伪造client_id、成功Session及原PKCE/OIDC续转。API枚举与System写校验及Admin保存联动，不能只测页面隐藏。 |
| R4/R6 | RedisVerificationCodeStoreTest、VerificationCodeServiceTest覆盖SMS固定输入摘要/key/TTL兼容、EMAIL/SMS/Client/用途隔离、错误次数、过期、并发一次消费、发送失败不激活；真实隔离Redis，不仅Mock。Mail/Sms Dispatcher、配置服务与假SMTP覆盖模板选择、重复/未知供应商、HTML转义、异常审计脱敏和真实SMTP提交，不投递外网。 |
| R7 | DefaultClientAccountDetailsServiceTest、HostedUserDetailsServiceTest、IdentitySourceBindingPolicyServiceTest、ProviderDirectoryPolicyServiceTest、IdentitySourceBindingIT覆盖两种目录优先级、主体一致性、完整Admission拒绝矩阵与锁/回滚/提交后回调；ProviderCacheCipherTest及原ProviderApplicationTokenCacheIT证明原密文可读、跨上下文/损坏拒绝及owner原子性。 |
| R1/R7兼容 | EmailCodeAuthenticationTokenSerializationTest、LegacyEmailPrincipalReaderTest与MaculaOAuth2AuthorizationServiceTest覆盖新email主体往返、旧Jackson2/3邮箱夹具恢复/内省/撤销、凭据清除及拒绝恶意type；原SMS/第三方主体仍可读。IamProtocolTest/MfaProtocolIT证明新旧流程、MFA拒绝和无跨链Provider Bean。 |
| R3迁移 | HostedEmailLoginMigrationIT使用新建隔离MySQL，验证NULL/空/合法显式/已包含EMAIL/非法/不支持类型及资源身份；只有目标字段变化，原Secret/hash、版本、绑定和协议保持；迁移后管理员取消EMAIL、重启和migrate不重开；OAuth2ClientApplicationConcurrencyIT证明旧快照409。无业务库测试。 |
| R5/R8 | LoginRequestMatchersTest与真实IamProtocolTest对照旧路径方法清单，保证近似/空/管理路径不放行，所有平台GET入口、其他方法行为、静态错误页资源、health、绑定与CSRF/策略顺序保持；图标与原链接/场景一致，浏览器实测320/390px及桌面无页面溢出、单行、键盘可达。 |
| R9 | IAM/API/System/Admin文档与实际支持目录核对；源码/静态资源引用无残留、计划范围与diff检查、当前规范Checkstyle通过。 |

计划命令（尚未运行本轮实现验证）：

1. `mvn -pl macula-cloud-iam -am test -Plocal`；System/API目标组合test后运行`mvn test -Plocal`，包迁移与删除后至少一次clean构建排除残留class。
2. `mvn -pl macula-cloud-iam -am verify -Plocal -Dit.test=MfaProtocolIT,ProviderApplicationTokenCacheIT,IdentitySourceBindingIT -Dfailsafe.failIfNoSpecifiedTests=false`；绑定IT只在专用测试URL/Redis标记与显式开关下运行，记录实际执行不能把跳过当绿。
3. 隔离MySQL运行`HostedEmailLoginMigrationIT`及必要的Client并发IT，隔离SMTP捕获、Redis生命周期由测试/执行器清理；完整迁移链包含已有V24但不修改V24。
4. Admin相关Vitest、`npm run build`及既有start-server-and-test包装的oauth-client-configuration.cy.js目标回归；IAM页面由真实模板+Mock安全链/隔离浏览器服务验证表单和图标，不把Admin测试当成IAM页面测试。
5. 对本轮现存变更Java使用仓库Checkstyle/header，执行`git diff --check`、源码删除符号检查与本计划精确清单比对。构建/测试产物与临时日志不入仓库。

## Implementation deviations

已按接受计划实施；实际偏差如下，验证记录另见verification.md。

- 实施偏差：真实邮箱OIDC回归发现Spring Security7的JwtGenerator从FactorGrantedAuthority获取auth_time。邮箱验证码核验并加载合法账号后记录真实OTT因子时间，不在签发点补造、不声明acr/amr或MFA完成；MFA会话上下文增加EMAIL分类。通用Access Token声明排除框架因子元数据，保留业务权限。仅补充为实现已批准OIDC和MFA边界必需的精确路径。

- 实施偏差：用户明确要求删除IAM模块的package-info.java，后续不再生成；职责说明由类级Javadoc及架构文档承担。仅删除包说明，不更改包注解或运行行为。

- 实施偏差：补充实际Thymeleaf登录页的专用Cypress用例，使用隔离4175预览服务；与Admin的Mock API用例分开记录。

- 实施偏差：OIDC真实认证时间引入框架FactorGrantedAuthority后，补充已授权业务资源仍保留角色且不泄漏框架因子的声明回归。

- 推送前复验发现旧JSON夹具格式化后，拒绝测试的紧凑字符串替换不再改变样本；改为结构化JSON变异，保留未认证/携带凭据/未知字段拒绝断言，不修改生产兼容读取器。

- 推送前可复现性检查：真实模板夹具改写模块target并携带静态资源，删除JUnit对个人临时路径的依赖；Cypress预览地址改为显式IAM_PREVIEW_URL，无环境时清楚跳过，完整验证显式开启；README补充可复现命令。

- 实施偏差：对实际暂存的完整Java差异执行Checkstyle，补充现有排版变动类型缺失的职责Javadoc及since；只补文档，不改变运行行为。

- 实施偏差：两项接受计划共同交付，正式Test报告同步架构和邮箱两个slug，仅补工程证据。
