# IAM Client 级 Token CORS 验证记录

日期：2026-10-28。

**Verification is green**。Code Round 2 两项 finding 的修复已完成目标测试、全量回归、隔离迁移、管理端和本地 Compose 运行时验证；此前 Code Round 1 三项 finding 的回归也保持通过。该结论不代表生产环境验证或 Code Round 3 评审通过。未提交、推送、发布或部署到远端环境；本地运行时验证临时写入的两个 OAuth Client Origin 已恢复为空。

## 修复后验证结果

| 实际命令/检查 | 退出码 | 实际结果 |
| --- | --- | --- |
| `mvn -pl macula-cloud-iam,macula-cloud-system -am -Plocal -Dtest=PublicTokenClientAuthenticationConverterTest,PublicTokenClientAuthenticationProviderTest,SysOAuth2ClientServiceImplTest,OAuth2TokenOriginFilterTest,ProtocolLoginCorsConfigurationSourceTest,IamProtocolTest,OAuth2ClientConfigServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 | IAM 65 项、System 15 项通过，0 失败/错误/跳过；`BUILD SUCCESS`，12.593s |
| `mvn test -Plocal` | 0 | 11 个 Reactor 模块全部成功；IAM 243 项通过，并包含 TinyID MySQL Testcontainers 集成测试；`BUILD SUCCESS`，37.676s |
| `SYSTEM_EXTERNAL_LOGIN_MIGRATION_IT=true ... mvn -pl macula-cloud-system -am -Dtest=ExternalCredentialLoginMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 0 | 全新 MySQL 8.4 隔离库从 V1/V8 经 V9、V10 升至 V11；1 项通过；`BUILD SUCCESS`，8.611s；临时容器已删除 |
| 管理端 `npm run test:unit` | 0 | 9 个文件、51/51 通过 |
| 管理端 `npm run build` | 0 | 2467 个模块构建成功；仅有既有 Sass `@import` 弃用和大 chunk 警告 |
| 管理端目标 Cypress `admin-identity-security-settings.cy.js` | 0 | 20/20 通过 |
| 管理端 `npm run test:e2e:ci` | 0 | 4 个 spec、30/30 通过；仅 Electron 弃用警告 |
| System API、IAM、System 变更 Java 主代码与测试的仓库 Checkstyle | 0 | 三模块精确检查均为 0 violation |
| 管理端变更文件 `npx eslint ...` | 0 | 无输出、无自动修改 |
| `docker compose ... --profile apps config --quiet` | 0 | Compose 配置解析成功 |
| `docker compose ... build macula-cloud-system macula-cloud-iam` 与目标 `up -d --no-deps --no-build --force-recreate --wait` | 0 | 只重建并替换 System/IAM；两服务最终均 healthy，未重建依赖服务或删除卷 |
| `git diff --check` | 0 | 无输出 |

保护配置 eval 不适用：未修改 `AGENTS.md`、`CLAUDE.md`、skill、hook、`REVIEW.md`、`bands.yaml` 或 reviewer 定义。

## Code Round 2 修复证据

1. `PublicTokenClientAuthenticationConverterTest` 的 16 项测试证明 converter 只接受配置 Token endpoint 的 POST；introspection、revocation、device authorization、PAR、GET 和近似路径在解析参数前返回 `null`。
2. `IamProtocolTest` 在真实共享 Client 认证过滤链中先签发有效 Token，再以 public `client_id + grant_type=password` 调用 introspection/revocation；两个请求只能进入现有登录失败路径且响应正文为空，随后 confidential introspection 仍返回 `active=true`，证明未读取或撤销目标 Token。
3. `SysOAuth2ClientServiceImplTest` 证明 RegisteredClient 与 Origin 索引使用不同 cache region；同一 `__allowed_origins__` 键分别保存 Client marker 与 `Set<String>` 不发生类型碰撞，8 路并发 miss 仍只加载一次。
4. `OAuth2ClientConfigServiceImplTest` 证明成功事务只在提交后清理两个 region，写入失败和回滚不清理；任一 region 清理失败或 region 缺失均显式失败，且会尝试清理另一个已取得的 region。
5. Compose 运行时加载后，Redis 中可见独立的 `cloud:iam:oauth2:client` Client 项与 `cloud:iam:oauth2:origin-index` 固定索引项。允许、跨 Client、未知 Origin、无 Origin和陈旧传输索引矩阵全部保持预期，说明 region 分离未改变最终授权边界。

## Code Round 1 修复证据

1. public Client 认证扩展只接管 password、sms、email、external_code、refresh_token，且要求单值 `client_id`、无 confidential 凭据、注册方式仅为 `none`、登记当前 grant。authorization_code 仍走官方 PKCE 路径，public client_credentials 仍拒绝。
2. 真实过滤链证明 public password 成功并签发 refresh token，public refresh 成功，Client B 使用 Client A refresh token 返回 `invalid_grant`；sms、email、external_code 均已越过 Client 认证并到达原 grant 校验。
3. Token 预检覆盖无声明 Header、Authorization、tenantId、二者组合、显式 Content-Type 和未知 Header；不再强制 safelisted Content-Type，验证码端点旧 Header 规则保持。
4. 固定键 Origin 索引的 8 路并发 cache miss 只加载一次；加载异常全部失败关闭。实际 Token POST 始终按已认证 `client_id` 走索引查询权威数据库行，陈旧传输缓存不能签发 Token。
5. System Client 新增、修改、改密、删除的事务提交后清理整个 OAuth Client 缓存区域；清理异常测试证明异常不会被吞掉。public refresh 由框架标准 Provider 校验归属，confidential compatibility refresh 回归保持通过。

## 本地 Compose 运行时证据

只重新构建并替换 `macula-cloud-system`、`macula-cloud-iam`，未重建依赖服务、未删除卷。共享库 `flyway_schema_history` 确认 V11 成功；最终所有 apps profile 服务及 MySQL、Redis、Nacos、RocketMQ 均为 healthy。

为避免记录 Token 或凭据，以下只记录状态、ACAO 和非敏感 OAuth 错误码：

| 场景 | 结果 |
| --- | --- |
| Client A + A 的允许 Origin | HTTP 200，ACAO 为精确 Origin |
| Client B + A 的 Origin（A 已获传输许可） | HTTP 400，ACAO 为 A，`invalid_request` |
| 未登记 Origin | HTTP 403，无 ACAO |
| 无 `Origin` 的服务端 password 请求 | HTTP 200，无 ACAO，兼容原行为 |
| 撤销 Client B Origin、传输索引仍陈旧 | HTTP 400，仍有传输 ACAO，但权威校验拒绝且未签发 Token |
| 清理 Client 缓存并重启 IAM 后使用 B 的旧 Origin | HTTP 403，无 ACAO |

两个测试 Client 的 `allowed_origins` 最终复核为 2 条空值。IAM 新鲜启动日志敏感模式检查为 `NO_SENSITIVE_LOG_MATCH`。

## 历史失败与修正

- 首次修复后定向测试发现 public converter 对无 `grant_type` 的非 Token/撤销请求调用 `Set.of().contains(null)`，已改为先做非空判断并增加回归测试。
- public password 首次到达用户认证后因测试夹具缺少 Client 租户/用户类型路由失败；补齐真实路由夹具后成功。
- 既有用户 grant 签发器显式排除了 `ClientAuthenticationMethod.NONE` 的 refresh token，与获批 public refresh 设计冲突；移除该旧限制后，public refresh 成功且跨 Client 归属拒绝通过。
- Service 缓存单测最初未匹配 MyBatis-Plus `selectOne(wrapper, throwEx)` 默认重载；修正 Mock 桩点后权威 Client 查询测试通过。
- 直接数据库撤销不会模拟 System 的缓存清理通知，因此仅重启 IAM 仍可能看到 Redis/本地传输索引；即使如此权威数据库校验始终返回 400 且不签发 Token。清理 Client 缓存并重启后按预期变为 CORS 403。
- Code Round 2 首次定向测试把无凭据 introspection/revocation 的失败合同预期为 `401 invalid_client`，真实安全链沿用项目现有 `302 /login` 且空正文；测试改为断言该既有合同，并继续用 confidential introspection 的 `active=true` 证明目标 Token 未被读取或撤销。修正后定向及全量测试均通过。
- 隔离迁移测试首次只设置启用开关，测试的安全门禁因缺少显式全新数据库连接而按预期失败，未接触现有数据库；创建一次性 MySQL 8.4 数据库并传入限定前缀连接后测试通过，容器随后删除。

## 边界与下一步

- 本地 Compose 证据不代表外部系统、生产迁移或 `bands.yaml` SLO 证据。
- 正式切换前仍须按发布清单为每个浏览器 OAuth Client 配置精确 Origin；空值按设计失败关闭。
- Stage 4 已完成；下一步是 Stage 5 Code Round 3，只审 Code Round 2 修复增量。

## 2026-10-29 历史 OAuth Client 保存补救验证

**Verification is green**。历史 `client_secret_basic,client_secret_post + password,sms`
Client 现在可在认证方式和 grant 语义集合不变时维护 Origin 等管理字段；新建、扩权或改变成另一
非法组合仍被拒绝。Admin 对可识别的业务校验错误显示精确原因，未知异常不展示任意内部 `cause`。
`.env`、Token 请求参数、Client secret 契约及既有 Client 协议字段均未改变。

### 实际命令与结果

| 实际命令/检查 | 退出码 | 实际结果 |
| --- | --- | --- |
| `mvn test -Plocal` | 0 | 11 个 Reactor 模块全部 `SUCCESS`；`BUILD SUCCESS`，37.483s。System 的 `OAuth2ClientConfigServiceImplTest` 20 项和 IAM `IamProtocolTest` 29 项通过；System 仅保留 1 项显式环境门禁跳过 |
| `SYSTEM_EXTERNAL_LOGIN_MIGRATION_IT=true SPRING_DATASOURCE_* ... mvn -pl macula-cloud-system -am -Dtest=ExternalCredentialLoginMigrationIT ... test -Plocal` | 0 | 全新临时 MySQL 8.4 隔离库验证 V1/V8 至 V11；1 项通过，4 个 Reactor 模块成功；`BUILD SUCCESS`，6.720s；临时容器已删除 |
| 管理端 `npm run test:unit -- --run` | 0 | 9 个文件、52/52 通过，1.44s |
| 管理端 `npm run build` | 0 | 2467 个模块构建成功，5.92s；仅有既有 Sass `@import` 弃用和大 chunk 警告 |
| 管理端 `npm run test:e2e:ci` | 0 | 4 个 spec、31/31 通过；目标 OAuth Client spec 为 21/21 |
| 管理端变更文件 `npx eslint ... --no-fix` | 0 | 无输出、未修改文件 |
| System 变更 Java 的仓库 Checkstyle | 0 | `You have 0 Checkstyle violations`，`BUILD SUCCESS` |
| `docker compose ... config --quiet` 和 `build macula-cloud-system` | 0 | Compose 解析成功；System 镜像构建成功 |
| `docker compose ... up -d --no-deps --no-build --force-recreate --wait macula-cloud-system` | 0 | 只替换 System；最终 `healthy`，容器镜像 ID 与新构建镜像一致；未重建其他服务或删除卷 |

保护配置 eval 不适用：未修改 `AGENTS.md`、`CLAUDE.md`、skill、hook、`REVIEW.md`、
`bands.yaml` 或 reviewer 定义。新增的 `application.yml` 变化只把
`SysOAuth2ClientConfigMapper` 日志固定为 INFO，避免 MyBatis DEBUG 参数日志输出 BCrypt 密钥哈希。

### 真实 Compose 证据

验证使用本地 Admin 密码登录和 Client 2 的真实 GET/PUT，不记录 Token、密码、secret 或密钥哈希：

| 场景 | 实际结果 |
| --- | --- |
| Admin password Token | HTTP 200 |
| GET OAuth Client 2 | HTTP 200 |
| 只增加临时 Origin 并保存 | HTTP 200，响应确认 Origin 已写入；认证方式和 grant 未改变 |
| 在历史 confidential Client 上非法增加 `email` grant | 统一异常封装 HTTP 500，但安全提取出的业务原因精确为“短信或邮箱验证码 grant 必须由公共客户端使用” |
| 恢复原 Origin | HTTP 200，响应与测试前 Origin 集合完全一致 |
| Admin 实际 UserInfo `/system/api/v1/users/me` | HTTP 200，返回非空 username |
| 新 System 容器日志 | access token、refresh token、client secret 值、Bearer Header、password grant 参数、BCrypt 值和 `client_secret` 字段命中均为 0 |

单独检查 OIDC `/userinfo` 返回 403，是因为历史 Admin Client 没有 `openid` scope；Admin 实际调用的
`/system/api/v1/users/me` 为 200，因此该结果不属于本次保存补救回归失败，也未修改 OIDC 契约。

### 失败回路与修正

- 全量 Cypress 首轮复用 Element Plus loading 后的 DOM alias，Cypress 自身读取旧 alias `selector`
  失败；改为断言当前可见保存按钮处于 loading/disabled，并继续断言只发出一次请求，目标与全量
  Cypress 随后均通过。
- 隔离 MySQL 首轮使用 `mysqladmin ping` 过早判定就绪而收到 1045；改为实际执行 `SELECT 1`。
  本轮第一次重跑又误传 `SYSTEM_MIGRATION_IT_*`，安全门禁因缺少 `SPRING_DATASOURCE_*` 明确失败；
  使用测试声明的变量后通过，两次失败均未接触现有数据库。
- Stage 4 首轮日志扫描发现两条 MyBatis `Parameters` 日志包含 BCrypt Client secret 哈希。虽然不是
  明文，仍按敏感日志失败处理；精确关闭 OAuth Client Mapper DEBUG 参数日志、重建 System 并重新
  执行根测试、前端测试、迁移、Compose 和真实保存后，全部敏感模式计数为 0。
- Checkstyle 首次手工命令漏传仓库规则路径，误用 `sun_checks.xml` 报出 205 条无关旧规则；使用
  `.config/checkstyle.xml` 与 `.config/checkstyle-header.txt` 重跑后为 0 violation。

### 边界与交接

- 以上是单元/浏览器 mock、隔离数据库和本地 Compose 证据，不代表生产、真实外部身份提供商或
  `bands.yaml` SLO 证据。
- 未提交、推送、创建 PR、发布或操作远端环境。
- Stage 4 验证完成；下一步进入 `sdlc-deploy` 的人工 PR 评审准备，不能由代理自批门禁。

## 2026-10-29 验证后 UI 布局反馈

在人工验收指出 OAuth Client 编辑抽屉贴边和联动区拥挤后，追加了纯布局调整，未改变字段、API、
联动映射或保存逻辑。目标 Vitest 10/10、OAuth 页面 Cypress 21/21、变更文件 ESLint/Prettier、
Admin production build 和本地 Docker 健康检查均通过，并已在真实本地页面确认正文留白、分组卡片、
登录方式双列及固定 footer 正常。该目标证据不替代完整 Stage 4；进入下一门禁前必须重新执行完整
前端与项目回归，因此上方 2026-10-29 的 green 结论只覆盖追加布局调整之前的代码快照。

后续同轮文案验收把“浏览器兼容凭证”改为“历史兼容 Code”，并将长 code type 与身份源绑定改为
纵向响应式布局；目标 Vitest 10/10、OAuth 页面 Cypress 21/21、ESLint/Prettier 和 production
build 通过。该结果同样属于目标反馈证据，不改变完整 Stage 4 仍需重跑的结论。

人工随后确认该功能为全新开发，不接受历史浏览器 code 兼容。实现已删除 Admin 的兼容入口与摘要、
System API 的四个 `*_BROWSER_CODE` 枚举值，以及 IAM 对应 verifier 分支；浏览器第三方登录只保留
`/oauth2/authorize` 路径。编辑包含旧试验值的表单时，Admin 会在提交前只保留终端模型支持的标准
code type 和绑定，不再回传旧值。目标 Vitest 11/11、OAuth 页面 Cypress 21/21、Admin production
build、前端 ESLint、相关 API/IAM/System 测试 25 项和变更 Java 精确 Checkstyle 均通过。首次直接
执行聚合 Checkstyle 命中了仓库既有文件，且默认还误用了 `sun_checks.xml`；改用仓库规则并限定本轮
变更 Java 文件后均为 0 violation。Admin、System、IAM 本地镜像已重建并仅重建这三个容器，最终均为
Docker healthy，Admin HTTP 为 200；两个 Java 服务的 Docker 健康检查是 TCP 端口检查，匿名请求其
受保护的 `/actuator/health` 会由现有安全/异常链返回 500，不能作为本轮业务健康端点证据。完整 Stage 4
仍需在进入下一门禁前重跑。
