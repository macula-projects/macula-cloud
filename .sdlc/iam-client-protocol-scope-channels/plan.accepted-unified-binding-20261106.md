# Plan: IAM协议、Scope、具体身份源与统一绑定策略 (from spec.md 2026-11-06)
Status: accepted

采用现有Java17/Spring Security/MyBatis-Plus/Flyway/Redis及Vue3/Element Plus技术栈，无新增框架或库。规格已由Rain接受；本计划已完成独立Plan评审（新版共3轮，最终NO_FINDINGS），Rain已以YES接受，开始按计划实施。企业身份源默认自动绑定的旧约定作废，所有来源统一在身份源界面配置。

## Files that change
路径相对仓库根目录。各组职责和具体变更见Order of work；文件名单不授权无关重排或清理。发现额外受影响文件，先记录具体路径、原因及验证影响，范围失效时返回人工决策。

### 共享标识与System模型/目录

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ProtocolMode.java`（新增）
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IdentitySourceType.java`（新增）
- `macula-cloud-system/src/main/resources/db/migration/V15__iam_protocol_scope_identity_source.sql`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/enums/IdentityProviderType.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IdentityProviderForm.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IdentityProviderVO.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IdentityProviderCatalogVO.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIdentityProvider.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImpl.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2ScopeCatalogProperties.java`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2ScopeCatalogConfiguration.java`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2ClientConfigurationCatalogService.java`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/OAuth2ClientConfigController.java`（修改）
- `macula-cloud-system/src/main/resources/application.yml`（修改）
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IdentityBindingRule.java`（新增）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/IdentityProviderController.java`（修改）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/converter/IdentityProviderConverter.java`（修改）

### IAM协议与来源链路

- `macula-cloud-iam/src/main/resources/templates/login.html`（修改；只呈现Client实际关联来源可用的网页登录入口）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/SmsVerificationCodeStore.java`（修改；用途/Client存储域及摘要，跨用途限流）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsService.java`（修改；显式验证码用途契约）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsCodePurpose.java`（新增；LOGIN与IDENTITY_BINDING稳定用途）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SmsServiceImpl.java`（修改；Client与用途双重分桶和限流）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysOAuth2ClientMapper.java`（修改；事务内读取当前配置/账号并加锁）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIdentityProviderMapper.java`（修改；事务内读取当前配置/账号并加锁）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysUserMapper.java`（修改；事务内读取当前配置/账号并加锁）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysRegistrationIdentityBinding.java`（新增）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysRegistrationIdentityBindingMapper.java`（新增）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`（新增）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolValidator.java`（新增）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyService.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepository.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionService.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/PendingThirdPartyBindingRepository.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicket.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicketStore.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingService.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeVerification.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolver.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeClientSettings.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProvider.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatClient.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatExternalCodeVerifier.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifier.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuClient.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifier.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClient.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifier.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifier.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/VerifiedExternalIdentity.java`（新增）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingPolicyService.java`（新增）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationToken.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ThirdPartyBindingController.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ExternalIdentityBindingController.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/form/ExternalIdentityBindingForm.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandler.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenEndpointFailureHandler.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalBindingRequiredException.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/ClientAwareAccountResolver.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysUserService.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysUserServiceImpl.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubClient.java`（修改）
- `macula-cloud-iam/src/main/resources/templates/third-party-bind.html`（修改）
- `macula-cloud-iam/src/main/resources/static/iam/auth.js`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationFilter.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationFilter.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationFilter.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationFilter.java`（修改）
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationFilter.java`（修改）

### Admin与文档

- `macula-cloud-admin/src/api/model/system/identityProvider.js`（修改）
- `macula-cloud-admin/src/views/system/identityProvider/index.vue`（修改）
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`（修改）
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`（修改）
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`（修改）
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`（修改）
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`（修改）
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`（修改）
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`（修改）
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`（修改）
- `macula-cloud-admin/src/locales/lang/zh-cn.js`（修改）
- `macula-cloud-admin/src/locales/lang/en.js`（修改）
- `macula-cloud-system/README.md`（修改）
- `macula-cloud-iam/README.md`（修改）
- `macula-cloud-admin/README.md`（修改）

### 验证文件

- `macula-cloud-system/src/test/java/dev/macula/cloud/system/vo/SettingsModelJsonTest.java`（修改；新目录构造器JSON传输回归）

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandlerTest.java`（修改；同步直接调用方新模型测试素材）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkLoginModeTest.java`（修改；同步直接调用方新模型测试素材）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuLoginModeTest.java`（修改；同步直接调用方新模型测试素材）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatLoginModeTest.java`（修改；同步直接调用方新模型测试素材）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java`（修改；同步直接调用方新模型测试素材）

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`（新增；新契约共用测试素材，避免生产兼容构造器）

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/SmsVerificationCodeStoreTest.java`（修改；真实Redis用途/Client隔离及手机号/IP限流）

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/userdetails/SmsServiceImplTest.java`（修改；用途/Client隔离和发送消费一致）

- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`（修改）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`（修改）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`（新增）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/OAuth2ClientConfigControllerTest.java`（修改）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ProtocolIdentitySourceMigrationIT.java`（新增）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`（修改）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`（修改）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/AdminSettingsAuthorizationTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepositoryTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingServiceTest.java`（新增）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingServiceTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicketStoreTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingServiceTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolverTest.java`（新增）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProviderTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatClientTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProviderTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomClientTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifierTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProviderTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuClientTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifierTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProviderTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClientTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifierTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProviderTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifierTest.java`（修改）
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`（修改）
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`（修改）
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`（修改）
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`（修改）
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingPolicyServiceTest.java`（新增）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionServiceTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ThirdPartyBindingControllerTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ExternalIdentityBindingControllerTest.java`（新增）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/userdetails/ClientAwareAccountResolverTest.java`（修改）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingIT.java`（新增）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenEndpointFailureHandlerTest.java`（新增）

### 直接调用方素材与边界清理
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginModeTest.java`（修改；具体来源ProviderConfig测试素材及尾随空白修正，验证企微网页/端内模式边界，不添加生产旧构造器）

## Order of work
1. 保留所有已有未提交成果，实施前将本轮文件当前内容与Git diff快照到临时目录；不提交、推送或改已执行V14迁移。确认V15未被应用，新增迁移。原plan-v1.md及旧评审仅作历史记录，本轮重新审查。
2. 定义共享OAuth2ProtocolMode、IdentitySourceType和IdentityBindingRule枚举，使用明确稳定协议值。来源按当前实际验证器列出微信网站/公众号/移动SDK/小程序、企微网页/SDK/小程序、飞书网页/AppLink/端内网页、钉钉网页/SDK/端内网页、GitHub OAuth、阿里云号码认证；支付宝仅凭据管理，不能宣称已有登录验证。明确每种类型所需独立凭据、场景、结果种类和可选绑定规则。删除旧综合schema的运行时回退。
3. V15追加protocol_mode、browser_provider_bindings、source_type、auto_bind_enabled和binding_rule。已有行不猜测协议/类型、不推断规则、不迁移旧账号绑定；新模型必填字段缺失时返回“需重新配置”，IAM拒绝将其投入认证，新保存数据必须完整。auto_bind_enabled默认false，关闭时binding_rule为空，开启时规则必填且该来源类型支持。数据库保留旧表但新运行时完全不查询。新增sys_registration_identity_binding：registration_id、identity_kind、identity_value、user_type、user_id及审计字段；字符串身份键区分大小写，唯一(registration_id,identity_kind,identity_value)和(registration_id,identity_kind,user_type,user_id)，绑定指向稳定用户ID。user_type保留真实用户目录边界，初期只开放当前已支持的员工目录，不伪造其他目录适配。来源ID不重用，引用存在时禁止删除；不清空任何业务库。
4. System身份源管理按sourceType校验独立凭据、脱敏及加密；schema及目录同时返回autoBindSupported、受支持规则、匹配字段和权限前提。所有来源（含企微、钉钉、飞书）只能显式开启，保存与IAM运行时均拒绝不支持组合。已保存类型和appId/corpId等身份域字段不可普通替换，Secret可轮换；配置更新使用完整快照CAS，失败409，不更新关联/发布成功缓存事件。移除按厂商全局单一启用来源限制，具体来源由Client关联确定；appId/corpId标识相同也不跨registration共享绑定。
5. Scope目录采用受控配置声明标识/名称/描述/适用场景，默认业务Scope为空，内置openid/profile仅与实际资料签发一致；不提供email/phone/address/offline_access伪支持，不从角色/URL/示例Scope推断。统一configuration-catalog返回协议、实际Scope、来源及场景能力和拒绝原因，不含密钥。Admin停止调用旧external-code-catalog；旧方法/VO如保留也不参与新运行时选择、校验或回退。无业务Scope时提示先登记，不提供手填逃生口。
6. OAuth2ClientForm/VO/Entity新增明确protocolMode和browserProviderBindings，新保存均需完整新合同，不保留旧PUT省略新字段的兼容路径。OIDC须openid且有用户授权码流程；OAuth2.1禁止OIDC专用Scope及ID Token，允许明确标注的项目扩展grant。移除password选项及运行时准入；Service保持仅client_credentials。所有授权码要求S256；public none不允许refresh grant，System、IAM和模板同策，code/sms/email/external_code实际响应均不签发公共刷新令牌。机密授权码可选框架真实支持的refresh，默认不复用且必须Client认证；不新增不完整公共轮换引擎。完整Client配置CAS包含新增字段及来源关联，0行409，不替换关联或发成功事件。
7. MaculaRegisteredClientRepository按明确协议构造Scope，禁止无条件补openid；存储缺失或矛盾配置拒绝启用，不默认为历史模式。Protocol validator组合框架redirect/scope/consent校验，实施S256及模式约束，不替换默认检查。使用原OAuth授权/Token机制，不改JWKS私钥管理。缓存刷新按现有事件机制，身份源解析及绑定写入前从权威当前配置重查安全字段，不依赖陈旧缓存放行。
8. Client存浏览器provider→registration固定关联（每Client同厂商至多一个浏览器来源），SDK/小程序使用code_type→registration明确映射；不同Client可关联同厂商不同浏览器来源，同一Client可关联同厂商网页与SDK/小程序。System和IAM统一校验来源启用、类型、场景及所需凭据；没有固定关联拒绝登录，不全局按family择源。浏览器发起、按钮/端内直登、授权URI、回调/provider Client全部使用确定registration；state保存clientId、registration、type、mode和受控saved target，回调一次消费并重校验关联。旧state/ticket缺字段要求重新登录，不设旧格式适配。
9. VerifiedExternalIdentity集中承载registration、稳定身份键（如有）、核验属性及证明种类。所有厂商浏览器Provider及ExternalCodeVerifier均由服务端换码结果构造，不接受请求自行携带employeeNo/userId/mobile。企微、钉钉取已验证成员ID为身份键及可配置username匹配属性；飞书改为稳定应用内open_id身份键，employee_no独立可缺失，缺工号不使合法身份核验直接失败，按规则转手动绑定；若稳定身份也缺失则核验失败而非虚构身份。微信显式区分UnionID/OpenID键类型，不把两种值静默相互替代；缺UnionID时只在已实现且核验响应确有OpenID的类型使用固定OPEN_ID键。GitHub使用数值ID，不据公开login/email自动匹配。号码验证只产生MOBILE_PROOF，不发明永久vendor subject。核验DTO与源码变动同步各相邻测试。
10. 新IdentitySourceBindingPolicyService统一浏览器/SDK的账号解析。先查registration+核验身份键的已有绑定，再按稳定userId加载当前账号并校验Client用户目录/租户认证范围/状态；已有合法绑定不受自动开关变化影响。没有绑定时读取来源开关：关闭或不支持→binding_required；开启则仅执行所选规则（企微/钉钉member ID→username、飞书employee_no→username、已核验mobile→mobile）。缺属性/零候选/多候选转绑定页；唯一合法候选先校验真实OAuthClientUserDetails认证范围，再进入事务建立关系。已禁用、越权、绑定冲突直接拒绝，不允许通过手动路径绕过。删除ThirdPartyIdentityResolutionService中的WECOM硬编码同名匹配，并替换ClientAwareAccountResolver旧family+subject/username查询，不保留企业身份源隐式自动绑定。
11. RegistrationIdentityBindingService提供同一事务边界供自动与手动绑定使用：重查来源/关联/策略和当前账号，依赖唯一索引及条件写入防止并发覆盖；同一合法关系可幂等，其他冲突拒绝。已有绑定只通过userId解析，用户名变更仍有效。不自动注册、合并或改绑，不按另一个未配置字段重试，不读取旧表。基于手机号的关联同时记录当前规范化手机号证明，登录或提交前要求账号当前手机号仍一致；手机号变更/被清空后旧关系拒绝使用，不能登录原账号或自动覆盖旧关系。
12. 手机号初次无关联也走统一开关：启用且唯一合法手机号匹配才建立MOBILE_PROOF关联并登录；关闭或无法匹配进入证明页。手动绑定只允许已证明账号的当前已登记手机号与本次核验手机号一致，不在本任务顺带修改账号手机号；为空或不同须先完成受控账号资料维护再重试。页面说明这一约束；不能仅凭展示用户名把号码挂到任意账号。既有有效MOBILE_PROOF关联可登录，变化后拒绝，防止原手机号快捷查询绕过策略。
13. 浏览器pending与SDK Redis ticket携带完整来源/Client/用户路由及服务端核验材料，短期有效、有尝试上限和一次claim/lease/consume语义。统一绑定页面保留现有本地密码/已支持短信/合格会话三种归属证明，绑定证明与Client托管登录按钮开关独立：仅第三方登录的应用即使PASSWORD/SMS托管选项关闭，仍可在有效已核验外部身份pending内证明本地账号归属。密码仅验证有密码的目标账号，短信须已部署可用短信服务和账号已登记唯一手机号，会话须同一启用账号及Client范围；不新增通用密码/SMS登录入口，也不因证明成功单独签发Token。浏览器只有第三方核验与本地证明均成功且绑定提交后才建立会话，SDK完成页不建立可复用SSO会话。在ThirdPartyBindingController新增POST /login/bind/sms-code（CSRF及pending/票据限次保护），依据服务端pending解析原Client/来源/目录，校验来源当前关联、目标账号状态/范围及其已登记手机号后发码；扩展SmsService及SmsVerificationCodeStore的issue/activate/consume为显式SmsCodePurpose+clientId发送/激活/消费，Redis键及验证码HMAC摘要同时绑定用途与Client；LOGIN与IDENTITY_BINDING使用独立带域标识的Redis键，不把用途拼接进任意客户端ID，原有登录调用明确使用LOGIN；分桶同时保留按手机号/IP限流防止通过换Client/用途刷短信。绑定发送与消费固定IDENTITY_BINDING及原clientId，防止托管登录码/其他用途码混用。不从SavedRequest猜Client，不使用前端指定clientId，也不改现有托管发码策略；模板发送按钮改为该专用入口，响应一致且不泄露账号是否存在。ThirdPartyBindingService和ExternalIdentityBindingService改用统一原子绑定服务，保持CSRF、会话更新及事务提交后消费票据；失败释放/限次，不吞基础设施故障。
14. SDK Token错误新增binding_uri，由可信IAM部署基址/服务端路由构造短期opaque链接，不使用未校验Host/Forwarded头，Token接口仍JSON而非302。GET仅把票据claim到当前浏览器pending并立刻重定向移除URL票据；不在GET建立绑定，页面/响应no-store和Referrer-Policy:no-referrer，不记录票据，claim释放仍保留原尝试/过期约束。复用/login/bind提交证明，客户端链接打开也不能覆盖其他待绑定上下文。SDK完成页只说明“已绑定，请返回应用重新登录”，本轮不添加自定义scheme/任意returnTo；浏览器继续原受控saved target。客户端重新获得新厂商CODE再登录，无新binding_completion grant、不重试已消费CODE。API原Bearer证明入口也使用同一票据/事务/当前策略，无绕过。
15. Admin身份源页面统一展示类型、独立字段、自动绑定开关及来源属性→本地字段下拉，默认关闭；开启后规则必填，不支持类型显示原因，权限不足/字段缺失时提示转手动。企业来源无自动开启模板或特殊开关。应用界面只关联具体来源与语义能力，协议和Scope均选择，隐藏原始CODE及兼容选项。类型切换清理不适用凭据/规则，编辑时类型不可改；目录失败/延迟不覆盖草稿，保存防重复。保持全宽四分区和390px响应式；平台端点紧凑文本/复制/失败重试，JWKS独立输入。托管方式明确“平台默认”仅全局password/SMS，不伪造实时有效值。
16. 更新System/IAM/Admin中文README，说明新模型重新配置、来源统一自动绑定、规则/权限/手动页面、已有绑定和手机号变化语义及SDK新CODE流程。按Proof先最小单元后全项目/浏览器/隔离数据库验证并记录实际输出，检查Javadoc/版权/Java17/定向Checkstyle/ESLint/diff。本轮无Git提交/推送权限；实现完成先过Build人工出口再正式Test。若沿用既有本地容器更新授权，只能验证完成并完成迁移预检后更新System/IAM/Admin，不更新其他容器。

## Risks
- 自动绑定是账号归属边界：企业属性映射必须由身份源显式开启，员工编号/成员ID不是天然本地用户名；当前没有工号列，禁止假造字段、公开邮箱证明或默认匹配。没有身份稳定键时只能使用受限手机号证明或拒绝，不能伪造永久身份。
- 无历史兼容意味着存量协议/来源需重新配置、旧state/ticket需重新登录、旧表绑定不再使用，可能中断原登录；不自动重键或清库。旧镜像不了解新身份域，不承诺直接回滚可安全使用新来源；需停用新来源并按已验证配置恢复服务。
- sourceId隔离、身份域字段不可变和权威策略重查必须同时成立；仅更改UI不够。策略或关联与绑定事务并发时需要同一源/Client配置版本校验和数据库锁，不能先检查后无条件插入；锁顺序固定Client→来源→账号→绑定，规避死锁。
- 关闭自动绑定只影响首次建关联，不能使正常既有绑定每次跳页；手机号证明关联例外需每次确认当前手机号仍相同。号码回收是手机号凭据固有边界，本轮不将其宣传成永久平台身份。
- 绑定证明决策：复用现有三种证明，但它与应用托管按钮设置独立；有效第三方核验pending为强制前提，不把“仅第三方登录”变成无法绑定的配置。证明只能完成该pending绑定，SDK不借此创建SSO/Token；必须覆盖PASSWORD/SMS托管均关闭且无会话的首次绑定。
- SDK页面票据是短期敏感能力，不能在日志、Referer、URL续转或前端持久存储泄漏；无可信外部基址时失败关闭，不用Host头猜地址。SDK完成后重新取CODE，需文档和错误契约同步。
- 平台字段和权限因来源/授权而异，目录“支持”不能替代本次核验；真实厂商/设备证据不能用Mock冒充。公开客户端刷新不以一个reuse配置冒充完整重放保护。
- 保留大量此前未提交改动；只对文件清单中与新范围相关的内容增量修改。配置目录与表单异步、全对象保存、CAS、Secret空值保留和缓存事件需一起验证，不能丢失用户草稿或误清凭据。

## Proof
- 协议与Scope：IamProtocolTest走真实Security链，OIDC有ID Token，OAuth无ID Token且openid/UserInfo不能绕过；S256成功、缺失/plain拒绝并保留默认redirect/scope/consent约束；缺失新模型字段拒绝启用；public code/sms/email/external_code均无refresh，机密授权码实际refresh需认证；password不准入。目录无虚构业务/资料Scope，空目录有明确提示，手写未知Scope服务端拒绝。
- 来源配置：全部具体类型独立字段/加密/脱敏/Secret轮换/不可变身份域；所有来源默认自动关闭，只有可支持规则可开，无企业默认旁路；配置CAS失败无副作用。不同Client同厂商多来源同时有效，网页/SDK/小程序不借用别处凭据。
- 统一绑定单元及真实IT：已有合法绑定直接登录；自动关闭、不支持、缺字段、零/多候选转页面，唯一合法候选建稳定userId关联；禁用/越权/冲突拒绝。特别验证企微同名账号在开关关闭时仍需绑定，钉钉/飞书同策；用户名改名不失联；旧表存在同family/subject也不能命中。MOBILE无关联受开关、手动仅相同已登记手机号，已绑定手机号变更/清空拒绝旧关系。
- 来源与事务：跨来源/企业同subject隔离、客户端/目录/租户限制、来源禁用/策略变更/关联替换、错误模式及客户端伪造属性拒绝；自动和手动并发、不同账号竞争同身份、同账号不同身份、保存与绑定并发，通过真实MySQL Mapper/Service、唯一索引和锁验证，幂等仅允许同一关系，不能用复刻SQL替代服务链。
- 页面及SDK：真实过滤链及Controller验证browser pending与SDK ticket→无票据URL页面→证明→完成；密码/短信/会话拒绝和成功、仅第三方Client托管PASSWORD/SMS均关闭且无会话仍可使用账号密码完成绑定、SDK完全没有SavedRequest时专用发码与消费成功且不同Client/用途码不能复用、SmsVerificationCodeStore真实Redis跨用途激活/消费拒绝且跨用途发送仍共享手机号/IP限流、CSRF、会话固定、限次、过期、重放、跨Client/来源、无效Host/returnTo、原CODE已消费且必须新CODE、无302 Token响应。日志和URL输出不含外部身份/手机号/密钥，票据no-store/no-referrer；绑定事务失败不消费成功票据，基础设施故障不被当成账号不匹配。
- 数据迁移：隔离MySQL/Redis由V14升V15，既有Client/来源/密钥/旧表指纹不变，新字段不猜测、旧绑定不迁移；新表外部键区分大小写和真实并发唯一性；保留SettingsPersistenceIT、Client CAS及所有已有IT回归。测试fixtures改为明确新合同，不保留旧认证行为断言。
- UI：Vitest和Cypress验证身份源统一策略、厂商类型字段/规则切换、来源过滤、协议/Scope选择、不支持原因、目录失败/重试/草稿保持、紧凑地址复制、全宽/390px、分区草稿和凭据生命周期回归。SDK/绑定页与既有UI风格一致。
- 命令：先最小相关Maven `-Plocal`和Vitest；扩大`mvn test -Plocal`、隔离MySQL/Redis运行全部已有及新增IT（`*IT`使用Failsafe verify），补跑环境门控升级用例；Admin `npm run test:unit -- --run`、`npm run build`、`npm run test:e2e:ci`；定向Java Checkstyle、前端不带fix的ESLint、`git diff --check`。只报告实跑输出及明确未运行的真机/厂商权限范围；未改技能/hooks/REVIEW，无对应评审eval。

## Acceptance
Rain于2026-11-06以“YES”接受该实施计划。

## Implementation refinements
- 测试源码需整体重新编译以避免Maven增量检查遗漏同模块ABI变化；添加测试专用新模型素材，不添加生产旧契约构造器。

- 登录页模板需同步来源能力：移除重复/不兼容入口，微信公众号仅在微信内显示；网页来源固定网页模式，不由UA误选SDK/端内模式。V15不猜测管理端自身Client，部署前必须明确初始化该Client的新模型配置。

- 身份域字段从可轮换材料中明确分离（identityFields）：企业/应用ID等不可改，支付宝公钥允许轮换。移除未实际使用的来源redirect字段，避免暴露无效配置。
- 真实V9索引已禁止重复用户名/手机号，因此真实IT断言重复写入被索引拒绝，多候选防御分支由Mock单元测试覆盖。隔离Mapper测试显式使用框架AuditMetaObjectHandler；不改生产审计行为。

## Build implementation evidence（开发期检查，非Stage 4签署）
- 2026-11-06：System/IAM及API相关单元测试共425项，424通过，1项既有环境门控用例跳过；最终新增绑定发码CSRF断言后的IamProtocolTest单独29项通过。
- Admin：Vitest 15文件/130项通过；生产构建通过；相关Cypress 22项通过（含自动绑定开关及清空规则、同厂商来源选择、1920px/390px DOM布局断言）。浏览器接口为Mock，不代表真实厂商联调。
- 专用MySQL 8.4/Redis 7.4容器：V14→V15迁移1项、绑定/票据/验证码11项、Client配置竞争1项、设置加密与缓存持久化6项通过。真实Mapper/事务/唯一索引/Redis路径，用户认证范围服务和短信投递仍为明确Mock；不代表完整厂商/权限联调。
- 127个相关Java文件定向Checkstyle通过；相关前端ESLint（无fix）和git diff --check通过。
- 未更新业务容器、未修改业务数据库；管理端自身Client必须在部署前明确配置protocol等新模型字段，不能由迁移猜测。正式Stage 4按Proof扩展全仓及其余门控IT，不以本开发检查替代。
