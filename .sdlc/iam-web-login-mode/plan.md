# Plan: Web/SPA 浏览器或端内登录二选一 (from spec.md 2026-11-12)
Status: accepted — 用户明确要求“你先实现刚刚的方案吧，bug等下再看”，进入Phase B

已接受[spec.md](spec.md)及[需求](intent.md)。本轮只区分架构类型和登录模式，复用已有流程；不用共存开关、动态分流、普通登录回退或新的认证框架。沿用Java17、现有Spring Security/Jackson/MyBatis/Flyway、Vue3/Element Plus及既有测试工具，不新增依赖。没有CLAUDE.md，按Cloud AGENTS及已读架构/后端/前端/测试/配置发布规则实施。Author仍待确认。

## Files that change

以下是本轮精确写入范围；此前import整理及邮件/SMS改动是受保护的未提交基线，不算本轮新增工作。

### 共享契约与规则

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2WebLoginMode.java`（新增）
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationType.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ApplicationLoginPolicy.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ClientCacheNames.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/OAuth2WebLoginModeTest.java`（新增）
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationTypeTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/security/OAuth2ApplicationLoginPolicyTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistryTest.java`

### System持久化、保存与目录

- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningService.java`
- `macula-cloud-system/src/main/resources/db/migration/V26__web_login_mode.sql`（新增）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/vo/SettingsModelJsonTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/WebLoginModeMigrationIT.java`（新增）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/WebLoginModeConcurrencyIT.java`（新增）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ApplicationLoginBoundaryConversionIT.java`（删除，测试由WebLoginModeConcurrencyIT接替）
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/HostedEmailLoginMigrationIT.java`

### IAM运行时

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/inapp/InAppLoginPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/inapp/InAppAuthorizationRequestFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/BrowserLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/AbstractThirdPartyAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolValidator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeClientSettings.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/inapp/InAppLoginPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/inapp/InAppAuthorizationRequestFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/BrowserLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/InAppLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/MfaProtocolIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingIT.java`

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/client/impl/SysOAuth2ClientServiceImplTest.java`（测试阶段补充：Origin索引夹具实际使用client_credentials，须明确为SERVICE，防止误建为Web）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverter.java`（测试阶段补充：机密客户端表单认证参数须按已认证方法接收，并从grant附加参数移除）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverterTest.java`（测试阶段补充：机密客户端表单认证参数须按已认证方法接收，并从grant附加参数移除）

### 管理端

- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/utils/identitySourceCompatibility.js`
- `macula-cloud-admin/src/utils/applicationProtocolRegistry.js`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/IdentitySourcePicker.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/src/locales/lang/zh-cn.js`
- `macula-cloud-admin/src/locales/lang/en.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/identity-source-compatibility.test.js`
- `macula-cloud-admin/tests/unit/application-protocol-registry.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`

### 文档与工件

- `macula-cloud-api/macula-cloud-system-api/README.md`
- `macula-cloud-system/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/docs/architecture.md`
- `macula-cloud-iam/docs/configuration.md`
- `macula-cloud-iam/docs/third-party-integration.md`
- `macula-cloud-admin/README.md`
- `.sdlc/iam-web-login-mode/plan.md`（新增）
- `.sdlc/iam-web-login-mode/review-findings.md`（新增）
- `.sdlc/iam-web-login-mode/build-notes.md`（新增）
- `.sdlc/iam-web-login-mode/verification.md`（新增）

## Order of work

1. **保护基线与共享模式**：保存Git状态和相关文件内容摘要；不回滚已有135文件import整理、邮件/SMS统一及用户原有修改。新增唯一生产Java类型OAuth2WebLoginMode（BROWSER/IN_APP，使用明确稳定值），移除OAuth2ClientApplicationType.IN_APP与convertToInApp契约。共享OAuth2ApplicationLoginPolicy接收应用类型、模式、具体来源映射及启用状态；不复制矩阵，不保留没有生产用途的便利重载，不为每个类新增接口/父类。BROWSER仅普通网页能力与原普通授权；IN_APP仅一个端内能力/注册，启用时恰一个、草稿最多一个；端内网页与JSAPI两条路径仍互斥。Native/Miniapp/Service原有矩阵不变。
2. **模式持久化与System**：追加V26__web_login_mode.sql（实施前再核对编号），仅ADD可空VARCHAR(16)字段web_login_mode，无UPDATE/DELETE或旧类型转换。正常Web/SPA存量NULL只按缺省BROWSER解释；新建Web/SPA写BROWSER，非Web类型为NULL且不可配置此选项。OAuth2ClientForm/VO及两侧实体增加webLoginMode；HTTP必须单值精确枚举字符串，重复、null、空、未知值或错误JSON类型拒绝；更新省略保留原值，省略不能把非法存量值降级为BROWSER。内部缺省与HTTP显式null分开。原applicationType=IN_APP或未知类型在保存、目录适配及IAM运行时均明确拒绝，不做兜底转换。
3. **完整保存与目录**：mode在普通/草稿/兼容保存入口均校验并加入matchOriginal CAS、授权版本与提交后缓存失效；现有version()序列化完整实体自动包含新增属性，保持该实现并用测试证明摘要变化。并发冲突409，回滚不驱逐缓存。受管Admin始终BROWSER，不允许借模式切换替代原受控类型限制；平台认领/幂等重跑不改ID、Secret或来源绑定，更新相关模式验证/内部Form比较。Catalog保留applicationTypes，并新增能力级webLoginModes以准确表达Web/SPA两模式，值由共享矩阵投影；普通来源旧投影不得覆盖显式能力。mode不进入OAuth协议settings。非法混合快照直接拒绝，服务器不偷偷删关联。
4. **IAM沿用端内流程**：把原“type=IN_APP”的入口/来源/会话隔离/MFA/回调/锁内绑定条件统一替换为共享“Web/SPA＋IN_APP模式”判断。InAppLoginPolicyService保留当前来源选择与证明职责；InAppAuthorizationRequestFilter仍在原协议链位置，仅对端内模式隔离本次未证明认证，finally恢复原上下文，不保存匿名Session。LoginController仍先判断端内模式再处理普通已登录用户；浏览器模式不自动按UA分流，端内错环境/失败保留专用错误及受控重试，不提供普通登录链接。HostedLoginClientPolicyService在发码与认证之前阻止端内PASSWORD/SMS/EMAIL；null继承全局不能在端内启用这些方式。模式不依赖query/header/cookie自报，只取已校验SavedRequest、当前Client及RegisteredClient视图。
5. **运行时策略与Code认证**：基础RegisteredClient和无缓存协议请求视图都投影同一模式标识；OAuth2ClientProtocolPolicy/Validator及来源/绑定规则都用当前共享矩阵，非法/缺失策略失败关闭。派生Client缓存升级版本，不改Token授权缓存格式或Token/Session类型/字段/UID/白名单。仅Web/SPA端内且已关联WEBVIEW Code可用external_code；WEB_SERVER须通过已配置的client_secret_basic/client_secret_post/private_key_jwt机密认证，SPA/Native/Miniapp仍仅none，不能让“存在非none主体”绕过来源/模式/授予许可。协议校验与Provider同步修改；普通浏览器Web/SPA不接受WEBVIEW，Native/Miniapp/Service不扩大。来源核验、Origin、nonce/Scope/PKCE、限流、replay、目录和MFA保持；直接Code不建浏览器SSO、不新增refresh，端内网页只授权码，端内JSAPI只external_code，普通Web原授权码refresh保持。Client/来源改配后在途state、pending、Code及已有平台授权按原版本机制拒绝。旧IN_APP授权不能借刷新/恢复绕过类型移除。
6. **管理端单选与精确清理**：从类型列表删除IN_APP及转换UI/helpers/提示；在现有Web/SPA应用配置内增加webLoginMode单选，类型仍只读、模式可明确切换，受管Admin禁改。切换先生成待确认副本和差异，确认后才清理不适用的来源/内置登录/grant/直登并提交新模式，取消或保存失败保留原已保存配置与当前可重试草稿；不保存隐藏的旧模式配置用于自动恢复。保留ID、Secret、全局身份源实体、用户绑定、其他未改配置。IN_APP清空普通网页登录/直登和内置方式，按所选唯一能力确定code或external_code；未选能力只保留合法草稿。BROWSER清除端内能力，不自动勾选PASSWORD/SMS/EMAIL或恢复已关闭登录。Web机密认证和有效凭据不得被JSAPI助手改为none；SPA保持none，Origin/回调只按实际路径校验，不用模式切换无差别清空。Picker/ProtocolSettings/Guide、payload白名单/归一化/重载、国际化及源目录过滤同步使用新模式。业务JSAPI示例区分SPA直交IAM与Web由后端持机密凭据提交，不生成浏览器Secret示例。
7. **测试、文档与清理**：用新WebLoginModeConcurrencyIT替代已失效的ApplicationLoginBoundaryConversionIT，保留其锁/CAS/凭据绑定不丢的证明目标。新增V26迁移及枚举测试；现有端内、安全链、模式/鉴权测试换成真实Web/SPA＋mode夹具，不把所有旧IN_APP字面量机械改成新类型。历史V25测试显式target25，保留历史IN_APP样本；历史Flyway/SDLC不改。当前IdentitySourceBindingIT升级schema guard到26，临时执行器先保留历史V23映射夹具验证，再在隔离库升级到26跑当前绑定IT，不能让新Mapper去读V23而报缺列。README/架构/配置/逐平台说明按现状重写相关段落，不追加流水账；保留现有官方平台合同，不宣称真机联调。新enum和修改类型/方法写必要职责与安全意图注释，显式import，不生成package-info.java。无证据不删SPI、兼容序列化类型或公共数据。
8. **构建与验证门**：Phase B完成实现、用例及偏差记录后，先clean test-compile -DskipTests和静态检查；按sdlc-build提问Is the implementation complete per the plan? 确认后进入sdlc-test，执行以下Proof并保留真实命令、失败、跳过及环境边界。当前Phase A不写源码、不执行本功能测试、不沿用邮件/SMS的绿色结果。

## Risks

- 新字段需要Schema与System/API/IAM/Admin协调版本；本轮只准备源码及全新隔离库测试，不操作业务库、Nacos、镜像或运行中服务。回退需版本/Schema协调，不能声称只换镜像即可回滚。V26不更新旧IN_APP记录，移除后旧类型明确拒绝；这种破坏性仅按用户指定范围实施，不扩散到正常Web/SPA。
- type与mode是不同维度。BROWSER不是对任何异常模式的默认值；仅正常Web/SPA字段缺省/历史NULL有缺省语义。其它类型/资源身份不接受Web模式；受管Admin不得切成端内。旧字段convertToInApp不再控制任何行为，未知/遗留写入不可触发转换。
- 端内必须恰一个能力，不能因mode字段改名而把PC/端内、浏览器/JSAPI混合，也不能用已有普通Session、客户端自报来源或空策略绕过。原Filter顺序、SavedRequest校验、finally恢复、Client/来源版本、锁内重查及平台证明发布边界保持。
- Web机密external_code是对现有Provider/协议校验none限制的有界修改，三种已支持机密方法均须真认证链正反测试；不扩展其他grant、终端或无来源客户端，不把设置requireProofKey等同于JSAPI已经执行PKCE。
- 模式切换会使原不适用登录配置失效；UI明确预览与确认，服务端校验最终完整快照而不相信布尔转换标记。取消、失败、并发不应丢凭据/关联或破坏其它Tab。用户绑定/身份源注册不是应被清理的Client配置项。
- 之前135文件import与邮件/SMS统一是未提交基线；本轮重叠文件按内容增量修改，未重叠文件按摘要保护。新增Java版权头/Javadoc/author使用仓库惯例，不推断需求Author；保留两个Token持久化合同。若发现必要遗漏路径，先在本计划列出精确路径、原因和证明影响再改；合同/安全语义变化需回到人工门。
- REVIEW.md没有指定Stage3 reviewer，默认codex-plan-review未安装；按skill使用sdlc-reviewer回退，只跑Plan，最多三轮，记录实际轮数、每项发现与处置，不自批人类门。当前没有提交/推送/合并/部署授权。

## Proof

- **R1/R2类型＋模式**：OAuth2WebLoginModeTest、OAuth2ClientApplicationTypeTest、OAuth2ApplicationLoginPolicyTest覆盖五类架构×BROWSER/IN_APP/缺省/非法值/非Web错误配置，端内一个能力、草稿零能力、启用必须一个、普通PC与端内/JSAPI混合拒绝；AppRegistryTest覆盖Web机密Code与原Native/Miniapp/Service边界。
- **R2/R4持久化与并发**：SettingsModelJsonTest覆盖单值字符串、重复/null/非法类型、内部与HTTP缺省；System服务/目录单元测试覆盖往返、更新省略保留、裸API混合配置拒绝、受管身份限制、Catalog类型＋模式元数据与完整指纹变化。WebLoginModeMigrationIT在全新独立MySQL从25到26证明仅新增列：普通Web/SPA默认BROWSER，旧IN_APP原值未转换，ID/Secret/协议/来源/绑定不变，重复migrate不重置主动选择。WebLoginModeConcurrencyIT用两连接同快照竞态切换模式，只有获胜者提交并驱逐两实例缓存，失败回滚不丢绑定/凭据；凭据重置等并发使用相同完整版本护栏。两个Provisioning单元测试证明认领/重跑不隐式改模式或身份。
- **R2/R3运行时二选一**：InAppLoginPolicyServiceTest、FilterTest、HostedLoginClientPolicyServiceTest、BrowserLoginClientPolicyServiceTest、InAppLoginFlowTest及IamProtocolTest验证Web/SPA两模式、SavedRequest、错UA不构成身份、企微/微信专用入口、飞书/钉钉JSAPI无假网页桥接；IN_APP拒绝PASSWORD/SMS/EMAIL发码和认证、已有普通/其它Client SSO及错误/重放/跨注册/Client或来源改配。BROWSER原密码、SMS、Email、第三方PC、普通SSO和授权码/refresh不回归。有效平台网页认证走Session、SavedRequest、S256、OIDC nonce/auth_time、UserInfo；JSAPI只发协议结果，无浏览器SSO，不伪造acr/amr。
- **R3认证与发布**：external_code Provider单元与真实IamProtocol/InApp安全链覆盖WEB_SERVER三种机密认证的真实成功/缺失/错误Secret或JWT签名/禁用方法，SPA none成功且有Secret也不能作为机密Web；BROWSER/错终端/错能力/来源禁用/缺proof在厂商/绑定/发Token前拒绝。两RegisteredClient仓库测试证明缓存基础对象与实时请求视图都有mode且变更即时生效；ProtocolPolicy、MfaProtocolIT及绑定测试证明平台证明与Client/来源版本贯穿授权、同意、换码、刷新、绑定锁及成果发布，拒绝不留下完整Session或半份Token；未知/旧IN_APP类型不能刷新或恢复为可执行配置。原精确序列化及旧邮箱兼容回归保持。
- **R4管理端**：相关Vitest/完整Admin单元、build、Cypress证明无独立IN_APP类型/convertToInApp入口；Web/SPA模式单选、筛选/唯一能力、真实payload/reload、切换确认/取消/失败/并发重试、机密凭据不变、普通登录未被自动开启，390/1440px布局和键盘可达。仅前端截图不能代替后端模式校验。
- **执行与证据**：Stage4执行目标API/System/IAM `mvn -pl macula-cloud-system,macula-cloud-iam -am test -Plocal`、隔离MySQL/Redis的模式迁移/并发/绑定及相关MFA/缓存Failsafe，再全仓`mvn test -Plocal`；Admin `npm run test:unit -- --run`、`npm run build`、全套Cypress（显式启用IAM真实模板夹具）；变更Java Checkstyle、JS/Vue无fix ESLint、git diff --check、旧type/转换引用扫描。历史SQL/迁移夹具及新模式值IN_APP是合理保留项，不能把它们误删。没有真实平台凭据/设备时如实说明未联调，不将Mock声称真实投递/平台成功；保护配置/skill/hook/REVIEW未改，无适用eval变更。使用测试自建实例并清理自有进程，不碰业务库。

## Plan review与人工门

- REVIEW.md没有指定Stage3审查器；已搜索可用技能，默认codex-plan-review未安装，依据sdlc-build使用sdlc-reviewer只读回退。
- web_mode_plan_review运行Plan Round1，结果NO_FINDINGS；累计实际1/3轮，重复抑制0。核对86条精确路径、CAS/版本/锁内模式、Catalog、机密Code、历史迁移target与当前schema、基线保护；没有必要遗漏或无依据扩张。
- 当前只更新本slug intent/spec/plan/review-findings，未写本功能生产代码或测试，也未执行功能测试。临时基线记录web-login-mode-implementation-baseline.json保护164个已有未提交文件，实施时对重叠文件做增量修改。
- 人工门：Engineer, does this plan match your approach? 未获明确确认前不进入Phase B；计划审查通过不等于人工批准。

实施授权记录（2026-11-12）：用户明确要求实施刚刚审查的方案；第三方登录bug暂停，不扩大为部署或业务数据修改。

## Phase B实施记录

2026-11-12按用户明确实施授权完成代码、测试源码及产品文档，见[build-notes.md](build-notes.md)。无合同或精确路径偏离；新增生产类型仅OAuth2WebLoginMode。平台Admin无效SDK认领分支及依赖随BROWSER约束清理，在本计划路径与规则范围内。

clean test-compile（跳过测试）、Checkstyle、无fix ESLint及diff检查通过；[verification.md](verification.md)仍为pending。未执行正式测试、业务库迁移、镜像或远端操作。Phase B完成门等待工程师确认，不自批。

测试阶段范围补充：首轮Origin索引测试暴露旧夹具缺少机器应用类型，新增上述精确测试路径，仅补SERVICE分类；不改生产Origin策略或范围。正式测试仍覆盖全部。

安全链测试发现原Converter白名单拒绝client_secret_post/private_key_jwt的框架认证字段。补记以上精确两路径：只接受与框架已认证方法一致的认证参数，拒绝重复/不匹配参数并在转换时丢弃凭据，不改变机密认证合同或Token序列化。

测试阶段补正：首轮授权拒绝暴露SAS专用异常边界，ProtocolAwareRegisteredClientRepository在基础缓存先拒绝旧类型时也转换成授权端点专用异常，避免ClassCastException；补对应拒绝回归。路径已在计划内，不改变类型/模式或授权许可。

Stage4最终记录：用户YES确认实现后，完成全仓verify、217项Admin单元、build、85项Cypress、42项显式隔离验证及静态检查。Verification is green；真实输出、默认跳过、已修失败和平台边界见verification.md。未进行提交、远端或业务环境操作。

运行更新授权与记录：用户在Verification is green之后明确要求“好，更新镜像”。按本地Compose相关三服务范围构建并依序替换System/IAM/Admin；System自动执行已验证的V26加列，先备份并保持原字段摘要不变。实际镜像、迁移、健康及冒烟见verification.md“用户授权后的本地镜像更新”。未进行Git/远端操作。

## 部署后空协议草稿缺陷修正

用户截图报告切换端内时报Cannot set properties of null (setting authorizationGrantTypes)。现有已接受合同允许尚未选协议的Web/SPA草稿，不应假设settings存在。本次只在已有精确路径oauthClientConfiguration.js、两个相关单元测试及oauth-client-configuration.cy.js补空协议草稿回归并修正空值处理，不生成协议默认值、不改认证/数据结构或业务库。用户已要求修复该交付缺陷，原镜像更新授权覆盖修正后的Admin定向更新；不涉及后端镜像或其他容器。

## 创建后固定模式修订（已接受）

用户最新要求：“选了端内还是浏览器后最好不给切换了，控制太复杂了。”本修订替代原Order of work第6项的保存后切换、确认与清理逻辑，不改变应用架构/模式分离、端内单来源证明或Web/SPA客户端认证规则。建议锁定时点为创建应用成功：创建表单可选择BROWSER/IN_APP，草稿创建完成后即固定，启用/停用不重新开放选择。已有Web/SPA按当前有效模式固定，历史NULL为BROWSER；想用另一模式须新建Client，不转换现有身份/绑定。

### Files that change（本修订精确范围）

- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientDraftForm.java`（补充路径：创建时接收严格webLoginMode）
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/vo/SettingsModelJsonTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/WebLoginModeConcurrencyIT.java`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`（补充路径：创建前选择模式）
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`（补充路径：创建请求携带模式，非Web不发送）
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-api/macula-cloud-system-api/README.md`
- `macula-cloud-system/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/docs/architecture.md`
- `macula-cloud-iam/docs/configuration.md`
- `macula-cloud-iam/docs/third-party-integration.md`
- `macula-cloud-admin/README.md`
- `.sdlc/iam-web-login-mode/spec.md`（确认后将现有切换合同改为创建后固定）
- `.sdlc/iam-web-login-mode/intent.md`（确认后同步约束）
- `.sdlc/iam-web-login-mode/plan.md`
- `.sdlc/iam-web-login-mode/review-findings.md`
- `.sdlc/iam-web-login-mode/build-notes.md`
- `.sdlc/iam-web-login-mode/verification.md`

### Order of work（修订）

1. 先确认创建成功即固定的合同；保留现有未提交工作与已运行镜像，不把刚完成的空settings补丁单独激活。用户确认后更新intent/spec及本节接受记录。
2. OAuth2ClientDraftForm接收webLoginMode；复用共享枚举严格解析，HTTP显式null/空/重复/错误类型/未知值拒绝。Web/SPA创建省略沿用BROWSER，其他架构不接受模式。System createDraft保存明确模式；save(null,完整Form)也支持首次设置。当前缓存/VO/枚举/数据库列直接复用，不新增迁移。
3. System更新仅接受与原有效模式相同的值；省略保留，历史NULL与显式BROWSER等价；IN_APP→BROWSER、BROWSER→IN_APP均在任何配置/关联写入前拒绝，DRAFT/ENABLED和启停一视同仁。原版本/CAS、授权版本与提交后缓存失效继续保护模式内的来源/协议修改，受管Admin始终BROWSER。
4. ClientCreate在Web/SPA类型下显示模式单选，首次提交传给createDraft。详情ClientApplication只读展示当前模式，移除changeMode、确认弹窗、模式切换差异/清理及对应imports；删除无生产调用的switchWebLoginMode。创建前可改选择，不临时生成协议、不清空或猜测凭据；非Web及资源服务器不发送残留模式。
5. 将旧切换/取消用例改为创建时选择、详情只读、请求往返/重载，以及通过API拒绝修改。保留空协议/空settings草稿回归，覆盖创建IN_APP后settings仍可为空、后续选择协议时Web保持机密、SPA保持none。ModeConcurrencyIT改为同一固定IN_APP模式内并发改来源/配置；模式改变单独验证拒绝且不写关联/不驱逐缓存，保留双实例Redis提交/回滚、ID/Secret/绑定保留证据。
6. 文档删除可切换/转换操作说明，直接介绍创建期选择和创建后固定。不删除IAM平台证明、会话隔离、回调/来源版本或仅external_code扩展。先编译及静态检查，Build完成后依技能工程师确认，再正式测试；镜像更新仅在新证据绿色后按用户既有更新授权定向替换System/Admin，IAM/Gateway/中间件不重建，无业务数据迁移。

### Risks（修订）

- 现有草稿也固定当前模式；不为了方便而改写现有Client ID、凭据或来源/账号绑定。若希望改模式，用户创建新应用；本轮不删除旧应用、不迁移绑定。
- UI只读不能替代后端不可变校验；所有保存入口必须受相同规则约束，不能只在启用时检查，或通过停用/空值/旧NULL绕过。
- 创建DTO现有严格字段白名单必须明确加入模式；后端支持前不能仅发布前端。新协议尚未配置时settings合法为空，必须保留这一回归，不能机械补OIDC设置来掩盖空值。
- 原空草稿热修221单元/87浏览器已通过，但尚未重建运行Admin；本修订后旧“切换成功”证据不再适用，需要新的创建/只读/拒绝证据。当前运行容器保持上一轮部署。

### Proof（修订）

- JSON/服务单元证明Web/SPA创建两模式、缺省BROWSER、严格输入、非Web拒绝；更新相同模式成功/省略保留，双向修改和伪造null/空值在协议/绑定/缓存副作用前拒绝，原类型/secret不变。
- 服务集成验证固定模式下配置并发仍只有一个获胜者，409与回滚不驱逐缓存、两Redis实例观察提交失效；显式改变模式被拒绝，无数据库新迁移。
- Admin单元与Cypress验证创建Web/SPA时可选模式、创建真实payload/刷新后只读，旧普通/端内现有Client不再有模式切换事件/弹窗，未选协议草稿无空值错误且不会被自动补协议；非Web/资源服务器无模式字段，390/1440布局及键盘可达。
- 相关System/API test后全仓verify、全套Admin单元/build/Cypress、相关隔离并发、Checkstyle/无fix ESLint/diff检查。保留已验证认证链；真实平台联调不在此修订内。完整新证据写verification，不能继续引用旧可切换用例通过宣称新合同绿色。

用户明确回复YES接受本修订，进入Phase B；Plan Round2/3已通过，不自批人类门。

创建后固定修订Plan审查：Round2/3，NO_FINDINGS，重复抑制0；只审本修订，未批准人工门。精确方案已可审阅，待工程师确认后同步spec并实施。

创建后固定模式接受记录：用户对创建时选择、创建成功含草稿即固定、现有模式保持及后端不可变校验的方案回复YES，2026-11-12进入实施。

创建后固定修订Phase B完成记录：创建DTO/持久化/写入前不可变校验、创建选择/详情只读、切换逻辑清理、对应测试源码和文档已实现。编译及静态检查通过，198个范围外已有改动摘要保持；未正式跑测试或更新容器。无偏离，待本轮Is the implementation complete per the plan?确认。

创建后固定修订完结记录：用户YES确认Build完成；正式Test绿色并记录新合同证据后，按已有授权仅替换System/Admin，运行检查通过。此记录不代表提交/PR/合并审批，未执行Git提交或远程动作。
