# Spec: 身份与安全设置管理 (from intent.md 2026-10-13)
Status: accepted

## Source intent
[已接受的意图](./intent.md)：在 System 与 Admin 提供身份源、安全、消息和 IAM 接入应用的管理配置；除邮件/短信测试发送外，不接入 IAM 的新运行时流程。

## Requirements
1. 管理端提供身份提供商、通用安全、密码策略、消息设置、IAM 接入应用五个可从菜单进入的管理功能；各页面具备读取、加载、空态、错误反馈和重复提交保护，并沿用现有 Vue 3/Element Plus/API 封装风格。
2. 身份提供商页面分“社交认证源”（GitHub、支付宝、微信）和“企业认证源”（企业微信、飞书、钉钉）页签；各页仅列出本类记录和本类可选类型，并提供名称查询、分页、添加、修改、启停、删除、图标/名称/状态展示。Gitee、QQ 不出现且服务端拒绝写入；服务端校验提供商与分类匹配，不能通过请求参数跨分类写入。
3. 身份提供商表单随类型显示并校验字段：GitHub 为 `clientId`、`clientSecret`；支付宝为 `appId`、`appPrivateKey`、`alipayPublicKey`；微信与飞书为 `appId`、`appSecret`；企业微信为 `corpId`、`agentId`、`appSecret`；钉钉为 `appKey`、`appSecret`，`corpId` 可选。名称、是否在登录页显示、启用状态与备注为通用字段。配置只保存，不在本轮接入第三方登录。
4. 身份提供商写入 API 接收结构化 JSON 对象 `config`，而非 JSON 字符串或要求管理员编辑原始 JSON。读取返回非敏感 `config` 对象与对应凭据的已配置标志，不返回已保存的私钥或 Secret；编辑时留空保留、显式输入替换。服务端拒绝未知字段、错误类型、缺失必需字段与跨类型残留字段；新增和更新均校验，提供商类型创建后不可变。
5. 通用安全配置覆盖截图中的用户并发数、会话时长展示、记住我时长、验证码有效期、连续失败统计窗口/次数、自动解锁时间及内容安全策略。仅为将来 IAM/网关接入保存设置；未接入的字段明确标注“暂未生效”，不宣称已改变认证行为。
6. 密码策略覆盖最小/最大长度、复杂度档位、过期与提醒、连续相同字符、用户信息检查、历史密码数量、非法字符序列、弱密码库及自定义弱密码；服务端校验边界与组合关系，保存后可回读。该策略本轮不参与密码设置或登录校验。
7. 邮件服务支持自定义、Microsoft Exchange 邮箱、阿里、腾讯、网易；Exchange 邮箱使用可配置服务器、端口、账号及 TLS 的 SMTP AUTH。短信服务支持阿里、腾讯、自定义。自定义短信服务可配置 HTTP POST 地址、鉴权及请求参数映射、成功响应判定，不允许执行用户提交的脚本。管理员可配置、启停及向临时输入的目标地址/手机号发送测试消息。测试使用已保存的当前服务配置，并返回明确的投递提交成功或失败结果；最终收件确认由管理员在收件端完成。
8. 邮件和短信模板类型由本仓库自有枚举定义默认名称、场景、变量与内容；未修改时读取默认值，启用或修改后写入数据库覆盖值。列表、编辑、预览和测试发送均读取有效模板；测试发送使用当前模板渲染，未知变量和缺失必需变量必须拒绝，不能把密码或密钥放入模板或日志。
9. 身份源以及邮件/短信供应商中需要可解密使用的凭据，在持久化 Entity 字段上使用 Macula Boot 现有的 `@CryptoField` 注解处理，不修改 Boot 加密实现。身份源的持久化 JSON 文本仅保存非敏感配置，密钥和支付宝应用私钥使用独立加密字段；读取接口只返回是否已设置凭据。密钥仅由部署环境注入，System 业务代码不记录凭据明文日志。现有 Boot 加密失败行为及风险见下文，不将“失败必定阻止写库”作为本轮验收承诺。
10. IAM 接入应用管理现有 `sys_oauth2_client`：分页查询、按 ID 查看、创建、编辑、删除和客户端密钥重置；录入时必须选择有效租户，并选择与 IAM `UserTypeEnum` 对齐的 `member`、`employee`、`odc`、`supplier`。不得把数据库 Entity 或已存储的密钥摘要直接作为 REST 响应。
11. 接入应用表单覆盖 `SysOAuth2Client` 的 clientId/name、认证方式、grant、回调 URI、scope、PKCE、授权确认、JWK URL、签名算法、code/access/refresh token TTL、token format、refresh token 复用、ID token 签名算法、用户类型及租户。必须支持授权码 + S256 PKCE 的公共客户端（`none`、无 client secret）和机密客户端，以及 `openid` scope、ID Token 签名与 OIDC 回调配置；不能将 `none` 认证或 `plain` challenge 当作免除 PKCE 的方式。机密客户端密钥仅在创建/重置时接收明文并按 IAM 现有 PasswordEncoder 格式单向哈希保存，不使用可逆的 `@CryptoField`；列表与详情不回显。服务端拒绝不合法的认证方式/grant/URI/算法/时长组合，保持现有客户端可读取。
12. 客户端创建、编辑、删除和密钥重置后，IAM 客户端缓存应失效，下一次读取使用新配置；失败的数据库写入不得先失效或产生部分更新。已签发授权和令牌的处理语义保持现状，并在页面提示配置变更不等于撤销令牌。
13. 所有新增管理 API，包括读取、写入和测试发送，默认只允许超级管理员访问；普通管理员、普通租户用户和匿名请求由 System 服务端拒绝。OAuth 客户端租户选择不得因请求参数绕过服务端校验。菜单/按钮可见性不是唯一授权边界。细粒度授权配置界面留待后续。
14. 新增表、索引、菜单和权限初始数据提供可升级迁移；不修改历史 V1/V2 已执行迁移。同步更新 System/Admin README 与操作说明，不提交真实凭据或供应商地址。

## Non-goals
- 不实现第三方身份源的授权回调、账号绑定或 IAM 登录页接入。
- 不将通用安全或密码策略应用到 IAM、Gateway 的运行时判断；不新增真正的验证码发送、登录锁定、密码历史写入或 CSP 响应头逻辑。
- 不替换现有 System 应用管理，也不实现 OAuth 动态客户端注册、授权记录清理或 token 撤销。
- 不开发权限配置界面，不升级前端框架、Spring Security 或供应商 SDK 的无关版本。
- 不复制 AGPL-3.0 eIAM 源码、HTML 模板或模块；只参考业务场景。

## Design
适用政策：根 `AGENTS.md`、`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`、`REVIEW.md`；未发现额外的组织级品牌或数据处理 Skill。Macula Boot 加密字段设计参考其 `macula-boot-starter-crypto/README.md` 与 MyBatis 拦截器实现。

System 提供管理 API、业务校验、模板默认值与持久化；Admin 以动态菜单加载页面，通过 `src/api/model/system` 访问 System。身份源、通用安全、密码策略、消息服务与模板使用平台级数据，不接受由前端传入的租户作为隔离依据。身份源分类由服务端按类型决定，社交/企业页签分别查询相应分类；服务端也验证请求中的分类与类型一致。前端用六种类型的字段描述生成可读表单，不让管理员编辑原始 JSON 字符串；服务端用自己的类型模式独立校验，拒绝多余字段。回调 URL 本轮仅作为可选管理配置，不生成可用的 IAM 第三方登录地址。

身份源请求的 `config` 为 JSON 对象，例如企业微信 `{ "corpId": "…", "agentId": "…", "appSecret": "…" }`。服务端拆分敏感和非敏感字段：`config_json` 保存规范化的非敏感 JSON 文本；Secret/支付宝应用私钥进入 `@CryptoField` 标注的加密列。详情只重建非敏感 `config` 并返回 `configuredSecrets` 标志，不能将密文、明文或私钥作为普通 JSON 字段回传。更新时敏感字段缺省或空字符串保留原值，显式非空值替换；非敏感字段完整校验后整体替换。类型不可修改，避免既有加密列被解释成另一提供商的凭据。

邮件模板使用本仓库自有场景枚举和内容资源；短信模板使用自有场景枚举，供应商模板 ID/CODE 按服务商及语言保存。有效模板为数据库已启用覆盖值，否则为枚举默认值。预览和测试共用受限变量渲染器；仅替换白名单变量并对邮件 HTML 做必要转义。测试发送不写入业务通知队列，不将测试目标持久化为业务联系人。

邮件发送统一由 System 中的供应商适配层处理，各提供商的连接参数和错误映射独立；Exchange 提供专门的邮箱类型，以目标邮箱已开放的 SMTP AUTH 发送，服务器、端口、账号及 TLS 由管理员配置。短信的阿里/腾讯/自定义适配分别处理认证与请求格式；自定义适配只发送配置好的 HTTP POST，支持固定请求头/鉴权字段、目标手机号和模板变量到参数的映射，以及受限的状态码/响应字段成功判定，不执行表达式或脚本。测试接口实施目标地址/手机号格式校验、短时限流、连接与响应超时、审计，并仅返回非敏感状态。真实投递需要外部供应商凭据和网络，自动化测试使用本地模拟服务与拒绝路径。

OAuth 客户端管理在 System 内使用独立 Entity、Form 和 VO 映射同一 `sys_oauth2_client` 表，不依赖 IAM 实现模块；Service 负责唯一性、租户存在性、与 IAM 枚举兼容的取值校验、密钥编码、事务及共享缓存失效。`client_id` 创建后不可变。公共客户端的 `client_secret` 为空且强制 S256 PKCE；机密客户端的密钥保留现有单向哈希格式，可配置强制 PKCE。OIDC 配置沿用 IAM 现有的 `openid` scope 与 ID Token 签名设置；IAM 当前总是向 RegisteredClient 加入 `openid`，页面不得误导管理员以为可以单独关闭 OIDC。不会要求浏览器保存机密客户端密钥。IAM 现有 `RegisteredClientRepository` 与用户租户加载路径保持兼容。

全部新增管理 API 在 System 服务端核对超级管理员身份，API URL 权限及菜单初始化与 Gateway 的现有机制对齐；默认拒绝无身份、普通管理员和普通租户用户。前端仅辅助隐藏入口。细粒度授权扩展留待后续。

## Data and interfaces
- 平台级身份源配置、全局安全配置、密码策略配置、邮件/短信服务配置与模板覆盖表沿现有 V2 结构继续升级。身份源需要备注列，并将现有 `app_secret` 凭据列容量扩至可容纳加密后的支付宝私钥；这些变更使用 V3 增量迁移，不修改已执行的 V1/V2。分类由 `provider_type` 确定，不另存一份可能冲突的分类值。空库仍顺序执行 V1→V2→V3。
- 身份源 API 保持 `/api/v1/identity-providers` 路径。列表增加 `category=social|enterprise` 过滤，详情和写入使用 `providerType`、`name`、`remark`、`enabled`、`visible`、结构化 `config`；返回 `category` 与 `configuredSecrets`，不返回凭据。非敏感配置序列化到现有 `config_json` 文本列；现有 `app_id`、`callback_url` 用于兼容旧记录，在迁移期间保留并按需同步。服务端对六种类型分别维护字段白名单和必需项。
- 现有旧记录不就地覆盖：新读取逻辑从 `provider_type`、`app_id`、`callback_url` 和有效的 `config_json` 合成非敏感 `config`；只暴露白名单字段，识别到未知或疑似敏感字段时拒绝自动转换并报告需要人工处理的记录。首次成功编辑后写入规范化 JSON。迁移前统计并备份实际记录；本地 Compose 库当前为 0 条，但不能代表其他环境。若外部环境存在无法无损转换的记录，暂停该环境升级，不静默丢弃配置。
- `sys_oauth2_client.tenant_id` 保留且必填；`user_type` 取值对应 IAM `UserTypeEnum`，不新增跨模块运行时依赖。既有 OAuth 客户端数据原样保留；新迁移将 `client_secret` 改为可空，以支持 `none` 公共客户端。需验证 IAM 现有 `RegisteredClientRepository` 对空密钥及 PKCE/OIDC 的兼容。
- System 其余管理路径使用 `/api/v1/security-settings`、`/api/v1/password-policy`、`/api/v1/message-settings`（含模板与测试子资源）、`/api/v1/oauth2-clients`。列表/详情 DTO 永不包含 secret 明文或哈希；更新凭据使用专门的写字段和“已配置”标志。
- Admin 新增相应 API 封装与动态菜单页面，菜单和按钮资源在数据库迁移中建立；页面文案同步中文与英文资源。

## Flagged concerns
- JSON 配置与敏感字段冲突：TopIAM 的单个 JSON 字符串可包含私钥/Secret，但本仓库要求凭据经 `@CryptoField` 加密且不得回显。上述“API 中为 JSON、持久化时拆分敏感列”是刻意不照搬参考实体的设计；blocking，Rain 已在本轮规格评审中明确接受该拆分方案，阻塞决策已解除。
- 已有身份源记录兼容：本地 Compose 库为 0 条，其他环境未知；V2 已执行，不能修改其校验和。V3 升级前必须备份并检查无效 JSON、未知字段和潜在明文密钥，无法无损转换时需由环境负责人决定修复或隔离；non-blocking（不阻止规格评审，阻止未经核对的数据环境升级）。
- 支付宝私钥列长度：当前 `app_secret varchar(2048)` 对加密后的 PEM 私钥可能不足，V3 必须扩容并验证实际长度；non-blocking（设计已有迁移路径，实施时须测试）。
- 加密失败可能落库明文：当前 Macula Boot MyBatis 加密/解密拦截器捕获异常后只记录日志，且对空值使用 `String.valueOf(null)`。Rain 已决定本轮按 Boot 原样使用 `@CryptoField`，不修改拦截器，也不承诺失败时阻止写库。该风险需在交付说明中明确；后续若要求失败即中止，应在 Boot 独立修复并验证，non-blocking。
- 公共客户端数据库兼容：已决定迁移 `client_secret` 为可空；还需用迁移与协议测试证明 IAM 对空密钥、`none`、PKCE/OIDC 以及旧机密客户端兼容，non-blocking。
- Exchange 邮箱连接：Rain 已确认目标邮箱开放 SMTP AUTH；本轮仅按此传输方式支持，若目标环境关闭 SMTP AUTH，须另立 EWS/Graph 接入需求，non-blocking。
- 超级管理员边界：已决定所有新增管理 API 服务端仅允许超级管理员；现有 Controller 多数未做方法级角色校验，需验证角色识别与服务直连拒绝，不能只依赖菜单或 Gateway，non-blocking。
- 旧客户端缓存：IAM `MaculaRegisteredClientRepository` 按 client ID 缓存，System 变更若未失效会继续使用旧密钥、scope 或租户。需证明共享缓存键和失效顺序，non-blocking。
- 邮件模板参考包含明文密码变量：eIAM 的重置密码成功模板有 `${password}`，与本仓库敏感信息规则冲突。本实现保留该业务场景但不发送明文密码，non-blocking。
- 外部服务联调：真实邮件、短信投递取决于供应商账号、网络、模板审核及收件端；本地自动化只能证明协议请求、成功/失败映射和模板渲染，部署验收需单独提供测试凭据，non-blocking。
- 配置效果范围：安全、密码和身份源配置尚不驱动 IAM 运行时，页面须明确提示；后续接入需另立需求，non-blocking。

## Verification strategy
1. System 单元测试覆盖社交/企业分类与类型不匹配拒绝、六种提供商的必需/可选/未知字段、JSON 对象结构、敏感字段拆分、凭据空白保留与替换、详情不回显、旧记录兼容和异常记录拒绝；同时覆盖全局配置校验、模板默认/覆盖优先级、变量转义与缺失拒绝、超级管理员允许及其他身份拒绝、OAuth 客户端参数组合、租户有效性及密钥编码。
2. 使用隔离数据库或 Testcontainers 验证 V1→V2→V3 及已执行 V2→V3 迁移、`@CryptoField` 正常路径往返、原始数据库 JSON 不含密钥、加密私钥列长度、旧身份源数据保留、旧机密客户端与新公共客户端读取，以及 System 写入后 IAM 缓存失效；记录 Boot 现有加密失败行为，不把失败中止作为通过条件。协议测试覆盖授权码 + S256 PKCE、`none` 拒绝无 PKCE、OIDC ID Token 和旧客户端兼容。不能以 mock 证明真实加密或共享缓存兼容。
3. 邮件和短信适配使用本地模拟服务验证真实网络调用、模板渲染、超时和错误；自定义短信测试覆盖 HTTP POST 参数映射、鉴权字段、成功响应判定与非成功响应拒绝。Exchange 使用 SMTP AUTH 模拟服务验证连接参数及失败映射。供应商实网投递仅在提供测试凭据时进行，并由收件端核实。
4. Admin Vitest 与 Cypress 验证社交/企业页签分类过滤、六种差异表单、切换类型后字段清理、JSON 请求对象、密钥不回显，以及各页加载、空态、异常、保存、重复提交、按钮/路由访问及不同模板测试发送参数；执行相关构建并检查页面文案。
5. 运行 System/API 定向 Maven 测试、前端相关单测与构建、`git diff --check`；按 `REVIEW.md` 分别检查 Bugs、Security、Compliance，明确未完成的实网验证。
