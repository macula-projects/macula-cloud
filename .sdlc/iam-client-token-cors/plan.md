# Plan: IAM Client 级 Token CORS (from spec.md 2026-10-27)

## Files that change
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
