# Plan: 身份与安全设置管理 (from spec.md 2026-10-13)
Status: accepted

## Files that change
- 数据和依赖：现有工作区已有 `macula-cloud-system/src/main/resources/db/migration/V2__admin_identity_security_settings.sql`、相关 POM 与配置改动。本轮新增 `macula-cloud-system/src/main/resources/db/migration/V3__identity_provider_json_config.sql`，仅扩容身份源凭据列并增加备注列；不编辑 V1/V2。其他依赖或部署文件只在验证确有必要且记录偏差后修改。
- System 持久化：现有工作区已包含 `pojo/entity/SysIdentityProvider.java`、其余五类设置/OAuth Entity 及对应 Mapper。本轮只调整 `SysIdentityProvider.java` 的备注映射和加密凭据列语义；必要时调整 `mapper/SysIdentityProviderMapper.java` 的查询，不修改 IAM Entity 或其他持久化模型。
- System API 模型：现有工作区已包含 `form/IdentityProviderForm.java`、`vo/IdentityProviderVO.java`、`enums/IdentityProviderType.java` 及其余设置和 OAuth 管理模型。本轮调整这三个身份源类型以支持分类、备注、对象型 `config` 与 `configuredSecrets`；视实现需要在 `service/impl/` 或 `enums/` 新增本仓库自有的六类字段模式/校验类型。新增 Java 顶层类型按版权头、类级 Javadoc、`@author Rain`、`@since` 编写，不依赖或复制 eIAM 类。
- System 业务与协议：现有工作区已包含五类 Service/Controller、`RootAdminGuard` 和消息适配。本轮修改 `service/IdentityProviderService.java`、`service/impl/IdentityProviderServiceImpl.java`、`controller/IdentityProviderController.java`、`pojo/entity/SysIdentityProvider.java`、`mapper/SysIdentityProviderMapper.java`（仅实际需要时），完成分类过滤、差异字段校验、敏感字段拆分与旧记录读取兼容；其他服务维持现有范围。
- Admin：现有工作区已包含五个页面和 API 封装。本轮修改 `macula-cloud-admin/src/views/system/identityProvider/index.vue`、`src/api/model/system/identityProvider.js`（仅参数适配时）、`src/locales/lang/zh-cn.js` 与 `en.js`；使用现有 Element Plus 组件展示社交/企业页签、六种专属字段及列表图标，不引入新前端依赖或复制参考项目素材。菜单仍沿 V2 动态路由。
- 测试与文档：修改 `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`、`macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`、`macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`，并在隔离数据库测试 `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java` 覆盖 V3 与加密列；修改 `macula-cloud-system/README.md`、`macula-cloud-admin/README.md`。其余现有测试保留，IAM 运行时不改。

## Order of work
1. 冻结现有工作树：检查 `git status --short`、现有 V2、身份源 Form/VO/Entity/Service/Controller、页面/API/测试及 README；只改本轮列出的身份源文件，保留其余功能和用户改动。先核实隔离数据库与本地 Compose 的身份源记录数量，不把本地空表推断为其他环境为空。
2. 先添加失败用例：后端覆盖六种字段模式、分类不匹配、对象型 `config`、未知或跨类型字段、Secret/私钥不回显、凭据保留、旧记录无损读取及无效记录拒绝；前端覆盖页签切换、分类查询、差异表单和请求体。测试数据只用虚构值。
3. V3 迁移与后端模型：使用现有 Flyway、MyBatis-Plus、Macula Boot `@CryptoField` 及项目已引入的 Jackson 工具，不增加依赖；在独立 V3 中增加备注并扩容加密凭据列，不触碰 V1/V2。将 `IdentityProviderForm`/VO 改为 `config` 对象，Entity 保留 `config_json` 文本与加密凭据列。为旧 `app_id`/`callback_url` 定义读取合成和同步策略，迁移不覆盖已有记录。
4. 后端业务和 API：按提供商白名单校验类别与字段，拒绝未知字段；非敏感字段写入规范 JSON，敏感字段拆出加密，更新空值保留、显式值替换；类型不可变；列表按分类过滤。读取旧记录只公开白名单字段，遇无效/可疑 JSON 明确失败并提示处理，不静默丢弃。
5. 前端页面：社交/企业页签分别查询、展示和添加，类型切换清理旧字段，按类型呈现标签与必需项；编辑只填回非敏感 `config`，凭据不回显；使用自有/现有图标和中英文文案。保留加载、空态、失败、重复提交和现有 Gateway URL 权限边界（V2 默认只授予 ROOT，可由系统配置调整）。
6. 文档与验证：补充 V3 备份/预检/回滚和新 API 契约；运行相关后端单测、System/API Maven 测试、隔离库迁移/加密往返、Admin Vitest/Cypress/构建及 `git diff --check`。按 `REVIEW.md` 做 Bugs、Security、Compliance 检查。正式测试签收属于 Stage 4；外部供应商实网投递仍需独立凭据和收件确认。

## Risks
- Boot 当前 `@CryptoField` 拦截器加密异常后可能继续写库。按已接受规格本轮不改 Boot、不承诺失败即中止；验证正常加解密路径，并在交付时明确残余风险，不把明文凭据放入应用日志。
- `sys_oauth2_client` 可空 secret 的迁移与 IAM 构造 `RegisteredClient` 的行为可能不兼容；需在开发早期用旧/新客户端样本验证。若必须改 IAM 运行时或扩大跨模块契约，先记录计划偏差并请工程师确认。
- System 与 IAM 的 Redis 缓存必须共享相同 cache name/key/序列化配置；缓存只能在事务成功提交后失效，避免旧密钥、租户或 scope 持续生效。
- SMTP AUTH 在目标 Exchange 邮箱已开放，但 TLS、证书和供应商账号/模板审核仍依赖外部环境；本地 SMTP/HTTP 模拟不能代表实网投递成功。自定义 HTTP POST 必须限制目标地址以防 SSRF，并禁止脚本/表达式。
- 新管理 API 不硬编码 ROOT 校验；Gateway URL 权限须覆盖读取、写入和测试发送，不能只靠菜单可见性。System 服务直连必须通过网络边界限制，否则可能绕过 Gateway URL 权限；全局配置不能受当前用户租户上下文误过滤。测试发送需限制滥用与敏感回显。
- 菜单 ID、权限码、Flyway 版本与已部署数据可能冲突；只增量迁移，提供数据库备份/回滚说明，不自动删除既有客户端、菜单或配置。
- V2 已在本地 Compose 执行，不能改历史迁移；外部环境可能已有身份源记录。V3 仅扩列，读取旧 JSON 时必须验证格式和字段；无法无损兼容的记录需由环境负责人处理后再升级，绝不自动删除或清洗。
- TopIAM 把私钥/Secret 放在单一 JSON 中，本仓库按已验收规格分离密文列。支付宝 PEM 私钥加密后可能超过旧列长度，V3 必须扩容并以长私钥样本做数据库往返；不得把原文持久化到 `config_json`、日志或 API 响应。

## Proof
- System 单测：Controller 不硬编码 ROOT；六种身份源的分类/字段/JSON/凭据更新与拒绝、旧记录兼容与异常拒绝；安全/密码边界、模板默认与覆盖、OAuth 租户和 PKCE/OIDC 等现有测试不退化。Gateway URL 授权与服务直连边界须单独验证。
- 数据库/跨服务集成：从 V1→V2→V3 和 V2→V3 升级、旧身份源行保留、长私钥密文往返且 `config_json` 无密钥；旧机密客户端与新公共客户端、IAM 缓存失效和 PKCE/OIDC 兼容维持原证明范围。区分真实隔离库验证与 mock。
- 网络适配测试：模拟 SMTP AUTH 和阿里/腾讯/自定义 HTTP 端点，覆盖成功、拒绝、超时、错误映射、临时目标与 POST 参数映射；仅凭真实测试账号和收件端确认实网投递。
- Admin Vitest/Cypress：社交/企业页签、六种字段、跨类型切换清理、对象型 `config`、凭据不回显，并保留五个入口的加载、空态、失败、保存与重复提交、租户选择、PKCE/OIDC、模板预览和临时测试目标；构建与 `git diff --check`。不把 IAM 第三方登录、运行时密码策略或细粒度权限配置误报为已实现。

## Deviations recorded during implementation
- Stage 5 二次复审发现 Redis 在 `afterCommit` 失效时不可用会留下已提交数据库值和旧 IAM 缓存之间的不一致窗口。当前缓存驱逐只在事务成功提交后执行，正常路径及隔离 Redis 往返已通过；故障路径没有可靠重试/持久化补偿，不能保证“下一次读取使用新配置”。这是一项 Important 阻断风险，需责任人明确接受或另行设计可靠失效机制，不能由代理自行豁免。
- 首次固定头修复复审仍发现嵌套 `authHeader` 豁免及 `X-Auth` 旁路；再次收窄豁免到顶层，并将固定头限制为 `Accept: application/json|text/plain`，保存和发送（含旧记录）双层校验。补充相应拒绝用例；独立复审确认原 Security Important 已解决。
- Stage 5 独立审查发现两项 Important：自定义短信固定参数/请求头中的 `X-API-KEY` 一类键名可能绕过原有明文密钥检查并在普通 JSON 写库/回显；模板被改成常量时缺少场景变量仍可能调用短信适配器。回到 Build：服务端将解析后的 JSON 键名规范化并递归拦截敏感键，限制 `authPrefix` 只能是方案名称加空格，UI 明示凭据只能填加密字段；渲染前校验场景全部必需变量，并补拒绝用例。修复后须重新执行 Stage 4 全量验证和受影响的隔离库测试，再复审这两项。
- Rain 后续明确要求移除 `RootAdminGuard`，权限由系统现有配置控制。以更新后的规格为准：V2 默认只为 ROOT 关联权限资源，Controller 不硬编码 ROOT。独立 Controller 测试只能证明该角色检查已移除，不能证明 Gateway URL 授权或服务直连防护；部署时必须限制 System 直连入口。
- Rain 参考 IAM `SmsLoginFilterConfigurer` 明确回调地址规则为 `IAM_URL` + `/login/{providerType}`。修正上一条临时采用的 `/login/oauth2/code/{providerType}` 路径；现有 providerType 数据 code 为大写，URL 段统一小写以匹配 `/login/sms` 约定。仅调整身份源类型元数据、VO 测试、Admin 浏览器 fixture 和文档，不改历史列或 IAM Filter。
- 本次浏览器验证发现身份源 Cypress 六类图标断言仍使用旧版 `img.provider-icon` 选择器，页面实际已改为 `el-icon` 内的 SVG。仅同步该测试断言到当前 DOM，以便继续验证只读回调文本及差异字段，不改页面图标行为。
- Rain 明确 `SysIdentityProvider` 不再映射历史 `callback_url` 列，但身份提供商 VO 与管理界面仍需只读显示将来 IAM `authentication` 包各提供商 `LoginFilter` 的回调地址。本轮在后端类型元数据中统一约定回调路径，VO/目录返回只读相对 URL，Admin 以既有 `IAM_URL` 组成完整展示文本；不加入 Form 或保存请求，不实现 IAM Filter。历史 V2 列保持原样，隔离库测试继续检查旧值不被覆盖；在 README 标明 Filter 尚未接入、地址须与后续实现一致。
- Rain 确认精简 `IdentityProviderConverter`：VO 的同名字段由 MapStruct 自动映射，仅显式标注从 Service 传入的分类、公开配置和凭据配置标志；Form→Entity 保留白名单字段映射，名称裁剪与布尔值归一化移回 Service。此调整减少映射注解和表达式，维持旧 JSON 校验、敏感字段不回显及现有 API 契约；以定向单测和生成代码检查证明。
- Rain 要求 `IdentityProviderVO` 不使用 record。本轮将其改为仓库常用的 Lombok `@Data` 普通类，并同步身份源单测及隔离库测试的 getter 调用；现有 MapStruct Converter 继续负责映射，JSON 字段名和脱敏边界不变，无数据库/API 结构变更。
- Rain 进一步要求将本批 System 管理配置中剩余的 record 全部改为传统类。表单、VO、身份源字段元数据、邮件/短信选项及密钥重置输入统一使用 Lombok Bean 与原字段名；同步 Service 和测试访问器，增加 JSON 读写兼容测试。不改 REST 路径、数据库或 IAM 运行时。
- Rain 要求身份提供商的 Entity/VO 等模型转换由独立的 MapStruct Converter 负责。本轮新增 `macula-cloud-system/src/main/java/dev/macula/cloud/system/converter/IdentityProviderConverter.java`，调整 `IdentityProviderServiceImpl.java` 与其单测；Service 保留旧 JSON 兼容校验、敏感字段拆分及写入规则，Converter 只映射已核验的公开字段，不改变 API、数据库或 IAM 行为。证明范围增加 Converter 的脱敏与字段映射断言。
- Rain 补充要求在六类身份提供商表单提供第三方创建应用链接和逐字段获取位置。本轮在 System `IdentityProviderType`/catalog VO 中增加官方入口、创建路径和字段提示，并由 Admin 表单展示；补充后端元数据与前端浏览器测试及 README。不改配置字段、数据库、IAM 登录运行时或权限边界。
- Rain 要求把 Admin `index.vue` 中的六家 `providers` 清单统一到后端，参考分类与类型设计。本轮新增 System `IdentityProviderCategory` 和 `IdentityProviderCatalogVO`，扩展 `IdentityProviderType` 的 code/标签/字段元数据及 ROOT 保护的 `GET /api/v1/identity-providers/catalog`；Admin 改为从接口加载分类和表单模式，保留既有数据库/API code 与凭据拆分逻辑，更新后端授权/元数据、Admin 单测和 Cypress。此项属于已接受第 5 步的规则归一化，不引入 eIAM AGPL 源码或新依赖。
- Rain 在官方图标版本部署后要求身份提供商列表改用角色管理同款 `scTable`，因为普通 `el-table` 加独立分页无法填满列表高度。仅调整 `macula-cloud-admin/src/views/system/identityProvider/index.vue` 的列表组件、查询/分页驱动与列表区伸缩样式；同步更新 Admin README、Vitest/Cypress 和规格要求 2。沿用现有 `scTable`、表格配置和身份源 API，不加依赖、不改 System/IAM；验证分类切换、搜索/分页请求和列表底部对齐。此项细化原计划第 5 步的页面布局实现。
- Rain 在本地部署后要求身份提供商列表使用六家各自的官方品牌图标，并参照角色管理列表收紧留白。Admin 将增加来自六家官网 favicon 的本地静态图标资源（不新增 npm 依赖、不在运行时访问第三方域名），修改 `identityProvider/index.vue` 为角色管理式顶栏和无内边距表格，更新 Admin README 与 Vitest/Cypress 验证。此项替代原计划第 5 步“自有/现有图标”的占位做法；不改后端、数据库或 IAM。
- Rain 要求身份提供商创建/修改入口改为 Drawer。仅将 Admin `identityProvider/index.vue` 的表单容器从 Dialog 换成右侧响应式 Drawer，保留字段、校验、提交与关闭重置行为；同步 Admin README、Vitest 和 Cypress，不改 API、System 或 IAM。
- Rain 要求检查同批管理配置界面并沿用 Drawer 交互。将 Admin `oauth2Client/index.vue` 的接入应用表单、`messageSettings/index.vue` 的供应商和模板编辑表单改为右侧响应式 Drawer；通用安全及密码策略继续页内编辑，临时测试发送和确认提示仍使用小弹框。同步 Admin README、Vitest 和 Cypress，不改后端契约或运行时。
- Rain 在部署后明确要求移除身份提供商表单的“回调地址”，由将来 IAM 登录代码确定回调路径。同步从 Admin 表单/请求、System Form/VO/写入逻辑移除 `callbackUrl`；保留已执行 V2 的 `callback_url` 列及既有值，不改迁移或 IAM 运行时。已同步更新 spec 和 README，前后端测试证明新请求不含该字段且编辑不会覆盖旧值。此项取代原计划第 3 步的 `callback_url` 同步策略。
- 身份源列表增加 `category` 参数后，现有 `controller/AdminSettingsAuthorizationTest.java` 的 mock 需要同步为五参数；该测试文件不在本轮原定文件表中，但属于既有授权契约的直接调用方。仅调整 mock，保持 ROOT/拒绝证明范围。
- `CLAUDE.md` 在该工作树不存在；按根 `AGENTS.md` 和 `.agents/rules/` 执行，未绕过仓库规则。
- 安全/密码配置使用单条 JSON 持久化，并分别新增 Form 与 VO；身份源和 OAuth 分页直接接收有界参数，未增加原计划的两个 Query 类型。字段仍经服务端验证，不改变 REST 路径。
- 消息设置 UI 的四个功能页签保留在一个 `messageSettings/index.vue`，未拆成四个只服务该页的子组件；发送适配器以 `*TestSender` 类实现，避免额外的空接口层。功能路径与原计划一致。
- Macula Boot 的加密自动配置只有 `macula.crypto.enable=true` 才生效；System `application.yml` 增加 AES/BASE64 和必需的 `MACULA_CRYPTO_PASSWORD` 环境占位。未修改 Boot，仍保留其加密异常处理风险。需要在环境提供 16/24/32 字符密钥后才能启动 System。
- 阿里/腾讯短信 SDK 版本未由 Boot BOM 管理，System POM 在目标模块单独固定本地可解析的 Apache-2.0 版本；未改根 POM或 API 模块。
- 自定义短信要求鉴权请求头与 `@CryptoField` 凭据，避免 Boot 拦截器对空凭据执行 `String.valueOf(null)` 加密后误认其已配置；OAuth 管理 API 不接受新 `client_secret_jwt` 配置，因本方案的单向哈希密钥无法供 HMAC 验签；`private_key_jwt` 仍保留。
- Stage 4 隔离数据库启动发现 System 原先没有 `CacheManager` Bean；已在 System POM 增加与 IAM 相同的 Macula Boot cache starter。为满足新的必需加密环境变量，增补 `deploy/docker-compose.yml` 的 System 环境映射及 `deploy/.env.example` 说明，未部署现有容器。
- 正式测试补充 `integration/IdentityProviderMigrationIT.java`：在显式启用且库名为 `macula_settings_it_*` 的全新空库，通过应用依赖的 Flyway 先升至 V2、插入旧行、再升至 V3，验证历史记录和扩列。独立 Flyway Maven 插件缺 MySQL 插件，不能代表应用运行时；首次执行还发现旧分支 `target/classes` 残留重复 V2，使用 Maven `clean` 清理生成产物后正式通过。该测试文件为原定文件表之外的验证补充。
- 正式浏览器测试发现身份源、通用安全与 OAuth 表单的 `saving` 锁在异步校验后才设置，存在双击重复提交窗口。修正这三个同构页面为校验前上锁，并在身份源 Cypress 中增加双击仅发一请求断言；`securitySettings/index.vue` 与 `oauth2Client/index.vue` 因直接属于本计划的重复提交保护要求，作为测试发现的文件表偏差记录。
