# Plan: IAM协议配置分区与接入自动适配 (from spec.md 2026-11-07)
Status: accepted

独立Plan评审累计2轮：Round1两项Important已修订，Round2 NO_FINDINGS。仅计划评审，非源码审核或测试证据，Rain已确认。

Admin固定OIDC；不同协议独立保存；CAS/SAML只预留类型化扩展点。本轮不部署、不commit/push。复用现有Java17/Spring Security原生provider、MyBatis/Flyway/Jackson、Vue3/Element Plus/Vitest/Cypress，不新增平行框架或密码学实现。根CLAUDE.md不存在，按AGENTS.md及按需规则执行。

## Files that change
以下为精确计划；存在者增量修改，不存在者新增。测试素材随契约迁移，不兼容生产旧共享列。若发现额外直接调用方，先记录路径与原因；改变设计契约则重新确认。

### 协议契约与扩展点
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ProtocolMode.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ClientCacheNames.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolSettings.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistry.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolSettings.java`

### System持久化与校验
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysApplicationProtocolConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysApplicationProtocolConfigMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2IssuerMetadataVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/OAuth2ClientConfigController.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2IssuerMetadataService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2IssuerMetadataServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2IssuerMetadataProperties.java`

### IAM协议选择与原生链路
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysApplicationProtocolConfig.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysApplicationProtocolConfigMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ApplicationProtocolResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolValidator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolCheckingTokenGenerator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolBoundAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/CompatibilityRefreshTokenAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/LocalAccessTokenIntrospector.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSource.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyService.java`

### Admin界面与示例
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ProtocolEndpoints.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCredentials.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/utils/applicationProtocolRegistry.js`
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/locales/lang/zh-cn.js`
- `macula-cloud-admin/src/locales/lang/en.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/application-protocol-registry.test.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`

### System测试
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/OAuth2ClientConfigControllerTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2IssuerMetadataServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ApplicationProtocolConfigurationMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ApplicationProtocolConfigurationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientProtocolsDraftMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/AdminClientProvisioningIT.java`

### IAM测试
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImplTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ApplicationProtocolResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolBoundAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSourceTest.java`

### 迁移与文档
- `macula-cloud-system/src/main/resources/db/migration/V17__application_protocol_configuration.sql`
- `macula-cloud-system/src/main/java/db/migration/V18__project_legacy_application_protocols.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistryTest.java`
- `macula-cloud-admin/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-system/README.md`
- `deploy/README.md`

`macula-cloud-admin/src/utils/adminAuthorizationCode.js`、CustomOidcTokenCustomizer.java、Gateway源码、既有迁移V16及以前均无计划修改；现有Admin OIDC安全流程继续回归。

## Order of work
1. 接受计划后记录Git与上述文件基线，保护前序未提交内容；核对最新迁移版本和本地依赖。读取目标模块pom/README、相邻实现及原生Security provider源码，确认构造/校验扩展接口。Stage3未配置专用reviewer时使用sdlc-reviewer Plan fallback，最多3轮，记录每轮。
2. System API定义协议代码/能力注册表与版本化类型化配置信封，仅注册OIDC/OAUTH2_1。通用契约不含必需OAuth字段，OAuth家族使用自己的配置类型。配置载荷上限32KiB、单应用最多2种当前支持协议，拒绝未知protocol/schemaVersion、类型错配、重复条目和未知配置字段；CAS/SAML不能伪造启用。前后端稳定协议代码一致，但后端注册表为权威。
3. V17追加协议子表：应用内部主键application_id+protocol唯一，schema_version、enabled、配置JSON及审计字段；有界索引，无秘密材料复制。V18追加Java迁移，以同一能力校验逻辑投影旧共享配置，严禁修改已执行脚本。单协议精确保留；双协议按能力拆分；无法合法拆分的启用应用转DRAFT，记录不含凭据的原因。旧列保留恢复依据，不作为新运行时回退；旧Secret摘要只留主表。空草稿不造假租户/Scope；迁移在隔离库证明Admin/Gateway单协议保持有效。两迁移不能在本轮自动应用到业务库。
4. ApplicationProtocolConfigurationService负责信封解析、白名单绑定、能力校验和完整配置快照。管理Form/VO使用protocolConfigurations；主记录保留默认协议、应用身份与Secret生命周期，启用列表从子记录派生，不保留多个权威来源。保存锁定主应用行再事务更新子配置/关联；并发比较完整主/子配置版本，陈旧页面拒绝，重置密钥与删除同样持有应用行锁。更新一个协议不得静默覆盖另一协议。提交后清理Client及Origin缓存；缓存升级v4。
5. 密钥仅保存一份。任一已保存协议（包含已关闭但保留的草稿）仍选择需Secret的认证方式时保留现有摘要；选择none不得删除另一协议所需摘要。无协议需密钥时提示并按既有安全生命周期清理；设置/轮换只能用现有受控入口。每个协议独立选择认证方式/客户端JWKS参数/算法，公共应用均强制none，服务应用禁OIDC。身份源、托管方式依旧应用级，但启用关联要求至少有兼容协议能力；不擅自删除来源关联。
6. IAM聚合主/子记录，未启用/非法/未知配置失败关闭。缓存只保存权威不可变配置，不能缓存请求相关effective RegisteredClient。明确分为baseRegisteredClientRepository与请求级适配仓库：MaculaOAuth2AuthorizationService恢复记录时通过显式Qualifier只读基础仓库；基础仓库不解析当前HTTP请求、不回查任何授权记录，不能委托回适配仓库。先按持久化数据恢复授权及其protocol绑定，再交给resolver构造有效视图，禁止形成findByToken→findById→findByToken递归。两种Bean的消费者显式限定，避免Primary/按类型注入误绑。ApplicationProtocolResolver以原始scope决定授权请求协议；consent POST、授权码兑换与refresh从原授权记录取绑定协议，不按当前默认值猜测。直接Token grant取OAuth；授权码/refresh无绑定或过期记录拒绝；迁移前未绑定旧code/refresh不继续兑换，要求重新授权。应用认证租户/目录快照仍核对。
7. ProtocolAwareRegisteredClientRepository在显式用途的请求级适配中构造effective RegisteredClient，保留同一id/凭据生命周期，认证方式、callback、Scope、ClientSettings、TokenSettings取当前协议。原生authorize/consent、client authentication、code exchange、refresh及扩展grant都必须消费该视图，不能仅在TokenGenerator最后校验后继续用共享TokenSettings。默认provider使用受控wrapper/适配仓库，保留原生PKCE、code一次消费、redirect匹配、签名校验和refresh原子性；不重新实现这些规则。禁止把请求视图写回主配置或跨请求共享可变状态。
8. 协议选择与密码学验证分开：客户端提供的code/state/断言仅用于查找服务端记录，不因找到记录而视为已验证；仍由原生provider验证。按绑定协议核验实际客户端认证方式、allowed grant、Scope及Origin，框架装配并集不能放行。OAuth2TokenContext、code生成和ID/opaque/JWT/refresh生成统一取有效协议策略，绑定protocol/schemaVersion并在每次签发前读取当前配置；只切默认不改变原授权。OIDC refresh缩小Scope仍保留原OIDC语义；跨协议时效/格式不能串用。
9. introspection/revocation的调用方身份与被查询Token的协议分开：Gateway按自身服务端OAuth配置认证，不拿Admin Token的OIDC配置验证Gateway。对任意单/双协议调用方，管理端点采用固定、公开的认证配置选择规则：优先使用已启用且合法机密认证的OAuth2.1配置，否则使用已启用且合法机密认证的OIDC配置；均不满足则拒绝。此选择不读取目标Token的protocol、默认协议、客户端传入的protocol声明或未验证JWT载荷；选择当前权威profile后仍由原生provider校验对应method/JWKS/算法/凭据。同方法不同JWKS不逐一试验、不自动切换，不支持在一个失败后用另一配置重试。默认协议改变不会改变该选择；启停/认证变化可能改变它，保存时明确提示并更新只读管理端点认证说明。UserInfo和OIDC退出依据已验证Token/原授权绑定处理，不按任意查询参数或默认协议选择。保持现有access claims及业务权限语义。HostedLogin/BrowserLogin读取已保存授权请求对应配置，保留F12 RequestCache修复；公共扩展和外部凭证仍绑定具体来源，不扩大授权。
10. 元数据服务按protocol分开获取/缓存与错误状态。可信issuer配置不变；从已配置可信Discovery抓取地址推导两个固定元数据路径，不接受管理请求提交任意URL。OIDC与RFC8414各自正确处理issuer路径；校验issuer、端点可信origin、大小、超时、重定向及HTTP边界。OAuth发现必须真实存在，不从OIDC文档伪造成功。IAM CORS新增OAuth元数据精确GET/OPTIONS，来源仅启用配置Origin，禁止credentials/wildcard；实际Token按选定协议Origin检验。
11. Admin注册表映射已支持协议的组件/标签；顶部默认单选只列已启用项，协议Tab各自开关及表单。无协议可启用时保存DRAFT，禁止应用ENABLED。关闭默认自动选剩余首个稳定顺序协议并提示；Tab切换不改启停/默认。各协议独立dirty状态与加载/错误/重试，关闭保留草稿，重启重验；丢失默认/不支持类型不能伪装成功。
12. OAuthProtocolSettings按能力联动：none/公共应用限制、S256锁定、OIDC必需openid/code、可选profile、纯OAuth业务Scope和专属扩展。已有不适用选择被移除时单次反馈，关键凭据变化需显示影响；后台同规则兜底。草稿可不完整但不能保留恶意类型/未知协议。ClientCredentials/应用配置/登录控制从对应协议或所有协议需求推导展示，不能继续读取旧共享grant/auth字段。
13. ProtocolEndpoints在各Tab底部折叠，实际共用端点可以相同；OAuth不显示OIDC UserInfo/退出。ClientGuide按默认/显式已启用协议生成示例；对内省/撤销额外展示由上述确定规则计算的只读managementAuthenticationProtocol及所需认证说明，不能把当前Tab的认证配置误当管理端点配置；OIDC含nonce/ID验证，OAuth不要求ID Token或偷加openid。Admin自身继续固定OIDC，不新增匿名应用配置接口，不改变adminAuthorizationCode.js。
14. 更新一次性Admin/Gateway初始化工具以写新协议对象；原ID/摘要/租户目录不变，幂等校验包含子配置，常规任务仍默认关闭。补README中能力、接口、迁移维护窗口与恢复依据。修复受契约影响的既有测试素材，不为测试给生产加旧列回退。本轮只编写迁移/工具，不切换镜像或业务库。
15. 按Proof进行开发检查并记录偏差；实现完成后按sdlc-build人工完成门禁，再正式Stage4。任何额外直接文件先记录，影响用户确认契约的问题先报告；不得自行批准/部署/提交。

## Risks
- 原生Spring Security在多个阶段读取RegisteredClient，不能只在自定义grant或最终生成器替换，否则code/refresh/客户端断言仍使用错误策略；实现必须通过真实链测试证明每条路径。
- 两个协议可选不同认证/JWKS/Token设置；全局聚合只用于装配，绝不能放宽某一协议。无绑定旧授权拒绝后需重新登录，不对旧code/refresh猜协议。
- OAuth空业务Scope目录仍无法启用授权码；可保存草稿并明确登记要求，不造Scope。公共客户端refresh仍禁止。
- V17/V18追加迁移涉及DDL和Java投影；旧双协议可能转DRAFT。正式部署前须隔离演练与影响清单、私密恢复依据、协调停旧IAM；本轮不操作业务库，不自动回滚/清库。
- 协议表不存Secret，Client ID/Secret仅OAuth家族共享；未来CAS/SAML只预留类型化注册及端点能力，当前API/UI均不接受未支持协议。
- 已有未提交改动与本地运行系统保持，不改Admin专用OIDC Token自定义逻辑，不复制/输出凭据。

## Proof
- 协议注册/载荷：API单测覆盖已知代码与版本、未知CAS/SAML、字段错配、超大JSON、无OAuth字段的通用接口；拒绝任意多态类型与未知字段。
- System单测及隔离MySQL/Redis：独立保存/重载/启停、默认联动、草稿状态、能力校验、密钥共享保留/轮换、唯一约束与审计、事务回滚、陈旧主/子快照拒绝和提交后v4缓存失效。并发更改另一协议/Secret不能覆盖；无秘密日志。
- V16→V18隔离迁移：Admin OIDC、Gateway OAuth保持，合法双协议拆分，非法双协议DRAFT与恢复依据，既有草稿不造数据；其他身份源/应用字段不误改，重新启动Flyway不重复修改。
- 真实IamProtocolTest：使用真实Redis授权持久化服务与显式基础仓库，完成authorize/consent→存储→code兑换→refresh，断言基础读取无反向调用及并发请求不串视图；禁止仅用InMemoryAuthorizationService掩盖仓库递归。另保留框架原生链测试。同一Client两个协议分别使用不同认证方法/JWKS、grant/Scope/Origin、TTL与Token格式；OIDC nonce/ID Token、纯OAuth无ID Token、正确S256及错误verifier、重复code、consent重入、grant与认证并集越权拒绝、code/refresh固定原协议、默认切换不串用、禁用/删除/缩小Scope/旧未绑定授权拒绝。
- 客户端身份边界：公共none与机密方法不能串协议；客户端断言签名仍由原生校验；Gateway机密内省Admin opaque/JWT Token正常且调用方/目标协议不混用；增加双协议调用方同为private_key_jwt但使用不同JWKS、OAuth优先/单OIDC回退/无机密配置拒绝、目标Token属于另一协议、默认切换不改变选择、关闭优先配置后的明确变更、错误公钥不得尝试另一配置的真实内省与撤销测试；UserInfo/退出只处理正确已验证绑定。扩展SMS/email/external_code、来源选择及托管登录/F12回归。
- Discovery真实Security链及服务测试：两个标准路径、带路径issuer变换、精确Origin GET/OPTIONS、实际Token按协议Origin、错误issuer/越域端点/超时/重定向/超大响应拒绝，各协议失败独立，不以OIDC自动填OAuth元数据。
- Vitest与Cypress：独立Tab值保存重载、关闭保留/重启重验、默认候选与自动切换/无启用草稿、错误/loading/重试、联动反馈、凭据跨协议影响、端点折叠与示例、390px；回归现有管理页及Admin OIDC授权回调/会话竞态/刷新/退出/F12。
- 执行现有目标Maven -am test -Plocal与隔离IT→全仓mvn test -Plocal→Admin全单测/build/Cypress→Checkstyle/无fix ESLint/diff检查；保留真实输出/跳过项与环境条件。源码mock、真实Security、隔离数据库和实际部署证据分列，不宣称CAS/SAML联调或已部署。

## Plan review corrections
- Round1 Important 1：授权恢复明确只注入无请求解析、无授权回查的基础仓库，协议视图在恢复后生成；增加真实Redis恢复链测试，禁止只依赖InMemory fixture。
- Round1 Important 2：内省/撤销以稳定的机密OAuth2.1优先、机密OIDC其次规则选调用方认证配置，与目标Token及默认协议分离；Tab展示只读说明；不同JWKS不试错回退，增加双协议/跨目标/启停/默认切换测试。该固定规则是本计划需由Rain一起审阅的具体实现选择。

## Implementation deviations
- 测试契约迁移补充精确路径：`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolTestFixtures.java`（仅测试素材投影，不供生产回退）、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`（直接调用方）、`macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`（直接保存调用方）。不改变功能范围。

- 发现直接协议读取入口：新增精确修改路径 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingService.java`、对应 `RegistrationIdentityBindingServiceTest.java` 与 `ExternalIdentityBindingServiceTest.java` 测试（均位于同名 authentication/thirdparty 测试包）。身份绑定事务继续锁主应用后锁子配置；验证码与consent在既有resolver中补显式用途，不扩大授权。

- 身份绑定隔离集成测试直接构造服务，补充精确路径 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingIT.java`，装配新增协议Mapper并使用新子配置。

- 测试素材辅助新增精确路径 `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ProtocolConfigurationTestFixtures.java`，仅在测试中将旧输入显式投影为新配置；新模型测试直接构造信封，不提供运行时兼容。

- 真实双JWKS测试发现Spring原生JwtClientAssertionDecoderFactory仅按Client ID缓存；在既有ProtocolOAuth2Configuration内增加有界策略缓存（ID/协议/JWKS/算法），每项仍由原生factory创建验证器，避免同ID跨协议或启停后继续使用错误JWKS。不改变选取规则，不实现密码学。

- 开发自查补充快照一致性：详情/列表与IAM主子读取使用同一只读快照；更新/轮换移除锁前普通读取，先取得应用当前行锁再读取子配置，避免MySQL RR旧快照混入当前版本。

## Acceptance
Rain以yes接受本实施计划及两轮评审记录，开始Phase B；不包含部署或Git提交授权。


## Phase B development evidence (2026-11-07)

实现已完成，等待Rain的Phase B人工完成确认；以下为开发检查，不代替Stage4签收，不自批门禁。

- 全仓`mvn test -Plocal`通过，最终输出`/private/tmp/adaptive-all-final.log`；涵盖真实Redis授权恢复/consent/code/refresh、双协议不同认证/TTL/格式、同Client不同Origin拒绝、双private_key_jwt JWKS管理选择及无回退。既有条件跳过保留在原始日志中，不计为覆盖。
- Admin全单测19文件/182用例通过（`adaptive-vitest-final.log`），生产构建通过（`adaptive-admin-final2-build.log`）。Cypress真实Electron+模拟API共14用例通过：应用配置10项、Admin固定OIDC授权码4项（`adaptive-browser-final.log`）。390px截图已检查，无页面横向溢出；此浏览器证据不表示第三方厂商或业务环境联调。
- 隔离MySQL V16→V18迁移通过（`adaptive-isolated-it.log`中ApplicationProtocolConfigurationMigrationIT成功；同次并发审计装配缺失后单独修正重跑），子配置并发/密钥快照/回滚通过（`adaptive-concurrency-it2.log`）。Admin/Gateway初始化与幂等、旧草稿升级分别通过（`adaptive-existing-it.log`）；同次应用并发测试装配错误已单独修正并通过（`adaptive-app-concurrency-it.log`）。未以失败整轮宣称全部通过。
- 来源绑定隔离MySQL/Redis 11用例通过（`adaptive-source-binding-it2.log`），使用明确测试端口与运行标记；上一轮缺少Redis环境参数未作为代码失败处理。
- 计划内当前存在Java文件Checkstyle通过（`adaptive-checkstyle-final2.log`）；前端无fix ESLint通过（`adaptive-eslint-final.log`）；`git diff --check`通过。
- 两个本次临时容器`macula-iam-protocol-tabs-test`、`macula-iam-protocol-tabs-redis`已清理。没有迁移业务库、重建/替换应用镜像、Git提交、推送或发布。备份位于`/private/tmp/iam-adaptive-build-baseline`，原有未提交工作保留。
- 当前只注册OIDC/OAUTH2_1。CAS/SAML只保留扩展契约，没有实现或联调承诺。正式部署前仍需独立Stage4签收及维护窗口授权。


## User-directed local image update (2026-11-07)

Rain明确要求“先更新镜像我看看”，本次授权覆盖本地System/IAM/Admin协调升级及必要的V17/V18迁移；取代本计划先前“本轮不部署”的执行限制，不表示Agent自批Stage4/PR门禁。

- 更新前私有数据库备份和旧镜像标识：`/private/tmp/macula-protocol-deploy-72lo261i`（目录0700，文件0600）。无凭据写入日志或回复。
- 仅构建并重建三个应用，先停止旧写入端，System完成V17/V18后启动IAM/Admin；Compose使用`--no-deps --no-build --force-recreate --wait`，其他应用与中间件保持运行。
- 新镜像：System `f9536908204302cf006506aaf3fafaba6cdbbb4adc486aeda71cef58c35f48bc`；IAM `49b87ea57920d3586d4b80cb620c159843afc96975cf5bad7d699a30050fa642`；Admin `142da1201aeaf9a435ace38b0660b09ea6df215cbbaa7cd213541b7ab7b6f1dd`。三容器均healthy。
- 本地检查：Admin页面200、IAM登录200、OIDC及OAuth发现200且issuer正确；Admin OIDC/code/S256请求跳转登录，System无Token接口401符合边界。与备份中主应用INSERT数据逐字比较一致，包含Secret摘要；启用应用仍2个，原有草稿3个。
- 构建首次Docker Hub令牌请求EOF，重试成功；未修改Dockerfile或认证设置绕过问题。实际构建日志`/private/tmp/adaptive-image-build2.log`，本地检查脚本`/private/tmp/adaptive-deploy-smoke.py`。
- 这是本地运行/迁移与入口验证，未宣称完整浏览器实际登录或第三方厂商联调；未Git提交、推送或远程发布。


## Local preview correction: introspection target identity

用户预览报告/users/me 500；Gateway日志证明IAM内省400。Spring原生内省provider在调用方认证后还通过findById读取目标令牌所属Client，原请求仓库错误地给公共Admin再选择机密管理协议。修复限定既有ProtocolOAuth2Configuration.java和IamProtocolTest.java：调用方认证仍用请求级视图；原生内省provider仅对目标身份/claims重建注入显式基础仓库，不改变管理认证/JWKS选择或Token有效性检查。增加公共OIDC目标+独立机密调用方真实Redis/native链回归，先复现失败再修复。属于已批准跨客户端内省要求的遗漏修正，用户报错隐含授权修复；修复后仅刷新IAM。


### Introspection correction evidence

- 先新增原生真实Redis回归：机密客户端内省公共且仅启用OIDC的目标，修复前稳定得到400（`/private/tmp/adaptive-introspection-red.log`）。
- 修复后IAM 287项测试与API模块测试通过（`/private/tmp/adaptive-introspection-green2.log`），Checkstyle通过（`/private/tmp/adaptive-introspection-style2.log`）。公共目标伪造Basic仍401；目标身份读取不改变调用方机密认证规则。
- 仅构建/重建IAM，镜像`db5f5e7bb5d2aa6639f6dabfd2b7041637c2010f62f21b57b0b53f6df4b22259`，healthy；其他应用/数据库未更改。
- 读取当前已保存Admin会话令牌，仅在本地诊断进程内使用，不输出或落盘；同一会话请求Gateway `/system/api/v1/users/me`，更新前500、更新后200。该检查直接覆盖用户截图故障，不再仅以入口200或容器healthy代替业务验证。


## Preview layout correction (user request 2026-11-07)
用户要求底部操作常驻、检查各分区拥挤布局，并询问OAuth/OIDC关系及Scope空目录。修复在已接受的满幅详情、独立草稿和响应式要求内：正文独立滚动，保存栏作为底部固定可见的兄弟区域；宽屏双列/窄屏单列，提示不挤占输入空间。额外精确路径：macula-cloud-admin/src/views/system/oauth2Client/components/ClientAccessAuthorization.vue（统一访问授权布局）。后端配置隔离和Scope目录规则不变，协议展示合并等待用户偏好。


布局检查补充精确路径：macula-cloud-admin/src/views/system/oauth2Client/components/IdentitySourcePicker.vue。弹窗统一限制视口高度、正文滚动及底部操作可见；修复直接调用方仍读取旧共享认证/grant字段导致SDK能力错误禁用的问题，改读已启用OAuth配置。此为已批准独立配置契约遗漏修正，不改变来源认证或权限。


### Preview layout evidence
管理端构建通过（adaptive-layout-dialog-build.log），全单测19文件182项通过（adaptive-layout-unit-complete.log），无fix ESLint通过（adaptive-layout-eslint-complete.log），14项Cypress通过（adaptive-layout-all-browser.log）。包括五分区桌面/390px正文滚动、操作栏上下左右边界、窄屏创建弹窗footer、空OAuth目录解释、OIDC profile可选、小程序按新OAuth配置添加SDK来源；截图与DOM几何均检查。首轮移动端被通用display:block/height:auto覆盖的问题已局部提升选择器优先级修复；范围仅本页面，不改通用布局CSS。Scope空态测试首次禁用节点选择错误后修正并通过。
用户正在本地预览的Admin镜像已单独更新，其他服务未重建，页面HTTP200；未改业务库、认证或Scope后台规则。统一OAuth/OIDC展示入口作为推荐方案已提出，当前未收到新的选项偏好，继续保留此前已确认的独立双Tab与两份配置，未擅自合并或丢弃策略。


## Local preview recovery: missing Gateway application (2026-11-07)

用户再次报告/users/me 500。Gateway同requestId日志显示内省401，与上一次目标Client误选管理协议导致的400不同。当前库查询证明Gateway client e4da4a32-592b-46f0-ae1d-784310e88423已不存在；不推断删除操作者或动机。

- 恢复前完整备份当前库：/private/tmp/macula-gateway-restore-d2cfegrm/current-before-restore.sql，目录0700、文件0600；事务脚本只从更新前备份恢复Gateway主记录并投影其OAuth协议子记录。未修改、新增或删除其他应用记录，未重建镜像。
- 原主键2空闲；恢复原Client ID、Secret摘要及所有原字段。mysqldump的TIMESTAMP签发时间按UTC会话恢复后逐字段比较；结果Gateway与原备份完全一致，其余四条现有应用记录逐字未变。
- Gateway为ENABLED/OAUTH2_1，client_secret_basic/client_secret_post。当前Admin会话真实Gateway /system/api/v1/users/me返回200。身份材料仅在私有脚本进程中读取，不输出令牌、密钥或用户信息。
- 此为本地依赖配置恢复，无源码行为变化，不以测试替代实际接口验证。平台自身依赖的Gateway接入身份必须保留，当前一般应用删除入口尚未区分平台依赖应用。


## Superseding requirement: one protocol per Client
Rain明确要求简化为一个Client只选择一个协议，移除多协议并行启用/默认协议/两套配置Tab的交互。后续统一纳入.sdlc/iam-resource-access-boundaries/intent.md；不再沿用本计划的双配置交互继续扩展。现有运行状态在新设计和实施计划接受前保持，不能只用前端隐藏绕过后台双协议状态。


## Preview token configuration visibility
Rain明确要求令牌相关配置不默认收起。现有已批准OAuthProtocolSettings.vue布局中移除令牌折叠容器，按适用条件直接显示格式、有效期、刷新策略与ID Token算法；不改接口或安全规则。单协议和OAuth profile的服务端行为变更统一纳入新的意图草案，不能只改前端绕过校验。

令牌配置直接展示的开发检查：管理端构建通过（iam-token-visible-build.log），182项单测通过（iam-token-visible-unit.log），14项浏览器回归通过（iam-token-visible-browser.log），无fix ESLint与git diff --check通过。本轮仅先完成既有批准布局中的展开调整；当前镜像未更新，新单协议及OAuth profile后端行为待新意图/设计/计划接受。
