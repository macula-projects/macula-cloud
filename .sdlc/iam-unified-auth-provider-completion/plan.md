# Plan: IAM统一认证补齐及MFA接入预留 (from spec.md 2026-11-08)
Status: accepted

Rain以YES接受spec.md及全部关注点。采用现有Java17、Spring Security 7.0.7、MyBatis-Plus、Flyway、Redis、CryptoManager、Spring异步执行器和Vue3/Element Plus；不新增认证框架或依赖。以下是待工程师批准的实施合同，不包含源码实施、Git或部署授权。

## Files that change
每条均为相对根路径，含现有修改与新增文件；仅为声明职责修改，不授权格式重排。某声明文件最终不需要修改时保持原样。新增额外文件先记录理由和验证影响，影响合同则返回工程师决策。

### 共享契约（macula-cloud-api/macula-cloud-system-api）
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IdentitySourceType.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IdentitySourceFlow.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IdentityDirectoryPolicy.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/IdentitySourceFlowTest.java`

### System模型、校验、目录与迁移
- `macula-cloud-system/src/main/resources/db/migration/V20__identity_source_flow_directory_policy.sql`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIdentityProvider.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IdentityProviderForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IdentityProviderVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IdentityProviderCatalogVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/enums/IdentityProviderType.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/converter/IdentityProviderConverter.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IdentitySourceFlowDirectoryMigrationIT.java`
- `macula-cloud-system/README.md`

### IAM来源、平台及公共策略
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/VerifiedExternalIdentity.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderIntegrationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderApplicationTokenCache.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderCacheCipher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryResult.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderMemberSnapshotStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryRefreshService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeInfrastructureException.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeConsumptionState.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeClientSettings.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeSecurityProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeReplayStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeRateLimiter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeParameterNames.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/resources/application.yml`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/docs/third-party-integration.md`

### 原始来源快照及评审补充文件
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/PendingThirdPartyBindingRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicket.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicketStore.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicketStoreTest.java`

### IAM MFA预留
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/AuthenticationCompletionContext.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/AuthenticationCheckpoint.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/AuthenticationMethod.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaSessionAuthenticationStrategy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/AdditionalAuthenticationRequiredException.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/MfaExtensionConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ThirdPartyBindingController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandler.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolCheckingTokenGenerator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationService.java`

### IAM测试
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderApplicationTokenCacheIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryRefreshServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderCacheCipherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/mfa/MfaSessionAuthenticationStrategyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/MfaProtocolIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ThirdPartyBindingControllerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeReplayStoreTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeRateLimiterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifierTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifierTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifierTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifierTest.java`

### Admin
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/src/views/system/identityProvider/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/IdentitySourcePicker.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/locales/lang/zh-cn.js`
- `macula-cloud-admin/src/locales/lang/en.js`
- `macula-cloud-admin/tests/unit/identity-provider-policy.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/cypress/e2e/identity-provider-policy.cy.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/README.md`

协作工件仅本目录intent.md、spec.md、plan.md、review-findings.md、verification.md及新建provider-contract-evidence.md。不修改Boot、Gateway源码、POM、部署文件、凭据或V14—V19迁移。

- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningServiceTest.java`（既有调用方测试素材同步）

- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`（既有调用方测试素材同步）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2ResourceOwnerBaseAuthenticationProvider.java`（现有私有grant签发器的MFA策略接线）

## Order of work
1. **基线与合同证据**：保留当前混合工作区，按上述精确列表建立仅本轮的私有基线和文件摘要；读取POM/README/相邻实现和测试。先落provider-contract-evidence.md：记录官方URL、版本、字段/权限、可读证据及未核实项，不保存真实凭据。钉钉已有官方SDK2.2.58字段证据；飞书网页、JSAPI、AppLink和通讯录接口继续核实，无法取得证据则保留对应能力未核实，不能把Mock或工程师批准代替官方证据。
2. **共享来源合同及追加迁移**：增加IdentitySourceFlow与IdentityDirectoryPolicy枚举，沿用现有SourceType/CodeType，不重命名协议值。V20仅为sys_identity_provider追加`identity_flow`（nullable）、`directory_policy`（NOT NULL，IDENTITY_ONLY）、`organization_id`（nullable）、`oauth_redirect_uri`（nullable），不变更已有绑定、Client或Secret。IDENTITY_ONLY保留当前身份核验/既有成员转换，不能被解释为取消已有组织限制。单一已核实flow按具体SourceType确定；存在不兼容版本的飞书来源必须明确配置flow，未配置/未实现不可认证。目录策略固定IDENTITY_ONLY、OPTIONAL、REQUIRED、BACKGROUND；组织ID与配置中现有corpId/相应租户标识核对，确认后不可普通替换。回调及flow变化递增来源version，旧pending/快照不能直接沿用。首次浏览器发起的ThirdPartyLoginContext固定原registration version、flow及实际回调；StoredState保存同一不可变上下文；回调在换码前与权威配置比对。每个Verifier以取到的ProviderConfig为原始核验快照，VerifiedExternalIdentity新增必填sourceVersion与flow，of工厂从该配置复制，不从随后读取的新行补值。绑定pending/ExternalBindingTicket序列化同一原始快照，缺字段旧state/ticket要求重新登录，不回退猜值；无需改数据库绑定唯一键。回调、账号解析、绑定锁内和签发前均比较原始version/flow及回调绑定与当前行，而非当前快照自比较；变更后重新取Code。原始Code核验的来源快照作为仅服务端认证元数据随扩展用户principal/授权上下文传到签发检查，不写入公开Token claims。
3. **前后端双重配置校验**：System Form/VO、两个服务的来源Entity和ProviderConfig同步字段；来源目录提供仅真实实现且有合同证据的flow与策略能力及不可用原因，OAuth Client目录复用同一声明。支持多个同平台来源，仍只使用Client绑定的registration。非企业来源不能选通讯录；REQUIRED/BACKGROUND必须有已实现的企业目录适配和完整组织凭据。未核实flow保留现有记录并显示原因，但不能启用/新关联；不批量清空数据或给其他来源补权限。
4. **钉钉及平台Client**：修复应用Token appKey/appSecret和用户Token grantType；逐项断言HTTP请求体。保留DINGTALK_WEBVIEW的企业免登与APP/BROWSER的OAuth差异，unionId仍转换为目标应用域成员userid。微信登录/号码Code、企微网页/SDK/小程序分别走已有准确接口，保留openid作用域、session_key保密及手机号证明语义。平台远端响应错误统一类型化，不泄露正文。
5. **飞书受控flow**：Browser以固定已登记IAM回调为上游redirect；AppLink以来源预登记oauth_redirect_uri为上游回调。基础external_code参数保持grant_type/client_id/code_type/code/scope/nonce；仅对已验证FEISHU_APP_CODE所选PKCE flow增补单值`provider_code_verifier`（43—128位RFC7636字符），不接受通用code_verifier、redirect_uri、state、Token URL、Secret或任意registration。该字段由Converter解析但只有完成Client和来源解析后才按精确flow放行；其他codeType/flow一律拒绝。Verifier通过带明确参数的只读请求上下文取得材料，不伪装成IAM授权码。上游Code和verifier的绑定由对应平台接口实际验证，Client只可使用其固定来源及固定回调，业务端校验其原始state且只接收可信授权回调。本场景没有必要先向IAM创建一笔通用登录事务：浏览器已有Session state，直接flow的挑战由上游验证、IAM回调固定；不增加新挑战/事务端点、不借用绑定票据。不使用PKCE的flow不接收多余verifier。若官方证明还需其他材料/状态，停止该flow实现，补充精确合同后再执行，不自行扩白名单。
6. **兑换阶段、防重放和限流**：新增NOT_SUBMITTED/UNKNOWN/CONSUMED类型化消费状态；基础设施异常默认UNKNOWN，仅取应用Token等尚未提交Code的明确失败可标NOT_SUBMITTED。Code POST/GET已开始后网络/5xx为UNKNOWN；用户Token已取得后的资料/准入失败为CONSUMED；两者都保留claim。Provider仍按Client→grant/type/registration→Scope/flow参数→限流→claim→平台→账号→签发执行。Redis摘要保持code_type+Code全Client域，TTL按CodeType受控有效期表验证且不小于最大已知有效期加时钟/网络余量；未知有效期的可重复Token不能仅靠默认10分钟宣称受保护，必须核实并设安全下界，否则该能力不得启用。增加Redis原子Client/IP计数；IP默认取连接地址，仅使用现有受信代理配置，不信任任意X-Forwarded-For。阈值/窗口放配置，错误不泄露Client存在性/上游内容，Redis故障失败关闭。
7. **应用Token安全缓存**：ProviderApplicationTokenCache只缓存应用/企业Token，loader返回Token+expires_in，平台用户Token不持久化。key按registration+version+flow+用途摘要，消息不含Token/Secret。使用已有Redis和有界分布式owner锁，读穿/刷新前再检查当前来源version；有效Token提前到期，短expires_in不无限补默认，刷新失败不覆盖有效值；多实例同key合并刷新。发布和解锁使用同一Redis Lua：只有锁仍属于本owner且有效时才SET加密值及到期并DEL锁；拿不到锁或失租的loader不能写缓存，也不得把自己的迟到Token直接返回。失租结果丢弃，读取获胜owner缓存；若获胜值尚未产生，只能有界等待/重试，超时失败关闭。锁异常/Redis不可用不退回未受发布栅栏保护的loader结果；源版本变更另由签发时原始快照重查拒绝。ProviderCacheCipher复用CryptoManager/CryptoProperties加密，对信封以现有服务端摘要密钥做独立域HMAC，绑定来源/版本/用途/到期，先验MAC再解密；拒绝BASE64等非保密配置和缺失有效密钥，不存明文，不记录密文材料。旧version不可命中，密钥/来源停用不能回退旧值；测试用合成密钥。
8. **企业准入与快照**：三个平台目录Client只查询固定HTTPS端点，使用明确的成员标识类型及租户应用Token，返回MEMBER/NOT_MEMBER/DISABLED/PERMISSION_DENIED/UNAVAILABLE及最小白名单资料，不把昵称/展示邮箱作为身份。IDENTITY_ONLY沿用当前认证结果；OPTIONAL仍拒绝明确非成员/停用，仅容忍资料权限不足/资料故障；REQUIRED无有效准入结果即拒绝；BACKGROUND保留当前身份校验并提交已验证/已绑定成员的后台刷新。服务端快照按registration/version/主体摘要加密存储，不覆盖SysUser或角色。ProviderDirectoryPolicyService由浏览器公共解析和ExternalCodeAccountResolver复用，已有绑定登录也检查；来源version、组织及资料状态由服务端形成，不能来自调用参数。
9. **绑定及后台事务边界**：绑定证明/完成时重新验证当前来源及需要的企业状态；远程目录调用在数据库事务外，得到只读验证快照，然后绑定事务锁内比较来源version、Client许可和账号状态。ThirdPartyBindingService/ExternalIdentityBindingService用明确TransactionTemplate包裹原CRUD及afterCommit票据finalize，避免自调用@Transactional及持锁网络等待；回滚、owner、失败计数、幂等规则保留。快照过期或配置变更要求重新验证，不因为已有ticket而绕过新准入。BACKGROUND使用独立有界Spring执行器、按源/成员去重及有限重试，Redis记录加密工作项/结果与TTL，拒绝创建无限任务；启动/周期检查只恢复本功能工作项。来源停用/版本变化取消旧任务，崩溃任务租约到期可恢复，失败不改绑定或本地账号权限；不使用只在进程内存在的fire-and-forget当可靠后台刷新。它不替代REQUIRED实时准入。
10. **MFA SPI及会话前检查**：新增MfaCompletionPolicy.check(context)，默认可覆盖Bean直接继续；Context只含可信Client/租户/稳定userId、入口、认证方式、来源ID及检查阶段，不携带原Authentication凭据或请求自报acr/amr。AuthenticationCheckpoint固定BROWSER_SESSION、BINDING_SESSION、AUTHORIZATION_CODE、USER_TOKEN、REFRESH_TOKEN；AuthenticationMethod只记实际已验证方式，不把第三方成功当多因素。用MfaSessionAuthenticationStrategy加入现有SessionManagementConfigurer的策略链，在所有AbstractAuthenticationProcessingFilter保存SecurityContext/Remember-Me之前调用；已核对本地Spring Security7.0.7源码，该附加策略在默认session fixation策略之前执行。保留原Session策略，不替换/绕过；裸门户无可信Client时显式标记Client未知，默认继续，自定义策略不能把请求client_id当可信。异常包装为安全SessionAuthenticationException，无完整新登录态。
11. **MFA绑定与签发栅栏**：浏览器绑定结果保留服务端Client/来源及证明方式，ThirdPartyBindingController在changeSessionId/保存SecurityContext之前调用同一SPI；SDK绑定不创建SSO，绑定成功不算MFA完成。ProtocolCheckingTokenGenerator在用户Token生成前检查，OAuth2UserGrantTokenIssuer在开始签发扩展用户令牌前检查。MaculaOAuth2AuthorizationService在Redis写入前对新产生的可用授权码/用户Token再检查，涵盖SSO和Consent及Refresh；不得仅挂GET授权Validator。比较前后Token判定新发布，撤销、失效标记/清理或匿名state保存不触发MFA拦截，不能让MFA阻止撤销。client_credentials不作用户MFA，RESOURCE_SERVER继续不能签发。SPI需幂等、无隐式签发副作用；AdditionalAuthenticationRequiredException在本轮无挑战实现时映射安全不可用错误（浏览器503；Token server_error），无challenge URI、无部分授权，不清其他合法会话；不增加MFA开关、因子表或acr/amr/auth_time声明。
12. **管理端、示例和文档**：现有来源页增加适用flow/目录策略/组织/固定回调，记录保留且失效原因可见；来源选择只显示真实兼容能力，SDK/网页登录仍分别关联。快速开始从已关联且可用Code Type中选择，不取数组首项；按真实Scope和端点生成对应平台步骤，手机号Code独立说明；仅在已实现的上游PKCE flow示例提供provider_code_verifier。未核实flow不生成可直接复制的成功示例。保留全部官方链接，记录SDK补证及未读正文；README说明MFA SPI只预留及覆盖边界、缓存和重试变化、追加迁移及回滚需配套版本。
13. **验证及交付**：按Proof运行最小再扩大，记录每个能力的官方合同证据、Mock、隔离MySQL/Redis、浏览器与真实平台验收状态。所有额外文件/顺序/合同偏差先记本文件，合同变化再确认。不提交/推送、不更新镜像、不迁移业务库；实现完成停在Phase B人工确认门，正式Stage4另行确认。

## Risks
- 飞书正文与浏览器工具目前不可用，外部flow/目录接口的正式证据仍可能阻塞对应能力启用。上游接口字段/权限变化不能靠用户批准或Mock弥补；未核实项在最终交付列未完成，不能静默以禁用代替“全部完成”。
- provider_code_verifier是新增的最小条件合同，仅供核实后的飞书PKCE flow；若当前官方flow不同，暂停该子项并补计划，不顺手开放任意参数。没有证据需要IAM事务时不新增。
- 不确定时保留Code占用会牺牲重试便利，必须提示重新取Code；锁租约、TTL、限流和加密参数需要边界测试，不能只靠串行Mock。源Token调用与用户Token调用的字段及用途不能混用。
- 新flow/组织策略必须同步System/IAM/API/Admin及V20；缺少配置不得猜企业或放宽。部署与迁移不在本轮授权，恢复须配套版本和数据备份。
- MFA默认No-op不代表真实多因素已实现；Policy启用后的SSO/Refresh证明生命周期仍由未来MFA模块实现。当前只保证检查点可拒绝、不会提前发布认证成果，不能把绑定、平台声明或requireProofKey作为第二因子。
- 用户工作区已有大量改动；本次基线必须独立于之前资源边界/OIDC基线。仅修改声明文件、保留其他在途工作，包括API Key/HMAC。

## Proof
- API/System：来源flow/目录策略枚举及能力矩阵；新增/编辑/版本/组织域不可替换、未知字段/无凭据拒绝、旧记录不猜填；管理目录与运行时一致。专用V19→V20 MySQL迁移验证保留ID/Secret/绑定，且V14—V19校验和不变。
- 平台合同：每个平台Client断言固定URL、HTTP方法、精确JSON/表单字段、Token身份、回调/verifier；跨registration/企业/CodeType、未知/重复字段、过期/重放、非成员、权限不足、可选资料缺失、3xx/超时/5xx和敏感日志逐项验证。真实厂商与设备另外列明。
- Redis集成：应用Token并发刷新、暂停旧loader至锁过期并由新owner刷新后恢复旧loader的发布/返回拒绝、锁owner/租约、expires_in、停用/轮换、跨源密文替换/篡改/过期、密钥缺失、Code消费三种状态及Client/IP限流；Redis状态不含原Code、Secret、明文Token或敏感主体。
- 绑定/目录：真实Mapper、事务与专用MySQL/Redis，覆盖已有/自动/手动绑定、目录模式、发起后改flow/回调/版本再回调及完成绑定的拒绝、来源并发变化、资料检查与事务分离、回滚不消费票据、后台租约恢复/重试/去重和停用；不得Mock整个resolver掩盖边界。
- MFA真实安全链：MfaProtocolIT使用默认、拒绝和抛异常策略，覆盖密码/SMS/每种浏览器来源、绑定后会话、SDK直接用户grant、已登录SSO、Consent、授权码交换、JWT/opaque、ID Token、机密Refresh。断言拒绝后无新完整SecurityContext/Remember-Me、无可用code或任何Token/半份授权；允许匿名pending和原合法Session保留。撤销仍成功、client_credentials语义不变，SDK绑定不建SSO；测试不伪造真实MFA成功。
- 执行：目标模块`mvn -pl macula-cloud-iam,macula-cloud-system -am test -Plocal`；然后Cloud `mvn test -Plocal`；显式启用隔离IT的适用`verify`。Admin Vitest、生产构建、无修复ESLint及Cypress1920/390px；变更Java Checkstyle、git diff --check。Gate前只记录真实运行证据，不把编译/Mock称作真实平台验收。

## Plan review
默认codex-plan-review未安装；按sdlc-build回退独立sdlc-reviewer子代理，仅Plan pass、最多三轮。round 1发现原始来源版本传递、缓存失租发布两项Important，已修订计划并补文件/证明；round 2仅审修订及连带影响，结果NO_FINDINGS；实际共2轮。具体见review-findings.md，该轮评审结束时plan.md尚为draft；后续工程师接受见下一节，不重写原审批时序。

## Engineer acceptance
Rain以YES明确接受完整计划及两轮Plan评审结果，开始Phase B。批准不包含提交、推送、部署或业务数据库迁移。

## Phase B 实施记录
- 2026-11-09：CLAUDE.md不存在，使用根AGENTS.md及目标规则。缓存独立Redis验证完成后先实现不依赖通讯录适配器的MFA SPI和发布栅栏，再回到通讯录、限流及管理端；依赖与功能合同不变。

- 2026-11-09：增加现有OAuth Client父页面一处catalog prop传递（路径已补入清单并保存增量基线）。Guide复用父页面已加载的来源目录，避免新增重复请求/独立配置。

- 2026-11-09：目录VO新增Flow投影后，同步PlatformOAuthClientProvisioningServiceTest的精确构造参数；身份源UI新合同同步既有Cypress目录fixture。两文件已列入清单并保存基线，不改变业务实现范围。

## 补充设计待确认：标准 OIDC 的主认证时间

Status: draft（本节未批准；不据此实现新的认证时间声明）

真实安全链新增验证发现：第三方回调能够创建 IAM Session，但随后通过标准 authorization_code 兑换 ID Token 时，Spring Security 7.0.7 的 `JwtGenerator.getAuthenticationTime(Authentication)` 找不到 `FactorGrantedAuthority`，抛出 `authenticationTime cannot be null`。密码认证由框架自动提供主认证因子元数据；自定义第三方/SMS/绑定会话没有同等适配。不是平台 HTTP Mock 或前端状态问题。

已接受计划第 11 项明确写着“不增加MFA开关、因子表或acr/amr/auth_time声明”。因此以下补充需要工程师明确选择，不能由实现者自行改变已接受合同。

建议方案：在真正核验成功的第三方/SMS主认证及绑定证明完成点，记录 Spring Security 标准的单次主认证元数据（包含服务端实际核验时间）。标准 OIDC 由原生 JwtGenerator 生成真实 auth_time/sid，与现有密码流程一致。仅凭已有会话绑定时继承原已核验元数据，不把当前时间当重新认证。反序列化不能重新生成时间。自有 external_code 仍不冒充标准 Authorization Code，不新增 auth_time、acr、amr，也不声明完成 MFA。

最小改动范围（确认后才加入源码清单）：ThirdPartyAuthenticationToken、SmsAuthenticationProvider、ThirdPartyBindingController；必要时自定义浏览器 Provider 在实际核验成功处传入元数据，复用已有 Spring Security 模块序列化，不创建因子表、挑战页或MFA开关。MaculaOAuth2AuthorizationService保留原元数据，不能给Redis恢复的旧认证盖新时间。

验证：第三方、短信和绑定后标准OIDC成功签发；auth_time等于实际主认证时点，恢复/刷新不无故变新；已有会话绑定不伪造本次密码认证；拒绝的MFA不发布会话/Code/Token；external_code不新增这些声明；元数据不当作业务角色或MFA完成证据。

备选“保持完全不返回auth_time”不能靠伪造时间或把authorization_code改成扩展grant来绕过原生校验；省略SessionInformation还会丢失sid/会话关联和部分标准OIDC语义，不建议作为静默修复。

- 2026-11-09：Base私有grant内部手动创建Issuer，补一处策略setter及工厂接线，保证SMS/email也使用同一策略Bean；不启用或删除任何grant。文件及基线已追加。

## 2026-11-09 身份源验收问题修复

Rain报告保存后数据格式错误、创建表单标签重叠及微信/企微网页场景不清。本轮在已接受身份源管理合同内修复：日志确认readAdditionalSecrets拒绝历史加密空值；MyBatis加密拦截器把null转换为字符串null。System将空附加密钥写为JSON空对象，并兼容历史JSON null；IAM只允许空对象/null，不接受非空旧附加密钥或损坏数据。Admin先校验业务响应，避免错误被通用分页解析掩盖；标签允许多行且移动端上下排布，补充微信网站/公众号和企微双场景说明。来源值、凭据域、绑定和认证合同不变，不改Boot、不改业务库或部署。

本轮文件沿用上述已接受清单，开发反馈补充IdentityProviderConfigServiceTest.java（已在原测试清单）、identity-provider-policy.test.js及identity-provider-policy.cy.js；无新增源码文件。验证目标为System/IAM定向回归、Admin单元/构建及桌面和移动端Mock浏览器回归。

### 本轮开发反馈证据

- System/IAM定向Maven回归25项通过（IdentityProviderServiceImplTest 17项、IdentityProviderConfigServiceTest 8项）；覆盖历史null/JSON null/空对象的详情、分页和认证读取，以及未知字段/损坏JSON拒绝。新增分页夹具首次运行分别缺少TableInfo和复用被convert原位修改的Page，已修正并重新通过。日志：/private/tmp/identity-source-backend-test.log。
- Admin完整Vitest：21文件198项通过；生产构建通过（沿用Sass与chunk告警）；本轮文件无自动修复ESLint无错误（原有格式告警保留）。日志：/private/tmp/identity-source-all-unit.log、identity-source-build.log、identity-source-eslint-errors.log。
- Cypress身份源专项6/6通过：1280/390px创建、标签边界与横向溢出、保存后的列表刷新、组织策略及禁用未核实来源、服务错误显示。初次新增创建用例的字段文字匹配命中场景说明，已改为精确表单标签，并从创建入口独立开始；未改业务行为以迁就测试。日志：/private/tmp/identity-source-cypress.log。
- 桌面/移动截图均已人工查看，正文留白、标签换行和固定页脚可用；截图位于已忽略的cypress/screenshots/identity-provider-policy.cy.js/。
- git diff --check通过；Boot工作树保持干净。修改前逐文件增量基线：/private/tmp/identity-source-fix-baseline。
- 本轮只做开发反馈，不推进整个统一认证计划的人工gate；未部署、提交、推送或修改业务库。浏览器用例使用Mock API，不代表真实厂商验收；运行中System仍是旧镜像。
