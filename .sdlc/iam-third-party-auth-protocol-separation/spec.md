# Spec: IAM 第三方身份认证与协议解耦及企业微信双场景登录 (from intent.md 2026-10-19)
Status: accepted

## Source intent
[已接受的意图](./intent.md)：第三方身份核验独立于 IAM 对外协议及 OAuth2 client；核验后安全关联本地账号，支持企业微信系统浏览器新版登录与内置 WebView 网页授权，并保持原有入口和回跳兼容。

## Requirements
1. GitHub、企业微信的登录发起、回调、身份核验、本地账号登录和令牌签发均不以租户或 IAM OAuth2 client 为前置条件。OAuth2/OIDC 等协议仍在各自入口及续转时执行自身的客户端和请求校验；第三方身份不能代替这些校验。OAuth2 client 中的租户配置用于登录后按租户加载角色权限，不参与身份匹配或决定能否签发令牌。
2. 第三方回调只产生已核验的提供商类型和稳定 subject；在完成本地账号关联、状态校验和权限装载前，不建立具有本地账号权限的认证会话。GitHub 只使用稳定数值用户 ID；企业微信只接受企业成员 `UserId`，不得退回昵称、邮箱、手机号或非成员标识。
3. GitHub 已有唯一有效绑定时可直接完成本地登录；未绑定时进入绑定界面。企业微信先以 `UserId` 查找唯一有效的全局本地 `username`，查不到才查已有绑定并在仍未绑定时进入绑定界面。直接匹配与既有绑定指向不同账号、重复匹配或账号无效时必须拒绝，不能自动改绑。
4. 新绑定仅允许传统密码、一次性短信验证码或当前已登录会话证明目标本地账号归属；输入 `username` 本身不是证明。证明成功后，才可原子创建 `(provider_type, provider_subject) → username` 绑定；不提供新用户注册或静默改绑。密码、短信验证码和会话凭据不得被回显、记录或持久化到绑定表。
5. 第三方授权 `state` 使用 Spring Security 的 `Base64StringKeyGenerator(Base64.getUrlEncoder())` 生成，并与浏览器会话绑定，保持短有效期、一次性消费和 CSRF 防护。外部身份待绑定状态也须绑定会话、短有效期且一次性消费，不能被当作已登录账号。绑定界面及接口须限制重复尝试、避免账号枚举，并在取消、过期、失败或会话更换后失败关闭。
6. 系统浏览器中的企业微信自建应用登录使用[官方新版文档](https://developer.work.weixin.qq.com/document/path/98152)的自构造链接 `https://login.work.weixin.qq.com/wwlogin/sso/login`，以 `login_type=CorpApp` 及受控的 CorpID、AgentID、redirect_uri、state 发起；不引入 JS 登录组件。企业微信内置 WebView 沿用适用的 `snsapi_base` 网页授权。两种方式的回调均只接受正确模式、提供商和一次性 state。
7. 优先在可明确识别的企业微信内置 WebView 选择网页授权；普通系统浏览器选择新版网页登录。对无法可靠识别的环境，在页面提供两个明确的登录地址，由用户选择；不能把不确定环境强行当成 WebView。现有 `GET /login/wecom` 入口、`<issuer>/login/wecom` 回调以及 GitHub 对外路径继续可用，不要求开放平台修改现有回调登记。
8. 外部 state 和待绑定上下文只保存服务端认可的协议续转目标；成功后沿用原有受控 redirect，直接打开 `/login` 而无待续转请求时使用现有默认落点 `/`。第三方回调参数、请求 Host、任意 return URL 或未校验的 Cookie 不能决定最终跳转。密码、短信、小程序及 OAuth2/OIDC 的独立入口继续可用；租户角色权限在账号登录后按受信的客户端上下文装载。
9. 身份源 `enabled=false` 时，入口与回调均拒绝；`visible=false` 只隐藏登录页入口，不改变启用状态。登录页不再因缺少 OAuth2 client 隐藏 GitHub 或企业微信入口。配置继续从受控数据库读取，密钥缺失、重复启用、网络失败和非法响应均失败关闭。
10. 绑定记录以全局唯一 `username` 作为目标账号标识，不新增租户字段；账号关联时按这一业务契约唯一解析本地账号，发现实际重复记录时失败关闭。登录和令牌签发只依赖经验证的全局本地账号，不从租户或 OAuth2 client 推导账号；需要租户角色权限时，再使用受信的客户端租户配置装载。不以默认租户、同名回退或第三方输入猜测身份或权限。
11. 更改绑定表、账号唯一性约束、登录路径、管理端元数据或配置约定时，同步提供 System 拥有的 Flyway 升级材料、README、相关调用方及测试；现有绑定数据不得静默重指向其他账号。

## Non-goals
- 不增加新的身份源、第三方服务商应用模式、GitHub Enterprise 或企业微信 JS 登录组件。
- 不在第三方身份核验阶段签发 IAM token、分配本地角色或处理 OAuth2 client；不修改 OAuth2/OIDC、CAS、SAML 的协议语义。未上线的旧实现、旧 state 格式与旧 token/sub 格式不要求兼容。
- 不提供账号注册、自动创建、无账号归属证明的绑定、静默改绑或本期之外的解绑/账号迁移管理。
- 不允许任意回跳 URL、请求传入的企业微信 CorpID/AgentID/Secret 或第三方 API 基址。

## Design
约束来源：根 `AGENTS.md`、`.agents/rules/architecture.md`、`backend-development.md`、`testing.md`、`frontend-development.md`、`dependencies-release.md`、`REVIEW.md` 和 `bands.yaml`；企业微信新版网页登录以用户指定的官方文档为协议依据。已发现的技能目录中没有额外适用的组织级品牌、合规或数据处理技能；不声称应用了不存在的组织政策。

将流程分为外部身份核验、账号关联、本地账号登录与令牌签发、登录后租户角色权限装载四个边界。提供商特有客户端继续负责授权链接、换码和稳定 subject；公共发起/回调层只管理固定路径、受信 issuer、一次性 state 和失败处理，不查询 OAuth2 client。授权 state 由 Spring Security `Base64StringKeyGenerator` 配合 URL-safe Base64 编码器生成，现有会话仓库仍负责过期和消费。协议侧 OAuth2 授权请求仍由对应安全链检查，成功登录后通过服务端保存的安全目标重新进入该链；该链的客户端租户配置只参与后续角色权限装载，不作为登录或发令牌的前提。没有协议请求时按现有 `/` 默认目标继续。

账号关联层先核对外部身份与现有绑定。企业微信 `UserId` 与唯一有效全局 `username` 一致时可直接关联；已有绑定与直接匹配结果冲突时拒绝。GitHub 只依据既有绑定，不按 GitHub login/email 自动匹配。未绑定外部身份进入仅代表“待绑定”的会话状态，账号证明成功前不写入完整认证。绑定证明使用现有密码校验、短信验证码或已登录会话，但必须核对所证明账号与目标全局 `username` 是同一账号；短信手机号可能对应多个账号时拒绝。绑定写入在服务事务中完成，并由数据库唯一约束抵御并发重复提交。已有登录会话发起绑定时，也不得把外部身份绑定到另一个账号。

企业微信保持 `/login/wecom` 作为公开兼容入口和唯一登记回调。明确的“系统浏览器登录”和“企业微信内打开”入口只决定发起方式，回调以会话 state 中的模式判别，绝不由回调请求自行选模式。登录页展示由 `enabled/visible` 决定，模糊环境展示两个入口；新入口与登录页文案、无障碍反馈同步更新。第三方登录失败统一返回非敏感错误提示，不暴露 code、token、Secret、验证码或账号是否存在。

绑定数据仍是全局 provider/subject 到 username 的关系，不把租户放进绑定表。认证完成前必须通过唯一账号解析器获得稳定本地账号标识与有效状态；本地账号登录和令牌签发不需要租户。登录后若需加载租户角色权限，才从经协议校验的客户端配置取得租户范围，且不能让未验证的 `client_id` 参数承担身份绑定职责。`IdentityProviderConfigService` 的配置读取职责与绑定事务职责分离，避免身份源配置查询隐含账号授权决定。

## Data and interfaces
- 浏览器保持 `GET /login/github`、`GET /login/wecom` 和原回调 URL；可增加两个明确的企业微信发起地址及同源、CSRF 保护的绑定页面/提交入口，具体命名由 Plan 固定。协议回调仍落到 `<issuer>/login/wecom`，不从请求 Host 推导。
- `sys_identity_provider` 保留现有 `GITHUB`/`WECOM`、`enabled`/`visible`、密钥及 `corpId`/`agentId` 配置语义；System 身份源目录仍展示既有回调。新增企业微信模式不需要额外 Secret 或前端 SDK。
- `sys_user_identity_binding` 保留全局 `(provider_type, provider_subject) → username` 契约及已有唯一约束；若为落实全局用户名唯一性而增加索引/校验，只能通过 System 的新 Flyway 迁移，发现重复数据必须停止，不能自动选择或清理用户。
- 当前 `sys_user.username` 在基线 SQL 中只有普通 `login_name` 索引；账号解析必须检查唯一性，不能以查到的第一条记录直接授权。若跨用户体系需要扩展查询契约，Plan 应固定 System API、鉴权和测试范围。
- 不把数据库 Entity 暴露为新 REST/Feign 模型；若新增跨服务查询，须同步定义 System API、鉴权和管理端调用影响。由于尚未上线，不要求兼容旧 token/sub 或旧 state 格式。

## Flagged concerns
- 全局用户名约束尚未落在数据库：用户已确认 `username` 全局唯一，而 `sys_user.username` 目前只有普通索引。实现须验证真实数据并在重复时失败关闭，Plan 再决定是否增加数据库唯一约束及跨用户体系查询；这是落实已确定业务规则的工程工作，不再要求重新选择租户模型，non-blocking。
- 现有短信账号证明路径不完整：`SysUserDetailsServiceImpl.loadUserByPhone` 仍是 TODO，`sys_user.mobile` 也没有唯一约束。实现须使一次性短信验证码能证明所选目标账号的手机号，重复手机号或账号不匹配时拒绝；在完成该实现及测试前不可启用短信绑定，non-blocking。
- `SysUserDetailsServiceImpl.loadUserByClient` 当前把账号装载与按租户加载角色权限写在一起；这是需要在 Plan 中拆开的实现细节。按已确认的边界，租户只参与登录后的角色权限装载，不阻断登录或令牌签发；不再以旧 token/sub 兼容性阻断设计，non-blocking。
- 企业微信桌面环境识别并非可信授权边界：客户端环境信息可伪造，无法识别时的两个明确入口是体验回退，不替代 state、模式和回调验证；真实桌面端/内置 WebView 仍需联调，non-blocking。
- 参考的 eIAM 文件采用 AGPL 许可：只借鉴用户指定的 Spring Security `Base64StringKeyGenerator` 公共 API 用法，不复制参考实现、代码片段或其特有流程；需在代码评审确认，non-blocking。

## Verification strategy
1. 单元测试固定 GitHub 数值 ID、企业微信成员 UserId、两种官方授权地址/参数和共同的已登记回调路径；验证环境自动识别与两个明确入口的回退，且未提供 OAuth2 client 时登录入口可用；断言 state 由 URL-safe `Base64StringKeyGenerator` 生成，并维持会话绑定、过期与一次性消费。
2. Security/MockMvc 测试覆盖 OAuth2 授权请求进入、第三方回调、绑定和原请求恢复，以及直接 `/login` 的默认落点；证明登录和令牌签发不依赖租户或第三方阶段的 client，同时原协议的无效 client 仍被拒绝。
3. 账号关联测试覆盖企业微信 UserId 唯一匹配、未匹配进入绑定、已有绑定、冲突/重复/禁用账号拒绝；GitHub 已绑定直接登录、未绑定需证明本地账号；绑定前无本地权限。
4. 绑定测试分别覆盖密码、短信、已登录会话、CSRF、超时、重放、换会话、暴力尝试、手机号重复、错误目标账号、并发绑定和唯一索引冲突；测试失败后不能留下半绑定或已认证会话。
5. 在隔离 MySQL 上验证账号数据唯一性与任何新增 Flyway 前置条件；用独立测试证明全局 username 解析规则、无租户登录及签发，以及登录后不同客户端租户的角色权限隔离。不以旧 token/sub 或旧 state 格式兼容性作为验收条件。
6. 运行 IAM 及依赖模块 `mvn -pl macula-cloud-iam -am test -Plocal`；若涉及 System 迁移/API，增加 System/API 定向测试和根 reactor `mvn test -Plocal`。检查 Thymeleaf 渲染、不同上下文路径与浏览器行为，并记录真实 GitHub/企微平台往返未验证或联调结果。执行 `git diff --check` 与 Secrets/固定外部值扫描。
