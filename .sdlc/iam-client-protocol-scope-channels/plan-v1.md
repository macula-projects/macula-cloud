# Plan: IAM协议、Scope与具体身份源类型 (from spec.md 2026-11-06)
Status: superseded

## Files that change

路径均相对仓库根目录；括号标注新增，其余为适配既有实现。仅该文件集进入实施；发现额外受影响调用方时先记录具体路径与原因。

### 共享标识与System模型/目录
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ProtocolMode.java`：新增；OIDC/OAUTH2_1，null仅作历史兼容。
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IdentitySourceType.java`：新增；具体来源类型、厂商、场景与稳定能力标识，不携带凭据值。
- `macula-cloud-system/src/main/resources/db/migration/V15__iam_protocol_scope_identity_source.sql`：新增；追加协议模式、来源类型、浏览器固定关联及新来源独立绑定表，保留V14和旧绑定表。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/enums/IdentityProviderType.java`：保留厂商及历史整包schema，委托新来源schema。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IdentityProviderForm.java`：来源类型presence与不可变约束。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IdentityProviderVO.java`：具体来源类型、脱敏配置与缺失能力。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IdentityProviderCatalogVO.java`：厂商下具体类型及独立字段schema。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIdentityProvider.java`：nullable source_type。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImpl.java`：按来源类型保存/校验/脱敏，旧记录保留原schema。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2ScopeCatalogProperties.java`：新增；受控资源Scope声明与配置校验。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2ScopeCatalogConfiguration.java`：新增；启用目录属性。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2ClientConfigurationCatalogService.java`：新增；协议、Scope和来源能力目录。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`：新增；按真实实现/凭据/来源类型生成可用及拒绝原因。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`：新增；目录契约，不返回密钥。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`：protocolMode/browserProviderBindings presence。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`：新增nullable字段映射。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`：脱敏模式与浏览器固定关联。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`：Scope增量白名单、模式/grant/来源组合与CAS扩展。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/OAuth2ClientConfigController.java`：只读configuration-catalog，保留旧目录接口。
- `macula-cloud-system/src/main/resources/application.yml`：资源Scope目录默认空，不伪造业务权限。

### IAM协议与来源链路
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProvider.java`：来源类型映射。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`：协议模式与浏览器固定关联。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysRegistrationIdentityBinding.java`：新增；仅新typed来源的registration隔离绑定。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysRegistrationIdentityBindingMapper.java`：新增；真实数据访问。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`：新增；按服务端registration解析/写入独立绑定，拒绝跨来源回退。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`：条件openid及显式模式防御校验、浏览器关联settings。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolValidator.java`：新增；显式模式授权码S256校验，组合框架默认验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`：装配协议校验，保留默认redirect/scope/consent校验。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`：按来源类型校验与按ID加载，legacy查询隔离。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyService.java`：可信授权上下文、固定registration、场景能力。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`：发起/回调/state固定同一来源。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`：服务端state增加registration ID与来源类型快照。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepository.java`：有界旧state兼容与新state一致性。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionService.java`：新typed来源不进入全局family+subject或企微用户名自动匹配。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/PendingThirdPartyBindingRepository.java`：pending保留确定来源，失败次数保持。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`：按来源重校验并写入对应绑定存储。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicket.java`：绑定票据承载可信registration。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicketStore.java`：票据来源一致性及旧票据兼容条件。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingService.java`：重校验Client当前固定关联及来源，再写绑定。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeVerification.java`：核验结果携带可信registration上下文。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolver.java`：按已核验来源查找，MOBILE继续真实手机号验证语义。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`：固定关联、来源类型与核验结果一致。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeClientSettings.java`：稳定settings标识。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`：按实际来源能力呈现按钮和自动直登模式。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProvider.java`：回调按state来源加载并解析身份。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProvider.java`：回调按state来源加载并解析身份。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProvider.java`：回调按state来源加载并解析身份。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProvider.java`：回调按state来源加载并解析身份。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProvider.java`：回调按state来源加载并解析身份。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatClient.java`：按具体来源所需凭据和场景取值，保留旧整包分支。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatExternalCodeVerifier.java`：服务端固定ID/类型/结果验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`：按具体来源所需凭据和场景取值，保留旧整包分支。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifier.java`：服务端固定ID/类型/结果验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuClient.java`：按具体来源所需凭据和场景取值，保留旧整包分支。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifier.java`：服务端固定ID/类型/结果验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClient.java`：按具体来源所需凭据和场景取值，保留旧整包分支。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifier.java`：服务端固定ID/类型/结果验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifier.java`：号码认证来源类型及固定ID校验。

### Admin与文档
- `macula-cloud-admin/src/api/model/system/identityProvider.js`：类型目录与脱敏来源模型。
- `macula-cloud-admin/src/views/system/identityProvider/index.vue`：厂商→具体类型、独立表单、历史只读类型。
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`：统一目录及协议/固定关联字段。
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`：按模式/来源能力模板与payload，旧项无损。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：统一目录异步状态及完整草稿。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`：新建明确协议默认值与Scope选择。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`：平台地址紧凑文本复制。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`：OIDC/OAuth2.1/历史兼容说明及Scope多选。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`：默认策略说明、具体来源关联与语义能力选择。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`：按来源模式示例，原始code参数仅在指南出现。
- `macula-cloud-admin/src/locales/lang/zh-cn.js`：文案。
- `macula-cloud-admin/src/locales/lang/en.js`：文案。
- `macula-cloud-system/README.md`：协议、Scope目录、来源类型、兼容与身份隔离说明。
- `macula-cloud-iam/README.md`：协议、Scope目录、来源类型、兼容与身份隔离说明。
- `macula-cloud-admin/README.md`：协议、Scope目录、来源类型、兼容与身份隔离说明。

### 验证文件
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`：模式、Scope增量、presence、关联及CAS。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`：来源独立字段与身份标识不可变。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`：新增；目录与实际能力。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/OAuth2ClientConfigControllerTest.java`：目录路由与三态JSON。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ProtocolIdentitySourceMigrationIT.java`：新增；V14→V15、旧记录/绑定/密钥指纹、新绑定唯一性。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`：新字段加入原子快照，旧PUT交错。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`：新建fixture、加密与缓存真实回归。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/AdminSettingsAuthorizationTest.java`：新目录仍管理权限。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepositoryTest.java`：条件openid、协议防御、legacy。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`：OIDC/OAuth2.1真实链、Token/UserInfo/S256。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`：按来源字段和模式取配置。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java`：固定关联/禁用/替换/错误模式拒绝。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingServiceTest.java`：新增；同subject跨来源不串号、旧表不回退。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingServiceTest.java`：pending来源不变与原子唯一性。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalBindingTicketStoreTest.java`：来源票据与旧票据。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingServiceTest.java`：票据与Client当前绑定一致。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolverTest.java`：新增；外部身份与MOBILE边界。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java`：已认证Client/来源快照一致。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProviderTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatClientTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProviderTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomClientTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifierTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProviderTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuClientTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifierTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProviderTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClientTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifierTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProviderTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifierTest.java`：相应来源类型/配置注入回归。
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`：协议/目录/类型表单/应用关联与紧凑地址。
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`：协议/目录/类型表单/应用关联与紧凑地址。
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`：协议/目录/类型表单/应用关联与紧凑地址。
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`：协议/目录/类型表单/应用关联与紧凑地址。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`：协议/目录/类型表单/应用关联与紧凑地址。

## Order of work
1. 记录Rain对规格的接受，快照全部现有未提交改动到临时目录。当前已部署V14不改checksum；确认V15在目标不存在。只记录本计划文件，无提交/推送或新业务Client修改。后续Docker更新沿用用户已有授权，但必须完成本轮验证与本地迁移预检后，只更新System/IAM/Admin。
2. 定义共享协议与来源类型，固定厂商/来源/场景/结果的含义。具体来源按实际实现列入：WECHAT_WEBSITE、WECHAT_OFFICIAL_ACCOUNT、WECHAT_MOBILE_APP、WECHAT_MINIAPP；WECOM_WEB、WECOM_SDK、WECOM_MINIAPP；FEISHU_WEB、FEISHU_APPLINK、FEISHU_WEBVIEW；DINGTALK_WEB、DINGTALK_SDK、DINGTALK_WEBVIEW；GITHUB_OAUTH、ALIYUN_MOBILE；ALIPAY_APPLICATION保留已有凭据管理但不宣称实现登录验证器。新来源默认disabled，类型schema中缺失能力有明确原因。
3. V15仅追加：sys_oauth2_client.protocol_mode、browser_provider_bindings可空；sys_identity_provider.source_type可空。旧NULL不猜测、不回填。另建sys_registration_identity_binding，主键id，registration_id、provider_subject、username及既有审计字段；唯一(registration_id, provider_subject)、唯一(registration_id, username)，subject使用大小写敏感比较。旧sys_user_identity_binding和其唯一索引完全保留，既有账号身份域不重键。新typed来源仅用新表，以服务端持久化registration ID隔离；不能回退旧表，也不自动跨来源合并账号。这是新来源的绑定存储，不迁移旧用户。
4. 来源管理区分厂商providerType与具体sourceType。新建必须选具体类型，按类型独立校验/加密/脱敏，只保存该类型所需凭据；微信小程序不要求网站/公众号凭据。已保存具体类型及决定外部身份域的标识（corpId/appId等）不可变，可旋转Secret与改显示名；改变身份域需新建来源。旧sourceType=NULL记录继续原schema和绑定路径，不自动转型。旧PUT省略sourceType保留；普通更新不能将typed清回legacy。并发编辑采用原sourceType及原配置快照CAS，失败409，不发布成功缓存副作用；原凭据空值保留/加密边界继续验证。
5. 实现受控业务Scope目录属性：标识、名称、描述、适用应用/grant，校验重复、保留OIDC名称和非法值；默认业务列表为空。内置仅openid/profile，profile明确只含真实用户名/昵称。`GET /api/v1/oauth2-clients/configuration-catalog`复用管理权限，返回协议/Scope/具体来源及可用/不可用原因，不含原文凭据或可恢复摘要。旧external-code-catalog保留兼容调用方，但新UI不直接渲染原始CODE。无业务Scope时Service/外部凭证创建显示先登记提示，不虚构默认scope、不回退手填。
6. Client表单新增字段做presence跟踪，不序列化内部标志。旧PUT省略protocolMode/browserProviderBindings保留；protocolMode一旦明确不能清回null规避校验，可在OIDC/OAUTH2_1间显式转换并校验差异；null仅历史兼容。Scope新添加值必须在目录，旧未登记值仅允许保留/明确移除，不扩散成可选目录项；协议禁止值仍必须明确修正。OIDC要求openid且授权码/refresh组合，OAuth2.1禁止openid与仅用于OIDC的profile、无ID Token，允许受控MACULA扩展并明确标注。password仅legacy允许；Service保留仅client_credentials约束。新现代授权码模式一律S256。明确刷新能力矩阵：legacy保留既有值；显式OIDC/OAUTH2_1的公共客户端（none）不提供refresh_token选择，服务端拒绝该组合，所有授权码/sms/email/external_code现代公共路径均不签发刷新令牌。当前框架公共授权码不签发refresh，不新增发行器或轮换/重放引擎；有长期登录需要时使用IAM既有SSO重新授权。机密授权码客户端可选择框架实际支持的refresh，现代模板默认不复用，仍需Client认证；Service维持无refresh。显式转换已有公共Client时显示差异，必须用户确认移除refresh grant；不靠读取/模板静默改写旧值。完整配置CAS加上protocolMode/browserProviderBindings，0行409且不替换任何关联、不发成功缓存事件。
7. IAM注册Client仅读真实配置：legacy保持原无条件补openid行为，OIDC必须持有openid，OAUTH2_1不得加入openid/ID Token；数据库绕过管理端的矛盾配置也拒绝；显式现代none+refresh组合在注册Client构造时失败关闭，sms等扩展不能沿旧默认reuse=true签发现代公共刷新令牌。设置稳定protocolMode ClientSettings供授权端校验。组合原框架redirect/scope验证与显式模式S256要求，不替换默认校验，不修改既有授权/refresh已签发状态；Scope目录变化不自动撤销已发Token。
8. 应用选择具体浏览器来源时存browserProviderBindings[providerFamily]=registrationId，同时保留现有browserLoginProviderTypes作为允许族；SDK/小程序保留code_type→registration现有绑定。按来源类型/应用类型/凭据完整性校验，来源Secret只在来源管理维护。保留同厂商全局单一启用浏览器registration限制（legacy与typed可用于浏览器的候选合计），SDK/小程序来源不误计入浏览器候选；多个浏览器来源时不新增关联且发起/回调失败关闭，不择一降级。微信网站与公众号同属该浏览器限制，本轮不同时启用多个独立浏览器registration；目录/UI明确解释，不虚构多源支持。
9. 浏览器发起、授权URI和回调通过当前Client固定ID及来源类型取凭据；legacy未固定时仅走原legacy唯一来源查询。state保存clientId、mode、registrationId/sourceType、受控saved target；回调消费一次state，重新核对Client仍关联同一来源、来源仍启用、类型/mode匹配，再换码和解析绑定，不接受query/header自选来源。各厂商AuthenticationProvider停止重新按family全局择源。旧state若无法证明仍是legacy关联则受控提示重新登录，不能流入新typed来源。LoginController按钮/自动直登也依赖匹配场景，网站来源不能自动走公众号端内模式。
10. SDK验证按已认证Client固定ID加载，核对来源类型允许当前code_type与真实结果类别；typed配置以其自身主凭据供对应Client方法使用，legacy继续旧多凭据字段分支，不能跨来源借用缺失Secret。服务端核验结果带registration ID，必须与请求注册Client绑定快照一致；来源类型不能由端侧参数覆盖。新typed EXTERNAL_IDENTITY只查新表；新typed企业微信不使用旧的同名username自动匹配。未绑定走既有显式绑定证明；MOBILE继续已核验唯一手机号语义，票据仍绑定来源与Client，不能任意手机号或跨Client使用。
11. 浏览器pending与SDK绑定ticket全程保留来源，绑定前重校验当前关联/类型/启用状态、原租户/用户范围及已认证用户证明，插入新表依靠唯一索引处理并发。旧legacy票据仅在仍为legacy来源链路时兼容，否则提示重启流程；缺少新增字段绝不默认为新来源。禁用/删除/替换来源后的旧state/ticket不能完成新绑定。新来源删除不清除历史绑定也不重用registration ID，引用仍在的Client需先解除；数据库或缓存失败不吞掉异常。
12. Admin身份源改为厂商→具体来源类型，schema决定字段、凭据帮助和适用模式；旧来源标为历史综合类型、只读类型，不自动拆或复制密钥。应用按类型关联来源及语义能力：网站/端内/SDK/小程序，微信小程序“身份登录/手机号授权”独立选择；选项和拒绝原因来自目录，隐藏原始CODE枚举，API payload/指南保留底层稳定参数。协议模式与Scope全部选择；现代公共客户端隐藏/禁用刷新授权并解释当前流程不签发refresh，不能显示一个无实际效果的rotation开关；机密授权码的刷新与SSO续登分别说明。旧未知项显示兼容标签并保持draft，目录延迟/失败不覆盖已编辑值，失败时可重试。创建类型切换保留ID/通用参数并清理不适用来源，四分区共用完整草稿。
13. 平台地址改紧凑名称/可换行文本链接/复制，不渲染输入框；保留元数据失败/重试和客户端JWKS独立输入。托管方式显示“使用平台默认/为此应用单独设置”，说明只跟随IAM密码/SMS全局开关；没有可信实时数据不声称当前有效开关值。保留null/[]和全局关闭优先，完全不改变既有托管过滤器信任链。
14. 更新中文模块文档及实际验证。先单元/组件再全项目Maven/Admin、全部Cypress，隔离MySQL V14→V15及真实来源绑定唯一性/并发，保留既有迁移和客户端CAS集成回归。Javadoc/版权/Java17与定向Checkstyle、ESLint、diff检查。Build出口先按技能要求确认实现完成；正式Stage4记录真实输出后才在已有授权范围更新本地容器。没有提交/推送/PR权限，不执行这些操作。

## Risks
- 多轮未提交成果与V14已上线：先快照，禁止整仓restore/reset和改已执行迁移。新旧来源路径分开；旧表不迁移不重键。回滚时新增结构保留，旧服务不认识新typed来源，因此回滚前必须停用新来源及其新关联，避免旧程序按family误用新配置；不能宣称旧镜像直接可用所有新来源。
- registration ID只有配合来源类型与外部身份标识不可变才是稳定边界。新来源不能通过修改corpId/appId接管已有绑定；Secret轮换不改变该身份域。新typed来源默认需显式绑定账号，不自动继承legacy同厂商账号；这是防串号选择。
- 不支持同厂商多个浏览器registration与细分类型共存有边界：可配置多种记录，但浏览器候选全局唯一要求仍在；公众号/网站同时独立启用不在本轮。非浏览器SDK/小程序来源可独立启用，以固定ID取配置，不能被浏览器的family计数误包含。
- 刷新能力按真实签发路径约束：当前框架公共授权码不签发refresh，项目公共sms旧路径可能签发可复用refresh。显式现代公共模式禁止refresh组合并在System/IAM/模板同策，不临时引入半套rotation；legacy保留但明确兼容，转换会使原刷新grant不可再用，需明确差异确认，不等于撤销已有access token或SSO。
- 标准Scope、资源Scope、URL权限与角色互不推断；目录声明不等于资源服务已落实权限校验。业务目录为空是明确阻止无依据新配置，不能临时塞message.read/userinfo示例值。
- legacy模式与未知scope保留可能被滥用成降级通道：新建必须明确模式、typed不清回null、未知值只能原集合保留；IAM执行端同样校验，而非只有UI限制。
- state/ticket旧序列化兼容不能带来来源回退。typed Client遇旧缺失字段时重新发起，不能复用legacy绑定或按provider全局择源。逐一覆盖发起、回调、待绑定与SDK票据。
- 新绑定表隔离身份但不新增自动跨源账号合并/注册。浏览器原有全局用户名与SDK原有用户范围加载保持各自原语义，本轮不扩大任何用户目录信任。
- 目录与配置异步、保存全对象、模式切换可能擦除旧值或扩大授权：单草稿、presence、差异确认、CAS和请求payload白名单共同保护。

## Proof
- 协议/Scope：实际过滤链分别完成OIDC授权与纯OAuth授权；后者无ID Token且不能通过手工openid得到UserInfo。S256正常，缺失/plain拒绝，保留默认redirect/scope拒绝；现代password非法、legacy旧组合继续；现代none+refresh组合System拒绝及IAM防御拒绝，实际公共code/sms/email/external_code响应均无refresh_token，机密授权码refresh实际可用且需要客户端认证；legacy原刷新回归继续。Scope目录只有openid/profile与显式登记业务项，email/phone/address/offline_access不伪装可用，旧未知值不丢失、新增未知值服务端拒绝、业务目录空时有明确错误。测试目录来源不等于资源授权承诺。
- 来源schema：每种具体类型创建/编辑显示并验证正确必需字段和Secret；微信小程序只用小程序凭据，网站不借用公众号凭据；新建type必选、旧PUT省略不变、typed类型与身份标识不可变、Secret可轮换、CAS0行无成功副作用、读取无原文/密钥摘要。
- 来源链路：服务端固定registration/类型/场景与SDK code匹配，禁用/删除/替换/错误类型/目录延迟都失败关闭；state篡改、重放、旧缺字段、跨Client、回调路径/mode及scope变更拒绝；pending/ticket来源一致和绑定前重查。新typed相同厂商相同subject在不同registration不能串号；不同企业同subject/username不使用企微自动匹配；旧来源仍命中旧绑定，新来源不fallback；唯一索引下并发绑定只有一个成功；合法MOBILE结果仍按既有手机号证明语义。
- 数据：独立MySQL由V14升V15，旧Client/来源/密钥/两类原有绑定及索引逐项指纹不变、nullable新列不猜测；新表唯一索引/大小写敏感subject与新Client CAS交错通过真实Mapper/Service验证。不能以复刻SQL测试代替关键Service链路。
- UI：厂商→具体来源类型与正确字段/凭据保留，按应用过滤来源，语义选择到稳定payload映射、无原始CODE勾选；Scope不允许手输，未知旧项只读保留；协议分区与地址文本复制/失败重试；平台默认范围说明；全宽/390px溢出、分区未保存/共享draft、密钥清除回归。
- 命令：`mvn test -Plocal`；隔离MySQL/Redis运行所有项目已有IT及新增OAuth2ProtocolIdentitySourceMigrationIT，并补跑环境门控升级测试；Admin `npm run test:unit -- --run`、`npm run build`、`npm run test:e2e:ci`；定向Java Checkstyle、变更前端ESLint和diff检查。只报告实跑输出，Mock与真实第三方设备分开；本轮未改变技能/hooks/REVIEW，不虚构评审eval。

## Superseded
Rain最新要求取消历史兼容并新增来源自动绑定/依据/手动页面流程。该计划与前两轮评审不能用于当前实现；待修订spec接受后重写计划和重新评审。尚未按本计划写源码。
