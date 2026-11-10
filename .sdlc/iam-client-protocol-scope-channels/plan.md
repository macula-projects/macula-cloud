# Plan: IAM应用配置交互与多协议启用 (from spec.md 2026-11-06)
Status: accepted

沿用Java17/Spring Security/MyBatis-Plus/Flyway/Redis和Vue3/Element Plus，无新增框架。上一轮已接受计划保存在plan.accepted-unified-binding-20261106.md；本计划只新增本轮范围，不授权覆盖既有未提交工作。规格已由Rain以YES接受；独立Plan评审共2轮，1项Important已修正，第2轮NO_FINDINGS。Rain已以YES接受实施计划，开始实施。

## Files that change
路径相对仓库根目录；新增文件在不存在时创建，已存在文件仅按本轮职责增量修改。发现其他直接调用方先记录具体路径与原因，不用旧计划覆盖本轮审查范围。

### 模型、草稿与持久化
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientConfigurationStatus.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ProtocolMode.java`
- `macula-cloud-system/src/main/resources/db/migration/V16__oauth_client_protocols_and_drafts.sql`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientDraftForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2ClientConfigService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/OAuth2ClientConfigController.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2ClientConfigurationCatalogService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`

### IAM启用状态与协议边界
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolValidator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolCheckingTokenGenerator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOidcTokenCustomizer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`

### 管理端交互
- `macula-cloud-admin/src/views/system/identityProvider/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCredentials.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientAccessAuthorization.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/IdentitySourcePicker.vue`
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`

### 验证与文档
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/OAuth2ClientConfigControllerTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/AdminSettingsAuthorizationTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/vo/SettingsModelJsonTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientProtocolsDraftMigrationIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginPolicyFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/userdetails/ClientAwareAccountResolverTest.java`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`
- `macula-cloud-admin/README.md`
- `macula-cloud-system/README.md`
- `macula-cloud-iam/README.md`

- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ProtocolIdentitySourceMigrationIT.java`（原V15迁移测试固定target=15，避免追加V16后改测别的升级路径）

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImplTest.java`（权威查询与Origin索引测试补显式启用状态及v3键素材）

## Order of work
1. 先保存工作区基线，阅读目标POM/README/测试和适用规则，确认V15已执行。只追加V16：新增enabled_protocols、default_protocol、configuration_status；状态用明确枚举DRAFT/ENABLED，新增列默认DRAFT，旧protocol_mode保留但新运行时不据此推断启用集合。旧Client不自动启用、不猜默认协议。为真实草稿允许tenant_id/user_type为NULL，不造租户或目录默认值；认证必需字段在启用校验中仍强制非空。其余现有NOT NULL字符串草稿以空值保存无授权能力，不造假回调/Scope/Secret。
2. 新增POST /api/v1/oauth2-clients/drafts及独立DraftForm，仅接受名称、应用类型、小程序平台，服务端生成UUID Client ID。创建无Token能力的草稿、无Secret和来源关联，返回id进入详情；新路径复用现有管理权限/审计，非匿名入口。完整PUT使用OAuth2ClientForm，显式携带配置状态、启用协议集合与默认协议；草稿保存允许未填认证字段，但有值即校验格式/目录/来源，不允许越界数据。开启ENABLED执行全部现有类型/租户/目录/Scope/认证/回调/来源校验。不提供绕过完整校验的独立开关。机密客户端首次完整配置沿用现有生成/一次展示Secret流程；来源与凭据生命周期不变。
3. System集合校验非空/无未知值，defaultProtocol属于集合；SERVICE、MINIAPP等不适用OIDC的类型只开放OAuth2.1。共享Client ID/客户端认证、grant、回调、Token参数；Scope目录按启用协议并集过滤，openid/profile仅在OIDC启用且授权码适用时提供。OIDC启用必须配置openid和授权码，纯OAuth不能含OIDC Scope；grant/来源约束不放宽。client_credentials及直接Token的sms/email/external_code扩展仅属于OAuth2.1；只启用OIDC时不可配置这些grant或添加其SDK能力，托管页短信经授权码登录仍可使用OIDC。双启用可同时提供两类入口；关闭OAuth2.1时若仍存在这些grant/来源，明确报错并要求先移除，不静默修改。System校验、协议选项、来源添加器和示例生成采用同一能力矩阵；扩展Token请求禁止openid/profile，不因共用Scope并集传入OIDC范围。将状态/集合/默认协议纳入现有完整快照CAS；并发保存、启用、禁用、重置Secret不可相互覆盖。保存失败回滚来源写入，事务提交后才驱逐缓存。
4. IAM读取客户端的统一服务默认只返回ENABLED且新模型有效的Client，草稿/未配置记录不能进入RegisteredClient、托管登录、第三方/SDK核验、账号解析或自动/手动绑定；本地无Client的平台登录保持既有规则。RegisteredClient缓存换v3命名空间，保存仍驱逐整组缓存，避免旧v2缓存绕过状态。共享OAuth2ClientProtocolPolicy定义集合、默认及请求协议判定，不散落字符串。
5. /authorize保留框架回调/Scope校验与S256，按显式openid判定OIDC，否则OAuth2.1；拒绝未启用协议，禁止Scope省略触发框架默认全Scope而隐式补openid（缺失时返回invalid_scope并在示例中显式给Scope）。defaultProtocol不覆盖请求语义，不替请求补openid/profile。默认仅选择快速开始示例/生成入口。声明的Scope仍需在目录内。
6. 在现有TokenGenerator外加ProtocolCheckingTokenGenerator，覆盖JWT/opaque/ID/refresh全部生成路径；在授权码兑换、刷新、client_credentials和所有扩展grant发Token前按当前数据库状态/启用协议重新校验，不能只信保存于授权码或RegisteredClient快照中的旧状态。以原授权Scope（刷新取原authorization而非本次缩小后的Scope）判定协议；ID Token必须为OIDC并已授权openid，client_credentials必须为OAuth2.1，SDK/短信等按其明确Scope判定且保留Macula扩展标识。无效/草稿/已禁用协议拒绝且不持久化Token。历史code、refresh在禁用后不能继续换Token；已签发access token按既有到期/撤销策略处理，本轮不承诺立即撤销存量access token。UserInfo原有openid授权门槛保留，纯OAuth访问拒绝。两个Customizer仅保持正确授权的claims，保留用户正在编辑的内容，不能机械覆盖。
7. Admin列表创建按钮打开ClientCreate对话框，桌面左类型卡片右名称、小程序附平台单选，窄屏上下排列，无认证域名及其他接入配置。取消不发请求，重复提交保护，成功跳详情/快速开始；展示“未完成配置，暂不可登录”，保存草稿与“保存并启用”明确区分。类型切换继续清空专有字段，保存后只读。完整配置由前端即时校验及服务端共同守卫，不为简化创建填假值。
8. 满幅详情固定五页签：快速开始、应用配置、协议配置、登录控制、访问授权。ClientApplication保留基本信息、凭据、回调和Origin，移走平台端点及用户类型；ClientAccessAuthorization只放用户类型，认证租户保留在应用配置，用户类型修改触发原有认证范围检查。ClientGuide作为快速开始内容，按默认/所选已启用协议及真实配置生成示例；草稿缺条件给出明确待配置提示，不生成看似可用的假示例。
9. ClientProtocol启用协议用勾选，默认协议单选，仅可在已启用项中选择；关闭默认前要求先切换，不静默改默认。共享配置单独说明，客户端认证/签名算法/Token格式等固定小集合用单选；Scope目录多选保留。客户端JWKS URL在对应private_key_jwt配置内。平台端点移到协议页最底部默认收起，按协议显示真实元数据，保留失败/重试/复制与长链接换行，不提供高级配置页。
10. 身份源管理的厂商和具体类型改单选，沿用目录过滤、切换清空、保存后类型/身份域只读，Secret仍可轮换。应用登录控制用已关联列表＋添加按钮；IdentitySourcePicker对话框统一支持网页/SDK/小程序、搜索、兼容性过滤和显式能力选择，选择后暂存草稿，取消无修改。只添加明确勾选的能力。每浏览器厂商/每SDK能力固定一个registration，冲突时需明确确认更换，不静默覆盖；去重。同一来源有多能力时可分选，不默认全开。
11. 已添加来源支持移除，移除默认直登源清空直登；所有关联删空时对应grant只在无其他依赖时清理，不能误关仍有本地登录或其他来源依赖的授权码。目录刷新保留已选失效项并标注，阻止启用/保存无效关联，不静默删除，也不自动选唯一候选恢复已移除的绑定。全部厂商/来源只在添加器中展示；没有兼容来源时显示创建身份源入口。保留分区草稿、离开提醒、目录失败重试、Secret临时显示销毁。
12. 更新三模块README及测试fixtures为显式新字段；保留上一轮绑定、加密、缓存与厂商边界回归。运行开发期最小检查并记录实际输出，正式Stage4在Build人工确认后执行。无Git提交/推送，无容器或业务库更新；后续部署须协调新IAM与草稿模型及管理端登录的既有阻塞，不沿用旧预览混跑作为新功能运行证据。

## Risks
- 这是V15后的新模型，不自动迁移旧协议或启用状态；上线前需明确配置管理端Client。既有管理端仍用password，完整新IAM上线前需独立适配授权码；本计划不夹带该工作。部署前旧镜像不能继续承担新草稿状态的安全校验。
- 配置启停和Token签发存在并发窗口，必须在发Token时读取当前配置且失败关闭，不能只做页面或authorize校验；既有access token保留到期/撤销语义，避免暗示禁用会立即撤销全部存量Token。
- 允许草稿tenant/user为空仅用于未启用记录，不能进入用户认证范围或绑定。创建接口不能接受任意clientId、状态、租户、Secret或grant越权启用。
- UI已有大量未提交改动，逐项修改并保存前后基线；新来源添加器不能隐式更换registration或丢失失效关联。只关联元数据，不把Secret/核验结果放前端。
- CustomOidcTokenCustomizer.java为用户明确引用且可能正在编辑的文件；读取当前内容并作最小语义变更，不因规范检查覆盖用户改动。

## Proof
- System/API：独立草稿Form白名单与鉴权；服务端ID生成、默认DRAFT、缺租户目录/回调/Scope草稿可存但不可启用；完整启用成功及非法集合/默认/类型/Scope组合拒绝；OIDC-only禁止client_credentials及sms/email/external_code，双启用允许，仍有纯OAuth grant/来源时关闭OAuth2.1拒绝；Secrets一次生命周期、CAS和事务副作用；JSON模型与缓存驱逐回归。
- IAM真实Security链：DRAFT/未知状态/缺新字段从authorize、所有Token grant、托管、SDK/第三方及绑定拒绝；OIDC/OAuth2.1单启用与双启用、默认变化不影响其他协议、禁用拒绝；显式Scope、无Scope不补openid、S256/default redirect/scope校验；纯OAuth无ID Token和UserInfo；JWT/opaque、授权码/refresh/SDK在配置禁用后不能用旧快照签发Token，refresh缩小Scope仍不能转换原协议；client_credentials仅OAuth。保留旧claims行为和所有绑定隔离回归。
- 隔离MySQL：V15→V16保留Client字段/密钥/来源绑定，新状态不猜测启用；nullable字段仅草稿使用，完整保存及启停CAS与Secret轮换竞争；SettingsPersistenceIT加密/缓存、IdentitySourceBindingIT全部继续。真实Redis与Mock权限范围分开报告，不用迁移空值推断为用户同意。
- Admin Vitest/Cypress：创建对话框结构、取消/重复提交/类型切换/草稿和启用；五页签、无高级区、用户类型位置、协议多启用及默认限制、端点默认收起/展开/复制/失败；身份源单选、添加器搜索/过滤/显式能力/取消/更换/删除/失效保留/直登清空、不全量铺开。桌面满幅与390px、草稿离开保护和Secret销毁回归。
- 命令：目标Maven -am test -Plocal、Admin Vitest/build、定向Cypress、相关Java Checkstyle/无fix ESLint/git diff --check；正式Stage4再按风险扩大全仓及已有IT。只报告实际执行证据，厂商/真机仍另行联调。

## Acceptance
Rain于2026-11-06以YES接受本轮实施计划。

## Implementation refinements
- V16追加后，既有V15迁移测试必须固定Flyway target=15，新增V16测试单独验证后续升级。原绑定IT新模型素材随V16显式启用，不新增历史回退。


## 本轮实施结果与开发检查（2026-11-06）
- 已实现V16、多协议集合/默认协议、草稿创建及启用完整校验；IAM当前配置检查覆盖授权码兑换/refresh和所有生成器格式，RegisteredClient/Origin索引v3。既有CustomOidcTokenCustomizer/CustomOAuth2TokenCustomizer的用户改动保留，协议签发边界在生成器外层统一实现，无需重写claims逻辑。
- 已实现创建对话框、五页签、固定小集合单选、默认折叠端点、访问授权用户类型、身份源单选与按需添加选择器。保留来源失效标记，显式能力选择/更换确认/移除直登；创建后路由守卫已回归。
- 目录接口现有allowedScopes按单协议可复用，System在配置校验中求启用协议并集，未扩大目录公共接口；HostedLoginClientPolicyService等统一调用已过滤的SysOAuth2ClientService，无需重复新增状态回退。计划中未实际需要修改的文件保持原样。
- 后端开发期测试：API 2项、IAM 276项、System 158项，共436项，435通过、1项既有环境门控跳过；最终日志/private/tmp/iam-multi-test7.log。真实Security链含旧code禁用、原OIDC refresh缩小Scope、显式Scope及原有JWT/opaque回归。
- Admin：15个测试文件/130项单测通过，构建通过；相关Cypress 24项通过，覆盖创建对话框、五页签、协议默认/启停、端点折叠、来源添加/取消/移除及1920px/390px DOM边界。接口Mock，不代表厂商或真实后端浏览器端到端联调。
- 独立临时MySQL8.4/Redis7.4：V15→V16/真实草稿创建1项、绑定/票据/验证码11项、设置加密与缓存6项、Client并发1项，共19项通过；不访问业务库。权限范围与短信投递仍为Mock，不能当真实供应商权限验证。
- 136个相关Java文件定向Checkstyle零违规，前端ESLint零错误（存在现有/新增的Prettier排版warning，未批量自动格式化），git diff --check通过。
- 本轮未更新业务容器或业务库、未Git提交/推送。正式Stage4及全仓/剩余门控IT待Build人工确认后执行；本段不构成Stage4或PR审批。
