# Plan: IAM Client 级 Token CORS (from spec.md 2026-10-27)

## Files that change
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/ExternalCodeType.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/ExternalCodeTypeTest.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatExternalCodeVerifier.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifierTest.java`
- `macula-cloud-iam/README.md`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/resources/application.yml`
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`
- `.sdlc/iam-client-token-cors/plan.md`、`.sdlc/iam-client-token-cors/review-findings.md`：记录获批实施顺序、Plan review 结果和后续实际偏差。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`：移除部署级 Token Origin 列表，把 Client 级协议 CORS source 和认证后 Token Origin 过滤器装入 Authorization Server 安全链。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSource.java`：在现有验证码 CORS 基础上覆盖 Token 端点；Token 预检和实际请求的传输层只按 Origin 全局存在性返回 CORS 许可，不把该结果当作 Client 授权。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilter.java`：新增仅作用于带 Origin Token POST 的过滤器，在 OAuth Client 认证完成后检查已认证 Client 的 `allowed_origins`，并以框架 OAuth 失败响应拒绝不匹配请求。
- `macula-cloud-iam/src/main/resources/application.yml`：删除 `token-cors-allowed-origins` 配置绑定。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSourceTest.java`：覆盖 Token/验证码预检、Token 传输层 Origin 全局存在性、Header 集合、重复 Origin 和拒绝路径。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilterTest.java`：覆盖带 Origin 请求的已认证 Client Origin 校验及无 Origin 服务端旁路。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`：在真实 SecurityFilterChain 中覆盖双 Client 交叉拒绝、全部 Token grant 的统一 Origin 边界、非浏览器兼容和敏感错误边界。
- `macula-cloud-iam/README.md`：记录 Client 级 Token CORS、保持不变的 Token 请求契约、迁移顺序并删除全局 Origin 配置说明。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`：把 `allowedOrigins` 从验证码 grant 专属 Origin 扩展为通用浏览器协议 Origin；保留短信/邮箱 grant 必须使用 public PKCE Client 的约束，并增加字段长度边界。
- `macula-cloud-system/src/main/resources/db/migration/V11__oauth_client_browser_protocol_origins.sql`：只更新 `allowed_origins` 字段注释，不回填或修改 Client 数据。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`：覆盖任意 Client Origin 配置、空集合、非法/通配/超长 Origin、旧 Origin/grant 耦合移除，以及短信/邮箱 public PKCE 约束保留。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ExternalCredentialLoginMigrationIT.java`：将隔离 MySQL 升级验证扩展到 V11，证明 Origin 值不变且字段注释更新。
- `macula-cloud-system/README.md`：记录字段新语义、升级不回填及上线前配置要求。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：将“验证码允许 Origin”改为“浏览器协议允许 Origin”，补充空值失败关闭和精确 Origin 提示。
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`：断言字段新文案和保存/回显数据不变。
- `macula-cloud-admin/cypress/e2e/example.cy.js`：在既有登录拦截中固定 Token URL、body 和 Header 合同，证明未新增 Client 提示 Header 或参数。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`：覆盖 OAuth Client Origin 配置、空值提示、保存和重新打开回显。
- `macula-cloud-admin/README.md`：记录浏览器直连 IAM 的 Client Origin 契约及迁移要求，不再把 `/iam` 同源代理描述为唯一正常方式，并明确 Token 请求参数及 Header 不变。
- `deploy/docker-compose.yml`：移除 `IAM_TOKEN_CORS_ALLOWED_ORIGINS` 注入。
- `deploy/README.md`：删除全局 Token Origin 配置说明，增加先配置 Client Origin、再切换 IAM 的部署顺序和回滚边界。

## Order of work
1. 扩展 `ProtocolLoginCorsConfigurationSource`：验证码端点保持现有 Header Client 精确匹配；Token OPTIONS 和带 Origin 的 Token POST 传输层只在请求方法、允许 Header 声明和 Origin 全局存在性满足时返回许可。Token 只允许 `POST`/`OPTIONS`，允许的 Header 为 `Content-Type`、`Authorization` 和 `tenantId`，不携带浏览器凭据。明确该 source 不决定具体 Token Client 的 Origin 授权。
2. 实现 `OAuth2TokenOriginFilter`，仅匹配 Token POST 且请求带单值 Origin。过滤器放在 `OAuth2ClientAuthenticationFilter` 后，从 SecurityContext 的已认证 `OAuth2ClientAuthenticationToken` 取得 `RegisteredClient`，精确检查其 `allowed_origins`；失败委托 Spring Authorization Server 的 OAuth 错误处理，不记录 Origin、Client、请求值或异常正文。通过本地 7.0.7 源码和真实过滤链测试确认它在 `OAuth2TokenEndpointFilter` 及所有 grant Provider 前拒绝。
3. 在 `ProtocolOAuth2Configuration` 组合统一 CORS source 和 Origin 过滤器，删除静态 `tokenCorsAllowedOrigins`、旧 Token CORS source 及 `application.yml` 配置。无 Origin 请求不进入新过滤器，继续走原生 Client 认证和 Token Endpoint；Token URL、form、query 和 Header 合同完全不变。
4. 先补 IAM 单元及协议测试：两个 Client 使用不相交 Origin；覆盖预检/传输层仅是 Origin 存在性许可、实际 Origin/已认证 Client 绑定、重复或空 Origin、未知 Client、空 Origin 配置、跨 Client 借用、敏感错误输出、全部已支持 grant，以及无 Origin 的 Basic、client_secret_post、public client 回归。用 Provider/Token Generator 可观察断言证明拒绝发生在签发前。
5. 调整 System 保存校验：保留短信/邮箱 grant 必须是 public PKCE Client 的既有约束，只移除“验证码 grant 必须配置非空 Origin”和“非验证码 grant 不得配置 Origin”的耦合；继续使用现有 URI 规范化，并显式约束序列化后的 `allowed_origins` 不超过数据库 2048 字符。当前历史 confidential+sms Client 不因本次变更获得新权限，只有满足原安全约束的保存请求才可通过。
6. 增加 V11 注释迁移，不改历史 V9/V10 文件、不回填数据；扩展单元和隔离 MySQL 升级测试，证明值逐字保持、空值仍为空、字段注释更新，并由 System 独占 Flyway。测试还要复现当前内置 Admin Client 的 historical `confidential + sms + allowed_origins 为空` 记录，再执行与部署文档一致的受控 Origin 更新，断言只改变 `allowed_origins`，认证方式、grant、secret 摘要和其他字段逐项不变。
7. 更新 Admin OAuth Client 页面字段名称、精确 Origin/空值说明。扩展 Vitest 和接入应用 Cypress spec，证明配置保存回显与空值提示；不修改登录 Token 请求代码，但扩展既有登录 Cypress 拦截以固定现有 URL、body 和 Header，证明调用方合同没有变化。
8. 同步 IAM、System、Admin 和 deploy 文档，删除 `IAM_TOKEN_CORS_ALLOWED_ORIGINS` 的有效配置声明。写明旧 IAM 运行期间先盘点并配置每个浏览器 Client，再切换新 IAM。普通合规 Client 可走现有管理 API；对于当前内置 `confidential + sms` 等无法通过整表保存的历史 Client，提供经授权 DBA 执行的事务化 SQL：先 `SELECT ... FOR UPDATE` 固定 client_id、认证方式、grant 和原 Origin，仅在预期原值匹配时更新 `allowed_origins`，要求影响行数恰为 1，再次查询确认除 Origin 外字段未变后提交，否则回滚。示例只能使用占位符和明确的本地值，不能自动推导或写入共享/生产域名。回滚旧 IAM 时恢复旧部署级配置，数据库已有 Origin 值可以保留。
9. 实施阶段只做针对性静态检查和编译级自检并记录实际偏差；完整 IAM/System/root Maven、Flyway 隔离 MySQL、Admin Vitest/build/Cypress、Compose 双 Client 运行时验证全部留给 Stage 4，不在 Build 阶段提前宣称测试通过，也不部署、提交实现或推送。

## Risks
- Spring Authorization Server 的过滤器顺序必须以当前解析的 7.0.7 版本为准；若自定义过滤器实际位于 Client 认证前或 Token Endpoint 后，会造成合法请求误拒绝或绑定绕过。实现时必须检查本地依赖类并由真实过滤链测试证明顺序，不能仅依赖类名推断。
- 浏览器预检和实际 POST 的 CORS 传输层尚无已认证 Client，只能按 Origin 全局存在性许可传输；安全性依赖认证后的 Origin/Client 精确校验，任何路径若绕过该过滤器都可能造成跨 Client 来源借用。
- `allowed_origins` 语义扩展会影响现有短信/邮箱校验；只能解除 Origin 与 grant 的耦合，不能放宽短信/邮箱必须为 public PKCE Client 的既有安全规则。数据库中已有的不合规历史 Client 不得因编辑或迁移自动获得新权限。
- 现有 Client 数据为空且不自动回填。错误的发布顺序会让 Admin 和其他浏览器系统同时无法登录；切换前配置是发布阻断条件，但不在代码中提供回退或绕过。
- 当前内置 Admin Client 是历史 `confidential + sms` 组合，按既有安全规则不能通过整表保存接口更新。迁移必须使用文档化、经授权、带原值与影响行校验的窄 SQL，只改变 `allowed_origins`；不得为了迁移放宽 public PKCE 规则或覆盖其他 Client 字段。
- V11 只改注释但仍会改变 `flyway_schema_history`；必须由 System 执行并在隔离 MySQL 验证 V10→V11，IAM 不得创建重复迁移。
- 现有 confidential browser client 风险不在本次解决；新增 CORS 不得被文档描述为保护公开到浏览器的 client secret。
- `VARCHAR(2048)` 可能限制大型 Origin 清单；保存时应在写库前返回明确的非敏感校验错误，不能依赖数据库截断或异常。

## Proof
- `ProtocolLoginCorsConfigurationSourceTest` 证明 Token OPTIONS/POST 传输层只检查方法、Header 声明和 Origin 全局存在性，验证码端点仍按现有 Header Client 精确绑定；非法方法、Origin、重复 Origin 和不允许的 Header 均不返回 CORS 许可。
- `OAuth2TokenOriginFilterTest` 证明仅带 Origin 的 Token POST 要求已认证 Client 的 `allowed_origins` 包含当前 Origin，失败使用通用 OAuth 错误且不继续过滤链；无 Origin 和非 Token 请求完全旁路。
- `IamProtocolTest` 以真实安全链证明 Client A 的 Origin 不能调用 Client B，合法 Client/Origin 可到达原有成功或预期认证失败，password、sms、email、external_code、authorization_code、refresh_token、client_credentials 共用同一 Origin 边界，无 Origin 的服务端认证方式保持兼容，Token 请求合同没有新增 Header 或参数。
- `OAuth2ClientConfigServiceImplTest` 证明 Origin 可独立于 grant 配置、空集合合法且代表失败关闭、HTTP(S) 精确规范化继续生效，短信/邮箱 public PKCE 约束不变，并拒绝通配、路径、查询、片段、用户信息、非 loopback HTTP 和超过 2048 字符的持久化值。
- `ExternalCredentialLoginMigrationIT` 在显式隔离 MySQL 中从 V10 升到 V11，证明现有 `allowed_origins` 内容逐字保持、空值不回填、列注释更新且 System 独占迁移；再针对内置 historical `confidential + sms` Client 执行受控窄更新，证明只改变目标 Origin、只影响一行且认证方式、grant、secret 摘要和其余字段不变。
- `admin-identity-security-settings.test.js` 和 `admin-identity-security-settings.cy.js` 证明 Origin 字段新语义可见，已有值保存和重开后不丢失，空值提示明确；`example.cy.js` 断言登录 Token URL、body 和 Header 保持原合同且不存在新增 Client 提示。
- Stage 4 依次运行 IAM/System 最小 Maven 测试、根 `mvn test -Plocal`、隔离 MySQL migration IT、Admin `npm run test:unit`、`npm run build`、目标及完整 Cypress、`git diff --check`；分别报告静态、mock 浏览器、隔离数据库和代码测试证据。
- 本地 Compose 运行时使用两个 Client 与不同 Origin 验证允许、跨 Client、未知 Origin、空配置和无 Origin 服务端请求，并做日志敏感信息检查；该证据不替代真实外部系统验收、生产迁移或 `bands.yaml` SLO 数据。

## Implementation record
- 2026-10-27：按批准顺序完成实现，没有计划外范围偏差。Token 请求合同保持不变；全局 Origin 配置已删除；实际浏览器 Token POST 在 Client 认证后、grant Provider 前按已认证 Client 精确校验 Origin；无 Origin 的服务端请求旁路该校验。
- Build 阶段仅执行编译级与静态自检：目标模块 `compile`、`test-compile`、Admin 目标文件 ESLint、Compose 配置渲染和 `git diff --check` 均通过。首次 `compile` 发现一处重命名后的旧方法调用并已就地修正；未提前运行 Stage 4 的单元、集成、构建或运行时测试。
- Test 阶段首次真实过滤链启动发现 Spring Security 7 不允许把动态加入的 `OAuth2ClientAuthenticationFilter` 作为外部 `addFilterAfter` 顺序锚点。依据当前 7.0.7 源码，将 Origin 过滤器显式放在已注册的 `UsernamePasswordAuthenticationFilter` 前；Authorization Server 自身把 Client 认证放在 `AbstractPreAuthenticatedProcessingFilter` 后、Token Endpoint 放在 `AuthorizationFilter` 后，因此仍严格位于 Client 认证与 grant 处理之间，不改变批准架构。
- Test 阶段真实预检还证明 `CorsConfigurationSource` 对已识别 Token 端点返回 `null` 只会省略 CORS 响应头，不会稳定产生计划要求的 403。Token 请求带 Origin 但来源、方法或请求 Header 非法时改为返回空 CORS 策略，由 Spring 标准 CORS 处理器显式拒绝；无 Origin 请求、非协议端点及既有验证码端点的原失败语义保持不变。
- Compose 日志泄漏检查发现 Nacos naming client 会在 IAM 启动时以 INFO 输出非空 `username/password` 初始化属性。IAM 将该第三方 logger 收紧为 WARN；不改变 Nacos 连接或认证配置，仅确保本次要求的密码日志检查可成立。
- 最终全量 Cypress 复跑发现既有“延迟目录”用例依赖两次 hash 导航重新请求目录，应用复用当前页面状态时第二次请求并不稳定发生。测试改为第二段注册一次性延迟 intercept 后显式 reload，使“目录晚到”成为确定前提；页面行为与生产代码不变。

## Code Round 1 remediation (from accepted spec revision 2026-10-28)

### Additional files that change
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverter.java`：只为 password、sms、email、external_code、refresh_token 的 Token POST 解析单值 public `client_id`；遇到 Basic、secret 或 assertion 凭据时不接管框架认证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationProvider.java`：从 `RegisteredClientRepository` 建立受限的 `ClientAuthenticationMethod.NONE` Principal，要求 Client 仅配置 `none` 且登记当前 grant；不处理 authorization_code PKCE 或 client_credentials。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverterTest.java`、`PublicTokenClientAuthenticationProviderTest.java`：覆盖允许的 public grant、重复/缺失 client_id、混入 confidential 凭据、未知 Client、错误认证方式、未登记 grant 及明确拒绝 public client_credentials。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysOAuth2ClientService.java`、`impl/SysOAuth2ClientServiceImpl.java`：把逐 Origin `FIND_IN_SET` 查询替换为固定键缓存的规范化 Origin 集合；缓存 miss 一次读取非空 Origin 列，后续匿名请求只做集合包含判断。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImplTest.java`：证明固定键缓存命中不重复访问数据库，集合规范化，缓存加载失败关闭。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/CompatibilityRefreshTokenAuthenticationProvider.java`：public Client 交给框架标准 refresh Provider 校验 Token 归属，既有 confidential password/sms openid 兼容路径保持不变。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuer.java`：移除旧有的 public Client refresh 排除条件，使已认证 public 用户 grant 在 Client 已登记 `refresh_token` 时可签发 refresh token；归属仍由框架 refresh Provider 校验。
- `.sdlc/iam-client-token-cors/verification.md`：只追加修复后的 Stage 4 重跑命令、结果、运行时证据和历史失败说明。

### Remediation order
1. 修正 Token 预检：只校验浏览器实际声明的 Header 均在 allowlist；不再强制声明 safelisted `Content-Type`，保留验证码端点必须声明 `Content-Type` 和 Client Header 的旧规则。
2. 增加受限 public Token Client converter/provider，并通过 Spring Authorization Server `clientAuthentication` 扩展点注册；authorization_code 继续由官方 PKCE converter/provider 处理，confidential Client 继续走官方认证，public client_credentials 不进入扩展。
3. 保持 `OAuth2TokenOriginFilter` 位于 Client 认证后、grant Provider 前，但不再把缓存中的 RegisteredClient Origin 当作最终授权依据：过滤器用已认证 Client 的 `client_id` 走现有唯一索引读取权威数据库行，再精确比较 Origin。为 public sms、email、external_code、password、refresh 增加真实过滤链可达性测试，并覆盖混入 secret/Authorization/assertion、跨 Client Origin 和 public client_credentials 拒绝。
4. 将预检/传输层的全局 Origin 存在性改为共享 OAuth Client 缓存区域的固定索引键，并启用同步 single-flight 加载；System 的 Client 新增、修改、改密、删除在事务提交后清理该缓存区域。清理失败时旧索引最多暂时允许浏览器发送实际请求，不能通过第 3 步的权威 Client/Origin 校验；新增 Origin 在缓存刷新前保持失败关闭。
5. 更新 IAM/System 单元测试、README 和验证记录，重新执行 Stage 4 的目标 Maven、根 Maven、隔离迁移、Admin 单测/构建/E2E 与 Compose 运行时矩阵；修复后回到 Stage 5 Code Round 2，只审本轮增量。

### Remediation risks
- public Client 无 secret：converter/provider 只能建立 `none` Client 身份，不能替代 grant 凭证校验；必须限制 endpoint、grant、参数形态和 RegisteredClient 配置，防止绕过官方 PKCE 或把 confidential Client 降级为 public。
- public refresh：必须由框架 refresh Provider 校验 refresh token 与 RegisteredClient 归属；既有 compatibility wrapper 只保留 confidential password/sms openid 分支，不能自行放宽 Token 归属，且 Client B 使用 Client A refresh token 必须在 Token Generator 前拒绝。
- 缓存失效：全局缓存只提供非授权性的 CORS 传输许可；撤销 Origin 的最终拒绝依赖已认证 Client ID 的权威数据库行而不是缓存。缓存读取、single-flight 加载或权威数据库查询异常必须拒绝请求；缓存清理失败不得成为继续签发 Token 的条件。
- 预检 Header：Token 与验证码端点规则不同；只修 Token 的 safelisted Header 语义，不能让验证码端点丢失现有 Client Header 约束。

### Remediation proof
- public Client converter/provider 单元测试和 `IamProtocolTest` 真实链证明 sms、email、external_code、password、refresh 的匹配 Origin 能到达原 grant 校验，同时 authorization_code PKCE、confidential Client、public client_credentials 和恶意混合凭据保持原安全边界；public Client B 使用 Client A refresh token 必须返回 `invalid_grant` 且 Token Generator 无调用，既有 confidential compatibility refresh 继续通过。
- `ProtocolLoginCorsConfigurationSourceTest` 分别覆盖无非 safelisted Header、仅 Authorization、仅 tenantId、二者组合、显式 Content-Type 和未知 Header；验证码预检旧规则继续通过。
- IAM Origin 索引缓存并发测试证明任意 Origin 共用一个缓存项，多个并发 cache miss 只触发一次全表加载，其余请求复用同一结果；加载异常使全部等待请求失败关闭。System 测试证明新增/更新/改密/删除只在事务提交后清理 OAuth Client 缓存区域；额外测试模拟清理失败和陈旧预检索引，证明实际 POST 仍按已认证 Client ID 查询权威数据库并拒绝已撤销 Origin。
- 修复后的完整 Stage 4 验证重新证明 Maven、Flyway、Admin、Cypress、Compose 双 Client、无 Origin server-to-server 和敏感日志边界。

### Remediation implementation record
- 2026-10-28：按获批修复顺序完成源码与测试实现。Token 预检不再要求 safelisted `Content-Type` 出现在 `Access-Control-Request-Headers`；验证码预检仍要求 `Content-Type` 与 Client Header。public Client 扩展只接管 password、sms、email、external_code、refresh_token 的单值 `client_id` 且无 confidential 凭据请求；authorization_code PKCE、confidential Client 和 public client_credentials 继续由框架原路径处理。
- Origin 全局索引改为 `@Cacheable(sync=true)` 的固定缓存键。实际 Token POST 不信任该缓存或 RegisteredClient 缓存中的 Origin，而是用已认证 Client ID 读取权威数据库行并精确比较；System Client 写事务提交后清理 OAuth Client 缓存区域。public refresh 直接委托框架标准 Provider 校验归属，既有 confidential compatibility 分支不变。
- 新增 converter/provider、固定索引并发、缓存异常、权威 Origin、public grant 真实过滤链和跨 Client refresh 测试。首次 `test-compile` 因测试使用了错误的 Spring Security 7 Client token 构造器失败，修正测试夹具后目标 IAM/System `test-compile` 成功；未运行测试用例。
- Stage 4 首次定向执行证明旧签发器仍显式排除 `ClientAuthenticationMethod.NONE` 的 refresh token；为实现已接受的 public refresh 要求，补充修改 `OAuth2UserGrantTokenIssuer`，仅移除该排除条件，不改变 Client 必须登记 `refresh_token`、Client 已认证和框架 refresh 归属校验。该实际偏差已补入 Additional files 并由成功、跨 Client 拒绝及 confidential 回归测试覆盖。
- 直接执行 Maven `checkstyle:check` 先触发插件默认 `sun_checks.xml`，随后使用仓库 `.config/checkstyle.xml` 时暴露 IAM/System API 的既有全量违规。改为用同一仓库规则和 header 精确检查本次修改的 IAM/System 主代码文件，均为 0 violation；同时修复了本次触及的 compatibility provider 中既有无花括号控制语句。`git diff --check` 通过。
- Stage 4 修复后验证于 2026-10-28 全绿：目标 IAM 57 项与 System 13 项、根 11 模块、隔离 MySQL V11、Admin 51 项 Vitest、20 项目标及 30 项全量 Cypress、生产构建、变更文件 Checkstyle/ESLint、Compose 配置和运行时矩阵全部通过。Compose 还证明陈旧传输索引下已撤销 Origin 仍被权威数据库校验拒绝；两个临时 Client Origin 已恢复为空。完整命令和证据见 `verification.md`。

## Code Round 2 remediation

### Additional files that change
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ClientCacheNames.java`：新增 IAM/System 共用的 Token Origin 索引缓存 region 名称，避免与任意 OAuth Client ID 共用键空间。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverter.java`：构造时接收 Token endpoint matcher，并在解析任何参数前只接受匹配的 POST Token 请求。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`：使用当前 `AuthorizationServerSettings` 的 Token endpoint 创建 matcher 后注册 public converter，不改变其他 Client 认证端点。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverterTest.java`：覆盖 Token POST 成功以及 introspection、revocation、device authorization、PAR、非 POST 和相似路径不接管。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`：在真实共享 Client 认证过滤链中证明 public Client 不能通过伪造 `grant_type` 认证 introspection/revocation；既有 confidential 端点与 public Token grant 保持通过。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImpl.java`：把固定 Origin 索引移到共享契约定义的独立 cache region，固定 region 内仍只使用单一索引键并保持 `sync=true`。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImplTest.java`：同时装配 Client 与 Origin 两个 cache region，证明相同字符串键可在两个 region 分别保存不同类型且 Origin 并发 single-flight 不回退。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`：事务提交后分别清理 RegisteredClient region 与 Token Origin 索引 region；任一 region 缺失或清理异常都显式失败。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`：覆盖提交后两个 region 都被清理、提交前/回滚不清理、region 缺失和任一清理异常显式失败。
- `.sdlc/iam-client-token-cors/plan.md`、`.sdlc/iam-client-token-cors/review-findings.md`、`.sdlc/iam-client-token-cors/verification.md`：记录修复计划、Plan review、实施偏差和重新验证证据。

### Remediation order
1. 在 System API 定义独立的 Token Origin 索引 cache region 常量，IAM 和 System 都只引用该共享契约；不修改 Macula Boot，也不更改现有 RegisteredClient cache region。
2. 将 `allowedOrigins()` 的固定索引键迁到新 region。System 的事务后清理同时覆盖旧 Client region 和新 Origin region，先解析并确认两个 region 都存在，再注册单个 `afterCommit` 回调，避免配置错误只清一半。
3. 为 public Client converter 注入严格的 POST Token endpoint matcher，并在读取 form/query 参数前拒绝所有非匹配请求。matcher 使用 `AuthorizationServerSettings.getTokenEndpoint()`，不硬编码 `/oauth2/token`，不影响官方 confidential、PKCE、introspection、revocation、device authorization 或 PAR converter/provider。
4. 增加 converter 单元、真实安全链和双 cache region 回归测试；保留 Code Round 1 的 public password/sms/email/external_code/refresh、跨 Client refresh、预检 Header、权威 Origin 与 single-flight 测试。
5. 实施完成后返回 Stage 4，重新执行目标 Maven、根 Maven、隔离迁移、Admin Vitest/build/Cypress、变更文件 Checkstyle/ESLint、Compose 配置与运行时矩阵；验证全绿后再进入 Code Round 3。

### Remediation risks
- 端点 matcher 必须在 converter 的最前面执行；若先解析参数，非 Token 端点仍可能被异常或参数语义影响。Token endpoint 可配置，禁止在 converter 内写死默认路径。
- Spring Authorization Server 的共享 Client 认证过滤器覆盖多个端点；负向测试必须经过真实过滤链，不能只依赖 converter 单测。
- 分离 cache region 后，System 必须对两个 region 保持同一事务提交后的失效语义；只清旧 region 会让新增 Origin 暂时失败关闭，只清新 region 会保留陈旧 RegisteredClient。两者都不能在事务回滚时清理。
- cache region 名称是 IAM/System 跨服务安全契约，应放在现有 System API 模块；不得在两个部署模块复制字符串，也不得为此把运行时实现引入 API 模块。
- 当前工作区尚未提交，Code Round 3 仍需以 remediation 文件范围和记录的 finding 为界去重，不能把旧 finding 重报为新问题。

### Remediation proof
- `PublicTokenClientAuthenticationConverterTest` 证明只有配置 Token endpoint 的 POST 请求可建立受限 public Client 请求；introspection、revocation、device authorization、PAR、GET 和近似路径均返回 `null`，且非 Token 请求不会因伪造 grant/client 参数抛出 public converter 错误。
- `IamProtocolTest` 先取得有效 Token，再证明 public Client 使用支持的伪造 `grant_type` 调用 introspection/revocation 不能建立 Client 身份或读取/撤销 Token；confidential introspection/revocation 和全部既有 public Token grant 回归保持。
- `SysOAuth2ClientServiceImplTest` 证明 Origin 索引 cache region 与 RegisteredClient region 名称不同，同一 `__allowed_origins__` 键可分别保存 `Set<String>` 与 Client marker，不发生类型碰撞；8 路并发 miss 仍只加载一次。
- `OAuth2ClientConfigServiceImplTest` 证明成功事务只在提交后清理两个 cache region，失败写入和回滚均不清理，region 缺失或清理异常可见；权威数据库 Origin 校验继续使陈旧传输索引失败关闭。
- 修复后的 Stage 4 完整证据追加到 `verification.md`，并把 Code Round 2 两项 finding 仅在验证完成后标为 fixed。

### Remediation implementation record
- 2026-10-28：按获批顺序完成 Code Round 2 修复，没有计划外范围偏差。新增 System API 共享缓存 region 常量；IAM Origin 索引迁到独立 region；System 在事务提交后清理 RegisteredClient 与 Origin 索引两个 region，并在 region 缺失或任一清理失败时显式暴露错误。
- public Client converter 现在使用基于当前 `AuthorizationServerSettings` Token endpoint 的严格 POST matcher，并在读取任何请求参数前拒绝 introspection、revocation、device authorization、PAR、非 POST 和近似路径。真实安全链回归覆盖 public Client 不能借伪造 `grant_type` 在 introspection/revocation 建立身份，且未授权 revocation 不会撤销目标 Token。
- 补充双 cache region 类型隔离、8 路 single-flight 保持、两类清理异常、region 缺失、提交前和回滚不清理测试。仓库不存在 `CLAUDE.md`，因此按根 `AGENTS.md` 的按需规则读取并遵循 architecture、backend-development 与 testing 规则，没有绕过项目约束。
- Build 阶段执行 IAM/System 及依赖模块 `test-compile`，5 个 reactor 模块成功；按仓库 Checkstyle 配置精确检查本轮变更的 System API、IAM、System 主代码文件，均为 0 violation；`git diff --check` 通过。尚未运行测试用例，正式验证留给 Stage 4。

## Runtime remediation: 历史 confidential 验证码 Client 可维护性

### Additional files that change
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`：更新时先读取现有 Client，再执行带历史状态的校验；只对“数据库中已存在且认证方式、grant 集合均未改变”的 confidential + sms/email 组合保留维护通道，新建或新增该非法组合仍拒绝。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`：复现内置 Client 原样保存失败，覆盖历史组合更新 Origin 成功、认证方式/grant 扩权拒绝、新建拒绝及整改为合法组合成功。
- `macula-cloud-system/src/main/resources/application.yml`：将 OAuth Client Mapper 日志固定为 INFO，避免 MyBatis DEBUG 参数日志输出单向哈希后的 Client secret。
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`：提供 OAuth Client 页面专用的安全错误摘要函数，只从服务端 `cause` 中提取明确的 `ResponseStatusException` 业务原因，其他异常继续使用公开 `msg`/兜底文案。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：保存失败时显示可执行的业务校验原因；历史 confidential 验证码 Client 显示兼容提示，但不自动修改认证方式、grant、secret 或 PKCE。
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`：覆盖历史 Client 无损回显/保存、错误摘要和兼容提示。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`：覆盖管理员为历史内置 Client 修改 Origin 并保存成功，以及非法改变认证方式/grant 时展示明确原因。
- `macula-cloud-system/README.md`、`macula-cloud-admin/README.md`、`deploy/README.md`：把“历史 Client 只能 DBA 窄更新”修订为切换前可用 DBA 路径、升级后可由管理 API 在不改变历史认证方式/grant 的条件下维护；继续禁止自动迁移和静默放宽。
- `.sdlc/iam-client-token-cors/plan.md`、`.sdlc/iam-client-token-cors/review-findings.md`、`.sdlc/iam-client-token-cors/verification.md`：记录运行时复现、补救计划评审、实施偏差与重新验证证据。

### Remediation order
1. 以本地 Compose 的同一份 GET/PUT 数据固定回归：内置 Client `client_secret_basic,client_secret_post + password,sms` 原样保存当前返回系统异常，后端实际原因为 public Client 约束。
2. 将 System 保存流程调整为更新时先读取现有行，再校验请求。新建 Client 继续严格执行 sms/email 必须 public PKCE；更新只有在现有行已经是 confidential 验证码组合，且请求的认证方式集合与 grant 集合都与数据库完全相同时，才允许修改 Origin、显示信息、回调、scope、TTL 等既有管理字段。增加 sms/email、认证方式和 grant 集合顺序差异的规范化比较，不能用 CSV 字符串顺序误判变更。历史豁免写入必须以读取时数据库中的原始认证方式和 grant 字符串作为条件更新；条件不匹配返回冲突并要求刷新，不能让旧表单覆盖并发完成的安全整改。
3. 对历史 Client 的任何认证方式或 grant 变化重新执行完整目标状态校验：删除 sms/email、完整迁移为 public PKCE 等整改后的合法状态允许；增加 sms/email、在 confidential 方法间切换、增加 grant 或留下新的非法组合均拒绝。不得自动删除 secret、改为 public、启用 PKCE 或重写已有数据；只有管理员显式提交且目标状态完整满足 public PKCE 约束时才允许整改。
4. Admin 保持完整表单契约和无损回显，增加历史兼容警示；保存错误只显示可识别的业务原因，不把任意内部异常 `cause` 暴露给用户。`.env`、Token 请求参数和 Client secret 契约不变。
5. 更新单元、Cypress 和 README 后进入 Stage 4：执行目标 System 测试、根 Maven、Admin Vitest/build/Cypress、变更 Java Checkstyle、前端 ESLint、Compose 配置及本地真实 GET/PUT/Token/UserInfo 回归。

### Remediation risks
- 历史豁免若只检查“目标仍含 sms/email”，会允许管理员把其他 Client 改造成非法 confidential 验证码 Client；必须同时证明数据库旧状态已非法且认证方式、grant 两个集合都完全未变。
- CSV 顺序不是授权语义；比较必须使用规范化集合，否则仅由 Admin 重排字段就会误拒绝，或重复值/空值被错误视为等价。
- 允许维护不等于认可历史安全模型。不得自动把 confidential 浏览器 Client 转成 public、清空 secret 或改变 grant；管理员显式提交且目标状态完整满足 public PKCE 约束时允许安全整改，其他安全演进仍是独立变更。
- 历史豁免基于读取快照时，普通 `updateById` 会让旧请求覆盖并发整改；豁免路径必须对原始 auth/grant 做条件更新，影响行数不是 1 时以冲突失败且不得清缓存。
- 直接展示统一异常响应的任意 `cause` 可能泄露内部实现。前端只识别固定的 HTTP 状态原因格式并提取引号内业务文本，未知格式回退公开 `msg`。
- 事务与缓存语义不得改变：成功更新仍只在事务提交后清理 RegisteredClient 与 Origin 索引两个 region，失败校验不写库、不清缓存。

### Remediation proof
- `OAuth2ClientConfigServiceImplTest` 使用与 V1 内置记录一致的历史实体，证明不改变 auth/grant 时可新增、删除或清空精确 Origin；新建 confidential sms/email、向合法 confidential Client 新增 sms/email、改变历史 Client auth/grant 为另一非法组合均拒绝；删除验证码 grant 或显式迁移到完整 public PKCE 配置允许。并发测试模拟读取后另一请求已整改 auth/grant，断言旧历史表单的条件更新影响 0 行、返回冲突、不清缓存且不能把非法组合写回。
- Admin Vitest 证明历史字段回显不丢失、兼容提示可见、明确的 `400 BAD_REQUEST "..."` 只提取业务原因，未知 `cause` 不展示；Cypress 证明 Origin 保存请求保持完整字段且成功后重新打开可回显，并覆盖非法变更错误态与重复提交保护。
- 目标 System 和根 Maven 回归证明现有严格新建规则、Origin 规范化、事务后双缓存失效及其他模块不回退；Admin build/lint 和完整 E2E 证明页面契约不破坏。
- 本地 Compose 使用管理员真实 Token 执行 GET Client、PUT 仅改 Origin、再次 GET、Token CORS 预检/签发和 UserInfo；记录 HTTP 状态与非敏感字段，不输出 secret 或 Token，并恢复测试前 Origin 值。

### Runtime remediation implementation record
- 2026-10-29：按获批补救顺序完成实现。System 只对数据库中已存在且 auth/grant 语义集合未改变的 confidential 验证码 Client 使用带原始 auth/grant 条件的更新；并发条件不匹配返回 409。新建、扩展或变成另一非法组合继续拒绝；删除验证码 grant 或管理员显式提交完整 public PKCE 目标状态仍允许，未自动改写 secret、PKCE 或协议字段。
- Admin 增加历史兼容提示，并只从固定 `4xx STATUS "业务原因"` 格式提取可展示原因；未知 `cause` 回退公开 `msg`。Origin 保存保持完整表单字段，保存中的重复触发由既有 `saving` 门闩拒绝。`.env`、Token 请求及 Client secret 合同未改变。
- 为满足 artifact gate 的精确路径匹配，在既有 `Files that change` 顶部重复列出四个已有目标路径；这只是计划工具兼容记录，不改变批准范围或实现语义。Cypress 首次新增用例命中隐藏 Drawer，改为限定当前可见 Drawer；重复提交覆盖首次把 `ok()` 静态响应再次包入 `body`，修正为在原静态响应上增加延迟。两项均为测试夹具修正，生产代码不变。
- Build 阶段反馈：System 目标测试 20 项、Admin 目标 Vitest 10 项、目标 Cypress 21 项、Admin production build、变更前端文件 ESLint 和两份变更 Java 的仓库规则 Checkstyle 均通过；`git diff --check` 无空白错误。首次扩展三种 Origin 维护场景时 Mockito 精确实体断言匹配两次，改为验证三次均走条件更新并直接断言返回协议字段不变。以上不替代 Stage 4 的根 Maven、完整前端、Compose 及真实 GET/PUT/Token/UserInfo 验证。
- Stage 4 首轮 Compose 日志检查发现 `SysOAuth2ClientConfigMapper` 的 MyBatis DEBUG `Parameters` 两次输出 BCrypt 密钥哈希；虽然不是明文，仍不满足敏感日志边界。按失败回路补充精确 logger 覆盖为 INFO，不影响其他 `dev.macula.cloud` DEBUG 日志，并要求重建 System 后重新执行完整验证。
- 2026-10-29 人工界面反馈指出 OAuth Client 编辑抽屉正文贴边、标签列过宽且登录联动区域层级拥挤。保持字段、联动和保存契约不变，将抽屉扩为最大 920px，增加 28px 正文留白，把基础信息和登录能力放入独立分组容器，标签列收窄为 150px，并为 720px 以下视口切换单列标签布局；目标 Vitest、Cypress、ESLint、Prettier 和生产构建通过，Admin 本地镜像已替换，完整 Stage 4 需在进入下一门禁前重跑。
- 同轮人工反馈确认“浏览器兼容凭证”会被误解为上方终端登录方式的重复配置。保留历史协议能力但改名为“历史兼容 Code”，明确上方已自动派生标准 code type、这里只服务旧调用方直接使用浏览器 Code 调用 `external_code` grant；身份源绑定改为纵向行，长 code 名不再作为 150px 表单标签，说明、提示和登录卡片补充行高与间距。`macula-samples-admin-bff/application.yml` 只配置资源服务器 JWT 与 app-key/secret，不构成这些历史浏览器 code type 的使用证据。
- 2026-10-29 人工进一步确认该能力属于全新开发，不需要任何历史浏览器 `external_code` 兼容。上一条“保留并改名”的决定作废：删除 Admin 兼容入口和未知类型摘要，编辑时只保留终端登录模型支持的标准 code type/绑定；同时删除 System API 枚举及 IAM 四个 verifier 中的 `*_BROWSER_CODE` 协议分支与文档说明。浏览器第三方登录统一从 `/oauth2/authorize` 进入，既有 APP、小程序和 WEBVIEW code type 保持不变；这属于人工需求纠正，不承诺旧试验数据兼容。
