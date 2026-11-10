# Plan: IAM单协议接入、Scope与资源访问边界优化 (from spec.md 2026-11-07)
Status: accepted

依据：已接受的 intent.md/spec.md；沿用现有 Java 17、Spring Security、MyBatis-Plus、Flyway、Redis、Vue 3/Element Plus，不增加认证框架。Rain 已确认功能、迁移影响和内省身份内部能力标记方案。本计划已由 Rain 接受，不包含 Git 操作或部署授权。

## 设计澄清（Rain 已确认）
内省调用者只能看到其获准资源。超出许可返回 active:false，网关无法证明该 Token 有效，因此对外返回 401；只有已成功内省且不满足路由资源/Scope 的 Token 返回 403。IAM 不可用或调用凭据配置错误仍是 503。此项明确收窄已接受 spec 中“有效但错误资源一律 403”的表述，不扩大 Gateway 内省许可，也不泄露越权 Token 资料；确认后同步 spec。

## Files that change
以下按目录列出文件名；每项等于目录与文件名拼接后的确切路径，不代表整个目录可修改。未列文件先记录偏差，影响契约时重新审核。测试文件允许在对应既有测试中调整本次变化的契约，不重写其他功能。

### Cloud：公共契约
目录 macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/
- protocol/ApplicationProtocolConfiguration.java、protocol/ApplicationProtocolRegistry.java、protocol/OAuth2ProtocolConfiguration.java、protocol/OAuth2ProtocolSettings.java
- enums/OAuth2ClientPurpose.java（新增）、security/OAuth2ClientCacheNames.java
- security/OAuth2ResourcePolicy.java、security/OAuth2ResourceDefinition.java、security/OAuth2ScopeDefinition.java（新增）

### Cloud：System
目录 macula-cloud-system/src/main/java/dev/macula/cloud/system/
- pojo/entity/SysOAuth2ClientConfig.java、pojo/entity/SysApplicationProtocolConfig.java
- form/OAuth2ClientForm.java、form/OAuth2ClientDraftForm.java、vo/OAuth2ClientVO.java、vo/OAuth2ClientConfigurationCatalogVO.java
- service/OAuth2ClientConfigService.java、service/OAuth2ClientConfigurationCatalogService.java
- service/impl/OAuth2ClientConfigServiceImpl.java、service/impl/ApplicationProtocolConfigurationService.java、service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java、service/impl/AdminClientProvisioningService.java
- config/OAuth2ScopeCatalogProperties.java、config/OAuth2ScopeCatalogConfiguration.java、config/AdminClientProvisioningProperties.java、config/AdminClientProvisioningRunner.java
- config/PlatformOAuthClientProperties.java、service/impl/PlatformOAuthClientProvisioningService.java（新增）
- controller/OAuth2ClientConfigController.java
- mapper/SysOAuth2ClientConfigMapper.java、mapper/SysApplicationProtocolConfigMapper.java
- config/SystemResourceServerConfiguration.java（新增）
其他 System 路径：
- macula-cloud-system/src/main/java/db/migration/V19__single_protocol_resource_boundaries.java（新增）
- macula-cloud-system/src/main/resources/application.yml
- macula-cloud-system/README.md

### Cloud：IAM
目录 macula-cloud-iam/src/main/java/dev/macula/cloud/iam/
- pojo/entity/SysOAuth2Client.java、pojo/entity/SysApplicationProtocolConfig.java
- mapper/SysOAuth2ClientMapper.java、mapper/SysApplicationProtocolConfigMapper.java
- service/support/impl/SysOAuth2ClientServiceImpl.java
- service/oauth2/MaculaRegisteredClientRepository.java、service/oauth2/MaculaOAuth2AuthorizationService.java
- protocol/oauth2/ApplicationProtocolResolver.java、protocol/oauth2/ProtocolAwareRegisteredClientRepository.java
- protocol/oauth2/ProtocolBoundAuthenticationProvider.java、protocol/oauth2/ProtocolCheckingTokenGenerator.java
- protocol/oauth2/CustomOAuth2TokenCustomizer.java、protocol/oauth2/CustomOidcTokenCustomizer.java、protocol/oauth2/LocalAccessTokenIntrospector.java
- protocol/oauth2/OAuth2ClientProtocolPolicy.java、protocol/oauth2/OAuth2ClientProtocolValidator.java
- protocol/oauth2/ResourceTokenIntrospectionAuthenticationProvider.java、protocol/oauth2/ResourceAuthorizationPolicy.java（新增）
- protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuer.java
- controller/ProfileController.java、service/profile/ProfileService.java、vo/ProfileVO.java（新增）
- config/ProtocolOAuth2Configuration.java
- authentication/thirdparty/RegistrationIdentityBindingService.java、authentication/thirdparty/ExternalIdentityBindingService.java（仅适配单协议契约）
其他 IAM 路径：macula-cloud-iam/src/main/resources/application.yml、macula-cloud-iam/README.md。

### Cloud：Admin 与部署契约
目录 macula-cloud-admin/
- src/views/system/oauth2Client/index.vue
- src/views/system/oauth2Client/components/ClientCreate.vue、ClientApplication.vue、ClientProtocol.vue、OAuthProtocolSettings.vue、ProtocolEndpoints.vue、ClientGuide.vue、ClientLoginControl.vue、ClientAccessAuthorization.vue、ClientCredentials.vue、IdentitySourcePicker.vue
- src/views/system/oauth2Client/components/ResourceServerSettings.vue（新增）
- src/utils/applicationProtocolRegistry.js、src/utils/oauthClientConfiguration.js、src/utils/adminAuthorizationCode.js、src/utils/request.js
- src/api/model/system/oauth2Client.js、src/locales/lang/zh-cn.js、src/locales/lang/en.js、README.md
其他 Cloud 路径：
- macula-cloud-gateway/src/main/resources/application.yml、macula-cloud-gateway/README.md
- deploy/docker-compose.yml、deploy/.env.example、deploy/README.md（仅新增配置传递和升级说明，不操作容器）

### Boot：Gateway 与 Servlet 安全能力
共享常量：macula-boot-commons/src/main/java/dev/macula/boot/constants/SecurityConstants.java，增加经过签名验证后使用的转发来源 claim 与固定来源值；不引入 Servlet 对 Gateway 模块的依赖。
目录 macula-boot-starters/macula-boot-starter-cloud/macula-boot-starter-cloud-gateway/
- src/main/java/dev/macula/boot/starter/cloud/gateway/security/ResourceServerConfiguration.java、ResourceServerAuthorizationManager.java
- src/main/java/dev/macula/boot/starter/cloud/gateway/security/OAuthResourceAccessPolicy.java（新增）
- src/main/java/dev/macula/boot/starter/cloud/gateway/filter/AddJwtGlobalFilter.java
- README.md
目录 macula-boot-starters/macula-boot-starter-security/
- src/main/java/dev/macula/boot/starter/security/config/ResourceServerConfiguration.java、SecurityProperties.java
- src/main/java/dev/macula/boot/starter/security/authorization/ResourceAccessPolicy.java（新增）
- src/main/java/dev/macula/boot/starter/security/authentication/ResourceTokenAuthenticationManagerResolver.java（新增）
- README.md
新增能力接入现有配置类，不新增自动配置入口；检查 imports 无需调整后记录。保持 Boot 未启用新能力时的默认行为。

### 测试文件
Cloud API 目录 macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/：ApplicationProtocolRegistryTest.java、OAuth2ResourcePolicyTest.java（新增）。
System 目录 macula-cloud-system/src/test/java/dev/macula/cloud/system/：
- service/impl/OAuth2ClientConfigServiceImplTest.java、ApplicationProtocolConfigurationServiceTest.java、OAuth2ClientConfigurationCatalogServiceImplTest.java、AdminClientProvisioningServiceTest.java、ProtocolConfigurationTestFixtures.java
- controller/OAuth2ClientConfigControllerTest.java
- integration/SingleProtocolResourceMigrationIT.java、integration/ResourceClientConcurrencyIT.java、config/SystemResourceBoundaryTest.java（新增）
IAM 目录 macula-cloud-iam/src/test/java/dev/macula/cloud/iam/：
- protocol/oauth2/ApplicationProtocolResolverTest.java、ProtocolAwareRegisteredClientRepositoryTest.java、ProtocolBoundAuthenticationProviderTest.java、OAuth2ClientProtocolPolicyTest.java、CustomOAuth2TokenCustomizerTest.java、ProtocolTestFixtures.java
- service/oauth2/MaculaOAuth2AuthorizationServiceTest.java、MaculaRegisteredClientRepositoryTest.java
- service/support/impl/SysOAuth2ClientServiceImplTest.java、config/IamProtocolTest.java
- protocol/oauth2/ResourceAuthorizationIntegrationTest.java、controller/ProfileControllerTest.java（新增）
Admin 目录 macula-cloud-admin/：
- tests/unit/application-protocol-registry.test.js、oauth-client-configuration.test.js、oauth-client-details.test.js
- tests/unit/resource-client-settings.test.js（新增）、cypress/e2e/oauth-client-configuration.cy.js
Boot Gateway 目录上述模块 src/test/java/dev/macula/boot/starter/cloud/gateway/：
- security/ResourceServerAuthorizationManagerTest.java、security/ApiKeySecurityChainTest.java
- security/OAuthResourceBoundaryTest.java、filter/OAuthBearerRelayTest.java（新增）
Boot Security 目录上述模块 src/test/java/dev/macula/boot/starter/security/test/：
- SecurityAutoConfigurationTest.java、ResourceAccessPolicyTest.java、ResourceTokenAuthenticationManagerResolverTest.java（后两项新增）

协作工件：Cloud .sdlc/iam-resource-access-boundaries/ 下 intent.md、spec.md、plan.md、review-findings.md；Boot 对应 .sdlc/iam-resource-access-boundaries/ 下同步引用同一批准版本的 intent.md、spec.md、plan.md，防止跨仓源码写入脱离已接受计划。不得自行接受新的范围偏差。

## Order of work
1. 保存两仓已有 diff 与未跟踪文件清单作为基线，仅读取相关 POM、README、配置、相邻实现和测试。核对当前数据库版本、配置来源和安全链，不读取或打印凭据。识别必要的 Mapper XML/Converter 等遗漏，补充计划后再实施。源码实施前不连接业务数据库执行迁移。
2. 公共契约先行：单 protocol/settings、用途枚举、资源/Scope 定义及授权版本。沿用现有配置目录作为受信任的资源/Scope 注册来源；许可存储在 Client 的显式资源/Scope/内省许可字段，普通配置写入只能选择目录允许项。固定目录首批仅 System 与 Profile，不建立动态目录 CRUD。服务器校验租户与操作权限，不把目录等同于自动授予。
3. System 保存与迁移：事务锁、ETag、用途不可变、managedKey 防伪、平台身份防删除/停用、配置变更版本递增。新 V19 将双协议对象置为待配置并移除歧义配置，只有管理员显式选择后才启用；保留主键/client_id/Secret 哈希。Admin/Gateway 由服务器可信配置显式认领并幂等初始化；冲突或配置缺失失败，不按名称猜测。Admin 保持 OIDC，Gateway 没有业务 grant。迁移与恢复先在隔离数据库验证。V18 源码保持原样，保留其调用的公共旧校验契约；新单协议限制在新运行时入口执行，避免历史迁移随共享类变化而改变行为。
4. IAM 有效授权：基础仓库只恢复身份；请求仓库读取单协议及用途，禁止旧多配置回退。复用原生客户端认证及密码编码；资源服务器内部能力标记不可由任何 Provider 签发。授权、Token 和所有扩展 grant 共用用途拒绝与版本校验。保存实际 Scope/aud，省略 Scope 仅取显式默认值；client_credentials 不能得到用户 profile。JWT/opaque 同源构造声明，ID Token 的 aud 仍是 client_id。协议/权限变化和 Secret 重置使旧 code/refresh/access 失效，刷新不得扩权；保留原生 PKCE、签名、code/refresh 原子消费。
5. 内省与资料：原生客户端认证之后验证 RESOURCE_SERVER 用途及资源许可；目标 Client 用基础仓库恢复，仅 Access Token 可内省。无效或越出资源许可 active:false；无效调用凭据 invalid_client。新增 Profile API，基于已验证授权和用户租户返回三项字段，复用最小资料映射；无用户或缺 profile/aud 拒绝。不能返回完整 users/me 或其他内部鉴权声明。
6. Boot/Gateway/System 联动：为 Cloud System 的 OAuth 流启用原始 Bearer 转发，Gateway 不再把该 OAuth Token 替换为 30 天内部 JWT；清理 Query 凭据并规范为 Authorization Header。API Key/HMAC 继续原有可信 principal→内部 JWT 流，不改验签/准入语义。Gateway 为 OAuth 逐请求非阻塞内省，关闭其正向缓存，先做路由资源/Scope 准入，再走角色/租户授权。Boot Servlet 提供可选、可覆盖的认证/资源策略：System 对 IAM 的原始 opaque/JWT Access Token 都做内省并检查资源、Scope、版本和租户；原有 Gateway JWT 只允许经过签名验证并带受信任 API Key/HMAC 来源的既有分支，旧 OAuth 换发 JWT 必须拒绝。来源标记由 AddJwtGlobalFilter 从验证后的 exchange/principal 确定，使用 commons 中共享的保留 claim；覆盖或拒绝原始属性中的冲突值，不能采信请求 Header 或未验证 JWT。Servlet 端先验证 Gateway 专用签名及固定 issuer，再按签名内的保留来源区分 API Key/HMAC；未知、缺失或 OAuth 来源不能进入该分支。System 内省与 Gateway 共享同一受信任的资源服务器凭据身份，不额外猜测创建平台 Client；内部 Feign 如需适配，先定位既有凭据传递，不加匿名回退。缺失边界会阻止组合验证通过。
7. 错误和接入配置：按上文待确认澄清执行 401/403/503 分类，Admin 不因依赖故障清会话或循环跳转；OAuth 端点保持协议错误。Admin OIDC 请求显式携带已授权的 openid/profile/system:access，协议切换示例从服务器目录生成。配置通过环境变量传递，不落真实 Secret。启动顺序、可信身份绑定与 Boot 本地依赖验证步骤写入 README。
8. 管理端改为单协议单选，移除默认协议/双配置数组/启用开关；新建对话框、终端切换清空和编辑锁定、协议切换确认、资源服务器用途独立表单。Scope 添加选择且按实际能力过滤；五区布局、可见页脚、紧凑地址、令牌设置展开、端点折叠、身份源添加选择统一整理。private_key_jwt 保持 JWKS URL。
9. 完成针对性单元、安全链、真实 MySQL/Redis 隔离集成、前端行为及组合验证；逐项对应 spec R1–R10。只记录实际运行证据，不以 Mock 代替真实签发/内省/直连验证。实现阶段可做开发检查；正式 Stage 4 结论另行出具。

## Risks
- Rain 已接受旧会话重新登录、双协议应用待配置及显式资源许可切换；不修改已执行迁移。V19 不自动批量给其他应用授予 System 权限。恢复须配套数据库备份和服务版本，不能只回退镜像。
- 网关原始 Bearer 转发与 System 接收必须一起验证和部署；Boot 默认不启用新模式，Cloud 显式开启。不开启、缺凭据、部分更新时不放宽校验。部署本身仍需另行授权。
- 网关换发 JWT 的旧缓存不能成为 OAuth 绕过；API Key/HMAC 的已验证来源与历史行为通过回归隔离，不能仅凭 JWT 自报来源放行。签名、issuer、用途同时核验。
- Gateway/System 双内省增加 IAM 压力，组合验证记录延迟、超时与故障分类；资源许可和授权版本变更必须实时检查，不能用旧缓存回退。
- 平台身份及明文 Secret 只来源于运行配置；迁移不能从哈希恢复明文，也不能用示例值覆盖。已有身份认领失败停止并给出安全诊断。
- 不改 Github 或其他厂商认证；第三方绑定只适配单协议容器。CAS/SAML 不实现；API Key/HMAC 不适用 OAuth Scope。内部 Feign 若发现契约缺口，记录明确文件和方案后补充审核，不借泛化文件列表扩大范围。
- Cloud 工作区已有大量改动；新变更仅叠加目标行为，保留原始基线。Boot 本地依赖如需安装，仅安装相关模块到本地 Maven，不发布远端；最终组合测试说明使用的实际依赖。

## Proof
- R1/R2：注册与保存测试拒绝多配置；Cypress 验证终端切换清空、编辑锁定、协议切换取消/确认；配置保存后旧 code/refresh/access 的真实拒绝测试。
- R3/R4：1440×900、390×844 五区/弹窗检查，底部按钮可见、无横向溢出、令牌直接展开、协议特有项与端点默认折叠；覆盖 loading/失败/空态/重复提交。
- R5/R6：目录、校验与实际 Profile 端点覆盖 OAuth profile、OIDC UserInfo、字段白名单、缺 Scope/aud、跨租户、服务令牌、撤销与过期。纯 OAuth 不生成 ID Token、不接受 openid。
- R7：真实 code 与 refresh 流验证 Scope/aud 不扩权、JWT/opaque 内省一致、配置/密钥变更失效；Gateway 重写前路径、root/onlyAuth 不绕过；直连 System 错误资源拒绝，旧 30 天 OAuth 内部 JWT 拒绝。
- 真实数据库迁移必须覆盖“空库按全部历史迁移至 V19”和“既有 V18 升级 V19”两条路径，保留平台身份及哈希，确保公共类收紧未破坏 V18。
- R8/R9：资源服务器所有标准/自定义 grant 均拒绝；非法认证拒绝，合法跨 Client 内省通过、越权内省 active:false、非 Access Token 拒绝；防伪造 managedKey、误删/停用、并发保存/重置/迁移回滚。
- 来源隔离：验证 API Key/HMAC 经过签名的来源标记、冲突属性处理、伪造/缺失来源及错误 issuer；API Key/HMAC 旧回归必须保留，旧 OAuth 内部 JWT 不可冒充。
- R10：组合环境 Admin OIDC 登录、用户资料、菜单及 System API；System/网关内省故障分别验证 401/403/503；包括 profile-only Token 向 System 请求时越出 Gateway 内省许可→active:false→401、成功内省但缺路由 Scope→403；前端不循环登录；API Key/HMAC 既有回归通过。
- 开发检查：Cloud 最小 API/System/IAM Maven 单测，Boot 两个目标 Starter 的 -am test；受影响跨模块扩大验证。独立 MySQL/Redis 执行指定 *IT，明确条件与清理范围。相关 Vitest、Cypress、无修复 ESLint、前端生产构建、变更 Java Checkstyle、git diff --check。不使用 npm run lint 自动改写无关内容。
- 交付记录明确每条实际命令、结果、未运行项、真实/Mock 边界及部署未执行；不输出 Token/Secret，不保存生成物到 Git。

## Plan review
REVIEW.md 未指定 Stage 3 reviewer，默认 codex-plan-review 未安装，按 sdlc-build 要求回退 sdlc-reviewer 子代理，仅 Plan pass，最多三轮；审核结果记录在 review-findings.md；round 1 的 1 Important 和 2 Minor 已在本计划修订，round 2 仅审查这些变化。人类批准前不改源码。

## Acceptance
Rain 以 YES 接受本实施计划、错误分类澄清及独立审核结果，开始 Phase B；不含提交、推送和部署授权。

## Implementation deviations
- 内省安全投影：自动审批拒绝“任一aud匹配即返回完整claims”的方案，未执行该操作。实现保留 canIntrospect 的全部覆盖约束，先生成调用者许可内的资源投影，再只返回投影aud/scope与白名单字段；System调用者不会获得Profile字段或Profile Scope。单一Profile令牌仍active:false。此调整不扩大内省许可，需验证多资源Token的跨资源资料不可见。

- 文件补充：IAM config/ProtocolLoginCorsConfigurationSource.java 为新增 Profile 端点提供精确 Origin 的 GET/Authorization CORS，禁止Cookie与其他请求头，属于已批准资料接口的浏览器接入合同。

- 文件补充：System integration/SettingsPersistenceIT.java 的单个客户端删除调用同步携带 configurationVersion；密钥重置与删除接口按已批准设计统一执行完整配置快照校验。

- 文件补充：System controller/AdminSettingsAuthorizationTest.java 的管理删除路由验证同步 configurationVersion 参数；原有权限边界不变。

- System直连角色边界：Boot ResourceAccessPolicy 增加可覆盖的请求级判定，Cloud System 复用现有URL角色缓存和可信网关路径前缀；原始OAuth直连不能绕过已登记的网关管理角色，编码路径按相同解码语义匹配。

- 验证补充：新增 service/impl/PlatformOAuthClientProvisioningServiceTest.java，验证显式平台身份认领保留 ID/哈希、提交后清缓存及重复执行不增加授权版本；不扩大初始化范围。

## Phase B implementation record — 2026-11-08

状态：Rain已明确确认实现完整；Stage 4最终结果以verification.md为准，未部署。

完成：业务Client单协议；按用途分离内省身份；实际Scope目录与OAuth Profile；授权版本绑定及旧code/refresh/access失效；受管身份保护及快照并发保护；Gateway原始OAuth转发和System独立aud/Scope/租户/URL角色校验；API Key/HMAC签名来源隔离；401/403/503分类及Admin的503会话保留。管理端的配置布局、自动client_id、协议与终端切换、展开令牌配置、收起端点及身份源选择器已实现。private_key_jwt保持JWKS URL。

开发验证（实际执行，不代替Stage 4和部署后的组合环境验收）：
- Boot：目标Security/Gateway及依赖 `mvn -pl macula-boot-starters/macula-boot-starter-security,macula-boot-starters/macula-boot-starter-cloud/macula-boot-starter-cloud-gateway -am test`，160 tests，0失败。相关依赖随后本地install供Cloud编译；没有发布远端。
- Cloud：`mvn test -Plocal`，508 tests，0失败，1 skipped；随后平台初始化幂等补充测试单独通过1项。单测包括本地Redis上的原生OAuth安全链和MockMvc，不能称为整套应用在线联调。
- 独立MySQL 8.4：`mvn -pl macula-cloud-system -am verify -Plocal -Dtest=NoUnitTestForThisMigration -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SingleProtocolResourceMigrationIT,ResourceClientConcurrencyIT -Dfailsafe.failIfNoSpecifiedTests=false`，3项通过；验证完整历史到V19、V18升级V19、真实行锁并发与回滚。未连接业务数据库；只清理任务自建测试容器。
- Admin：`npm run test:unit -- --run` 20文件184项通过；`npm run build`通过；`npm run test:e2e:ci` 7个spec共45项通过并正常退出。Cypress使用接口fixtures验证交互，不能代替真实IAM登录。首次错误地把--spec追加给启动包装器，虽45项断言通过但包装器退出1；已修正命令重跑成功。
- 变更Java文件使用两仓真实Checkstyle配置执行 `mvn -f /private/tmp/iam-resource-checkstyle-<repo>.xml checkstyle:check`，0违规。前端精确变更文件使用ESLint且不带--fix：0 errors，1566条排版warnings保留，未自动重排已有代码。两仓任务文件 `git diff --check`通过。
- 私有验证日志：`/private/tmp/iam-resource-{boot-checks,cloud-all-final,migration-it,platform-test,admin-unit-final,admin-build-final,cypress-final,style-boot-final,style-cloud-final,admin-eslint-final}.log`。初始工作树备份和精确任务文件清单在`/private/tmp/iam-resource-build-pqhrc366/`，保留已有无关改动。

未执行：应用Docker镜像重建/容器更新、业务数据库V19迁移、真实组合环境Admin登录/菜单/System/API Key/HMAC验收、远端提交/推送/发布。新增环境参数须由操作者显式配置；现有运行镜像不代表本轮源码行为。下一步严格按sdlc-build Phase B gate确认后再进入正式Stage 4。

## Stage 4 handoff — 2026-11-08
Rain明确YES确认Phase B实现完整，已进入sdlc-test并重新执行两仓全量测试、集成阶段及前端验证。完整证据见[verification.md](verification.md)。正式验证状态incomplete：计划要求的新版多服务组合验收、部分既有条件集成套件与完整桌面视觉证据尚缺；不自批绿色，不进入PR审查，不代表部署授权。

## Stage 4 failure-loop corrections
- 独立环境补跑：ApplicationProtocolConfigurationConcurrencyIT 仍写入两个协议，改为单协议切换与快照冲突；IdentitySourceBindingIT 的运行时fixture升级为V19；ApplicationProtocolConfigurationMigrationIT 明确target18，保留V17双协议历史迁移断言，最新V19由专门迁移IT证明。原默认跳过未暴露这些旧假设。
- AdminClientProvisioningService 的内部初始化表单补充单协议公开字段及purpose，避免新VO对比误报身份漂移；不扩大已弃用旧工具用途，不改变新Platform初始化器边界。相关真实IT须重跑。

- SettingsPersistenceIT 的认证方式断言改为唯一协议settings，不再读取已清空的历史共享列；实际跨实例Redis提交后驱逐断言保留。

- 故障恢复补充：Admin utils/adminAuthBootstrap.js 与 router/index.js 在恢复已提交会话时对503保留会话、阻止导航并提示稍后刷新；候选会话和401仍按原边界清理。新增admin-auth-bootstrap.test.js对应回归。真实联调发现原实现只在Axios层保留，会被初始化catch再次清除，必须同步修正。

- 最终真实缓存回归发现提交后立即读取偶发旧值；配置保存与平台认领的afterCommit改用Cache.invalidate（当前缓存立即失效合同）代替不承诺立即移除的clear，单测同步保留回滚与两缓存故障断言。SettingsPersistenceIT继续验证真实Redis与另一个缓存实例，不放宽断言。

- 真实故障注入修正：Reactive/Servlet资源安全链显式关闭AuthenticationServiceException默认重抛，确保走本次503入口；仅资源模式启用。新增Gateway OAuthDependencyFailureChainTest覆盖真实过滤器链，避免只测异常类型掩盖HTTP层行为。

- 新增Servlet ResourceDependencyFailureChainTest，使用生产Security配置和真实Bearer过滤器验证503，明确Mock远程依赖，无网络；与Reactive过滤器链回归共同防止异常被再次分派为401/500。

- 真实JWT矩阵修正：IAM/Gateway复用本地演示公钥时，验证网关签名成功不意味着网关issuer；固定网关issuer才走保留来源检查，其余令牌仍独立在线内省。补充共享公钥/不同issuer回归，不放宽旧网关JWT或伪造来源。

## Final verification — 2026-11-08
Rain确认Build完成后，已完成正式测试与隔离真实链路，前述早期incomplete缺口已逐项补齐。**Verification is green**；证据、实际失败修正、可选基础设施skip边界见[verification.md](verification.md)。停止并展示证据后方可进入sdlc-deploy审核；不代表人类PR批准或部署授权。

## 用户修订：仅Cloud承担资源安全实现（2026-11-09）

用户明确要求恢复macula-boot，随后要求更新镜像。Boot现已恢复到干净工作树。本次沿用既有已接受的资源/Scope、可信来源、Bearer转发、401/403/503合同，仅迁移实现位置，不放宽安全边界，不改Boot源码，不把此授权解释为auth_time补充设计已批准。

Cloud自有Servlet/Reactive安全配置替换Starter默认安全装配，复用原Spring Security和Boot已有通用组件；应用策略/可信来源标识/错误码归Cloud。原Boot改动和相应拒绝/成功测试迁回Cloud包，使用原Boot源码重新构建依赖后验证，不能依赖遗留修改版Maven缓存。后续仅更新System/IAM/Gateway/Admin，不重建中间件或删除卷；启动前保存数据库备份和旧镜像身份。

精确迁移/配套文件：
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/security/ResourceAccessPolicy.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/security/ResourceTokenAuthenticationManagerResolver.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/CloudResourceServerConfiguration.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/security/ResourceServerConfiguration.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/security/ResourceServerAuthorizationManager.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/security/OAuthResourceAccessPolicy.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/filter/AddJwtGlobalFilter.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/CloudSecurityConfiguration.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/config/CloudGatewayConfiguration.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/security/ResourceDependencyFailureChainTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/security/ResourceAccessPolicyTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/security/ResourceTokenAuthenticationManagerResolverTest.java`
- `macula-cloud-gateway/src/test/java/dev/macula/cloud/gateway/security/OAuthResourceBoundaryTest.java`
- `macula-cloud-gateway/src/test/java/dev/macula/cloud/gateway/security/OAuthDependencyFailureChainTest.java`
- `macula-cloud-gateway/src/test/java/dev/macula/cloud/gateway/filter/OAuthBearerRelayTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/TrustedGatewayClaims.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/ResourceErrorCode.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/SystemSecurityProperties.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/security/ResourceResponseUtils.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/SystemResourceServerConfiguration.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/config/SystemResourceBoundaryTest.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/MaculaSystemApplication.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/MaculaGatewayApplication.java`
- `macula-cloud-system/README.md`
- `macula-cloud-gateway/README.md`
- `deploy/README.md`

迁移前增量基线：`/private/tmp/iam-cloud-only-baseline-98eqv0_y`。不自行批准新行为或改变MFA待确认项。

- `macula-cloud-gateway/pom.xml`：引用既有System API的Cloud共享可信来源/错误合同，不新增外部依赖版本。

## 仅Cloud实现及本地镜像更新结果（2026-11-09）

用户要求恢复Boot后，已把原资源安全策略、来源声明、Bearer转发及错误响应迁入Cloud，Boot工作区保持干净。使用恢复后的Boot源码clean install到本地，再执行Cloud相关模块测试：API 8、Gateway 4、IAM 332、System 178，0失败/错误，System 1项既有条件跳过。System实际启动发现两份策略Bean，移除Cloud默认fallback，新增组合装配测试；该测试连同资源边界/503链共5项通过，Java Checkstyle 0违规，最终package通过。不是重新开放或改变安全规则。

四个本地应用镜像已构建并逐个通过`up -d --no-deps --no-build --force-recreate --wait`替换：System、IAM、Gateway、Admin。构建使用经核对的本地最终JAR作为现有Dockerfile运行阶段输入，避免Docker Maven缓存引入撤回的Boot类；运行容器JAR哈希与构建记录相同。Admin使用原Compose构建。

更新前已保存macula-system完整逻辑备份；System正常迁移到V20。四容器healthy，镜像身份已核对，其他10个Compose服务ID未变。Admin首页、IAM Discovery/JWKS为200；System/Gateway无Token请求为预期401；既有内省凭据返回active:false/200；RESOURCE_SERVER不能用client_credentials取Token，返回400。未提交、推送或发布远端镜像。

私有备份、镜像/JAR来源、构建及启动日志：`/private/tmp/iam-cloud-only-deploy-8rhh8l2u`。不把这次本地更新当作四平台真实联调或MFA正式验收；标准OIDC自定义主认证auth_time补充仍待确认。

## 用户界面修订：集中客户端凭据（2026-11-09）

用户明确要求Client ID和Client Secret放一起。沿用已接受的应用配置布局范围，仅调整ClientApplication.vue、ClientCredentials.vue、ResourceServerSettings.vue：移除基本信息中的重复ID，把只读ID/复制与按认证方式显示的Secret/生成/复制/重置集中到同一凭据区；桌面相邻排列、窄屏上下排列。none及private_key_jwt仍不显示Secret，已有Secret不能读取，重置合同不变。无后端、Boot、配置或认证行为变化；运行既有组件/页面测试与构建，不新增镜像更新或提交授权。

增量备份：`/private/tmp/iam-client-credentials-layout-bqok5bhx`。

凭据布局验证：既有相关Vitest 46项通过，Docker模式构建通过，OAuth Client Cypress 16/16通过（API Mock；桌面及移动截图已检查），三个目标文件无修复ESLint 0错误/85格式警告，git diff --check通过。仅调整Cloud Admin源码，Boot仍干净；本次未更新运行镜像。

## 用户撤销额外设计：内省资源许可（2026-11-09）

用户明确指出“允许内省的资源”完全没有设计要求，并质疑连带Boot扩展。本次撤销introspectionResources页面、表单/VO/实体契约、校验、平台初始化赋值和内省资源过滤，不隐藏为内部固定许可。内省保留既有机密Client认证、用途/配置版本校验、令牌有效/撤销判断及安全字段白名单；不因调用方与目标业务Client不同而拒绝，不按资源许可改写Token的aud/Scope。API自身现有资源/Scope/角色规则不在本次撤销范围，Boot保持干净。

已执行V19/V20不重写、不新增破坏性数据库迁移；历史introspection_resources列不再由模型读取或写入，不再产生任何运行时配置要求。旧迁移及其历史结构证明保留。仅按用户明确撤销项修改，不视作auth_time补充已接受，不提交/部署。文件：
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ResourcePolicy.java`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ResourceServerSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-admin/tests/unit/resource-client-settings.test.js`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ResourceAuthorizationIntegrationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/OAuth2ResourcePolicyTest.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ResourceTokenIntrospectionAuthenticationProvider.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ResourceClientConcurrencyIT.java`
- `macula-cloud-iam/README.md`
- `macula-cloud-system/README.md`
- `macula-cloud-admin/README.md`

增量基线：`/private/tmp/iam-remove-introspection-resources-playnjm3`。

撤销内省资源设计的验证：API 7、Gateway 4、IAM 332、System 179，0失败/错误、System 1项既有条件跳过；Admin 196项通过，Docker模式构建通过。已新增/调整验证：没有introspectionResources也能保存机密内省身份、经过认证的调用方可查不同业务Client/不同资源Token、原始aud/Scope保持、安全字段过滤及普通业务身份不能冒充内省身份。未修改Boot、未执行业务数据库DDL，未更新本地镜像。历史已执行V19结构及旧日志只作历史证据，不再是运行要求。

## 用户要求完整撤销Cloud替代装配（2026-11-09）

用户指出CloudGatewayConfiguration等仍在，明确要求执行完整撤销。本次移除Cloud中搬来的安全装配、资源/audience/Scope拦截、原始Bearer转发、专用来源声明及配套错误处理，恢复原Starter自动配置、内省/缓存、角色授权和内部JWT转发路径。Gateway/System入口不再排除Boot自动配置；移除仅为替代实现增加的Gateway→System API依赖、应用配置和System内省凭据传递。保留其他IAM协议、第三方来源、账号绑定、MFA接入点、管理端布局及已执行迁移，不重写历史迁移，不触碰Boot源码，不更新镜像或提交。

撤销后不再声称有这些自定义资源拦截、System二次内省、独立网关来源或统一503行为；前端已有503会话保留处理继续存在。

归档移出：
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/security/ResourceAccessPolicy.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/security/ResourceTokenAuthenticationManagerResolver.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/CloudResourceServerConfiguration.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/security/ResourceServerConfiguration.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/security/ResourceServerAuthorizationManager.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/security/OAuthResourceAccessPolicy.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/filter/AddJwtGlobalFilter.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/CloudSecurityConfiguration.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/config/CloudGatewayConfiguration.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/security/ResourceDependencyFailureChainTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/security/ResourceAccessPolicyTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/security/ResourceTokenAuthenticationManagerResolverTest.java`
- `macula-cloud-gateway/src/test/java/dev/macula/cloud/gateway/security/OAuthResourceBoundaryTest.java`
- `macula-cloud-gateway/src/test/java/dev/macula/cloud/gateway/security/OAuthDependencyFailureChainTest.java`
- `macula-cloud-gateway/src/test/java/dev/macula/cloud/gateway/filter/OAuthBearerRelayTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/TrustedGatewayClaims.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/ResourceErrorCode.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/SystemSecurityProperties.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/security/ResourceResponseUtils.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/SystemResourceServerConfiguration.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/config/SystemResourceBoundaryTest.java`

恢复/配套修改：
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/MaculaSystemApplication.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/MaculaGatewayApplication.java`
- `macula-cloud-gateway/pom.xml`
- `macula-cloud-gateway/src/main/resources/application.yml`
- `macula-cloud-system/src/main/resources/application.yml`
- `deploy/docker-compose.yml`
- `macula-cloud-system/README.md`
- `macula-cloud-gateway/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-admin/README.md`
- `deploy/README.md`
- `macula-cloud-iam/docs/third-party-integration.md`

撤销前完整备份：`/private/tmp/iam-remove-cloud-overrides-89r18nn2`。验证使用clean test/package清除旧class，核对没有替代类/排除配置/失效属性，Boot工作区仍干净。

完整撤销验证结果：Cloud目标模块`clean test -Plocal`成功，API 7、IAM 332、System 169，0失败/错误、1既有条件跳过；Gateway无独立测试，原Starter及其依赖定向测试153项全通过。Cloud相关模块package成功。System/Gateway最终JAR中未残留本次替代安全类，两个入口及Gateway POM与迁移前基线逐字节一致，源码与YAML不存在已移除替代类引用或resource-rules/resource-policy，Compose config --quiet及git diff --check通过。Boot工作区仍干净。当前容器未更新，本轮没有运行时/真实平台验收，不把旧镜像健康状态当成撤销版本的运行证明。

日志：`/private/tmp/iam-original-starters-tests.log`、`iam-original-starters-boot-tests.log`、`iam-original-starters-package.log`。

## 用户修订：认证方式与凭据同区（2026-11-09）

用户指出Secret依赖认证方式，不应放应用页；认证选项直接显示英文且布局整齐。本次把Client ID、认证方式、Secret/JWKS集中在协议页的ClientCredentials内（资源服务器仍在其专用配置内复用同一组件），应用页只保留基本信息。none不显示Secret，client_secret_basic/post显示Secret，private_key_jwt显示既有JWKS URL/签名算法；不新增认证类型、不改变认证/授权约束或重置合同。英文选项使用协议原值，统一等高两列/窄屏单列。保留ID/Secret相邻及所有复制/生成/重置行为。复用既有normalizeProtocol处理认证变化，不新增后端、Boot或镜像操作。

精确文件：
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCredentials.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ResourceServerSettings.vue`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`

增量基线：`/private/tmp/iam-client-auth-layout-e2lyg7g8`。

- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`：同步既有只读ID断言到协议配置，保存各分区不回归的断言保留。

认证与凭据布局修订验证：Admin 21文件196项单元通过；Docker模式构建通过；OAuth Client Cypress 17/17通过（Mock API），新增四种英文认证选项切换、Secret掩码生成/按需显示、private_key_jwt JWKS显示、none无Secret、ID只读/复制位置验证。1440/390px页面宽度、认证选项44px等高及各容器无横向溢出通过；截图已检查。无修复ESLint 0错误/610既有与目标排版警告，git diff --check通过。未改后端/Boot，未更新镜像。

镜像更新启动发现：System平台身份初始化仍通过IAM_INTROSPECTION_CLIENT_ID认领现有Gateway Client，撤销二次内省时误删了这条共享ID的Compose传递。恢复System环境中的ID一项，供既有平台初始化使用；不恢复System内省Secret/URI、二次内省或资源策略。属于本次恢复原Starter的部署接线修正，不增加设计范围。

## 用户再次授权镜像更新：原Starter与认证凭据布局版本（2026-11-09）

最新源码已构建并更新本地System/IAM/Gateway/Admin镜像，使用原Starter装配且运行JAR不存在已撤销替代类。Admin含协议页英文认证选项及按类型显示的凭据，应用页无Secret，旧内省资源选择不存在。构建先生成当前clean package，Java镜像使用该JAR的既有Dockerfile运行阶段，避免旧Docker Maven缓存；Admin使用Compose原构建。

更新前完整逻辑备份已保存。System初次启动因撤销时误删了平台初始化也使用的Gateway ID环境传递而退出；恢复ID一项后重建System，未恢复Secret/URI或二次内省策略。四容器最终healthy且RestartCount=0，运行镜像与Tag匹配、JAR SHA256相同。Admin首页、IAM Discovery/JWKS为200；System/Gateway匿名菜单请求401；既有内省凭据可用（未知Token active:false/200），内省身份申请client_credentials返回400。其他10个服务容器ID未变，Boot工作区干净，未提交/推送/发布远端。未把这些入口检查当作真实平台或完整用户登录联调。

私有镜像来源、数据库备份和验证：`/private/tmp/iam-original-starters-deploy-b1sypo50`；本轮package日志`/private/tmp/iam-original-starters-update-package.log`。

## 用户精简认证选项排版（2026-11-09）

用户要求认证类型一行完成，取消宽卡片。仅修改ClientCredentials.vue认证单选样式为按内容宽度横向排列，去除边框/卡片背景，窄屏自然换行；认证、Secret/JWKS逻辑不变。既有Cypress同区测试同步验证桌面选项同一行，保留各认证类型行为和窄屏无溢出断言。本次不涉及后端/Boot或镜像更新。增量备份：`/private/tmp/iam-auth-inline-7p6ckz4k`。

一行认证选项验证：Docker模式构建通过，现有OAuth Client Cypress 17/17通过；桌面所有认证单选同一行，手机自然换行且无横向溢出，Secret/JWKS条件行为不变。git diff --check通过，未更新镜像。

## 用户要求整页设计统一（2026-11-09）

用户要求停止局部来回拼接，从整页设计统筹。保持功能/字段/认证策略及英文一行认证选择不变：全宽工作区、左对齐适宜阅读宽度，固定页签与操作区，统一24/20/12px分组节奏；基本信息不重复凭据，协议按协议选择→客户端认证及条件凭据→授权/回调/Scope→令牌→折叠端点排列；凭据复制采用输入框内操作，Secret操作紧随字段，已保存Secret只显示配置状态/重置；不增加表格、能力或后端设计。移动端自然单列、页签横向滚动、按钮可见。仅下列Admin文件；沿用已接受的UI优化范围，验收现有单元/构建/浏览器并逐页检查桌面/手机截图，不更新镜像或修改Boot。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCredentials.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientAccessAuthorization.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`

增量基线：`/private/tmp/iam-client-design-pass-lsl9kq4c`。

整页设计验收：Admin 196项单元通过、Docker模式构建通过；OAuth Client Cypress 17/17通过，覆盖所有分区1440/390px滚动、固定页签/操作、操作区右边界、认证方式同一行、按类型显示凭据及已有保存/复制/重置入口。桌面与手机截图已人工视检；无自动修复ESLint 0错误，保留目标文件排版警告。此次仅Admin，Boot与后端未修改，未更新镜像。

用户随后明确要求更新镜像：仅Admin已使用整页设计版本构建并以--no-deps --no-build替换，健康、零重启、运行镜像与Tag匹配，固定页签设计已进入运行包，首页HTTP 200。其他13个服务容器ID未变，未更新后端或Boot。私有构建前状态及验证：`/private/tmp/iam-admin-design-update-ukarm0hq`。

## 用户授权修复Gateway内省凭据（2026-11-09）

用户报告登录后users/me 500；实际日志为Gateway内省401/invalid_client。运行Secret与当前DB BCrypt哈希不一致，旧配置不足16位。用户明确“可以”同意生成强随机新Secret并同步两侧。已私有备份该平台客户端旧哈希/版本、deploy/.env及容器身份；仅更新platform-gateway一行Secret及authorization_version（旧ID/用途/状态/认证方式/版本/旧哈希条件限定），将同一新Secret写入本地deploy/.env，不输出Secret。

只删除该客户端RegisteredClient的精确Redis键，并通过现有TwoLevelCache的序列化CacheEvictMessage通知本地缓存失效，不清理会话、Token或其他客户端。IAM新凭据内省200/active:false，哈希匹配。仅Gateway以--no-deps --no-build重建，healthy/零重启，其他13个容器ID不变。携带无效Token经Gateway及Admin代理访问users/me均401，不再是内省凭据错误造成的500。Boot及认证源码未改，未提交/推送。

当前浏览器访问宿主不可用，不能代替用户刷新确认正向登录，不宣称完成用户登录端到端验收。私有修复记录及原值备份：`/private/tmp/iam-gateway-credential-repair-vqzbwkid`。

## 2026-11-09 创建和应用配置验收修正

用户明确要求创建界面不选择协议、应用配置保持单列，并拉开协议页分隔线与客户端认证标题间距。本轮在既有接受的UI范围内：创建仅提交名称、类型、平台与用途，沿用服务端已有无协议DRAFT合同，不默认提交协议；协议在已保存应用的协议页选择。快速开始在尚未选协议时提示先配置，避免误显示OAuth2.1。应用基本信息按单列显示类型、名称、认证租户；已有只读类型、凭据所属分区不变。认证区域以独立外层分组承载分隔线和24px内间距，避免父页面configuration-panel的padding重置覆盖间距。

文件：上述已接受ClientCreate.vue、ClientApplication.vue、ClientProtocol.vue、ClientGuide.vue、index.vue、现有oauth-client-configuration.cy.js与Admin README；不改后端、认证策略、数据库或Boot，不更新镜像。增量基线：/private/tmp/client-ui-refinement-kfz81nvr。

本轮开发反馈：Admin完整Vitest 21文件198项通过；Docker模式生产构建通过；OAuth Client Cypress 19/19通过，含无协议创建请求、创建后显式选择协议及1440/390px应用字段同列、分隔线至凭据标题至少24px。桌面/手机截图已查看；截图增加等待loading遮罩消失后复跑19/19通过。无自动修复ESLint无错误，git diff --check通过。日志：/private/tmp/client-ui-unit.log、client-ui-build.log、client-ui-cypress.log、client-ui-eslint.log。未更新镜像，未改后端、Boot及业务库。
