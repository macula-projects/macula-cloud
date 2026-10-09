# Spec: IAM 接入指南与交互演示 (from intent.md 2026-10-09)
Status: accepted

## Source intent
[Rain 已接受的 intent.md](./intent.md)：让移动端、H5、Device 等接入方通过真实演示理解如何对接 IAM；演示环境访问不限，生产关闭。

## Requirements
1. 按移动端、H5、Device、服务端、兼容接入组织中文指南，而非通用 API 调试器；每个场景说明参与方、准备条件、参数、完整步骤、可复制示例和错误处理。
2. 可真实完成授权码 S256 PKCE、OIDC 登录/UserInfo/退出；显示 state、nonce、回调及令牌的用途，严格验证绑定，区分 JWT 解码与验签结果。
3. Device 完成发码、另一浏览器登录/确认、设备轮询及取令牌；处理 authorization_pending、slow_down、拒绝、过期和重复兑换，不把客户端取消轮询说成撤销设备码。
4. 真实演示客户端凭据、刷新、introspection、撤销；仅在该客户端与当前令牌支持时启用操作，区分 opaque access token、JWT access token、ID Token。
5. password/sms 单列为兼容接入，保持原参数及业务行为；手机号身份源未实现时展示真实错误和接入前提，不伪造成功，不修改验证码默认 true。
6. 演示环境可匿名访问指南和开始演示，但用户授权仍需正常登录/同意；所有会话隔离。生产及功能关闭时页面、辅助 API、演示客户端与专用资源均不可用。
7. 桌面/移动端可操作，支持键盘、复制、加载/失败/重试/超时及停止轮询；凭据不进入共享日志，不自动导出可复用令牌。

## Non-goals
不做通用请求代理、任意 issuer/客户端调试、动态注册、原生 SDK 或独立移动 App；不改 Vue Admin、Gateway 默认鉴权或正常业务客户端；不新增短信供应商，不自动创建用户/修改数据库或生产配置，不部署。

## Design
### 页面与调用
- 在 IAM 内复用 Thymeleaf 和本地 CSS/JavaScript，入口 `/playground`；沿用登录页品牌和移动布局。场景导航、步骤区、实际请求/响应区并列，窄屏按顺序显示。
- H5/移动端演示采用公共客户端 S256 PKCE，同源回调 `/playground/callback`；浏览器仅保存本次回调所需的短期 state/nonce/verifier，完成或失败后清理。移动端说明系统浏览器、应用回调和安全存储要求；网页只演示协议，不冒充原生 SDK。
- 使用框架实际授权端点，保留正常登录和同意页；只在相同会话、正确 state、正确 redirect_uri 下换码。OIDC 验证签名、iss/aud/exp/nonce；未验证的解析结果明确标为“仅解码”。
- 公共客户端授权码当前不签发 refresh token，不改变框架策略；另设“应用后端/机密客户端”路径演示刷新，secret 仅留服务端，页面明确标记谁在发请求。
- Device 左侧为设备请求者，右侧链接打开独立验证页面；复用框架 device_authorization/device_verification/device_code，补齐自定义设备确认页及公共 Device 客户端认证适配。该适配仅匹配演示客户端和设备端点/设备 grant，不放宽授权码 PKCE 或其他认证方式；指南明确此适配是当前部署的接入前提。
- 轮询采用返回的 interval（缺省 5 秒），slow_down 后至少增加 5 秒，超时退避，终态或离页停止；不并发轮询，不在 URL 暴露 device_code。

### 演示客户端与身份（待批准的方案）
- 使用专用、最小权限的公共 PKCE、机密客户端和公共 Device 客户端；通过非生产配置声明并在演示启用时参与查询，保留数据库仓库对业务客户端的原有处理。
- 使用保留的客户端 ID 前缀且拒绝冲突；不写入数据库、不复用业务客户端缓存。关闭演示后按 ID 查询和令牌认证均拒绝这些客户端及其遗留授权。
- 固定允许的 issuer、回调和 scope，不接受浏览器指定上游 URL/secret/client_id；机密客户端 secret 外部注入，缺失时显示未配置，不提供内置共享密钥。
- 用户仍通过现有 IAM 身份源登录；运维准备无业务权限的测试身份，不新增默认账号。演示客户端无 Gateway/System 业务权限；若现有平台仅凭角色而忽略 client/scope，必须在实现前明确隔离环境或补足演示 token 隔离策略，不静默放行。

### 辅助 API 与数据安全
- `/api/v1/iam-playground/**` 仅提供固定动作：开始流程、机密换码/刷新、Device 轮询、UserInfo、状态查询和撤销；由 Controller 校验、Service 编排真实 HTTP 协议请求，不直接调用 Provider 伪造协议结果。
- 服务端只接受当前会话创建的流程 ID/令牌引用，不接受任意 token 用于查询或撤销；固定目的地址、禁止跟随重定向到其他主机，设置连接/读取超时，避免通用代理和 SSRF。
- 指南匿名可读；有副作用的辅助请求要求会话 CSRF，校验来源，禁止跨域放行。会话上下文最多保留 10 分钟、每会话最多 5 个流程，不超过令牌/设备码自身有效期；不保存密码、验证码或历史请求正文。
- 敏感响应默认遮罩；只允许当前会话主动查看本次演示值，不展示 secret/password，不写日志或 URL、不放 localStorage；退出/重置/超时清理，响应 Cache-Control: no-store，复制示例默认使用占位符。

## Data and interfaces
- 新增 `macula.cloud.iam.playground.enabled`（默认 false）及显式非生产环境许可；`prd` 或 `production` 任一激活时强制关闭，即使同时激活 local 或启用开关也无效。部署必须正确声明环境，不能靠代码猜测服务器属性。
- 页面、资源和辅助 API 同时受门禁约束；不只隐藏导航。正常 IAM 协议端点、客户端和登录仍按原配置工作。
- 新增演示客户端/issuer/回调配置校验、临时会话上下文和说明文档；无数据库迁移、无跨服务 API 契约变化，不开放任意 CORS。
- 部署在反向代理后时使用显式外部 issuer/精确回调，不信任浏览器传入的 Host 拼接上游。关闭后演示 token 的存量拒绝路径必须验证。

## Flagged concerns
Rain 在完整规格及关注点展示后回复 YES，接受以下三项设计决策：专用最小权限客户端、测试身份与隔离演示环境；公共客户端保持现有刷新策略，另以机密客户端演示；补齐演示范围内的 Device 认证适配、确认页和验证。以下 blocking 标记保留审批前的关注点分类，设计决策已获批准；具体权限隔离和验证仍是实现验收条件，不能据此宣称风险或测试已通过。非阻断限制继续保留。
- 匿名演示与业务权限隔离：须 Rain 批准专用客户端、无业务权限测试身份及隔离演示环境；不能让匿名机密客户端演示成为业务 token 签发入口，blocking。
- 公共客户端刷新限制：本地 Security 7.0.7 的 OAuth2RefreshTokenGenerator 明确不向公共授权码客户端发 refresh token；须批准机密客户端另行演示刷新，不承诺原生客户端刷新能力已补齐，blocking。
- Device 补齐范围：本地公共客户端认证 Provider 只校验 PKCE，仓库缺少独立 Device 验证页面/协议回归；须批准演示范围内公共 Device 认证适配、页面及真实协议验证，不扩大为全平台设备管理，blocking。
- 生产识别依赖环境声明：误将生产标为允许的演示环境无法自动识别；部署负责人须保证 profile/配置正确，生产关闭测试不能替代运维隔离，non-blocking。
- 移动技术栈及期限未指定：先提供协议级与浏览器请求示例，不交付原生 SDK；真实设备回调联调另列证据，non-blocking。
- SMS 身份源及默认 true 占位：保持用户明确要求，演示只报告真实结果，不能宣传生产可用短信验证，non-blocking。

## Verification strategy
- 固定场景测试：PKCE/OIDC 成功与 state/nonce/verifier/回调错误、授权码重放；机密刷新、scope 扩权拒绝、introspection/撤销及撤销后访问；兼容 password/sms 不回归。
- Device 用真实 SecurityFilterChain 和隔离 Redis，覆盖 pending、slow_down、批准、拒绝、到期、重放、错误客户端、未知用户码、未登录/CSRF；两个独立浏览器上下文跑完整授权，不仅 mock 响应。
- 安全测试：匿名指南、跨会话流程访问/撤销拒绝、CSRF、目标 URL 注入拒绝、secret/日志泄露检查；默认关闭、非生产开启、prd/production 与 local 混合配置均覆盖，确认关闭后存量演示 token 无法访问。
- 页面覆盖 320/390/844/1440px、键盘、复制、网络错误、超时、停止与恢复操作；区分 UI mock、真实 IAM 协议及真实设备验证。
- 运行 IAM test/package、全后端测试及项目既有前端套件；结果记录命令、输出、跳过和环境限制。本设计阶段不执行测试、不声明实现完成。

## Governing policy and evidence
- 已读取 AGENTS.md、.agents/rules/architecture.md、backend-development.md、testing.md、dependencies-release.md；无额外可适用的组织品牌/合规技能。遵守 Java 17、Jackson 3、分层、凭据保护及人审门禁。
- 当前 IAM README、ProtocolOAuth2Configuration、DefaultSecurityConfiguration、客户端仓库和既有测试为现状依据；本地 Security 7.0.7 源码核对 PublicClientAuthenticationProvider、OAuth2RefreshTokenGenerator。不将框架端点存在视为 Device 已跑通。
- [RFC 8628](https://www.rfc-editor.org/rfc/rfc8628.html) 约束设备流与轮询；[Spring Security 授权服务器](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/) 为框架能力参考。
