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
