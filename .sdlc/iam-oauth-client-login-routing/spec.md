# Spec: IAM OAuth Client 网页登录路由 (from intent.md 2026-10-22)
Status: accepted.

## Source intent
[已接受的 IAM OAuth Client 网页登录路由 intent](./intent.md)：每个 OAuth Client 显式限定网页登录提供商，
并在受控的 `/oauth2/authorize` 会话中按客户端环境自动续转或展示选择页。

## Requirements
1. OAuth Client 必须显式配置允许的网页登录提供商集合；旧 Client 和空集合默认只允许本地账号密码、短信或
   邮箱验证码认证，不得展示或放行任何第三方网页登录入口。
2. OAuth Client 可以配置一个可选的直接登录提供商。该值必须属于允许集合；未配置时展示登录页供用户选择，
   配置后在没有更适配端内环境时直接进入该提供商认证。
3. 只有经过 Authorization Server 校验并保存的 `/oauth2/authorize` 请求才能触发 Client 级第三方路由。
   IAM 不得把查询参数、Cookie、User-Agent 或请求 Host 单独作为 Client 授权依据。
4. 企业微信 User-Agent 且当前 Client 允许 `WECOM` 时，IAM 必须优先进入企业微信端内网页 OAuth；微信
   User-Agent 且当前 Client 允许 `WECHAT` 时，IAM 必须进入微信公众号网页授权。企业微信识别优先于微信，
   避免同时包含 `wxwork` 和 `MicroMessenger` 的 User-Agent 被错路由。
5. User-Agent 只选择 browser/webview 交互模式，不产生身份或权限。最终身份仍必须由固定提供商配置换码、
   一次性 `state`、回调校验和现有绑定规则确定。
6. 自动路由、配置的直接登录和用户点击 `/login/{provider}` 必须使用同一 Client 策略校验。直接访问入口、
   修改 Cookie 或伪造 Client ID 均不得绕过允许集合。
7. 第三方登录发起时必须把已验证的 `clientId`、provider、login mode、创建时间和受控 SavedRequest 目标绑定
   到一次性 Session state；回调时重新校验当前 Client 仍存在、仍允许该 provider，策略被收紧后失败关闭。
8. 当请求包含受控登录错误时，IAM 必须停止自动跳转并展示登录页及固定错误原因，防止提供商失败后形成
   重定向循环。禁用/缺失身份源、未知环境或不允许的提供商均回到可理解的本地登录或选择页面。
9. 登录页第三方入口必须是“当前 Client 允许”与“身份源 enabled 且 visible”的交集。`visible=false` 只影响
   选择页展示；已由 Client 配置为直接登录或端内自动路由的提供商仍必须要求身份源 enabled。
10. `WECOM_WEBVIEW_CODE` 必须从 `ExternalCodeType`、verifier、管理目录和测试中移除。企业微信端内网页只走
    `/oauth2/authorize` 浏览器认证闭环；`WECOM_MINIAPP_CODE` 继续只接收企微小程序端通过
    `wx.qy.login()` 取得的 code。
11. 微信和企业微信小程序不得进入 `/oauth2/authorize` 自动路由，也不得复用网页授权 code type；它们只在
    小程序端取得受支持 code 后调用 `external_code`。
12. System 和 Admin 必须允许管理员配置网页登录提供商允许集合及可选直接登录提供商，并在服务端校验枚举、
    authorization_code grant、回调 URI、身份源存在性和直接登录值的一致性。
13. 变更不得改变 authorization code + S256 PKCE、OIDC、精确 redirect URI、授权确认、Token、scope、账号
    绑定及现有本地认证协议。未配置 Client 不得因为升级自动获得第三方能力。
14. 日志不得记录第三方 code、Token、state、Cookie、完整 SavedRequest、用户标识或提供商响应；只保留既有
    白名单诊断字段。

## Non-goals
- 不在 IAM 页面集成飞书、钉钉、企业微信或微信 JSSDK，也不实现 APP、小程序或业务网页前端代码。
- 不根据 User-Agent 直接认定用户身份、租户或权限，不允许客户端请求覆盖身份源配置或回调地址。
- 不为网页登录增加新的 Token grant，不把 GitHub、企微端内网页或微信公众号网页改成 `external_code`。
- 不取消仍受支持的 `FEISHU_WEBVIEW_CODE`、`DINGTALK_WEBVIEW_CODE`、APP、小程序或普通浏览器
  `external_code` 类型。
- 不在本期支持同一 provider type 为不同 OAuth Client 选择不同身份源 registration；继续使用现有唯一启用
  provider 配置。
- 不修改 Gateway Token 校验、业务服务 API、资源服务器权限模型或自动注册用户规则。

## Design
### Governing policies
- 根 `AGENTS.md`：认证、OAuth Client、身份源和数据库是跨服务安全契约；保留用户未提交改动。
- `.agents/rules/architecture.md`：IAM 负责认证路由，System 负责共享表管理和 Flyway，Admin 同步配置界面。
- `.agents/rules/backend-development.md`：安全入口默认拒绝，协议字符串使用共享常量或枚举，敏感数据不入日志。
- `.agents/rules/testing.md`：认证变化必须覆盖允许和拒绝路径，真实平台证据与 Mock 测试分开报告。
- `REVIEW.md`：实现完成后分别执行 Bugs、Security、Compliance 评审。

### Client policy
在 `macula-cloud-system-api` 增加共享枚举 `BrowserLoginProviderType`，稳定值为 `GITHUB`、`WECOM`、
`FEISHU`、`DINGTALK`、`WECHAT`。System 与 IAM 均使用该枚举，不以自由字符串或 IAM 私有枚举形成双重合同。

OAuth Client 增加：
- `browserLoginProviderTypes`：允许的网页登录提供商集合，规范化排序后以 CSV 持久化；默认空集合。
- `directLoginProviderType`：可空的直接登录提供商；必须属于允许集合。

只有启用 `authorization_code`、具有非空精确 redirect URI 的 Client 才能配置网页登录提供商。System 保存时
校验每个 provider type 已存在唯一启用的身份源配置；运行时仍重新校验，配置随后禁用时失败关闭。

### Routing priority
未认证用户访问有效 `/oauth2/authorize` 后，由 Spring Security 保存原请求并进入 `/login`。路由服务只从
受控 SavedRequest 解析 `client_id`，再通过 `SysOAuth2ClientService` 加载策略；Cookie 仅可继续服务既有本地
用户路由，不作为网页登录授权证明。优先级固定为：

1. 带受控 `error` 的登录页：不自动跳转，展示固定错误和仍可用的本地/第三方选项。
2. `wxwork` 环境且 Client 允许 `WECOM`：进入 `/login/wecom/webview`。
3. 非企微的 `MicroMessenger` 环境且 Client 允许 `WECHAT`：进入 `/login/wechat/webview`。
4. Client 配置了 `directLoginProviderType`：进入该 provider 的自动入口 `/login/{provider}`。
5. 其他情况：渲染登录页，只展示 Client 允许且身份源 enabled/visible 的第三方入口。

环境匹配但 Client 未允许对应 provider 时不回退猜测其他第三方提供商，而是继续执行直接登录配置或展示选择页。
未知环境不会猜测 webview。GitHub 使用现有网页登录；飞书、钉钉普通浏览器使用已有 browser 路径；企微、微信
由各自 LoginMode 固定 browser/webview 合同。

### Entry and callback enforcement
新增 Client 网页登录策略服务，供 `LoginController` 与第三方认证公共过滤器复用。首次发起时要求存在有效
SavedRequest、Client 允许 provider、身份源 enabled；创建 state 时将 `clientId` 写入
`ThirdPartyLoginContext`。回调消费 state 后再次核对 SavedRequest 目标、context clientId、当前 Client 策略和
provider，任何不一致均使用统一浏览器失败处理器返回固定原因。

登录页链接和显式 `/login/{provider}` 只是交互入口，不能替代过滤器中的授权检查。GitHub、企微、飞书、钉钉、
微信均受同一策略约束；绑定 required、成功处理和 SavedRequest 恢复逻辑保持不变。

### External code cleanup
从共享 `ExternalCodeType` 删除 `WECOM_WEBVIEW_CODE`，企微 verifier 仅保留 APP、MINIAPP、BROWSER。现有以
该枚举作为通用测试样例的测试改用仍受支持的类型，不改变被测组件语义。Flyway 迁移删除该过时类型的
Client-provider 映射，并从 Client CSV 中移除精确 token；这是默认拒绝的配置收敛，不修改用户或身份绑定数据。

## Data and interfaces
- 新增共享枚举：`BrowserLoginProviderType`。
- `sys_oauth2_client` 新增 `browser_login_provider_types VARCHAR(255) NOT NULL DEFAULT ''` 和
  `direct_login_provider_type VARCHAR(32) NULL`。
- 新增 `V10__iam_oauth_client_login_routing.sql`，不修改可能已经执行的 V9；V10 同时清理
  `WECOM_WEBVIEW_CODE` 的过时 Client 配置和映射。
- System `OAuth2ClientForm`、`OAuth2ClientVO`、Entity、Service 和 Admin API 模型增加
  `browserLoginProviderTypes`、`directLoginProviderType`；现有 API 字段保持兼容。
- Admin OAuth Client 页面增加“网页登录提供商”多选和“直接登录提供商”可空单选；直接登录候选只来自已选
  集合，空值显示“由用户选择”。
- IAM `SysOAuth2Client`、Client Repository/Service 和登录策略服务读取新字段；不新增公开登录端点。
- `ThirdPartyLoginContext` 增加 `clientId`，Session 内旧对象不做兼容迁移，发布切换后未完成的短期登录 state
  失效并要求用户重新发起登录。

## Flagged concerns
- 共享数据库迁移：V10 会新增 Client 策略列并移除过时的 `WECOM_WEBVIEW_CODE` 配置，需要在实施前确认
  System 管理的目标数据库 Flyway 历史并完成升级备份与回滚准备，blocking。
- 旧 Client 默认行为：空 allowlist 将隐藏并拒绝过去全局可用的 GitHub/企微等入口，这是用户明确要求的
  默认拒绝，但管理员必须在升级后为需要第三方登录的 Client 补配置，blocking。
- User-Agent 可伪造：识别结果只改变跳转路径，真正身份仍由第三方回调和 state 校验，因此不会单独授予权限；
  真实企微/微信版本差异仍需设备联调，non-blocking。
- 身份源仍是 provider type 级唯一启用配置：本期不能让不同 Client 使用同一平台的不同 App 凭据；如有该
  需求需另行扩展 Client-registration 映射，non-blocking。
- Session state 结构变化：发布时进行中的第三方登录会失效并要求重试，不影响已签发 Token，non-blocking。
- Docker/Flyway 当前状态查询无响应：设计固定使用新 V10 避免改写 V9，但实施和部署前仍必须重新取得实际
  `flyway_schema_history` 证据，non-blocking。

## Verification strategy
1. 共享枚举测试固定全部网页登录 provider 稳定值，并证明 `WECOM_WEBVIEW_CODE` 已从外部凭证枚举移除，
   `WECOM_MINIAPP_CODE` 保留。
2. System Service 测试覆盖空 allowlist、未知 provider、无 authorization_code、无 redirect URI、禁用/重复身份
   源、direct 不在 allowlist、合法选择/直达保存回显和缓存失效。
3. V10 隔离 MySQL 测试分别覆盖 V9→V10 与干净库初始化，验证新列默认值、旧 Client 默认拒绝、过时企微
   webview type/mapping 清理、其他 external code type 和用户/绑定数据不变。
4. 登录路由单元测试覆盖企微优先于微信、微信公众号、配置 direct、选择页、未知 UA、伪造 Cookie/Client ID、
   无 SavedRequest、错误回退不循环和未允许 provider 拒绝。
5. 第三方公共流程测试覆盖五个平台发起与回调两次 Client 策略校验、策略中途收紧、跨 Client state、重放、
   SavedRequest 错配、禁用身份源以及成功恢复原 `/oauth2/authorize` 请求。
6. `IamProtocolTest` 使用真实 SecurityFilterChain 验证 `/oauth2/authorize` 到企微/微信公众号自动续转、选择页
   可见性、直接登录、本地认证回退、authorization code + S256 PKCE 和既有 GitHub/绑定回归。
7. Admin Vitest 覆盖网页登录 allowlist、直接登录候选联动、空态默认拒绝、编辑回显和服务端错误展示。
8. 捕获日志测试注入 code、state、Cookie、SavedRequest 和主体标记，断言日志及失败响应不泄漏。
9. Stage 4 分别报告单元/协议测试、隔离 MySQL 迁移和真实企微/微信设备联调；Mock 或 User-Agent 测试不得
   冒充真实平台成功。
