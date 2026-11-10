# Plan: IAM 应用配置模型与接入体验重构 (from spec.md 2026-11-06)
Status: accepted

## Files that change
### SDLC 与迁移整合
- `.sdlc/iam-application-configuration/intent.md`、`spec.md`、`research.md`、`plan.md`、`review-findings.md`：范围接受、依据、计划与评审记录。
- `.sdlc/iam-client-terminal-login-config-ui/plan.md`、`.sdlc/iam-client-grant-selection/plan.md`：只追加被本轮设计取代的部分与保留成果，不重写历史验证证据。
- `macula-cloud-system/src/main/resources/db/migration/V14__oauth_client_terminal_type.sql`（移除未部署草稿）、`V14__oauth_client_application_configuration.sql`（新增）：在确认本地历史停留V13后整合为三个nullable列，不回填、不改已有凭据/绑定。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientTerminalType.java`（移除未部署草稿）：由架构类型和小程序平台取代，删除前保留本次工作快照。

### System API 共享标识
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationType.java`（新增）：WEB_SERVER、SPA、NATIVE、SERVICE、MINIAPP。
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2MiniappPlatform.java`（新增）：WECHAT、WECOM。
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2HostedLoginMethod.java`（新增）：PASSWORD、SMS，与Token grant值分开。

### System 配置与 Discovery
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`：新增字段及presence语义，兼容旧PUT。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`、`vo/OAuth2ClientVO.java`：新类型、平台与nullable托管策略。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`：新建/确认类型、不可变校验、类型模板兼容及null/空集合持久化。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/OAuth2ClientConfigController.java`：增加只读 `/metadata`，沿用Client管理权限。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2IssuerMetadataProperties.java`（新增）：只接受服务器配置的Discovery获取地址、预期Issuer、超时与缓存时间。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/OAuth2IssuerMetadataConfiguration.java`（新增）：专用无凭据HTTP客户端，禁用重定向。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2IssuerMetadataService.java`（新增）、`service/impl/OAuth2IssuerMetadataServiceImpl.java`（新增）：延迟获取、限长解析、验证和短期缓存，不在启动时访问IAM。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2IssuerMetadataVO.java`（新增）：仅返回允许的Issuer/端点字段和状态，不透传原始JSON。
- `macula-cloud-system/src/main/resources/application.yml`、`deploy/docker-compose.yml`、`deploy/.env.example`、`deploy/README.md`：明确内网Discovery地址与对外Issuer，沿用既有IAM_ISSUER_URI；不新增System→IAM的启动依赖。

### IAM 托管登录策略
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`：只读映射三列。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`（新增）：解析受控SavedRequest和Client策略；嵌套不可变上下文保存Client/租户/用户类型及允许方式。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginPolicyFilter.java`（新增）：只保护密码/SMS表单和浏览器发码入口，拒绝非法上下文，向后续链写入可信请求属性。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`：接入策略过滤器，保持CSRF先执行、策略校验先于密码/SMS认证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`、`controller/SmsCodeController.java`：呈现有效方式，发码使用同一Client策略和验证码上下文。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsAuthenticationFilter.java`：将受信任Client上下文带入验证码token；不从任意请求参数取信任来源。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/SysUserDetailsServiceImpl.java`：浏览器受控上下文优先路由；Token扩展入口保持既有认证后的路径。
- `macula-cloud-iam/src/main/resources/templates/login.html`、`static/iam/auth.js`：PASSWORD/SMS单独开启、仅一个方式和无可用方式状态；不依赖固定密码表单存在。

### Admin 创建与四分区详情
- `macula-cloud-admin/src/router/index.js`、`src/layout/index.vue`、`src/layout/components/tags.vue`：仅本应用配置路由启用按path复用实例，避免全局fullPath key在query分区切换时丢失草稿；其他页面保留原行为。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：保留分页列表，使用现有路由query进入创建/详情，不把全部字段塞入抽屉；处理未保存/关闭/重载和敏感清理。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`（新增）：架构类型、小程序平台及必要字段，ID自动生成只读。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`（新增）：名称/范围/回调/Origin/密钥状态和平台端点。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`（新增）：紧凑grant、客户端认证、PKCE、TTL及兼容区。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`（新增）：托管PASSWORD/SMS策略与现有身份源/渠道关联。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`（新增）：按类型接入步骤和安全请求示例。
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCredentials.vue`（新增）：复用生成/复制/重置、一次性展示与清理，不把示例临时密钥放入保存草稿。
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`：新字段、元数据API及身份源能力目录适配，移除仅作导航/反向猜测的终端逻辑。
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`：保留UUID、Basic编码、安全随机与shell引用；新增类型模板/清理/差异与保存payload构造。
- `macula-cloud-admin/src/locales/lang/zh-cn.js`、`lang/en.js`：新流程文案，并修正与实际身份源能力不符的旧“尚未接入”提示。
- `macula-cloud-admin/README.md`、`macula-cloud-system/README.md`、`macula-cloud-iam/README.md`：新流程、兼容/继承、端点与能力限制。

### 验证文件
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`：新建公共Client的既有集成fixture明确SPA类型，延续真实Redis提交后驱逐证明。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/AdminSettingsAuthorizationTest.java`：适配只读元数据服务注入。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`：类型/不可变/legacy/presence及凭据初始化。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2IssuerMetadataServiceImplTest.java`（新增）：服务器来源、Issuer、超时/限长/重定向、缓存和无凭据。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/OAuth2ClientConfigControllerTest.java`（新增）：JSON字段presence、元数据路由和错误契约。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ExternalCredentialLoginMigrationIT.java`：V13→V14列与历史不回填、null/空字符串区分。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`（新增）：隔离MySQL、真实Mapper/Service、两个独立事务竞争确认及与旧PUT交错，检查409、绑定和缓存副作用。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`（新增）、`HostedLoginPolicyFilterTest.java`（新增）：上下文与拒绝路径。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedLoginFlowTest.java`（新增）：真实Security过滤链与MockMvc验证页面/认证/发码/跨Client一致。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/sms/SmsAuthenticationFilterTest.java`、`service/userdetails/SmsServiceImplTest.java`、`authentication/thirdparty/ThirdPartyUserDetailsServiceTest.java`、`config/IamProtocolTest.java`：适配注入和信任上下文并保留协议回归。
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`、`tests/unit/admin-identity-security-settings.test.js`：模板/清理/保留/示例及路由API。
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`（新增）：组件保存/切换/关闭生命周期，不仅检查源码字符串。
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`、`cypress/e2e/admin-identity-security-settings.cy.js`：替换旧抽屉/终端叠加用例，覆盖创建和四分区。

## Order of work
1. 保存本次开始时未提交改动的快照到临时目录，记录已有文件清单；只处理本计划声明的文件。已核实本地MySQL Flyway最新为V13，且没有terminal_type列；保留旧SDLC证据并记录模型替代关系。其他环境若已执行V14，停止替换该迁移并先调整为追加版本。
2. 增加共享枚举、V14三列和System/IAM映射，移除未部署的终端枚举/迁移；所有新列nullable，不推断、不回填Client或账号身份。旧托管策略NULL继承全局；空字符串表示显式关闭全部本地方式。
3. Form对新字段显式追踪JSON presence，标记不序列化到API；省略字段保留现值，hostedLoginMethods:null显式恢复继承，[]显式关闭。类型一旦非空不得清空/改变；MINIAPP平台同样不可变。所有更新（包括legacy分支和省略新字段的旧PUT）在数据库WHERE中同时比较读取时的类型/平台，NULL使用IS NULL；影响行数必须为1，否则返回HTTP 409并回滚，不替换绑定、不注册成功缓存失效回调。以此拒绝并发确认不同类型以及确认与旧PUT交错覆盖，不能只用读取后的Java比较。旧未分类Client允许原有配置维护，显式确认类型时校验差异而不自动重置字段。
4. 新建模板：WEB_SERVER默认Basic与code/refresh；SPA/NATIVE默认none与S256 code/refresh；SERVICE默认Basic和client_credentials、明确API scope、无用户登录；MINIAPP按微信/企微选择既有外部凭证及固定身份源。切换创建类型/平台清除登录相关字段并重建模板，保留名称/通用参数与同一次创建的Client ID。新ID由现有安全UUID工具生成且只读；服务端校验唯一和已保存ID不可变。
5. 明确模板与兼容边界：新建不启用password/implicit；旧grant原样维护，现有sms/email/external_code拒绝条件和legacy完整集合例外保留。已分类公共类型不配置无用途密钥；旧未分类特殊组合标明兼容。SERVICE不提供托管登录/refresh。若原记录无密钥而改用密钥认证，显式初始化新密钥与配置原子提交；已有密钥替换仍必须走reset接口，不能借普通保存旋转。
6. 实现元数据服务：只访问服务器属性中的Discovery URL，独立无认证HTTP客户端不跟随重定向；连接/读取超时、限长256KiB、只缓存有效数据60秒。校验Issuer等于配置的对外Issuer，过滤端点URI到允许的HTTP(S)并拒绝userinfo/fragment，HTTPS用于生产，HTTP仅在明确本地开发配置中使用。请求参数不能覆盖URL；失败返回受控状态，不透传网络异常或原始JSON。接口不在Bean创建时请求IAM，System健康不依赖IAM先启动。
7. 实现托管Client策略：在会话SavedRequest中核对授权端点、唯一client_id、response_type=code、注册Client/grant及精确redirect_uri。无上下文且无Client声明时保留既有门户；无效/冲突上下文不能降级为门户。PASSWORD/SMS有效集合=Client配置或历史继承，再与全局能力取交集；SERVICE拒绝托管用户认证。
8. 策略过滤器在CSRF之后、密码和SMS认证之前执行，仅匹配POST /login、/login/sms、/api/v1/sms-code。将受控Client ID/用户路由写入请求属性；UserDetails读取该属性，不让header/form/cookie覆盖。密码/SMS提交失败走受控认证失败流程；发码拒绝返回非2xx而不redirect成200登录页。SMS发送与消费使用相同Client ID，取消浏览器路径落入全局验证码桶；Token扩展接口不走此过滤器，保留现有独立检查。
9. 模板与auth.js按有效集合显示/初始化本地表单，覆盖仅SMS、仅PASSWORD、均关闭但第三方可用和完全无方式。现有SSO会话不追溯撤销；本轮不增加认证强度要求。第三方配置仍按全局唯一源，外部码按固定registration；不顺手改写state、账号标识域或自动注册策略。
10. 拆分Admin列表、创建和详情组件。详情保留同一脱敏草稿并用query表达id/tab；切页提示未保存，单分区保存不会清空别的分区，未知/旧字段不得被默认值覆盖。创建成功进入应用配置，类型/平台只读；历史确认一次时展示变化，修正冲突后保存。协议参数与托管登录开关各归其分区，不互相隐式启用grant。
11. 应用配置显示只读元数据及复制；Client JWKS输入仅在private_key_jwt协议配置中显示，平台JWKS只读。凭据组件复用Web Crypto、Basic编码及临时生命周期；仅创建/重置成功后标记生效，离开详情/关闭/切换Client立即清除原密钥和派生内容，隐藏状态复位。新建表单未保存时的生成值不得伪装成已生效凭据。
12. 更新文档与测试，先执行最小相关Maven/Vitest，再扩大到System+IAM及API、完整Admin构建和两组Cypress。隔离MySQL验证升级，不使用业务库；桌面/390px截图核对创建、四分区及长代码滚动。最后执行Java Checkstyle、变更文件ESLint及git diff --check。Build结束按技能要求等待实现完成确认；不在本计划内提交、推送或部署。

## Risks
- 已有未提交改动较多：快照后按文件迁移，禁用整仓restore/reset和自动格式化无关文件；旧SDLC记录只追加，不用新验证覆盖旧证据。
- 迁移替换仅适用于未执行V14的目标：本机已证实最新V13；其他环境发布前必须核对，不能覆盖已执行checksum。回滚应用不删除新增列或历史数据。
- 并发确认可能覆盖不可变类型：条件更新同时保护类型/平台，所有更新分支检查影响行数并在0时事务回滚；既有legacy认证/grant条件与此合并，不得走无条件updateById绕过。
- 三态JSON最容易误清策略：必须测试省略/null/[]三种请求及旧前端PUT；仅靠Java字段默认值无法表达这个合同。
- 页面保存完整对象可能写入其他分区默认值：使用从真实VO创建的单一草稿，presence与dirty字段明确，异步响应不得覆盖用户已编辑状态。
- 受控Client上下文不能从header/body/cookie取得：无效SavedRequest拒绝，策略失效也不能回退为全局允许。短信必须在发送、token及账号加载三处保持同一Client边界。
- 过滤器顺序与失败响应不能只靠单元测试：实际链中CSRF先于策略、策略先于两类认证，并验证验证码拒绝为非2xx。直接API Token grant与既有SSO保持各自语义。
- System和IAM当前存在启动依赖，元数据若启动时加载会造成循环：必须懒加载、限时失败、健康解耦；元数据响应只用于接入信息，不成为认证信任配置来源。
- 类型模板改变不能自动改已有Client/管理端自身登录模式。旧未分类记录仍能维护原值；选择类型属于明确操作，不自动旋转ID/Secret或删除身份绑定。
- 浏览器同平台多身份源、邮箱托管页、CAS/SAML、注册/MFA不在本轮；界面不展示虚假能力。真实第三方设备验证与Mock覆盖分开报告。

## Proof
- System/API：类型/平台不可变，新建与legacy确认、两个请求竞争不同类型、确认与旧PUT交错（两种提交顺序）、条件更新0行的409与绑定/缓存无副作用；允许/拒绝组合、三个字段presence、credential初始化与reset边界、缓存after-commit与rollback；metadata白名单/Issuer/URL不可覆盖/重定向/超时/限长/过期和启动不联网。
- IAM：SavedRequest缺失/伪造/重复参数/错误grant或回调；header/cookie/body与可信属性冲突；PASSWORD-only、SMS-only、全部关闭与null继承、全局关闭优先、SERVICE拒绝；发码无副作用、跨Client验证码消费拒绝；实际Security链的CSRF和过滤器顺序；Token扩展、SSO和第三方原回归不退化。
- Admin：创建类型与mini平台切换清理、ID稳定且只读、默认grant/认证正确；四分区回显和保存不丢值、未保存提示、旧类型确认差异；密钥关闭清理/失败重试/Clipboard失败，metadata加载/失败/缺失字段；不把平台JWKS当客户端JWKS，不显示未支持的托管邮箱/MFA。
- 隔离MySQL：V13→V14只加nullable三列，旧ID/密钥/grant/绑定逐项快照一致；NULL与空策略准确往返；通过真实Mapper/Service的两个独立事务验证竞争确认和旧PUT交错，最终类型与平台只有一个获胜版本且失败返回409，不以复刻条件SQL的测试替代服务路径。执行已有外部码升级负向样本，保留失败预检能力。
- 命令：目标Maven -pl macula-cloud-system,macula-cloud-iam -am test -Plocal；按项目Failsafe配置运行隔离ExternalCredentialLoginMigrationIT和OAuth2ClientApplicationConcurrencyIT；Admin npm run test:unit -- --run、npm run build、两组OAuth Client Cypress；定向Checkstyle、ESLint和diff检查。仅报告实际执行结果，正式Stage 4按sdlc-test另行进入。

## Engineer approval
2026-11-06：Rain 在计划与两轮评审结果展示后回复“YES”，明确接受实施计划，进入Build Phase B。仓库无CLAUDE.md，使用AGENTS.md及按需规则。未提交改动快照保存在临时目录，不进入版本库。

## Implementation deviations
- 编译发现 AdminSettingsAuthorizationTest 直接构造 OAuth2ClientConfigController，需同步注入元数据服务mock；将该文件加入验证文件清单，不改变鉴权行为和验证范围。
- Secret重置改为仅更新密钥列并比较原认证方式，避免整Entity更新覆盖并发应用确认/托管策略；与计划的原子更新边界一致。
- 并发证明细化：仅比较类型/平台无法识别“旧PUT先提交且类型仍NULL”的交错。条件更新扩大为原始完整配置快照（含认证、回调、策略与凭据），仍不新增版本列；真实Service并发测试覆盖两种先后顺序。这增强已有原子更新合同，不改变接口。
- HostedLoginFlowTest复用现有IamProtocolTest.App的真实过滤链装配，将测试配置类型公开以避免复制另一套测试Security配置。
- 浏览器真实壳层发现布局使用fullPath作为组件key，会在id/tab变化时重建页面。补充路由专用reuseQueryView标记及布局按path复用；在组件停用时额外清除敏感临时值。验证继续覆盖完整管理壳层而非仅独立挂载组件。
- 同一应用路由query切换原先也会重复创建管理标签，tags.vue仅对reuseQueryView路由按path归并，其他标签保持fullPath语义。

## Build implementation and self-check evidence — 2026-11-06

实现已覆盖应用模型/三态字段/类型不可变与完整快照CAS、无密钥初始化、可信Discovery、托管PASSWORD/SMS安全链、四分区草稿及凭据清理。计划偏差见上方Implementation deviations，均为装配/真实布局/并发证明所需适配，未改变业务范围。未提交、推送或部署，未修改业务数据库；临时MySQL仅用于隔离验证。

- `mvn -pl macula-cloud-system,macula-cloud-iam -am test -Plocal`：API 2、IAM 251、System 146，均无失败，System原有环境门控用例跳过1项。此后追加无密钥初始化与元数据超时/过期边界：目标System 26项通过（含新增用例）。
- 全新MySQL 8.4.6隔离schema，通过Surefire显式`-Dtest=ExternalCredentialLoginMigrationIT,OAuth2ClientApplicationConcurrencyIT`执行集成类2/2通过，非业务数据库；实际走真实Mapper/Service与两个事务。V8→V14历史数据保持、策略NULL/空字符串往返、两次竞争确认及旧PUT两种交错均验证。V9负向预检故意拒绝脏样本的日志是断言的一部分。
- Admin `npm run test:unit -- --run`：15文件134项通过；`npm run build`成功，仅既有分包大小提示。
- 两组Cypress：21/21通过（应用8、身份源/消息13）。追加内部容器横向溢出断言后，应用8/8再次通过；已查看1440px/390px截图。浏览器API为受控Mock，未当作真实IAM/第三方设备联调。
- 定向32个Java文件Checkstyle：0违规；功能文件ESLint与路由/布局ESLint检查通过；`git diff --check`通过；Compose `config --quiet`通过。
- System元数据测试使用隔离HTTP服务器，验证不带认证、不跟随跳转、限长、Issuer、危险URI、读取超时、有效缓存与失效；正式Stage 4签收仍须按sdlc-test进入，Build出口等待Rain确认实现完成。

桌面/手机截图位于Admin的ignored Cypress screenshots目录，生成物不进入提交。临时MySQL容器将在本阶段结束前移除。

- Stage 4完整集成套件核对发现SettingsPersistenceIT的新建fixture缺少本轮要求的应用类型；补充SPA，不改变生产代码。Docker镜像的业务代码不受该测试fixture适配影响。
2026-11-06：Rain以YES确认实现完成，随后明确要求“更新docker”，授权更新本轮System/IAM/Admin及必要的已验证新增列迁移，替代前述本轮不部署的范围限制。

## Stage 4与本地Docker更新
2026-11-06：正式验证完整输出及规格对应见verification.md。全项目默认套件、所有IT、完整Admin/Cypress均已执行并通过；门控用例已隔离补跑。按Rain“更新docker”更新System/IAM/Admin，三者healthy、V14成功、历史指纹保持、其他容器未重建。正式代码审阅/PR尚未进行。

## 页面风格反馈修正
Rain指出接入应用页未铺满、风格偏离其他管理页。本次在已接受的Admin页面范围内修正：去除1320px居中限制及详情卡片外观，复用el-container/el-header/el-main/el-footer，列表表格填满主体，详情占满可用宽高。沿用已授权本地Docker更新，仅更新Admin。验证沿用组件测试、构建、目标Cypress（大屏满幅及390px无溢出）和ESLint，不改变后台契约。

## 创建应用单选行对齐修正（2026-11-09）
用户截图指出接入用途 / 小程序平台标签和单选项不对齐。原创建组件没有表单容器，继承页面为 top 标签设计的 line-height / padding 后与默认横向单选行不匹配；另有宽泛 span 样式。沿用已接受文件清单，在 ClientCreate 内限定样式：桌面固定 100px 标签、32px 行高垂直居中、两组同一起点；600px 以下上下排列，限制描述文字样式只作用于类型卡片。复用 oauth-client-configuration.cy.js 已有创建 / 手机页脚用例增加真实矩形断言及截图。不修改模型、协议或认证行为，沿用本轮 Admin 镜像更新授权交付。

## 2026-11-09 兼容渠道约束补全（用户明确要求继续优化代码）
本次落实已接受 Spec Requirements 2/7/8/12 与 Verification strategy：应用架构、端内渠道分开，只有兼容来源可关联，服务端不能只依赖前端隐藏。Rain 在终端矩阵及手机扫码问题讨论后明确回复“OK，你先仔细研究我之前给你的文档，核对一下。继续优化代码”，授权该范围核对与修正。

补充原文件清单中直接受影响的实现（既有行为修复，不新增Schema、SDK依赖或未经核实的厂商Flow）：
- 共享 OAuth2ClientApplicationType.java 追加受控 Code / 网页来源适用性；IdentitySourceType.java 追加已核实网页环境；新增 IdentityBrowserEnvironment.java 与精确白名单测试。
- System OAuth2ClientConfigServiceImpl.java 与对应测试校验应用 / Code 组合；目录 Service / VO 与对应测试返回可用应用类型和浏览器环境元数据。
- IAM MaculaRegisteredClientRepository.java、ExternalCodeClientSettings.java、OAuth2ExternalCodeAuthenticationProvider.java 及相邻测试传递并校验受信任应用类型；BrowserLoginClientPolicyService.java、AbstractThirdPartyAuthenticationFilter.java、LoginController.java、login.html 及相邻单元 / SecurityFilterChain 测试统一显示与发起约束。
- Admin IdentitySourcePicker.vue、ClientLoginControl.vue、ClientGuide.vue、oauth2Client.js、oauthClientConfiguration.js 与已有 Vitest / Cypress 统一渠道能力过滤、协议提示及不兼容历史关系说明，不静默删除已保存关联。
- research.md、verification.md、三模块README及第三方接入文档记录逐接口证据、兼容与限制。

顺序：先逐原链接核实 Code 获取 / 换码 / 权限与设备差异，后共享白名单、System保存、IAM核验 / 网页路由、Admin选择 / 协议提示；最后执行正反向单元、全后端、前端全单元 / 构建 / 浏览器及静态检查。空类型历史记录保留兼容入口，明确类型必须按白名单验证；不重置凭据、绑定或业务记录。UA仅用于体验路由，不能作为可信身份；回调继续以服务端state绑定的来源 / 模式 / 版本核验，不根据回调UA改道。

范围限制：不新增端内运行环境持久字段，不引入飞书 / 钉钉JSAPI到IAM网页SSO的新桥接；已有WEBVIEW direct-Code能力仅向适用Web/SPA且满足既有公共Client条件展示。需要网页SSO的新桥接属于独立设计，不能伪装已实现。微信 / 企微手机普通浏览器不展示PC入口；飞书 / 钉钉手机网页登录未核实的部分明确标注，不能猜测客户端唤起。

补充证据及文件：官方飞书页面公布Markdown后，完整核实AppLink及v3 Token，故开放已有FEISHU_APPLINK_V3预留适配（固定回调＋平台PKCE），不继续以旧抓取失败判不可用；FEISHU_WEB增加手机/飞书内OAuth环境并在该容器直接续转。FEISHU_WEBVIEW_V1的旧JSAPI换码合同仍单独待核实，不新增桥接。新增独立源文件 src/utils/identitySourceCompatibility.js 与 tests/unit/identity-source-compatibility.test.js，避免将策略耦合HTTP模块；新增共享OAuth2ClientApplicationTypeTest.java。MaculaRegisteredClientRepositoryTest、BrowserLoginClientPolicyServiceTest、ThirdPartyAuthenticationFlowTest、FeishuClientTest及既有生产Token provider测试均补充直接影响范围，精确文件以验证记录清单核对。

## 2026-11-10 飞书端内Code补全
Rain纠正“飞书端内也是code啊”，沿前一轮继续优化授权补全该独立能力。此前仅提供标准OAuth作为端内替代，未完成JSAPI Code合同；本次沿官方H5步骤→官方免登示例→v1 Token→v2 Token迁移→v3 Token逐链取得原文。旧v1明确历史不推荐，追加FEISHU_WEBVIEW_V3并使用既有v3客户端密钥换码，不默默改写历史显式v1来源。实施精确涉及IdentitySourceFlow.java、FeishuClient.java / FeishuClientTest.java、共享应用矩阵测试、生产ExternalCodeProvider测试、ClientGuide.vue、Cypress应用配置spec及本计划/研究/验证和三模块文档。先核实合同、后修适配与精确请求/跨端拒绝测试，最后全后端和前端验证。没有新增Schema、Token接口、JSAPI网页SSO桥接或业务数据操作。

补充直接影响文件：身份源index.vue此前对所有WEBVIEW场景展示浏览器回调，现限定实际网页登录来源；默认Flow不在表单编辑，但按目录唯一可用项判断必要参数，避免新AppLink缺少回调输入。该调整不更改持久化值；本次变更前快照单独保存/private/tmp/feishu-webview-baseline。

## 2026-11-10 按登录入口分离的人工确认修正
Rain说明同一小程序/原生客户端既可直接平台Code也可拉起IAM页面；飞书/钉钉端内H5直接Code，企微端内沿IAM自动授权。完整模型展示后Rain回复“是的”，确认两条入口可并存。此修正替代上一轮把MINIAPP排除网页来源/本地托管的过度限制；直接Code架构/平台白名单不放宽。

精确实施：共享OAuth2ClientApplicationType.java仅为SERVICE禁止网页登录，System OAuth2ClientConfigServiceImpl.java小程序允许授权码/本平台外部Code任一或组合；IAM LoginController.java小程序默认保留选择页，只有明确直登才自动续转，其他端内自动授权保持；Admin ClientLoginControl.vue新增独立统一登录开关及两条路径说明，identitySourceCompatibility.js、oauthClientConfiguration.js、IdentitySourcePicker.vue同步来源/校验/提示；相邻System/API/IAM/前端测试及Cypress、research/spec/verification/README记录。无新Schema、无小程序前端SDK/回跳桥接实现、无第三方身份字段或权限变化。先改共用规则和保存，再改IAM与UI，后验单路径/双路径/关闭/跨端拒绝。保留旧显式hostedLoginMethods空集合，不自动为已有应用开启密码短信。


## 2026-11-10 身份源多能力模型实施修订（Rain已批准）

本附录是本轮已批准完整增量计划；原计划的accepted及历史批准只适用于此前范围，不自动覆盖新增字段和网页登录能力关联。输入是spec.md本日修订与credential-audit.md，用户最新澄清优先：微信四类应用分开建源，不新增聚合父表。应用/Client创建模型、密码短信、公共PKCE、绑定映射及已接受的双入口行为保留。

### Files that change

共享契约（目录前缀macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/）：
- enums/IdentityCredentialType.java（新增）：具体注册类型，微信四种分开，企微/飞书/钉钉具体应用共用凭据的范围。
- enums/IdentityLoginCapability.java（新增）：稳定能力标识、实际已实现状态、适用运行环境、旧sourceType/Code Type转换；不使用显示名称或枚举name替代协议值。
- enums/IdentitySourceType.java、IdentitySourceFlow.java、OAuth2ClientApplicationType.java：保留旧兼容合同，能力默认只选择已核实接口；公共应用与Code白名单复用。

System（前缀macula-cloud-system/src/main/java/dev/macula/cloud/system/）：
- pojo/entity/SysIdentityProvider.java、form/IdentityProviderForm.java、vo/IdentityProviderVO.java、vo/IdentityProviderCatalogVO.java、converter/IdentityProviderConverter.java、service/impl/IdentityProviderServiceImpl.java：新注册类型/能力配置、目录、脱敏、旧字段presence与版本CAS。
- form/OAuth2ClientForm.java、vo/OAuth2ClientVO.java、pojo/entity/SysOAuth2ClientConfig.java、vo/OAuth2ExternalCodeCatalogVO.java、service/impl/OAuth2ClientConfigServiceImpl.java：按能力固定网页登录registration，来源目录按真实能力投影；旧关联保持兼容。
- src/main/resources/db/migration/V22__identity_provider_login_capabilities.sql（相对System模块；若V22已占用顺延，不改既有迁移）。

IAM（前缀macula-cloud-iam/src/main/java/dev/macula/cloud/iam/）：
- pojo/entity/SysIdentityProvider.java、pojo/entity/SysOAuth2Client.java：只读映射新增列。
- authentication/thirdparty/IdentityProviderConfigService.java、BrowserLoginClientPolicyService.java、ThirdPartyLoginContext.java、HttpSessionThirdPartyLoginStateRepository.java、VerifiedExternalIdentity.java、RegistrationIdentityBindingService.java、IdentitySourceBindingPolicyService.java、AbstractThirdPartyAuthenticationFilter.java：从受控Client/场景解析能力，生成对应厂商合同视图；state/待绑定及事务重查核对注册/能力/来源版本。
- authentication/thirdparty/ProviderDirectoryPolicyService.java、ProviderDirectoryRefreshService.java、ProviderApplicationTokenCache.java（后两项同authentication/thirdparty目录）：目录准入、异步刷新和应用Token写缓存前后的来源复查都按同一选定能力解析，不能退回旧sourceType/flow锚点。
- authentication/wechat/WechatAuthenticationFilter.java、authentication/wecom/WecomAuthenticationFilter.java、authentication/feishu/FeishuAuthenticationFilter.java、authentication/dingtalk/DingtalkAuthenticationFilter.java：发起与回调传递服务器选定能力；不改变已核实换码HTTP合同。
- authentication/wechat/WechatExternalCodeVerifier.java、authentication/wecom/WecomExternalCodeVerifier.java、authentication/feishu/FeishuExternalCodeVerifier.java、authentication/dingtalk/DingtalkExternalCodeVerifier.java、protocol/oauth2/grant/externalcode/ExternalCodeAccountResolver.java：Code Type绑定实际已启用能力；厂商调用前拒绝错误来源。
- controller/LoginController.java：按真实环境和已关联能力显示/直登，保留小程序显式打开IAM选择页策略。

Admin（前缀macula-cloud-admin/）：
- src/api/model/system/identityProvider.js、src/api/model/system/oauth2Client.js。
- src/views/system/identityProvider/index.vue：真实应用类型单选、能力多选、凭据一次填写；微信各类型独立；受控能力参数及空/失败/冲突态。
- src/utils/identitySourceCompatibility.js、src/utils/oauthClientConfiguration.js、src/views/system/oauth2Client/components/IdentitySourcePicker.vue、ClientLoginControl.vue、ClientGuide.vue：能力关联、环境说明、保存不丢既有关系、指南从所选能力生成。
- tests/unit/identity-provider-policy.test.js、identity-source-compatibility.test.js、oauth-client-configuration.test.js、oauth-client-details.test.js及cypress/e2e/admin-identity-security-settings.cy.js、oauth-client-configuration.cy.js。

后端测试：共享API新增IdentityCredentialTypeTest、IdentityLoginCapabilityTest；System扩展IdentityProviderServiceImplTest、OAuth2ClientConfigServiceImplTest并新增IdentitySourceCapabilitiesMigrationIT、IdentitySourceCapabilitiesConcurrencyIT；IAM扩展IdentityProviderConfigServiceTest、BrowserLoginClientPolicyServiceTest、RegistrationIdentityBindingServiceTest、IdentitySourceBindingPolicyServiceTest、IdentitySourceBindingIT、authentication/wecom/WecomExternalCodeVerifierTest.java、authentication/feishu/FeishuExternalCodeVerifierTest.java、authentication/dingtalk/DingtalkExternalCodeVerifierTest.java、authentication/wechat/WechatClientTest.java、authentication/thirdparty/ThirdPartyBindingServiceTest.java、authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java、authentication/thirdparty/ProviderDirectoryPolicyServiceTest.java、authentication/thirdparty/ProviderDirectoryRefreshServiceTest.java、authentication/thirdparty/ProviderApplicationTokenCacheTest.java、config/IamProtocolTest.java。包路径均相对于相应模块src/test/java/dev/macula/cloud/{system或iam}/；共享API测试位于src/test/java/dev/macula/cloud/system/enums/，System服务测试位于service/impl/、IT位于integration/；IAM其余短类名位于authentication/thirdparty/。不批量改无关测试。

文档：macula-cloud-system/README.md、macula-cloud-iam/README.md、macula-cloud-admin/README.md、macula-cloud-iam/docs/third-party-integration.md、本slug spec/plan/research/verification/review-findings；不改Boot、Gateway、依赖、REVIEW、skills或凭据环境文件。

### Order of work
1. 保存本轮文件基线和旧数据结构证据，确认实际最高迁移版本；使用现有MyBatis、事务、Jackson、CryptoField、Redis及Vue/Element Plus，不引入依赖。读取实际模块pom/README及相邻测试后实施。
2. 共享模型：注册类型固定，能力多选；定义每种能力对应的厂商Code/flow、运行环境、必填参数及真实可用性。旧sourceType仅作兼容锚点，不继续代表能力集合。一条来源的公共身份映射仅允许其启用能力共同支持的可信字段；不同能力需独立映射的扩展不在首版。
3. 追加迁移：sys_identity_provider新增credential_type和capabilities_json；sys_oauth2_client新增browser_capability_bindings。能力JSON以服务器稳定能力代码为键，保存固定默认/旧合同及能力必要参数，密钥继续只在现有加密列存一次。旧数据按确切sourceType补原能力；企微旧网页映射原来已有的PC+端内能力，微信小程序映射原支持的两种Code；其他严格等价，不自动新增SDK/小程序。旧源版本/ID/密文/用户绑定不变，空库走完整迁移。
4. System写入：新建明确注册类型和能力，校验平台/应用/已实现合同；微信只接受当前类型的AppID/Secret，企微注册包含CorpID/AgentID/Secret。已建立的具体注册类型和标识不可通过更换能力重定向到另一个平台应用；密钥重置仍递增版本。更新以version CAS原子提交能力/凭据/映射，失败409，不替换关联或驱逐成功缓存。旧PUT省略新字段保留原能力，显式空集合按停用能力处理，未知/重复键拒绝，VO不返回密钥。
5. 关联兼容：新增browserCapabilityBindings为“能力→固定registrationId”；旧browserProviderBindings保持原值并仅等价投影原来源已具备的网页能力。新客户端同时提交旧/新字段时，必须语义一致，否则拒绝；未知/跨平台/非网页能力拒绝。新字段已存在时，旧PUT不得删除/覆盖它。Web与服务号可以分别关联不同微信registration；同一能力只选一个源，不按列表第一个或UA猜凭据。兼容入口仍按旧唯一源限制；新能力入口采用已固定来源，不扩大旧全局查找。
6. IAM统一requireCapability注册解析：从可信Code Type或SavedRequest+已存Client策略选择能力，不相信请求任意credentialId/sourceType。据能力构造原厂商适配器合同，使用该注册唯一凭据。state绑定Client/能力/registration/版本/模式/适用回调与PKCE，回调不按当前UA重选。已有注册级绑定和反重放设施复用；待绑定事务再次检查同一能力仍启用。目录准入current、异步目录刷新run的厂商调用前后、应用Token缓存requireCurrent及owner写入前后，都使用选定能力对应合同重查版本/flow/组织，不能退回旧sourceType锚点。缓存仍保留registration/version/flow/purpose及owner隔离，不为共享凭据放宽凭证用途。停用或改密使旧state/票据/目录快照失效。
7. Admin创建/编辑按注册类型渲染多能力；企微/飞书共享凭据只填一次；微信四种来源分开创建、编辑时不可跨类型切换。应用侧显示能力和运行环境，保存关联真实registration；缺乏已核实合同的钉钉SDK保持不可选并给出原因。无用回调不出现，不再展示flow/version/通讯录开关。
8. 验证最小模块后跑完整后端及Admin单测/构建/Cypress，单独运行隔离迁移/并发IT；Java定向Checkstyle、前端无fix ESLint、diff和脱敏检查。记录通过、跳过和未实测项；真机、真实第三方Code与运行镜像更新需单列证据，不以Mock或源码代替。用户本次要求改版不包含自动改业务库或自动更新镜像。

### Risks
- 注册身份域：绝不合并微信不同AppID或企微不同AgentID；同名/共同UnionID不是自动迁移依据。保留既有registrationId与用户绑定，新增能力用同一真实注册才可复用身份。
- 兼容读写：旧flow、只配置型来源和旧PUT不可被新UI重置。无法安全表达新能力关联的旧客户端请求应拒绝，不能静默降级并覆盖。首次启用新模型后不能把不识别新字段的旧IAM直接回滚上线；保留升级前备份，先停新写入口并部署理解新合同的版本，不提供破坏性down迁移。
- 能力撤销与并发：来源版本必须覆盖能力/凭据变化；引用保留不代表可登录，发起和锁内绑定都校验当前启用状态。缓存更新只after-commit，失败不驱逐成功版本。
- 官方证据：企微小程序必须实际关联该应用；SDK注册必须匹配AgentID。未核实钉钉旧SDK不得自动接新版Token端点；飞书资料Token新版迁移与本次能力模型分离，不顺带改HTTP合同。

### Proof
- 共享类型/服务：微信注册独立、企微/飞书同注册多选、非法能力/未实现合同拒绝、凭据最小填写与不回显；老字段省略/显式清空/重复键、版本冲突及缓存回滚。
- 隔离MySQL：V21升级及空库初始化；迁移前后ID、密文、现有绑定/Client关联逐项快照相等，只有确切旧能力等价投影；并发改密/改能力CAS与失败无半更新。IT只用隔离库，不用业务库。
- IAM：同一企微源网页/SDK/小程序按独立端点合同调用；同一飞书源网页/AppLink/JSAPI能力互不混码；微信网站/服务号同平台不同registration的固定选择、跨注册相同openid不串绑定；用户输入能力/registration伪造拒绝；停用/改密后在途state与待绑定拒绝，原防重放/PKCE/组织准入/成员ID映射保持；专门证明非旧锚点新增能力可完成目录准入、应用Token缓存及后台刷新，且厂商调用期间停用/改密时不写过期Token或目录结果。
- Admin：真实多选组件保存回显、切换创建类型清理派生值、编辑不变注册、关联能力过滤与重复/失效源修复、微信四类型独立凭据、Code与网页登录双入口保持；1440/390px截图及溢出/页脚断言。
- 命令：mvn -pl macula-cloud-system,macula-cloud-iam -am test -Plocal；mvn test -Plocal；mvn -pl macula-cloud-system,macula-cloud-iam -am verify -Plocal -Dit.test=IdentitySourceCapabilitiesMigrationIT,IdentitySourceCapabilitiesConcurrencyIT,IdentitySourceBindingIT -Dfailsafe.failIfNoSpecifiedTests=false；Admin npm run test:unit -- --run、npm run build -- --mode docker、npm run test:e2e:ci；精确Java文件Checkstyle、无fix ESLint与git diff --check。先确认各IT的隔离数据库/Redis条件，未执行不写通过。

升级时旧state缺少能力绑定不能猜测补齐，应安全失败并提示重新发起；不删除或清理整个会话/Redis。旧同平台全局绑定数据仍保留，不新增向不同registration回退的路径。

### Plan review and approval
REVIEW.md未指定Stage3 reviewer，当前可用工具/技能未发现codex-plan-review；按sdlc-build回退sdlc-reviewer独立Plan pass，最多三轮，每轮仅复核新增问题与直接影响，记录本附录发现，不进行Bugs/Security/Compliance代码审查。审阅完成后向Rain展示本附录及发现并请求批准；未获本轮计划批准前不写源码、不改业务库、不更新镜像。

本附录Plan审阅实际2轮：Round 1发现1项Important（目录与应用Token缓存仍使用单合同锚点复查），已补齐文件/步骤/证明；Round 2仅复核修正，返回NO_FINDINGS。详见review-findings.md本日记录。审阅结束时状态为待批准；其后Rain已以OK批准，当前实现和实际验证见verification.md本日记录。

2026-11-10：Rain在修订计划及钉钉范围说明展示后回复“OK”，批准本轮修订，进入实施；微信四类型独立建源。此记录只适用于该修订，不批准提交、推送或运行环境更新。

实施清单补全：四个平台 authentication/{wechat,wecom,feishu,dingtalk}/*AuthenticationProvider.java 与 authentication/thirdparty/ThirdPartyIdentityResolutionService.java 同步消费已固定能力，否则回调核验仍退回旧锚点；此为步骤6既定认证链必要调用方，无新增登录合同。

实施清单补全：authentication/mfa/MfaCompletionService.java的现有来源证明增加已选能力并复查，避免完成栅栏回退到单flow锚点；不新增MFA方式或策略，旧缺能力的在途证明安全要求重新发起。

实施清单补全：System service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java、vo/OAuth2ClientConfigurationCatalogVO.java按每项能力发布可用性、架构/环境及合同摘要，原来源级sourceType不能代表新能力集合；这是步骤5/7实际消费的目录入口。

测试清单补全：IdentitySourceTestFixtures.java与ThirdPartyAuthenticationFlowTest.java适配固定能力标记和模式方法重载；四个平台及GitHub已有AuthenticationProviderTest通过共享fixture继续检查精确核验身份，不放宽Mock匹配。

测试清单校准：应用Token缓存现有真实Redis测试名为ProviderApplicationTokenCacheIT（非Test），本轮在该测试补非旧锚点能力验证；共享API凭据边界并入IdentityLoginCapabilityTest避免重复测试。

实施清单补全：PendingThirdPartyBindingRepository.java构造SDK账号证明上下文时直接携带已核验identity.loginCapability，而不使用网页模式推导；隔离绑定IT发现此共享构造器调用方，未新增SDK登录合同。

实施清单补全：System ApplicationProtocolConfigurationService.java和Admin src/utils/applicationProtocolRegistry.js同步清除协议投影/显式换协议时的新网页能力关联，防止旧投影残留；JSON重复字段检查与内部程序化setter分开以兼容BeanUtils投影。浏览器关联列采用TEXT并强制受控JSON，沿用原关联字段的字符串CAS语义；来源能力列仍JSON。

本轮实现未更改HTTP API路径；api/model连接器和ClientGuide组件直接复用既有调用，新增字段由clientPayload与目录消费，不为清单机械修改无行为变化的文件。未实现的IdentityCredentialTypeTest边界合并入IdentityLoginCapabilityTest；缓存验证使用现有ProviderApplicationTokenCacheIT。

实施收尾：已完成多能力模型、V22、运行时固定能力、管理端多选、按能力Client关联与兼容回归。实际577后端测试（576通过、1跳过）、21隔离集成、206前端单测、64E2E及静态检查结果见verification.md。未自批实现/发布门禁，未部署镜像或变更业务库。

2026-11-10：Rain回复“YES”并附根README.md，明确确认实现完成。已读取根README及testing.md，进入正式Stage4证据核验；本轮未新增源码改动、未重复请求实现确认。

2026-11-10：Rain截图要求只有一个应用类型时默认选中并隐藏选择。沿已接受的身份源创建/编辑UI范围，精确修改identityProvider/index.vue、identity-provider-policy.test.js、admin-identity-security-settings.cy.js及Admin README：仅新建/切换平台初始化唯一类型，编辑不调用清空配置的类型切换逻辑，微信多类型保留选择。无后端/Schema变化，验证采用现有相关组件单测、桌面/手机Cypress与构建。

2026-11-10：Rain指出钉钉移动SDK官方章节已明确统一应用。本次从官方页面脚本提供的.md入口取得完整iOS/Android/HarmonyOS及其Token/用户信息文档，确认SDK appId=Client ID/AppKey，服务端clientId=同一AppKey、clientSecret=同一AppSecret，authCode走现有userAccessToken。撤销“因HTML正文未取得而禁止新SDK能力”的错误限制，沿步骤2/4/6已批准的真实合同范围实施；精确文件：IdentityLoginCapability.java及相邻Test、IdentityProviderServiceImpl.java及Test、DingtalkExternalCodeVerifierTest.java、DingtalkClientTest.java、Admin ClientGuide.vue、已有identity-provider-policy.test.js及admin-identity-security-settings.cy.js、三模块README和本slug证据/研究说明。无需新Schema或新增换码端点；既有DINGTALK_OAUTH_V1合同和成员准入保持。

钉钉纠正同步identityProvider/index.vue的能力说明：企微小程序提示仅企微显示，钉钉SDK提示同一Client ID/AppKey和平台回跳配置，不在钉钉页面混入企微说明。


### 2026-11-10 已接受回调合同的表单缺陷修正

Rain报告飞书组织及回调用途不清、保存失败；运行日志确认URI前导空格触发后端400，且输入为HTTP。沿现有已接受HTTPS/组织合同补充字段说明，在Admin提交前trim并校验HTTPS、主机、userinfo、fragment及空白；不放宽后端校验或改变组织/授权合同。补充对应组件回归并运行前端单测/构建。多条绑定增量仍是独立draft，本修复不实施该增量。


## 2026-11-10 回调与多条绑定增量计划
Status: accepted
Author: Rain
来源：已接受applink-callback-spec.md及multi-binding-spec.md；本段独立待工程师接受，不改变此前已接受/已交付部分的状态。

## Files that change

以下均为仓库相对路径；花括号逐项展开为精确文件，不授权目录级任意修改。

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/{IdentityBindingMapping.java,IdentityBindingMappings.java}`：沿用单条类型，新增有界、无重复的集合解析/语义校验，兼容旧单条，不含数据库或厂商调用；新类型为本次新增。
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/{IdentityBindingMappingTest.java,IdentityBindingMappingsTest.java}`：共享语义及集合边界。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IdentityProviderForm.java`：bindingMappings输入及省略/显式空/显式null/重复JSON键；列表项只接收sourceField、targetField，禁止未知属性。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/{pojo/entity/SysIdentityProvider.java,vo/IdentityProviderVO.java,converter/IdentityProviderConverter.java}`：新增bindingMappings集合展示与存储字段；集合大于一项时旧单条字段不投影第一项。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/{IdentityProviderServiceImpl.java,OAuth2ClientConfigurationCatalogServiceImpl.java,OAuth2ClientConfigServiceImpl.java}`：保存、展示和应用渠道目录使用同一集合规则；移除旧来源回调对AppLink可用性的限制。
- `macula-cloud-system/src/main/resources/db/migration/V23__identity_source_binding_mappings.sql`：只新增nullable TEXT binding_mappings_json，不回填或修改已有凭据、版本、用户身份绑定和旧单条字段；空库和V22升级均可执行。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/{service/impl/IdentityProviderServiceImplTest.java,service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java,service/impl/OAuth2ClientConfigServiceImplTest.java,vo/SettingsModelJsonTest.java,integration/IdentityBindingMappingsMigrationIT.java}`：新旧JSON写入、CAS、能力兼容及隔离升级；最后一个为新增IT。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProvider.java`：只读集合列。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/{IdentityProviderConfigService.java,IdentityBindingMatcher.java,IdentitySourceBindingPolicyService.java,RegistrationIdentityBindingService.java}`：加载、不可变能力视图及集合匹配；初次解析与FOR UPDATE事务重查复用同一算法，不读取非权威旧快照。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/{OAuth2ExternalCodeParameterNames.java,OAuth2ExternalCodeAuthenticationConverter.java,OAuth2ExternalCodeAuthenticationProvider.java,ExternalCodeVerifier.java}`：provider_redirect_uri条件参数、已认证Client精确白名单及向厂商适配器受控传递。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/{FeishuExternalCodeVerifier.java,FeishuClient.java}`：仅AppLink使用本次受控回调及专用PKCE；不回退来源旧值。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/{IdentityProviderConfigServiceTest.java,IdentityBindingMatcherTest.java,IdentitySourceBindingPolicyServiceTest.java,RegistrationIdentityBindingServiceTest.java,IdentitySourceBindingIT.java}`：完整集合、来源视图及锁内绑定/并发。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/{OAuth2ExternalCodeAuthenticationConverterTest.java,OAuth2ExternalCodeAuthenticationProviderTest.java,ExternalCodeVerifierRegistryTest.java}`：参数拒绝、透传和默认适配器边界。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/{FeishuExternalCodeVerifierTest.java,FeishuClientTest.java}`：飞书精确HTTP合同及网页/JSAPI不回归。
- `macula-cloud-admin/src/views/system/{identityProvider/index.vue,oauth2Client/components/ClientGuide.vue,oauth2Client/components/OAuthProtocolSettings.vue}`：删除来源回调、展示多行绑定映射及增删操作、来源列表摘要和开关/编辑/状态更新无损；更新AppLink运行时代码示例。
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`：AppLink专用回调登记前置校验及指南参数生成。
- `macula-cloud-admin/src/locales/lang/{zh-cn.js,en.js}`：对应中文、英文及必要提示。
- `macula-cloud-admin/tests/unit/{identity-provider-policy.test.js,admin-identity-security-settings.test.js,oauth-client-details.test.js,oauth-client-configuration.test.js}`：多行交互、兼容回显和指南参数（复用现有应用详情/指南测试，不新增平行测试文件）。
- `macula-cloud-admin/cypress/e2e/{admin-identity-security-settings.cy.js,identity-provider-policy.cy.js,oauth-client-configuration.cy.js}`：1440/390px下多条规则创建/编辑/关闭、保存失败不丢草稿和AppLink无需来源回调。
- `macula-cloud-{system,iam,admin}/README.md`、`macula-cloud-iam/docs/third-party-integration.md`：字段/API/升级约束及代码示例。
- `.sdlc/iam-application-configuration/{spec.md,applink-callback-spec.md,multi-binding-spec.md,plan.md,review-findings.md,verification.md}`：接受记录、真实偏差、评审及验证证据。

## Order of work

1. 先核对实际POM/README/相邻实现与测试，保存本轮基线，保留既有混合工作区；不新增依赖，不修改Boot、Gateway、版本或部署配置。
2. 实现共享集合合同：精确sourceField/targetField元素；总数不超过全局合法映射组合数，禁止重复项、null项、未知键/枚举和半组。集合只表达已启用能力交集中的可信属性。统一线格式严格为`[{"sourceField":"mobile","targetField":"mobile"}]`，使用稳定枚举value而非枚举名称/Record默认source与target属性；IdentityBindingMappings提供显式严格JSON codec和受控条目映射，System Form/VO、存储及IAM共用其合同。拒绝JSON重复键、未知属性及嵌套对象，不直接把Record默认序列化结果写入列。
3. 追加V23及两侧Entity、System Form/VO/Converter与保存/目录校验。JSON列NULL按旧单条解析，非NULL即集合权威，坏数据不回退；旧省略写保留集合，显式null拒绝，[]仅允许关闭自动绑定；新旧字段同时提交必须等价，旧显式写入多条配置拒绝，不静默截断。关闭自动绑定清空映射，已建立用户绑定不动。来源CAS、递增版本、提交后缓存失效保持。
4. 将IAM ProviderConfig改为不可变集合，原构造重载仅把旧单条包装为一项；forCapability/forCode保留完整集合并逐条校验。初次解析和绑定事务锁内扫描同一集合：缺失/无匹配不贡献候选，任一规则多账号或多条规则命中不同用户立即拒绝自动绑定；命中均为同一用户才绑定。确定性排序执行规则，保留范围锁隔离级别校验、Client/租户/用户类型、目录准入和手机号证明专用约束。原绑定优先，不回写用户资料，不新增敏感字段授权。
5. AppLink增加provider_redirect_uri：converter限制单值、最长2048、合法HTTPS、无userinfo/fragment/首尾空白；provider在防重放占用及厂商调用前确认FEISHU_APP_CODE、来源能力及专用PKCE，再按该已认证Client.getRedirectUris精确匹配。不得用其他Client或前缀/通配匹配；不规范化后扩大匹配。其他Code类型一律拒绝此参数。未传参数返回invalid_request；不得读取来源旧回调补齐。平台URL仍固定，回调只作为飞书换码JSON值，不发起对回调的HTTP请求。修正provider来源检查为forCode选择实际能力视图，不能仍使用旧sourceType锚点判断。
6. 扩展ExternalCodeVerifier受控重载，默认拒绝回调或PKCE扩参；飞书适配器只在AppLink兑换使用provider_redirect_uri及provider_code_verifier，网页仍由IAM生成回调、JSAPI不发送这两个参数。删除System保存、渠道目录、IAM加载/能力选择中的来源回调必填/格式限制；旧列保留且忽略，不让历史坏值阻止新流程。
7. Admin删除来源回调字段与校验（包括上一轮刚增加的提示/HTTPS本地校验）；映射按行增删、各行目标随源字段联动且已选项去重。新建可添加规则，编辑及状态开关发送完整新数组；显示旧单项时等价转换。关闭后清空草稿映射，开启时要求至少一条。多能力变更须校验所有规则；保留凭据、组织及未保存值。接入指南展示真实必填参数，不把平台PKCE与IAM公共授权码PKCE混淆。OAuthProtocolSettings在authorization_code或FEISHU_APP_CODE任一启用时显示现有Client回调白名单，纯external_code时标明AppLink用途；共用现有settings.redirectUris保存，不新建来源字段。前端configurationIssues和System启用校验均要求FEISHU_APP_CODE在现有白名单中至少有一条满足AppLink边界的HTTPS地址，并保留原授权码条件；同一Client同时使用授权码时允许保留原合同内的localhost/127.0.0.1回调，不能因新增AppLink拒绝这些供IAM授权码使用的地址，但它们不能作为provider_redirect_uri；编辑状态切换不能清空其他用途回调。指南从Client配置读取示例回调。
8. 同步文档并执行下述Proof。未经用户要求不更新业务数据库、构建/重建镜像、提交或推送；一次性MySQL/Redis资源仅用于隔离验证，结束删除本轮创建资源。

## Risks

- 本次Token接口增加条件必填参数，旧FEISHU_APP_CODE调用必须升级；来源旧回调不再兜底。配套IAM/System/Admin部署后再维护多条规则，旧IAM看不到完整集合时不能保证新语义，禁止混版启用新配置；回退需先显式缩为一条或关闭自动绑定，不自动选首项。
- IAM Client登记地址与飞书后台登记地址是两处不同校验；现有Client白名单未包含业务App回调时须在应用协议配置补登记，无需在身份源重复填写。飞书后台实际匹配仍由平台负责。
- 任一命中且全部命中一致不要求所有字段必有值；冲突/多账号进入原账号证明。整个结果必须在原绑定事务内重查，不能只靠首次候选集合防止并发误绑。
- 来源字段能力交集、手机号证明及企业成员准入保持，尤其企微手机号/邮箱敏感授权仍暂缓。第三方实际未返回字段时不会凭配置伪造属性。
- V23仅加列，保留V21/V22及原列、密文、版本；历史非法集合必须安全失败，不能退回旧规则掩盖损坏。所有SQL验证仅在一次性本地库运行。

## Proof

- API/System：真实Jackson请求→集合校验→持久化JSON→IAM读取→VO回显往返保持sourceField/targetField及稳定值，未知/重复键拒绝；纯external_code+FEISHU_APP_CODE配置回调可保存、缺失被拒绝，开关用途不丢地址。集合空值/边界/重复/未知JSON属性、旧字段兼容与冲突、新旧字段并存等价性、旧PUT不截断、开关清空、能力交集、CAS失败无半更新/成功版本缓存不被驱逐；AppLink无来源回调仍可保存并出现在应用目录。
- IAM绑定：已有绑定优先；手机号或邮箱单独命中、同一账号双命中、不同账号冲突、单规则重复账号、全部无值/无匹配、非法字段、手机号证明边界、跨来源/Client/租户/用户类型；多个能力视图保持完整规则。锁内配置撤销/版本变化、属性或账号变更时不能沿用首次结果。
- IAM协议：AppLink正确回调+PKCE精确传入v3 Token JSON；缺失/重复/超长/HTTP/userinfo/fragment/空白/前缀/其他Client/其他Code类型均在调用前拒绝；有旧来源值但缺新参数仍失败；旧sourceType锚点为WEB的同一来源也能正确选择AppLink。网页、JSAPI、其他厂商及反重放继续通过。
- 隔离MySQL：空库迁移、V22→V23升级及再次启动0迁移，迁移前后旧来源/凭据/版本/Client/用户绑定摘要一致；隔离MySQL+Redis绑定IT验证事务范围锁与并发改规则，不连接业务库。
- 前端：多行新增/删除/联动/回显/重复拒绝、旧单项转数组、显式关闭/状态更新、失败保留编辑值；AppLink无需来源回调可保存，指南含专用运行时参数；没有authorization_code时AppLink客户端仍可登记、保存、回显回调白名单及指南地址，无需误开托管授权码。Vitest、Docker模式build、目标Cypress桌面/390px通过。
- 从最小相关Maven测试到根mvn test -Plocal，执行适用隔离IT；Java变更Checkstyle、前端无fix ESLint及git diff --check。记录实际输出与未运行边界；Mock不代表真实平台/真机授权成功。

Plan review：REVIEW.md未配置Stage3专用reviewer，默认codex-plan-review未安装，按sdlc-build回退sdlc-reviewer，仅Plan pass，最多3轮；结果记录review-findings.md。人工计划门尚未通过，不写源代码。


本增量Plan审阅结果：共2轮；第1轮2项已修正，第2轮NO_FINDINGS。见review-findings.md。待Rain明确接受本增量计划后进入代码实施；此前YES已记录为两项规格接受，不自批本次新增人工计划门。

2026-11-10 Rain在完整合并计划及两轮审阅结果展示后回复YES，接受本增量计划，开始实施。CLAUDE.md不存在，使用AGENTS.md及其按需规则；未提交或推送。


### 2026-11-10 本增量实施记录（Stage 3）

已按接受顺序实现共享严格集合codec、V23两侧映射、System保存/VO/目录、IAM完整集合匹配及锁内重查、AppLink运行时参数、Client白名单、飞书换码、Admin多行编辑和纯Code白名单入口；文档及开发回归用例同步。没有超出已批准文件范围，没有新增依赖、接口或凭据操作。实际沿用ProviderConfig旧构造重载/单条只读投影兼容既有测试与调用方，多条配置的投影为空，运行逻辑全部用完整集合。列表项用共享Entry的受控setter拒绝重复/未知JSON键及非字符串，存储用显式稳定值codec。

开发检查发现并修正：旧单条测试期待已替换的请求字段、旧来源回调本地验证；新增Client测试漏声明飞书固定合同；保留的APP模式旧测试曾使用网页合同绕过专用PKCE，现模式边界明确拒绝；前端迁移遗留的重复bindingMappings键及未使用selectedFlow已清除。测试不放宽候选/权限断言。原代码审批范围未变，无需新增计划偏差。原计划清单中的部分兼容构造调用/测试未修改，因保留重载且既有用例继续通过；实际新增用例集中在匹配器、来源加载、实际绑定IT和协议/保存契约。

开发检查已实际运行相关后端单测、Admin单测/构建、相关三组Cypress、Checkstyle及无fix ESLint；最新均通过。尚未执行本增量全量后端及隔离MySQL/Redis正式验证，尚未自批Stage4或发布门；详见verification.md本日Stage3开发检查节。需Rain确认实现完整后交sdlc-test正式验证。未更新业务库、镜像、提交或推送。


### 2026-11-10 正式验证中的适用测试前置适配

Rain确认实现完整后进入Stage4。现有`macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IdentitySourceCapabilitiesConcurrencyIT.java`把隔离库前置版本固定为V22；新增Entity读取V23集合列后，继续对V22运行会因缺列失去验证意义。本轮将其隔离库前置断言更新为V23并加入本增量文件清单，保留原CAS并发断言不变，执行最新Schema上的真实Mapper回归。这是同一验证策略下的测试环境适配，不修改认证行为或扩大功能范围，无需重新批准规格。


2026-11-10 Rain在实现结果及开发检查展示后回复YES，确认实现完整并授权进入正式Stage4。正式输出、Proof逐项映射与环境边界已记录verification.md“回调与多条绑定增量正式Stage4”节。本增量未更新业务数据库或镜像、未提交或推送。


### 2026-11-10 多条绑定行布局收敛

Rain基于已部署截图要求“搞成一行吧”。沿已接受多规则界面范围，将每条规则的源/目标下拉框、方向箭头和移除按钮排在同一行，权限提示单独放在行下方；不改匹配、校验、API、凭据或数据库。精确文件为identityProvider/index.vue及既有identity-provider-policy.cy.js，复用原浏览器场景核对1440/390px同排与无溢出，不添加镜像实现的单元测试。完成前端检查后沿同会话镜像更新授权仅更新Admin，其他服务保持。


## 2026-11-10 应用类型排他边界增量计划
Status: accepted
Author: Rain
来源：已接受application-capability-boundary-spec.md。此附录独立待工程师接受，旧plan已接受状态不代替本次批准。

## Files that change

路径均相对仓库；花括号按逐项精确文件展开，不授权任意目录。除明确标注新增，其余为已有文件。

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/{OAuth2ClientApplicationType.java,IdentityLoginCapability.java,IdentitySourceType.java}`：新增IN_APP、能力级网页/端内分类、各类型入口/数量白名单及已核实钉钉H5环境。
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ApplicationLoginPolicy.java`（新增）：纯共享矩阵与来源集合校验；数量按registrationId而非Code个数计算，小程序双Code同源；不访问数据库或厂商。
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/{ApplicationProtocolRegistry.java,OAuth2ProtocolConfiguration.java}`：IN_APP的网页/JSAPI协议组合、Service严格仅client_credentials及运行时规则；不改变历史迁移校验合同。
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/{enums/OAuth2ClientApplicationTypeTest.java,enums/IdentityLoginCapabilityTest.java,protocol/ApplicationProtocolRegistryTest.java,security/OAuth2ApplicationLoginPolicyTest.java}`：矩阵与组合正反用例；最后一个新增。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`：仅写入的convertToInApp显式确认标记，默认false，拒绝重复JSON键，不存数据库或VO。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/{OAuth2ClientConfigServiceImpl.java,OAuth2ClientConfigurationCatalogServiceImpl.java}`：正常/草稿/兼容路径共同校验能力和数量，受控转换及旧关联待整改信息；渠道目录按能力生成applicationTypes，不再按WECOM_WEB整源推断。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/{service/impl/OAuth2ClientConfigServiceImplTest.java,service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java,vo/SettingsModelJsonTest.java,integration/ApplicationLoginBoundaryConversionIT.java}`：保存、数量、转换原子性与隔离并发，最后一个新增IT。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/{pojo/entity/SysOAuth2Client.java,service/support/impl/SysOAuth2ClientServiceImpl.java,service/oauth2/MaculaRegisteredClientRepository.java}`：在既有RR读取中加载所需外部来源关联到非持久化视图，避免只看Code字符串无法证明同源；RegisteredClient固定注册关联且不从第一条猜来源。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/{OAuth2ClientProtocolPolicy.java,ProtocolAwareRegisteredClientRepository.java,ProtocolCheckingTokenGenerator.java,OAuth2ClientProtocolValidator.java}`：运行时入口/grant边界、当前授权版本及来源证明复核，保留原回调/Scope/S256验证。合法本地入口不因历史失配网页登录引用整体停用，只拒绝对应失配渠道。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/{BrowserLoginClientPolicyService.java,ThirdPartyLoginContext.java,AbstractThirdPartyAuthenticationFilter.java,RegistrationIdentityBindingService.java,PendingThirdPartyBindingRepository.java}`：能力级发起/回调/锁内绑定校验，state记录Client authorizationVersion，IN_APP固定来源证明，Native/Miniapp拒绝浏览器来源。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/inapp/{InAppLoginPolicyService.java,InAppAuthorizationRequestFilter.java}`（均新增）：专职端内当前来源/会话证明校验，以及仅授权请求范围内的安全上下文隔离，不改写全局Session或手工生成授权码。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/{HostedLoginClientPolicyService.java,HostedLoginPolicyFilter.java}`：端内/Service标准登录和短信发送拒绝，Native/Miniapp内置方式保留。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionService.java`：现有私有source proof携带服务端Client标识和授权版本，供MFA完成后保留端内来源约束；不降低原MFA要求或增加因子功能。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/{LoginController.java,ThirdPartyBindingController.java}`：端内分支先于“已有登录跳首页”判断、错误页不循环；绑定完成后沿原外部证明续转，不能把普通账号证明提升为任意端内登录。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/{ProtocolOAuth2Configuration.java,DefaultSecurityConfiguration.java}`：准确安装端内授权过滤器、同策成功/失败续转；不新增Servlet自动注册或改变其他过滤链顺序。
- `macula-cloud-iam/src/main/resources/templates/in-app-login-error.html`（新增）：仅展示容器不符/来源失效等可恢复错误及受控重试，不显示标准登录表单/平台Code/敏感参数。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/{authentication/inapp/InAppLoginPolicyServiceTest.java,authentication/inapp/InAppAuthorizationRequestFilterTest.java,controller/InAppLoginFlowTest.java}`（均新增）：普通会话不绕过、有效外部证明续转、完整安全链负例与环境分支。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/{authentication/hosted/HostedLoginClientPolicyServiceTest.java,authentication/hosted/HostedLoginPolicyFilterTest.java,authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java,authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java,authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java,authentication/thirdparty/RegistrationIdentityBindingServiceTest.java,authentication/thirdparty/IdentitySourceBindingIT.java,authentication/mfa/MfaCompletionServiceTest.java,controller/HostedLoginFlowTest.java,controller/ThirdPartyBindingControllerTest.java}`：已有入口、state/证明序列化、绑定/准入/MFA及当前版本回归。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/{service/oauth2/MaculaRegisteredClientRepositoryTest.java,protocol/oauth2/OAuth2ClientProtocolPolicyTest.java,protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java,protocol/oauth2/ResourceAuthorizationIntegrationTest.java,protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java}`：有效协议视图、数量/同源及最终发Token边界。
- `macula-cloud-admin/src/utils/{identitySourceCompatibility.js,oauthClientConfiguration.js,applicationProtocolRegistry.js}`：新类型模板、精确能力分类、单来源选择/替换、转换差异与Service模板；机器值与服务器元数据一致，不猜其他小程序平台。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`及其`components/{ClientCreate.vue,ClientApplication.vue,ClientLoginControl.vue,IdentitySourcePicker.vue,ClientProtocol.vue,OAuthProtocolSettings.vue,ClientGuide.vue,ProtocolEndpoints.vue}`：第六类应用卡片；Native/Miniapp可选内置与一个直接来源，Web/SPA显示网页来源图标，IN_APP只一端内能力；Service仅必要认证/Scope/Token参数；旧类型转换明确预览确认，不静默清理引用。
- `macula-cloud-admin/src/locales/lang/{zh-cn.js,en.js}`：新类型与入口/排他/整改/转换说明。
- `macula-cloud-admin/tests/unit/{identity-source-compatibility.test.js,oauth-client-configuration.test.js,application-protocol-registry.test.js,oauth-client-details.test.js,admin-identity-security-settings.test.js}`及`cypress/e2e/oauth-client-configuration.cy.js`：全矩阵、单来源替换、双Code同源、Service简化、转换与桌面/390px体验。
- `macula-cloud-{system,iam,admin}/README.md`、`macula-cloud-iam/docs/third-party-integration.md`：按六种类型说明实际入口、接入步骤和升级影响，保留厂商接口/官方原链接。
- `.sdlc/iam-application-configuration/{spec.md,application-capability-boundary-spec.md,plan.md,review-findings.md,verification.md}`：接受依据、实际偏差、评审和验证证据。

## Order of work

1. 核对真实POM/README/相邻实现与测试，保存本轮基线；保留混合工作区。使用既有Java17/Spring Security/Vue3/Element Plus/Jackson，不增依赖。现有application_type为VARCHAR(32)，现有authorizationVersion、配置CAS、关联表和协议子配置足够，不新增Schema、不修改历史Flyway或业务数据。先定义共享规则及正反矩阵。
2. 明确能力集合：WEB_SERVER/SPA仅WECHAT_WEBSITE、WECOM_BROWSER、FEISHU_WEB、DINGTALK_WEB、GITHUB_OAUTH；NATIVE仅APP场景（移动SDK/AppLink/现有号码认证）；MINIAPP只本平台已实现能力且两个微信Code同一registration；IN_APP仅WECHAT_OFFICIAL_ACCOUNT、WECOM_WEBVIEW、FEISHU_WEBVIEW、DINGTALK_WEBVIEW；SERVICE无用户来源。Native最多一个注册来源，Miniapp最多一个，IN_APP启用时恰一个来源和一个端内能力。草稿允许尚未关联，但不能夹带不支持的渠道。网页登录/端内分类按能力而非旧scene或sourceType锚点。
3. System正常save、草稿及兼容分支应用共享白名单和注册数量；新Native/Miniapp的browserCapabilityBindings和旧派生browserProviderBindings/Types、directProvider均须为空，IAM内置入口独立保存；Web/SPA拒绝external_code关联；Service启用只OAUTH2_1+client_credentials，拒绝用户Scope/源/内置登录和刷新；IN_APP根据选定能力自动约束网页授权码或JSAPI external_code。无源时不得发起登录。禁用/草稿不等于允许非法类型和跨平台引用，未完成的合法配置只保留草稿。
4. 实现显式转换：仅既有APPLICATION用途WEB_SERVER/SPA可提交convertToInApp=true变为IN_APP；平台管理的managed Client/RESOURCE_SERVER/其他类型或反向转换拒绝。操作仍走既有完整快照save，必须现有configurationVersion CAS；不能顺手换租户/用户类型或重置Client ID/原密钥/注册用户绑定。转换前UI预览保留的唯一端内能力、待移除的网页登录/不适用grant、内置入口和Origin/回调差异，必须明确选择后提交，不从旧Code自动推断。后端复核最终快照全部合法再原子更新主表、协议及关联，失败整体回滚；authorizationVersion递增，缓存仅提交后驱逐。浏览器端内能力可保留原机密认证；JSAPI必须none。原密钥哈希即使因none暂不参与认证也保留原值且后续IN_APP保存不隐式清空，绝不导出原文或让旧secret认证继续生效；这项受限保留仅用于已转换的IN_APP，普通公共客户端合同不放宽。
5. IAM从权威当前Client和具体关联读取共享规则：Native/Miniapp隐藏且拒绝所有第三方浏览器入口/回调；WEB/SPA只展示通用网页能力并默认停留IAM页，不按UA自动直登，明确配置的既有直登仅在合法通用网页能力内生效；钉钉通用网页登录按已核实官方PC/H5合同允许MOBILE_BROWSER。显示、显式发起、回调均校验，不能只改Picker。旧不兼容引用保留供整改，拒绝该失配渠道；Native/Miniapp合法内置登录及其正常SSO不因旧浏览器引用一并停用，不追溯撤销全局会话。
6. IN_APP网页入口：LoginController先解析受控SavedRequest及当前Client/唯一端内能力，再处理已有Authentication；有效企微/微信环境直接走已有平台授权路径，错环境/无源/撤销只显示专用错误及受控重试，不进入标准页，不循环跳转。JSAPI型只允许业务前端取码+external_code，不提供authorization_code托管入口或伪造通用SDK/重定向桥接。
7. 堵住普通SSO捷径：在授权服务器链内SecurityContext加载后、OAuth2AuthorizationEndpointFilter之前安装InAppAuthorizationRequestFilter，按单值client_id读取当前权威Client，只对IN_APP网页授权请求核对会话中的私有外部来源证明。有效证明必须经平台认证/账号绑定及原MFA完成，包含相同Client ID和authorizationVersion、当前唯一registration/capability/sourceVersion/flow，并绑定同一已认证用户；不能信任UA、query、表单或客户端自报证明。证明缺失/陈旧时仅为本次请求创建空SecurityContext交给原SAS流程，保留原Session中的其他应用SSO，不清空共享上下文或手动保存匿名会话；finally恢复请求上下文。框架仍先校验redirect/scope/PKCE再触发原RequestCache/登录入口，过滤器绝不向未校验redirect跳转或自行签授权码。IN_APP授权前、同意提交、Code换Token及最终生成前均重查当前版本和证明；普通密码/短信、其他Client、同厂商其他注册/能力及旧版本证明均不能绕过。
8. 证明复用现有ThirdPartyLoginContext/ThirdPartyAuthenticationToken和MfaCompletionService私有SOURCE_PROOF：发起记录Client authorizationVersion；回调再次校验，经过Session/MFA完成检查后才可作为有效认证凭据；MFA后若改成普通Authentication，须保留同一可信Client/来源证明，不在未完成阶段发Token。旧序列化context缺少新字段不得用于IN_APP，但普通既有网页状态按原合同兼容。手动绑定须已有当前外部pending证明，绑定成功后的sourceAuthentication沿相同检查续转；独立密码登录不能伪造绑定结果或来源证明。JSAPI直接Token不创建浏览器会话。来源停用/改密及Client转换/能力变化使在途旧state、pending和原授权Code按现有版本机制失效。
9. Runtime grant与锁内绑定使用同一类型/数量/来源规则。IN_APP标准PASSWORD/SMS入口和发码同时禁止；SERVICE所有非client_credentials Token请求拒绝；WEB/SPA拒绝端内Code；Native唯一SDK、Miniapp唯一注册/双Code同源不因请求自己挑registration而绕过。保留账号绑定、目录准入、反重放、Origin、Scope、PKCE及MFA原检查。
10. Admin根据类型呈现最少内容：Native单选SDK/AppLink来源并可开IAM内置；Miniapp单选本平台来源，微信来源两个Code可同时勾选；Web/SPA网页来源多选；IN_APP单选一个端内能力且不展示IAM标准登录开关；Service不展示用户登录/身份源/OIDC/回调/PKCE/刷新/用户资料字段，只保留应用、客户端认证、Scope、有效期和接口示例。单选替换须明确确认并移除草稿原引用，保存不截断。类型确定后仍只读，仅旧Web/SPA展示受控转换入口及差异。指南按实际入口给步骤，不展示不支持能力。
11. 同步文档，完成下述Proof，记录实测失败与修复；不更新镜像/业务库、不提交或推送。不改AGENTS、REVIEW、skills、hooks或MFA策略定义，不以配置绕开测试。正式Stage4仍按流程在实现确认后执行。

## Risks

- 旧Native/Miniapp的第三方网页引用、Web/SPA端内Code和多来源SDK不再允许新发起；保留引用明确待整改，不静默删用户绑定或猜类型。恢复需明确整改/转换，不能以旧缓存绕过新规则。未分类历史Client仍显示“待确认”，不能借缺类型启用任何新增能力。
- 普通IAM会话与端内来源证明是两回事：过滤器必须仅隔离本次授权请求，不能改写原Session导致其他应用退出；跨Client/来源/版本/MFA未完成证明均拒绝。授权端点、同意提交及Token阶段存在并发配置窗口，均须复查现有authorizationVersion与固定来源；不能只保护GET /login。
- 显式转换影响应用类型/grant/入口，必须CAS原子保存，保留Client ID、凭据哈希及已有用户绑定。JSAPI的none不等于将旧密钥解密交给前端；保留的未使用哈希不得让已禁用的认证方法复活。转换后回退类型仍禁止，需后续专门方案，不自动回滚业务数据。
- IN_APP网页方案需要真实平台容器；JSAPI需要业务前端集成，不自动生成业务App。小程序同一来源双Code仍不能混码，手机号Code不代表永久平台subject。测试不会将UA视为身份或把Mock声明成真机成功。

## Proof

- 共享矩阵：六类应用×全部能力正反测试；Native一来源、Miniapp一注册/两Code同源、IN_APP恰一端内能力、WEB/SPA可多通用网页且无JSAPI、Service只client_credentials；草稿、未知类型/能力、半组关联、缺失/重复映射及历史兼容边界。
- System：新旧/完整快照/草稿保存同策，失败无半更新；转换标记缺失/伪造、managed/资源Client、其他方向、租户/用户类型变更拒绝；合法转换保留ID/密钥及用户绑定，版本递增和提交后缓存失效；并发转换只有一个胜者，失败不覆盖协议/关联或驱逐成功配置。隔离MySQL用ApplicationLoginBoundaryConversionIT验证实际Mapper/事务，不用业务库；无Schema新增，空库仍走现有迁移。
- IAM真实安全链/Mock厂商：Native/Miniapp标准页只有内置且不能显式/回调绕过，标准Web/SPA第三方图标及不按UA抢先跳转、钉钉手机网页登录；端内网页无会话/普通密码会话均进入固定平台授权而非直接发Code，正确证明才能授权；同意提交/Code兑换/Token生成时撤销或转换失败；非法redirect/scope/重复参数/PKCE请求不能先被重定向。其他普通应用既有SSO不受临时上下文隔离影响，Session fixation后当前来源证明仍有效，旧序列化/跨Client/跨注册/跨能力/过期版本拒绝；失败页不循环、无内置回退。
- 绑定/MFA：有效平台pending后才能显示专用账号证明；普通本地会话或伪造绑定上下文不能创建IN_APP来源证明。自动绑定、手动绑定、MFA通过/待完成/拒绝均覆盖，未完成不得有完整会话或Token；来源版本和Client版本变化后在途state/pending拒绝；直接SDK/JSAPI不建立浏览器SSO。
- Token负例：SERVICE请求authorization_code/external_code/sms/email/refresh均拒绝；Native误传WEBVIEW、Miniapp错平台/跨注册双Code、Web/SPA直交JSAPI、IN_APP误传SDK或本地grant均在厂商/绑定/发Token副作用前拒绝。转换后的旧密钥认证方法不能继续用，授权码/绑定/SSO不会绕过最新来源和Client版本。
- Admin：六类创建、数量限制/替换确认/双Code同源、第三方图标位置、IAM内置入口并存、IN_APP隐藏标准页、Service简化、旧失配提示/转换预览/失败保留及ID密钥不丢；桌面1440与窄屏390px单测和Cypress，来源配置既有单行多规则不回归。
- 验证顺序：最小相关API/System/IAM单测→根mvn test -Plocal；适用隔离MySQL/Redis验证转换/绑定及现有缓存，随后Admin全单测/build/全Cypress；变更Java Checkstyle、前端无fix ESLint及git diff --check。区分静态/Mock/数据库和真实厂商/设备证据；真实设备仍是部署环境验收边界，不在本计划伪造平台凭据。

Plan review：REVIEW.md未配置Stage3 reviewer，默认codex-plan-review未安装，按sdlc-build回退sdlc-reviewer，仅Plan pass，本增量最多3轮；结果记录review-findings.md。待独立审阅完成后展示人工计划门，不写源代码。


本增量Plan审阅完成：实际1轮，NO_FINDINGS，见review-findings.md。本轮未修改源代码、未执行测试、未更新配置/镜像或业务数据。待Rain明确接受本增量计划后实施；规格接受不自批该人工计划门。

2026-11-10 Rain在计划及第1轮NO_FINDINGS展示后回复y，明确接受本增量计划，开始实施。CLAUDE.md不存在，遵循AGENTS及按需规则；未提交/推送。


实施偏差（数量校验直接调用点）：补充精确文件`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`。原清单列了其测试、步骤9要求Token数量校验，但实际类需在厂商调用前核对整个受控关联集合，不能只依赖改枚举。此为已接受运行时数量约束的直接实现补全，不增加新行为/接口。

实施偏差：新增类型必须同步既有API模型常量 `macula-cloud-admin/src/api/model/system/oauth2Client.js`；该精确文件是类型卡片数据源，纳入步骤10，不改变接口范围。

实施偏差：pending绑定的当前Client检查位于 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`，补入精确文件以在账号证明前拒绝端内过期Client版本，不改变绑定策略。

测试素材偏差：补入 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`，为SDK素材明确NATIVE及映射Code字段，防止旧默认WEB_SERVER掩盖新矩阵。

测试装配偏差：补入 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`，真实安全链测试容器导入新策略Bean；测试当前Client明确写入固定来源，不再依赖基础RegisteredClient的旧映射。

运行时装配实测：SAS授权过滤器的顺序在configure阶段才注册，直接addFilterBefore该类会启动失败。改用已注册的SecurityContextHolderFilter作为addFilterAfter锚点，仍满足安全上下文加载之后、SAS授权处理之前的既定顺序，安全链测试核实实际位置。

真实端内OIDC授权码换Token实测发现框架兼容缺口：Spring Security 7 JwtGenerator从FactorGrantedAuthority读取authenticationTime，已有ThirdPartyAuthenticationToken成功结果及手动绑定会话缺该记录。补入精确文件 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationToken.java`，仅在平台Code已验证的authenticated工厂添加标准AUTHORIZATION_CODE认证记录，绑定成功沿用它；不声明密码/短信等额外因子，不改变MFA完成策略或其检查点。新增真实链换Token及撤销用例证明。

Service简化落实：现有V16已允许user_type为空，无需Schema变更。SERVICE不再要求用户目录，隐藏访问授权的用户类型分区；IAM仅对SERVICE允许缺用户目录，其余用户应用仍强制租户和用户目录。租户与资源Scope约束保留，不生成虚构员工目录默认值。现有精确文件内实现并补机器Token测试。

测试素材偏差：补入 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImplTest.java`，直接覆盖当前协议/来源映射装配与重复映射失败关闭，以及旧网页引用不全局停用Native入口；使用生产两参数构造，不依赖兼容构造。

锁内版本复核补充：精确补入 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingPolicyService.java` 与 `ThirdPartyIdentityResolutionService.java`，将浏览器服务端context中的authorizationVersion透传至自动绑定；手动pending也透传至同一bind重载。端内浏览器绑定在Client锁内比较原版本，关联表锁定读避免RR旧快照；直接Code和普通Web兼容重载保持原合同。


### 应用类型排他边界：Stage 3 实施记录

代码已按本增量步骤1—11实施，等待Rain确认实现范围；此记录不自批Build退出门，也不代表Stage4通过。

- 新增IN_APP与共享能力/数量规则；System草稿、完整快照、兼容保存及IAM发起/回调、直接Code、锁内绑定与最终Token复核同步使用。
- Native仅一个SDK/AppLink，Miniapp仅一个本平台注册（微信双Code可同源），可同时使用IAM内置入口；其IAM页面没有第三方网页入口。Web/SPA只开放通用网页能力，不按UA自动分流。
- 端内网页使用固定平台授权，JSAPI使用业务前端取码；普通SSO缺少当前平台证明时仅隔离该次授权请求，保留共享Session；授权/同意提交/Code换Token、来源版本和Client版本撤销均有开发检查。
- 受控Web/SPA→IN_APP转换有预览和Boolean确认、原完整CAS，保留Client ID、原密钥哈希和用户绑定，更新授权版本；none不能使用旧密钥认证。
- Service固定OAuth2.1/client_credentials，不要求用户目录，不展示用户登录/用户目录、回调、Origin、PKCE及刷新等配置，认证租户与业务Scope保留。
- 真实SAS换Token用例暴露并修复SS7第三方认证时间记录缺失；关联锁定读及原Client版本透传堵住旧pending在并发改配后的绑定写入窗口。精确文件补充及原因见本节之前逐项偏差。
- ApplicationProtocolRegistry本身无需新增注册项或Schema，实际协议约束落在OAuth2ProtocolConfiguration及System/IAM调用点；未为保持文件清单而制造注册表改动。DefaultSecurityConfiguration原成功/失败续转与私有证明机制可复用，无需新增分支。

开发检查：后端25个相关测试类去重187项通过（多批次，非根全量）；Admin 213项单测、build、应用配置页33项Cypress通过；46个本轮Java文件Checkstyle 0错误；无fix ESLint 0错误（1732条排版等warning，保留工作区既有风格）；差异空白检查通过。实测失败和修复、命令及边界见verification.md的本轮Stage3节。

新增ApplicationLoginBoundaryConversionIT代码已经testCompile，尚未执行隔离MySQL。根全量Maven、全Cypress与适用隔离数据库/缓存正式回归属于待确认后的Stage4。未变更历史Flyway/Schema或业务数据，未更新镜像、提交或推送。

2026-11-10 Rain回复“YES，更新镜像”，确认Build实现并授权本地System/IAM/Admin镜像刷新。正式Stage4首跑转换IT的旧素材断言cache.clear，但现有生产清理使用立即invalidate；更新两处断言为invalidate，仍要求提交一次、冲突/回滚零额外失效，不修改生产清理策略。修正后重跑正式验证。

正式Stage4完成且Verification is green；按Rain同条镜像更新授权已刷新System/IAM/Admin，三服务健康，现有配置摘要不变，其余11容器未重建。完整正式证据及部署记录见verification.md对应本轮节；未自批PR/合并、未提交或推送。
