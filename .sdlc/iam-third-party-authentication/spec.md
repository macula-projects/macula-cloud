# Spec: IAM 企业微信与 GitHub 第三方身份认证 (from intent.md 2026-10-17)
Status: accepted

## Source intent
[已接受的意图](./intent.md)：IAM 增加企业微信和 GitHub 登录，配置直接从数据库读取，兼容现有认证方式，并以独立 `authentication` 子包形成可扩展边界。

## Requirements
1. IAM 登录页在有效的本地 OAuth 客户端上下文中，为 `enabled=true` 且 `visible=true` 的企业微信和 GitHub 配置显示登录入口；`visible=false` 只隐藏入口，`enabled=false` 同时禁止直接发起和完成该身份源的登录。密码、短信、小程序及既有 OAuth 2.1/OIDC 流程保持兼容。
2. 企业微信和 GitHub 的配置必须在每次登录流程中从共享的 `sys_identity_provider` 数据读取；每个 `provider_type` 最多只能有一条启用配置。IAM 不接受请求参数覆盖 `corpId`、`agentId`、`clientId`、密钥、第三方端点或回调地址。
3. 第三方登录的当前租户和用户类型必须来自经过校验的本地 OAuth `client_id` 对应的 `sys_oauth2_client.tenant_id` 与 `user_type`。没有有效本地客户端上下文、租户或用户类型的请求不得发起第三方登录；回调不得根据第三方参数、Host 头、任意 return URL 或未经校验的 Cookie 改变租户或用户类型。
4. GitHub 使用授权码 Web Flow，并在每次登录后通过 GitHub 用户接口重新取得稳定数值 `id`。IAM 仅以该稳定 `id` 的十进制字符串作为第三方 subject，不用 login、昵称或邮箱匹配本地账号。
5. GitHub 登录必须以 `(provider_type, provider_subject)` 命中唯一的全局预置绑定并取得 `username`，再按当前 OAuth 客户端的 `user_type`、`tenant_id` 和该 username 走现有用户认证信息路由。只有对应用户体系返回唯一、未删除且启用的账号时才允许登录；未绑定、重复、用户类型不匹配、无当前租户访问资格或禁用账号均拒绝，且不自动创建、绑定或改写本地账号。
6. 企业微信使用官方网页授权的最小 `snsapi_base` 范围取得成员 `UserId`，并按用户确认的规则将 `UserId` 作为 `username`，再按当前 OAuth 客户端的 `user_type` 和 `tenant_id` 走同一用户认证信息路由。必须验证所选用户体系中的账号有效；非企业成员、缺少 `UserId`、零匹配或多匹配均拒绝，不回退到手机号、邮箱或昵称。
7. 两种登录都必须生成密码学安全、一次性、有时限且绑定当前 HTTP 会话、provider、本地客户端、租户和用户类型的 `state`；缺失、过期、重放或不匹配的 state 在交换 code 前即被拒绝。回调时重新读取 OAuth 客户端，租户或用户类型已变化则拒绝本次流程；成功后恢复原始、由服务端保存的 OAuth 授权请求，不接受第三方提供的跳转目标。
8. 第三方 access token、企业微信应用 access token 和授权 code 只可在完成当前认证所需的短生命周期内存在，不写数据库、缓存、浏览器、响应、审计或普通日志。客户端密钥继续加密存储，IAM 必须使用与 System 相同的外部密钥解密；密钥缺失、解密失败或配置冲突时对该身份源失败关闭。
9. 第三方拒绝授权、配置不可用、网络超时、协议响应非法、state 校验失败、未绑定、用户不存在/禁用等情况都必须返回稳定且不泄露凭据、token、code、第三方原始响应或账号枚举信息的登录失败提示；服务端日志只记录 provider、稳定错误类别和关联 ID。
10. GitHub 与企业微信分别位于 `dev.macula.cloud.iam.authentication.github` 和 `dev.macula.cloud.iam.authentication.wecom`；共享编排只抽取 state、配置读取、账号解析和成功/失败处理等确有共同语义的接口，不把 provider 特有参数塞入通用模型。
11. 数据库迁移、IAM/System 配置、部署材料、IAM/System README 和自动化测试必须同步说明绑定表、启用唯一性、回调路径、租户来源、密钥注入、失败关闭及不保存第三方 token 的约束。

## Non-goals
- 本期不提供 GitHub 或其他社交账号的绑定/解绑页面、REST 写入 API、自助绑定或自动注册；绑定记录由受控的外部流程或后续功能预置。
- 本期不为企业微信建立绑定记录，不新增独立员工号字段；已确认直接把企业微信 `UserId` 作为当前 OAuth 客户端所选用户类型中的 username。
- 不按 GitHub 邮箱、GitHub login 或企业微信手机号/邮箱做兜底匹配；全局绑定可以解析同一 username，但每次登录都必须按当前 OAuth 客户端的用户类型和租户重新获取认证信息并验证账号状态。
- 不长期保存、刷新或复用第三方 access token，不访问仓库、组织或其他与登录无关的数据。
- 不在本期实现 GitHub Enterprise、自定义第三方 API 基址、微信开放平台、飞书、钉钉、支付宝或通用 OIDC/SAML 配置。
- 不重做 Vue Admin 身份源管理页；登录入口属于 IAM 的 Thymeleaf 登录页。

## Design
适用政策为根 `AGENTS.md`、`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`、`REVIEW.md` 和 `bands.yaml`。未发现额外的组织级品牌、合规或数据处理 Skill。设计同时核对 GitHub 官方 OAuth Web Flow/稳定用户 ID 指南和企业微信官方网页授权的 `snsapi_base`/`UserId` 语义。

IAM 增加一个窄的第三方认证编排层，以及 `github`、`wecom` 两个独立实现包。每个 provider 提供发起授权、交换 code、读取稳定 subject 的适配器，并通过各自的 Spring Security filter/configurer 接入现有默认安全链；认证成功后构造已认证 token 并复用当前用户权限装载及保存请求恢复机制。provider 客户端只能访问代码中固定的官方 HTTPS 主机，设置连接/读取超时、禁止将响应体写日志，并允许测试注入替身。

浏览器入口和回调沿用 System 目录已公开的 `/login/github`、`/login/wecom`。同一路径在没有 provider 回调参数时发起授权，在带有 `code`/`state` 或 provider 错误参数时处理回调。IAM 使用已配置且受信任的 issuer/base URL 生成精确回调地址，不从请求 Host 推导。每个会话同一 provider 只保留一个待完成 state，默认五分钟过期；读取后先原子消费再交换 code，避免重放。失败返回 `/login` 的稳定错误 code，由模板映射为中文提示，不回显第三方文本。

登录页只查询不含密钥的 provider 投影。按钮显示还要求当前请求能解析并验证本地 OAuth 客户端；普通直接访问 `/login` 时继续提供现有登录方式，但隐藏第三方入口。点击按钮时，IAM 把已验证的本地 client ID、tenant ID、user type、provider 和原始 SavedRequest 关联到服务端 state。回调重新读取客户端并与 state 快照核对，只使用这份服务端上下文，完成后恢复原始授权请求。

IAM 直接读取共享库中的平台级身份源配置。System 保存配置时在事务内拒绝同类型第二条启用记录，数据库唯一约束负责并发兜底。`visible` 只决定登录页是否展示，不能让已禁用 provider 被直接调用。IAM 获取密钥时复用 Macula Boot 加密能力和与 System 相同的 `MACULA_CRYPTO_PASSWORD`；仅配置元数据查询不得无谓解密密钥。

账号解析在 IAM Service 层完成，不在 Filter 中拼 SQL，也不把第三方登录固定到 `sys_user`。GitHub subject 先以 `(provider_type, provider_subject)` 查询全局绑定得到 `username`；企业微信直接把 `UserId` 作为 username。两条路径随后都使用 OAuth 客户端的 `UserTypeEnum`、tenant ID 和 username，复用现有 `UserAuthInfoService#getUserAuthInfo(userType, tenantId, username)`/系统用户回退契约及 `SysUserDetails` 组装，使会员、行政员工、外包、供应商等用户体系继续由 OAuth client 选择。绑定本身不带租户和用户类型；同一绑定能否用于某个客户端，完全由该客户端选定的用户类型和租户能否解析出有效账号决定。

## Data and interfaces
- 新增共享表 `sys_user_identity_binding`：`id bigint`、`provider_type varchar(32)`、`provider_subject varchar(255)`、`username varchar(64)`、审计时间/创建人字段。该表不含 `tenant_id` 和 `user_id`；首期只读取 `provider_type=GITHUB`，且不保存 profile、邮箱、昵称、access token 或 refresh token。
- 唯一约束：`(provider_type, provider_subject)` 防止一个第三方身份绑定多个 username；`(provider_type, username)` 防止一个 username 绑定多个同类第三方身份。绑定表不重复保存 `user_type`；查询后必须使用 OAuth 客户端确定的 `UserTypeEnum` 和 tenant ID，通过现有用户认证信息服务重新解析 username、权限与账号状态。
- `sys_identity_provider` 新增数据库级“同一 `provider_type` 最多一条 `enabled=1`”约束；迁移前若发现重复启用记录则停止并报告，由数据负责人选择保留项，迁移不得自动禁用。
- 浏览器接口：`GET /login/github`、`GET /login/wecom` 同时承担发起与回调；只接受各 provider 明确定义的回调参数。现有 `/login`、`POST /login`、`POST /login/sms`、OAuth/OIDC 端点不变。
- GitHub 仅申请识别用户所需的最小范围，调用固定的官方授权、token 和 `GET /user` 接口；以返回的稳定 `id` 为 subject。企业微信使用配置中的 `corpId/agentId/appSecret`、官方 `snsapi_base` 网页授权和成员身份接口；只接受成员 `UserId`。
- 不新增公共 REST/Feign 契约，不向 `macula-cloud-api` 暴露第三方 SDK 类型。System 的现有身份源管理 API 保持响应不含密钥，但在尝试启用同类型第二条配置时返回冲突。
- IAM 的 `local/docker/dev/stg/pet/prd` 配置与部署材料记录 `MACULA_CRYPTO_PASSWORD` 注入要求；不得提交真实值。配置缺失或解密失败不影响密码/短信登录，但相关第三方 provider 不得可用。

## Flagged concerns
- 当前工作树基线落后于 `main`，且按 MAC-6 顶层任务规则实现必须以 `main` 为基线；spec 可评审，但进入 Build 前必须在保留现有用户改动的前提下刷新基线，否则不得编码，blocking。
- GitHub 绑定预置：本期刻意不提供写入 API 或 UI，所以上线前必须由受控流程写入并审计 `sys_user_identity_binding`；没有预置记录的用户会按设计被拒绝，non-blocking。
- 生产历史数据：现有表允许同类型多条启用配置；新迁移会在发现重复时停止，环境数据负责人须在部署前明确保留项，不能由迁移脚本猜测，non-blocking。
- 共享解密密钥：IAM 将新增对 System 同一 `MACULA_CRYPTO_PASSWORD` 的运行时依赖；各环境必须安全分发并支持轮换，缺失时第三方登录失败关闭，non-blocking。
- 企业微信可见范围：即使本地账号存在，成员不在企业微信自建应用可见范围内仍会被官方流程拒绝；这是企业微信后台配置前置条件，需要部署验收，non-blocking。
- 外部可用性：第三方授权端点故障、限流或网络超时会使对应登录方式暂时不可用；现有本地认证必须保持可用，且本期不增加跨请求 token 缓存或自动重试，non-blocking。

## Verification strategy
1. IAM 单元测试覆盖配置选择、`enabled/visible` 语义、固定回调地址、state 的生成/过期/错配/原子消费/重放、第三方拒绝、超时、非法响应和敏感字段不进入日志/响应。
2. GitHub provider 测试覆盖最小授权请求、code 交换、每次调用用户接口、只使用稳定 `id`、全局 username 绑定成功、未绑定、重复绑定，以及相同 username 在不同 OAuth client 用户类型/租户下分别成功或拒绝。
3. 企业微信 provider 测试覆盖 `snsapi_base`、成员 `UserId` 成功、非成员/缺失 UserId，并验证 `UserId` 作为 username 时由 OAuth client 的 `MEMBER`、`EMPLOYEE`、`ODC`、`SUPPLIER` 类型选择正确用户体系；零/多匹配和禁用用户必须拒绝。
4. Spring Security/MockMvc 集成测试从匿名 `/oauth2/authorize` 开始，证明 SavedRequest、本地客户端租户、外部往返、认证会话和原授权请求恢复；同时回归密码、短信、小程序、CSRF、PKCE/OIDC 及失败提示。
5. System Service/Web 测试覆盖同类型第二条启用配置的冲突、并发兜底和现有管理 API 密钥不回显。真实 MySQL 隔离迁移测试覆盖空库升级、绑定表唯一约束、无重复配置升级成功及重复启用配置时安全停止。
6. 模板/UI 测试覆盖有效客户端下按钮显示、`visible=false` 隐藏、无客户端上下文隐藏、失败提示、键盘/移动端可用性，以及原有表单与短信 Tab 不回归。
7. Build 后按阶段运行 IAM、System/API 的定向 Maven 测试和 package、必要的真实 MySQL 迁移测试、UI 检查及 `git diff --check`；随后按 `REVIEW.md` 独立执行 Bugs、Security、Compliance 三个检查。
