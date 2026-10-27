# Spec: IAM Client 级 Token CORS (from intent.md 2026-10-27)
Status: accepted

## Source intent
[intent.md](./intent.md)：允许多个浏览器系统直接访问统一 IAM Token 端点，并把精确 Origin 权限绑定到实际发起请求的 OAuth Client；未配置 Origin 时失败关闭。

## Requirements
1. 浏览器跨域调用 `/oauth2/token` 时，IAM 必须要求单值 `Origin` 和单值 `X-OAuth-Client-Id`，并且只在该 Client 的 `allowed_origins` 精确包含当前 Origin 时返回允许跨域的响应头。
2. 实际 Token POST 必须确认 `X-OAuth-Client-Id` 与 Spring Authorization Server 已认证的 OAuth Client 完全一致；未知 Client、重复或空 Header、Client 不一致、Origin 未登记及空 Origin 配置都必须在令牌签发前拒绝。
3. CORS 检查必须与 `grant_type` 无关，从而覆盖 password、sms、email、external_code、authorization_code、refresh_token、client_credentials 以及服务端支持的其他 Token grant；CORS 放行不得绕过各 grant、Client 认证或用户认证的原有校验，也不表示建议在浏览器使用 `client_credentials` 或暴露 confidential client 凭据。
4. 浏览器预检必须只允许 `POST`，要求声明 `Content-Type` 与 `X-OAuth-Client-Id`，并可接受 Token Client 认证或既有租户传递所需的 `Authorization`、`tenantId` Header。预检只能在请求 Origin 至少属于一个已登记 OAuth Client 时通过，不能把预检结果当作 Client 授权结果。
5. 不带 `Origin` 的服务端到服务端 Token 请求保持现有行为，不新增 `X-OAuth-Client-Id` 要求，不改变成功或失败协议。
6. `allowed_origins` 扩展为 OAuth Client 的统一浏览器协议 Origin allowlist，验证码申请端点与 Token 端点复用同一组精确 Origin；不允许 `*`、路径、查询参数、片段、用户信息或非 HTTP(S) Origin。
7. System 的 OAuth Client 保存校验必须允许任何需要浏览器直连 Token 端点的 Client 配置 `allowed_origins`，不再把该字段限制为仅短信或邮箱验证码 grant 使用；空集合继续合法并表示拒绝全部浏览器跨域协议请求。
8. Admin OAuth Client 页面必须将该字段表达为“浏览器协议允许 Origin”，说明每行一个精确 Origin、空值拒绝跨域，并在 Token 请求中发送与表单 `client_id` 相同的 `X-OAuth-Client-Id`。
9. 部署级 `IAM_TOKEN_CORS_ALLOWED_ORIGINS` 不再参与 Token CORS 决策，也不得作为 Client 空配置的回退；相关应用配置、Compose 示例和文档必须删除或明确标记为失效，避免运维误以为全局白名单仍能授权。
10. 现有 `allowed_origins` 数据必须原样保留，只修改字段语义和数据库注释；不得根据 redirect URI、请求 Host、旧全局白名单或环境域名自动推导、回填或扩大 Origin。
11. 发布前必须由管理员逐个盘点需要浏览器直连的 OAuth Client 并写入允许 Origin；未完成配置的 Client 在切换后按设计返回 403 CORS 拒绝。迁移步骤必须支持在旧 IAM 仍运行时先完成配置，再升级 IAM。
12. CORS 拒绝、Header/Client 不一致和 Client 未配置不得记录 secret、密码、Token、验证码、授权码、完整请求体或任意异常消息；对外错误不得泄露某个 Client 是否存在或其 Origin 配置。

## Non-goals
- 不要求或移除接入系统自己的反向代理；同源代理仍可作为某个系统的部署选择，但不是接入前提。
- 不改变 OAuth2/OIDC grant、Client 认证方式、PKCE、scope、Token 格式、Token 内容、TTL、用户认证或租户权限语义。
- 不把 confidential client 转换为 public client，也不解决现有浏览器中携带 client secret 的兼容模式；该安全演进应另立变更。
- 不从 redirect URI 自动生成 Origin，不支持通配子域、Origin pattern、正则或 `*`。
- 不自动修改现有 OAuth Client 的数据，不把本机地址写入共享或生产数据库。
- 不向 `/login`、授权页、绑定页或其他 IAM 页面开放跨域。

## Design
适用政策包括根 `AGENTS.md`，`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`，以及根 `REVIEW.md` 和 `bands.yaml`。可用技能中未发现额外的组织级安全、合规或数据处理策略；设计遵循仓库现有认证、敏感信息、分层、迁移和人工发布门禁。

IAM 继续在 Authorization Server 最高优先级安全链内处理协议 CORS，但用 Client 级来源替换当前 Token 端点的部署级静态来源。验证码端点已有的 `ProtocolLoginCorsConfigurationSource` 行为作为统一模型：预检阶段由于浏览器不会发送自定义 Header 的值，只根据请求方法、声明的 Header 集合以及“Origin 是否至少存在于某个 Client”决定是否返回 CORS 许可；这一步只允许浏览器发送实际请求。

实际 Token POST 在 CORS 阶段读取 `X-OAuth-Client-Id`，查找对应 `RegisteredClient` 并精确检查其 `allowed_origins`。随后在 Spring Authorization Server 完成 Client 认证后、进入 Token Endpoint grant 处理前，通过专用协议过滤器比较 Header Client ID 与已认证 Client ID。任何不一致都走统一、非敏感的 OAuth 失败响应，并且不能进入 grant Provider 或 Token Generator。该位置使校验同时覆盖标准 grant 和自定义 grant，不在每个 Converter/Provider 中复制安全逻辑。

不带 `Origin` 的请求被视为非浏览器协议调用：不生成 CORS 响应头，也不执行新的 Header 绑定要求，继续由原生 Client 认证和 Token Endpoint 处理。带 `Origin` 的请求即使来自同机、内网或受信域，也必须执行完整 Client 级校验。

System 继续作为 `macula-system` 数据库和 OAuth Client 配置的所有者。现有 `allowed_origins` 列、Form、VO、Entity 和 RegisteredClient setting 保持数据形态不变，只扩大业务含义和校验范围。Admin 的高级配置字段同步改名和说明；Macula Cloud Admin 自身的 Token POST 添加 `X-OAuth-Client-Id`。其他浏览器接入方必须按同一公开契约升级。

上线采用先配置、后切换：在旧 IAM 仍接受部署级白名单时，管理员通过现有 System API/Admin 为每个浏览器 Client 写入精确 Origin 并复核；之后再部署 System 文档/校验、Admin 调用方和 IAM Client 级 CORS。新版本不提供运行时全局回退，遗漏配置会显式失败而不是扩大权限。

## Data and interfaces
- 数据库复用 `macula-system.sys_oauth2_client.allowed_origins VARCHAR(2048)`，不新增列、不转换数据。由 System 新增下一版本 Flyway migration，仅把列注释从“验证码协议端点允许的精确 Origin”改为“浏览器协议端点允许的精确 Origin”；迁移必须保留所有现有值，并验证升级前后数据一致。
- System OAuth Client REST 请求和响应继续使用 `allowedOrigins: string[]`，没有字段名或 JSON 形态变化；服务端 Origin 规范化和精确值校验扩展到通用浏览器协议用途。
- 浏览器 Token 请求新增必需 Header `X-OAuth-Client-Id: <client_id>`。当存在 `Origin` 时，该 Header 必须与实际认证 Client 一致；无 `Origin` 的服务端请求不需要该 Header。
- `/oauth2/token` 仍接收现有 `application/x-www-form-urlencoded` 参数及 Client 认证方式，响应成功结构不变。CORS 拒绝使用 HTTP 403 且不返回 `Access-Control-Allow-Origin`；已进入实际 POST 后发现 Client 绑定不一致时返回通用 OAuth Client/请求错误，不泄露 Client 或 Origin 配置详情。
- 预检响应允许的方法为 `POST`，允许 Header 为 `Content-Type`、`X-OAuth-Client-Id`、`Authorization` 和既有 `tenantId`，`allowCredentials=false`，Origin 为单个精确回显值；缓存时间保持有限且在测试中固定上界。
- 删除 IAM 对 `macula.cloud.iam.token-cors-allowed-origins` / `IAM_TOKEN_CORS_ALLOWED_ORIGINS` 的运行时授权依赖，并同步 `application.yml`、Compose、IAM README、System README、Admin README 和 deploy README。

## Flagged concerns
- 现有浏览器 Client 和调用方迁移：新版本要求先配置 `allowed_origins` 并发送 `X-OAuth-Client-Id`，否则会按设计中断登录；设计可以继续，但发布负责人必须在 IAM 切换前完成 Client 清单、配置和调用方升级，non-blocking。
- 预检无法携带 Client Header 的值：预检只能证明 Origin 至少被某个 Client 使用，不能证明本次请求属于哪个 Client；实际 POST 的 Origin/Header/已认证 Client 三方校验是授权边界，评审和测试不能把预检成功误报为 Client 授权成功，non-blocking。
- 本地和全新环境引导：数据库不会自动写入 `localhost` 或 `127.0.0.1`，因此新环境在管理员显式配置 Origin 前不能从浏览器跨域登录；部署文档必须提供在 IAM 切换前通过受控 System API 或数据库管理流程完成配置的步骤，non-blocking。
- 浏览器 confidential client：现有 Admin 会把 client secret 交给浏览器，本变更只收紧来源而不消除该既有风险；不得把 CORS 描述为 secret 保护或 OAuth Client 强认证替代品，后续是否迁移 public client + PKCE 需独立决策，non-blocking。
- 数据库字段容量：`allowed_origins` 继续使用 `VARCHAR(2048)`，本次不扩大字段；大量 Origin 可能达到长度上限，System 必须在保存时明确拒绝超长数据，若未来需要大规模动态来源应另行建模，non-blocking。

## Verification strategy
1. IAM 单元测试覆盖预检允许/拒绝、Origin 精确匹配、空配置、未知 Client、重复 Header、通配值拒绝、可选 Header 组合，以及实际 POST 只为 Header 指定 Client 的 Origin 返回 CORS 配置。
2. IAM Security/MockMvc 测试在真实过滤链中使用两个 Client 和不同 Origin，证明合法 Origin + 匹配 Client 可到达原有 Token 认证结果；跨 Client 借用 Origin、缺 Header、Header 与认证 Client 不一致、未配置 Origin 和恶意 Origin均在发 Token 前拒绝。
3. 参数化协议测试覆盖 password、sms、email、external_code、authorization_code、refresh_token 和 client_credentials，证明 CORS 层不按 grant 分叉；各 grant 原有成功、失败和 Client 认证语义保持不变。
4. 服务端回归测试使用不带 `Origin` 的 Basic、client_secret_post 和 public client 请求，证明无需 `X-OAuth-Client-Id` 且结果与变更前一致。
5. System 单元测试覆盖 Origin 规范化、精确 HTTP(S) Origin 校验、任意浏览器 Token Client 可配置、空集合失败关闭语义、超长集合拒绝，以及删除“仅短信/邮箱 grant 才能配置”的旧限制。
6. System Flyway 集成测试从现有 V10 数据升级，断言新迁移不修改 `allowed_origins` 值，只更新字段注释；同时验证空值仍为空，不自动写入本机或部署 Origin。
7. Admin Vitest/Cypress 覆盖字段新文案、编辑回显/保存不丢值、Token POST 同时发送表单 `client_id` 与相同 Header，并证明配置为空时界面明确提示浏览器跨域将被拒绝。
8. 执行相关 IAM/System Maven 测试、根 `mvn test -Plocal`、Admin 单测与生产构建；涉及的真实外部服务或数据库条件必须单独报告，不以 MockMvc 替代运行时证据。
9. 在本地 Compose 中为两个测试 Client 配置不同 Origin，分别验证允许来源成功、交叉 Client 403、未知来源 403、无 Origin 的服务端请求保持原结果；检查日志不包含 secret、密码、Token、验证码、授权码、完整请求体或异常消息。
10. 发布验证必须记录 Client 配置清单、调用方 Header 升级状态和切换顺序；不执行自动生产部署，不把本地 Docker 结果描述为生产迁移证明或 `bands.yaml` 的 SLO 证据。
