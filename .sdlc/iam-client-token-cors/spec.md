# Spec: IAM Client 级 Token CORS (from intent.md 2026-10-27)
Status: accepted (Code Round 1 remediation 2026-10-28)

## Source intent
[intent.md](./intent.md)：允许多个浏览器系统直接访问统一 IAM Token 端点，并把精确 Origin 权限绑定到实际发起请求的 OAuth Client；未配置 Origin 时失败关闭。

## Requirements
1. 浏览器跨域调用 `/oauth2/token` 时，IAM 必须要求单值 `Origin`，并且只在已认证 OAuth Client 的 `allowed_origins` 精确包含当前 Origin 时允许请求进入 grant 处理和令牌签发。
2. 实际 Token POST 必须在 Spring Authorization Server 完成 OAuth Client 认证后，使用该已认证 Client 与请求 Origin 做精确绑定校验；对于无密钥 public Client，认证链只能在单值 `client_id`、无 confidential Client 凭据、Client 认证方式为 `none` 且 Client 已登记当前 grant 时建立受信 Client Principal。未知 Client、Origin 未登记及 Client 的空 Origin 配置都必须在令牌签发前拒绝。
3. CORS 检查必须与 `grant_type` 无关，从而覆盖 password、sms、email、external_code、authorization_code、refresh_token、client_credentials 以及服务端支持的其他 Token grant；CORS 放行不得绕过各 grant、Client 认证或用户认证的原有校验，也不表示建议在浏览器使用 `client_credentials` 或暴露 confidential client 凭据。
4. 浏览器预检必须只允许 `POST`，允许 Token 表单及 Client 认证或既有租户传递所需的 `Content-Type`、`Authorization`、`tenantId` Header，并拒绝声明 allowlist 以外的 Header。由于 `application/x-www-form-urlencoded` 的 `Content-Type` 属于 CORS safelisted request-header，预检不得要求浏览器必须在 `Access-Control-Request-Headers` 中声明它；预检可只声明 `Authorization`、只声明 `tenantId`、同时声明二者，或不声明非 safelisted Header。预检只能在请求 Origin 至少属于一个已登记 OAuth Client 时通过，不能把预检结果当作 Client 授权结果。
5. 不带 `Origin` 的服务端到服务端 Token 请求保持现有行为，不新增请求参数或 Header，不改变成功或失败协议。
6. `allowed_origins` 扩展为 OAuth Client 的统一浏览器协议 Origin allowlist，验证码申请端点与 Token 端点复用同一组精确 Origin；不允许 `*`、路径、查询参数、片段、用户信息或非 HTTP(S) Origin。
7. System 的 OAuth Client 保存校验必须允许任何需要浏览器直连 Token 端点的 Client 配置 `allowed_origins`，不再把该字段限制为仅短信或邮箱验证码 grant 使用；空集合继续合法并表示拒绝全部浏览器跨域协议请求。
8. Admin OAuth Client 页面必须将该字段表达为“浏览器协议允许 Origin”，说明每行一个精确 Origin、空值拒绝跨域；Admin 和其他调用方的现有 Token 请求参数及 Header 契约保持不变。
9. 部署级 `IAM_TOKEN_CORS_ALLOWED_ORIGINS` 不再参与 Token CORS 决策，也不得作为 Client 空配置的回退；相关应用配置、Compose 示例和文档必须删除或明确标记为失效，避免运维误以为全局白名单仍能授权。
10. 现有 `allowed_origins` 数据必须原样保留，只修改字段语义和数据库注释；不得根据 redirect URI、请求 Host、旧全局白名单或环境域名自动推导、回填或扩大 Origin。
11. 发布前必须由管理员逐个盘点需要浏览器直连的 OAuth Client 并写入允许 Origin；未完成配置的 Client 在切换后按设计返回 403 CORS 拒绝。迁移步骤必须支持在旧 IAM 仍运行时先完成配置，再升级 IAM。
12. CORS 拒绝、Origin/Client 绑定失败和 Client 未配置不得记录 secret、密码、Token、验证码、授权码、完整请求体或任意异常消息；对外错误不得泄露某个 Client 是否存在或其 Origin 配置。
13. Token CORS 的“Origin 是否至少被一个 Client 登记”检查不得让匿名请求按 Origin 基数反复触发 OAuth Client 表全表扫描；必须使用固定键的规范化 Origin 索引缓存或可索引持久化结构，并在 OAuth Client 新增、修改、删除后与现有 Client 缓存一起失效。缓存加载或失效异常不得放宽为允许。

## Non-goals
- 不要求或移除接入系统自己的反向代理；同源代理仍可作为某个系统的部署选择，但不是接入前提。
- 不改变 OAuth2/OIDC grant、Client 认证方式、PKCE、scope、Token 格式、Token 内容、TTL、用户认证或租户权限语义。
- 不把 confidential client 转换为 public client，也不解决现有浏览器中携带 client secret 的兼容模式；该安全演进应另立变更。
- 不从 redirect URI 自动生成 Origin，不支持通配子域、Origin pattern、正则或 `*`。
- 不自动修改现有 OAuth Client 的数据，不把本机地址写入共享或生产数据库。
- 不向 `/login`、授权页、绑定页或其他 IAM 页面开放跨域。

## Design
适用政策包括根 `AGENTS.md`，`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`，以及根 `REVIEW.md` 和 `bands.yaml`。可用技能中未发现额外的组织级安全、合规或数据处理策略；设计遵循仓库现有认证、敏感信息、分层、迁移和人工发布门禁。

IAM 继续在 Authorization Server 最高优先级安全链内处理协议 CORS，但用 Client 级来源替换当前 Token 端点的部署级静态来源。验证码端点已有的 `ProtocolLoginCorsConfigurationSource` 行为作为参考：预检阶段只根据请求方法、声明的 Header 集合以及“Origin 是否至少存在于某个 Client”决定是否返回 CORS 许可；这一步只允许浏览器发送实际请求。

实际 Token POST 的 CORS 传输层与预检一样，只确认 Origin 至少被一个 Client 登记。全局存在性由一个固定缓存键保存规范化 Origin 集合，缓存 miss 时一次性读取非空 `allowed_origins`，而不是按请求 Origin 执行 `FIND_IN_SET`；System 在 OAuth Client 新增、修改或删除事务提交后清理现有 OAuth Client 缓存，使 Client 记录与 Origin 索引一起失效。

随后在 Spring Authorization Server 完成 Client 认证后、进入 Token Endpoint grant 处理前，通过专用协议过滤器从 `OAuth2ClientAuthenticationToken` 取得已认证 Client，并精确检查其 `allowed_origins` 是否包含当前 Origin。confidential Client 继续复用框架认证；authorization_code public Client 继续复用框架 PKCE 认证。对 password、sms、email、external_code 和 refresh_token 的 public Client，Client 认证扩展只接受 Token POST 中单值 `client_id`，拒绝同时出现 `Authorization`、`client_secret` 或 assertion 凭据，并要求 RegisteredClient 的唯一认证方式为 `none`、已启用当前 grant；它只建立 public Client 身份，不替代验证码、外部凭证、refresh token 归属或其他 grant 校验。public `client_credentials` 仍拒绝。任何 Origin 不匹配都走统一、非敏感的 OAuth 失败响应，并且不能进入 grant Provider 或 Token Generator。该位置使校验同时覆盖标准 grant、自定义 grant 和所有受支持的 Client 认证方式，不在每个 grant Provider 中复制 Origin 规则。

不带 `Origin` 的请求被视为非浏览器协议调用：不生成 CORS 响应头，也不执行新的 Origin/Client 绑定校验，继续由原生 Client 认证和 Token Endpoint 处理。带 `Origin` 的请求即使来自同机、内网或受信域，也必须执行完整 Client 级校验。

System 继续作为 `macula-system` 数据库和 OAuth Client 配置的所有者。现有 `allowed_origins` 列、Form、VO、Entity 和 RegisteredClient setting 保持数据形态不变，只扩大业务含义和校验范围。Admin 的高级配置字段同步改名和说明；Macula Cloud Admin 和其他浏览器接入方的 Token 请求契约保持不变。

上线采用先配置、后切换：在旧 IAM 仍接受部署级白名单时，管理员通过现有 System API/Admin 为每个浏览器 Client 写入精确 Origin 并复核；之后再部署 System 文档/校验、Admin 调用方和 IAM Client 级 CORS。新版本不提供运行时全局回退，遗漏配置会显式失败而不是扩大权限。

## Data and interfaces
- 数据库复用 `macula-system.sys_oauth2_client.allowed_origins VARCHAR(2048)`，不新增列、不转换数据。由 System 新增下一版本 Flyway migration，仅把列注释从“验证码协议端点允许的精确 Origin”改为“浏览器协议端点允许的精确 Origin”；迁移必须保留所有现有值，并验证升级前后数据一致。
- System OAuth Client REST 请求和响应继续使用 `allowedOrigins: string[]`，没有字段名或 JSON 形态变化；服务端 Origin 规范化和精确值校验扩展到通用浏览器协议用途。
- 浏览器与服务端 Token 请求不新增 Header、query 或 form 参数；Client 身份以 Spring Authorization Server 完成的认证结果为唯一受信来源。
- `/oauth2/token` 仍接收现有 `application/x-www-form-urlencoded` 参数及 Client 认证方式，响应成功结构不变。CORS 拒绝使用 HTTP 403 且不返回 `Access-Control-Allow-Origin`；已进入实际 POST 后发现 Client 绑定不一致时返回通用 OAuth Client/请求错误，不泄露 Client 或 Origin 配置详情。
- 预检响应允许的方法为 `POST`，允许 Header 为 `Content-Type`、`Authorization` 和既有 `tenantId`，`allowCredentials=false`，Origin 为单个精确回显值；只校验实际声明的 Header 是 allowlist 子集，不强制声明 safelisted `Content-Type`，缓存时间保持有限且在测试中固定上界。
- 无密钥 public Client 仍使用既有 `client_id` form 参数；只为 password、sms、email、external_code、refresh_token 增加受限的 `none` Client 身份建立过程，不改变 authorization_code 的 PKCE 验证，不允许 public `client_credentials`，不接受任何 secret/assertion 替代或新增提示 Header。
- Token CORS 全局 Origin 集合复用现有 OAuth Client 缓存区域的固定键；System 的 Client 写事务提交后清理该缓存区域，以同时失效 RegisteredClient 和全局 Origin 索引，不新增外部 API 或数据库表。
- 删除 IAM 对 `macula.cloud.iam.token-cors-allowed-origins` / `IAM_TOKEN_CORS_ALLOWED_ORIGINS` 的运行时授权依赖，并同步 `application.yml`、Compose、IAM README、System README、Admin README 和 deploy README。

## Flagged concerns
- 现有浏览器 Client 数据迁移：新版本要求先配置 `allowed_origins`，否则会按设计中断跨域登录；Token 请求契约不变，但发布负责人必须在 IAM 切换前完成 Client 清单和 Origin 配置，non-blocking。
- 预检阶段尚无已认证 Client：预检只能证明 Origin 至少被某个 Client 使用，不能证明本次请求属于哪个 Client；实际 POST 的 Origin/已认证 Client 绑定校验是授权边界，评审和测试不能把预检成功误报为 Client 授权成功，non-blocking。
- 本地和全新环境引导：数据库不会自动写入 `localhost` 或 `127.0.0.1`，因此新环境在管理员显式配置 Origin 前不能从浏览器跨域登录；部署文档必须提供在 IAM 切换前通过受控 System API 或数据库管理流程完成配置的步骤，non-blocking。
- 浏览器 confidential client：现有 Admin 会把 client secret 交给浏览器，本变更只收紧来源而不消除该既有风险；不得把 CORS 描述为 secret 保护或 OAuth Client 强认证替代品，后续是否迁移 public client + PKCE 需独立决策，non-blocking。
- 数据库字段容量：`allowed_origins` 继续使用 `VARCHAR(2048)`，本次不扩大字段；大量 Origin 可能达到长度上限，System 必须在保存时明确拒绝超长数据，若未来需要大规模动态来源应另行建模，non-blocking。
- public Client 身份建立：public Client 没有可验证 secret，本次仅在受限 Token grant 上按框架 `ClientAuthenticationMethod.NONE` 建立 Client Principal，安全性仍依赖 grant 自身的验证码、外部凭证、PKCE 或 refresh token 归属校验；不得将它描述为 confidential Client 强认证，non-blocking。
- Origin 索引缓存一致性：固定键缓存依赖 System 在事务提交后清理共享 OAuth Client 缓存；失效会短暂清除其他 Client 缓存并产生可恢复的 cache miss，但不能保留已撤销 Origin 或在缓存异常时默认允许，non-blocking。

## Verification strategy
1. IAM 单元测试覆盖预检允许/拒绝、Origin 精确匹配、空配置、未知 Client、重复 Origin Header、通配值拒绝和可选请求 Header 组合；Token 预检分别覆盖无非 safelisted Header、仅 `Authorization`、仅 `tenantId`、二者组合、显式 `Content-Type` 以及未知 Header，并明确预检及传输层的 Origin 全局存在性检查不是 Client 授权结果。
2. IAM Security/MockMvc 测试在真实过滤链中使用 confidential 与 public Client 及不同 Origin，证明合法 Origin + 匹配的已认证 Client 可到达原有 Token 认证结果；跨 Client 借用 Origin、未配置 Origin 和恶意 Origin 均在发 Token 前拒绝。
3. 参数化协议测试覆盖 confidential 与 public password、sms、email、external_code、authorization_code、refresh_token 和 client_credentials：public 自定义 grant 及 refresh 的匹配 Origin 能到达各自 grant Provider，authorization_code 仍要求 PKCE，public client_credentials、重复 client_id 和混入 secret/Authorization/assertion 的 public 请求拒绝；各 grant 原有业务凭证、Token 归属和 Client 认证语义保持不变。
4. 服务端回归测试使用不带 `Origin` 的 Basic、client_secret_post 和 public client 请求，证明不需要任何新增 Header 或参数，且结果与变更前一致。
5. System 单元测试覆盖 Origin 规范化、精确 HTTP(S) Origin 校验、任意浏览器 Token Client 可配置、空集合失败关闭语义、超长集合拒绝，以及删除“仅短信/邮箱 grant 才能配置”的旧限制。
6. System Flyway 集成测试从现有 V10 数据升级，断言新迁移不修改 `allowed_origins` 值，只更新字段注释；同时验证空值仍为空，不自动写入本机或部署 Origin。
7. Admin Vitest/Cypress 覆盖字段新文案、编辑回显/保存不丢值和空值失败关闭提示，并证明登录 Token POST 的现有 URL、body 及 Header 契约不变。
8. 执行相关 IAM/System Maven 测试、根 `mvn test -Plocal`、Admin 单测与生产构建；涉及的真实外部服务或数据库条件必须单独报告，不以 MockMvc 替代运行时证据。
9. 在本地 Compose 中为两个测试 Client 配置不同 Origin，分别验证允许来源成功、交叉 Client 在 CORS 传输许可后返回不泄露配置的通用 OAuth 请求错误、未知或全局未登记来源返回 403 且不带 ACAO、无 Origin 的服务端请求保持原结果；检查日志不包含 secret、密码、Token、验证码、授权码、完整请求体或异常消息。
10. 发布验证必须记录 Client Origin 配置清单和切换顺序；不执行自动生产部署，不把本地 Docker 结果描述为生产迁移证明或 `bands.yaml` 的 SLO 证据。
11. IAM 缓存测试证明任意 Origin 请求只读取同一个缓存索引，缓存命中不访问数据库；System 保存、改密和删除事务提交后清理 OAuth Client 缓存区域，随后重载能看到新增 Origin且不再接受已删除 Origin。并发缓存 miss 或缓存设施异常必须失败关闭，不以未命中冒充允许。
