# IAM 第三方登录正式验证记录

## 最新远端 main 重放后的交付前复核

日期：2026-10-18。获得人类 LGTM 与 `feat/MAC-6` 推送授权后，先获取最新远端引用，再将已批准的四个 MAC-6 提交无冲突重放到 `origin/main` `ab42cd0`。功能提交重放后的 commit 为 `9b4e865`，相对最新基线为 0 behind / 4 ahead；本节记录最终基线上的新鲜交付前证据。

| 实际命令 | 退出码 | 实际输出摘要 |
| --- | --- | --- |
| `mvn test -Plocal` | 0 | 11 个 Reactor 模块全部成功；共 154 项，0 失败、0 错误；`BUILD SUCCESS`，总耗时 36.768 秒。较前轮增加的 1 项来自最新 `main` 的 System 测试 |
| `SYSTEM_MIGRATION_IT=true SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32778/macula_settings_it_mac6_delivery?...' ... mvn -pl macula-cloud-system -am -Dtest=IdentityProviderMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 0 | MySQL 8.4 上正常 V1→V6 成功；历史重复启用 GITHUB 时 V6 按预期失败；清理冲突后 `repair + migrate` 成功到 V6；测试 1 项通过，`BUILD SUCCESS` |
| `mvn -pl macula-cloud-iam,macula-cloud-system -am package -DskipTests -Plocal` | 0 | IAM、System 及依赖共 5 个模块打包成功；`BUILD SUCCESS`，总耗时 2.649 秒 |
| 本机静态服务 + `PLAYWRIGHT_MODULE=<本机缓存>/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs` | 0 | 登录/授权页在 1440×960、390×844、320×640、844×390 共 8 组检查中均为 `overflow: false`、`interaction: passed` |
| `git diff --check origin/main..HEAD` | 0 | 无输出 |

真实 MySQL 使用一次性容器 `macula_mac6_mysql_it_delivery` 和专用数据库 `macula_settings_it_mac6_delivery`；测试结束后容器已删除。浏览器首次尝试临时安装 Playwright 时遭遇 TLS 连接重置，随后复用本机已有 Playwright/Chromium 缓存执行同一脚本并通过；临时 HTTP 服务已精确终止。`AGENTS.md` 与 `.agent_context/` 仍是用户/HiFox 运行时已有内容，未纳入提交。

本次重放没有代码冲突，最新 `main` 的新增内容与本功能不重叠；独立复审的 **Important 0、Nit 0** 结论未被改变。本节只补充最终基线验证证据，不扩展人类授权范围：按顶层任务规则只推送 `feat/MAC-6`，不创建 PR、不合并、不部署。

## TOCTOU 修复后的重新正式验证（当前结论）

日期：2026-10-18。用户确认第二轮 Stage 5 返工实现完整后再次进入 `sdlc-test`；本节只采用 OAuth client 单次快照修复后的新鲜结果。未提交、推送、部署或自行批准评审门。

**Verification is green**，适用于 accepted spec/plan 中可在本地隔离环境验证的范围。真实 GitHub、企业微信、生产网关、生产数据和企业微信应用可见范围尚未联调，仍属于部署环境验收边界。

| 实际命令 | 退出码 | 实际输出摘要 |
| --- | --- | --- |
| `mvn test -Plocal` | 0 | 11 个 Reactor 模块全部成功；IAM 71、System 47、TinyID 33、RocketMQ 1、Docs 1，共 153 项，0 失败/错误/跳过；`BUILD SUCCESS`，总耗时 34.180 秒 |
| `SYSTEM_MIGRATION_IT=true SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32777/macula_settings_it_mac6_toctou?...' ... mvn -pl macula-cloud-system -am -Dtest=IdentityProviderMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 0 | MySQL 8.4 上正常 V1→V6 成功；历史重复启用 GITHUB 时 V6 按预期失败且绑定表不存在；删除重复记录后 `repair + migrate` 成功到 V6；测试 1 项通过，`BUILD SUCCESS` |
| `mvn -pl macula-cloud-iam,macula-cloud-system -am package -DskipTests -Plocal` | 0 | IAM、System 及依赖共 5 个模块打包成功；`BUILD SUCCESS` |
| 临时静态服务 + `PLAYWRIGHT_MODULE=<临时目录>/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs` | 0 | 登录/授权页在 1440×960、390×844、320×640、844×390 共 8 组检查中均为 `overflow: false`、`interaction: passed` |
| `git diff --check` | 0 | 无输出 |

真实 MySQL 使用一次性容器 `macula_mac6_mysql_it_stage4_toctou` 和专用数据库 `macula_settings_it_mac6_toctou`；测试结束后容器已删除。浏览器检查结束后精确终止临时 HTTP 服务并删除临时 Playwright 目录。

### 当前返工的直接证据

- `ThirdPartyUserDetailsServiceTest` 5 项包含在 IAM 71 项绿灯中：第三方编排把 state 绑定的 tenantId/userType 精确传入用户路由；当前 client 与快照不一致时，在访问任何用户源前失败关闭；一次调用只读取一次 OAuth client。
- `SysUserDetailsServiceImpl` 在同一方法中读取 client、核对期望 tenantId/userType，并使用该对象的同一组值查询账号；不存在“第一次校验旧值、第二次使用新值”的读取窗口。
- 既有 `MEMBER`、`EMPLOYEE`、`ODC`、`SUPPLIER` 路由与仅 `EMPLOYEE` 系统用户兼容回退继续通过，绑定表仍不能决定租户或用户类型。

### Proof、eval 与边界

- IAM 71、System 47 及全仓其余 35 项覆盖 state/SavedRequest、原子消费、provider 固定端点、最小 scope、企业微信 `UserId`、身份源冲突、OAuth2/OIDC/PKCE/CSRF 和既有认证回归。
- MySQL 真实验证绑定表字段边界、双唯一约束、身份源启用唯一性，以及重复历史数据的安全停止和可恢复重跑。
- 未修改 `CLAUDE.md`、skill 或 hook，配置 eval 不适用；`AGENTS.md` 与 `.agent_context/` 是用户/HiFox 运行时已有内容，未纳入本功能。
- 第三方 HTTP 使用测试替身，没有真实供应商凭据或网络调用；页面使用本机 Chrome 模拟视口，不替代真实移动设备及企业微信内置浏览器验收。

下一步可再次进入 `sdlc-deploy`，对当前代码重新独立执行 Bugs、Security、Compliance。MAC-6 是顶层任务，按项目规则最终只推送 `feat/MAC-6`、不创建 PR；本轮不自动提交、推送或部署。

## 第一轮 Stage 5 修复后的重新正式验证（历史记录）

日期：2026-10-18。用户重新确认 Stage 3 实现完整后进入 `sdlc-test`；本节只采用五条 Important 修复完成后的新鲜测试结果。未提交、推送、部署或自行批准评审门。

**Verification is green**，适用于 accepted spec/plan 中可在本地隔离环境验证的范围。真实 GitHub、企业微信、生产网关、生产数据和企业微信应用可见范围尚未联调，仍属于部署环境验收边界。

| 实际命令 | 退出码 | 实际输出摘要 |
| --- | --- | --- |
| `mvn test -Plocal` | 0 | 11 个 Reactor 模块全部成功；IAM 71、System 47、TinyID 33、RocketMQ 1、Docs 1，共 153 项，0 失败/错误/跳过；`BUILD SUCCESS`，总耗时 36.292 秒 |
| `SYSTEM_MIGRATION_IT=true SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32776/macula_settings_it_mac6_stage4?...' ... mvn -pl macula-cloud-system -am -Dtest=IdentityProviderMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 0 | MySQL 8.4 上正常 V1→V6 成功；历史重复启用 GITHUB 时 V6 按预期失败且绑定表不存在；删除重复记录后 `repair + migrate` 成功到 V6；测试 1 项通过，`BUILD SUCCESS` |
| `mvn -pl macula-cloud-iam,macula-cloud-system -am package -DskipTests -Plocal` | 0 | IAM、System 及依赖共 5 个模块打包成功；`BUILD SUCCESS` |
| 临时静态服务 + `PLAYWRIGHT_MODULE=<临时目录>/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs` | 0 | 登录/授权页在 1440×960、390×844、320×640、844×390 共 8 组检查中均为 `overflow: false`、`interaction: passed` |
| `git diff --check` | 0 | 无输出 |

真实 MySQL 使用一次性容器 `macula_mac6_mysql_it_stage4` 和专用数据库 `macula_settings_it_mac6_stage4`；测试结束后容器已删除。浏览器检查结束后精确终止其临时 HTTP 服务并删除临时 Playwright 目录。未触碰其他数据库或持续服务。

### 五条 Important 修复的回归证据

1. SavedRequest 与 state 绑定：`ThirdPartyAuthenticationFlowTest` 3 项通过，证明只快照与 client 匹配的 `/oauth2/authorize` 相对目标、拒绝 client 不匹配，并且成功回调忽略之后被覆盖的共享 RequestCache。
2. 用户类型失败关闭：`ThirdPartyUserDetailsServiceTest` 5 项通过，证明显式 `MEMBER`、`ODC`、`SUPPLIER` 不再回退系统用户；仅 `EMPLOYEE` 保留兼容回退。tenantId 和 userType 始终来自当前 OAuth client。
3. V6 可恢复：真实 MySQL 测试证明唯一性预检失败发生在创建 `sys_user_identity_binding` 之前；清理重复记录并执行 Flyway repair 后可安全重跑。
4. state 原子消费：`HttpSessionThirdPartyLoginStateRepositoryTest` 4 项通过，其中并发回调只有一个能消费成功，另一个被拒绝。
5. GitHub 最小授权：`GithubAuthenticationProviderTest` 2 项通过，授权 URI 不再请求额外 `scope`，仍只使用稳定数值 `id`。

### 其余 Proof 与边界

- IAM 71 项与 System 47 项完整回归覆盖固定第三方端点、配置失败关闭、绑定恰好一条、企业微信 `snsapi_base`/`UserId`、身份源冲突和现有 OAuth2/OIDC/PKCE/CSRF/密码/短信/小程序行为。
- MySQL 验证绑定表不含 `tenant_id`、`user_id`、`user_type`、token 字段，保留 subject/username 双唯一约束、utf8mb4 和 username 长度约束；绑定关系不能决定租户或用户类型。
- 第三方 HTTP 测试使用替身，没有真实供应商凭据或网络调用；access token 仅在调用栈中使用，未写入数据库或响应。
- 页面验证使用本机 Chrome 的 Playwright 模拟视口，不等同于真实移动设备、企业微信内置浏览器或 Safari 软键盘验收。
- 本功能未修改 `CLAUDE.md`、skills、hooks 等代理保护配置，配置 eval 不适用。工作树中的 `AGENTS.md` 与 `.agent_context/` 是用户/HiFox 运行时已有内容，未纳入本功能改动。

下一步可进入 `sdlc-deploy`，针对修复后的代码重新独立执行 `REVIEW.md` 的 Bugs、Security、Compliance 检查。本轮停在 Stage 4，不自动提交、推送或部署。

## 首轮验证（修复前历史记录）

> 以下绿灯已被随后 Stage 5 发现的五条 Important 取代，仅保留为历史证据，不得用于当前交付。

日期：2026-10-18。用户确认 Stage 3 实现完整后进入 `sdlc-test`。本轮验证 GitHub 全局账号绑定、企业微信 UserId 直连用户名、OAuth client 决定租户与用户类型，以及迁移、协议兼容和页面行为。未提交、推送、部署或自行批准评审门。

## 结果

**Verification is green**，仅指已接受 spec/plan 中可在隔离环境自动验证的范围；不代表真实 GitHub、企业微信、生产网关或生产数据已经联调，也不代表 Bugs、Security、Compliance 评审已经通过。

| 实际命令 | 退出码 | 实际输出摘要 |
| --- | --- | --- |
| `mvn -pl macula-cloud-iam -am test -Plocal` | 0 | IAM 65 项，0 失败/错误/跳过；`BUILD SUCCESS` |
| `mvn -pl macula-cloud-system -am -Dtest=IdentityProviderServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 0 | 身份源 Service 12 项，0 失败/错误/跳过；`BUILD SUCCESS` |
| `SYSTEM_MIGRATION_IT=true SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32774/macula_settings_it_mac6?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=<隔离测试密码> mvn -pl macula-cloud-system -am -Dtest=IdentityProviderMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 0 | MySQL 8.4 上 V1→V6 成功；预置重复启用 GITHUB 时 V6 按预期失败并被测试断言；测试 1 项通过，`BUILD SUCCESS` |
| `mvn test -Plocal`（补证据后最终重跑） | 0 | 11 个 Reactor 模块全部成功；IAM 65、System 47、TinyID 33、RocketMQ 1、Docs 1，共 147 项，0 失败/错误/跳过；总耗时 32.121 秒 |
| `mvn -pl macula-cloud-iam,macula-cloud-system -am package -DskipTests -Plocal` | 0 | IAM、System 及依赖共 5 个模块打包成功；`BUILD SUCCESS` |
| 临时静态服务 + `PLAYWRIGHT_MODULE=/tmp/macula-mac6-playwright.jEnMeY/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs` | 0 | 登录/授权页在 1440×960、390×844、320×640、844×390 共 8 组检查中均为 `overflow: false`、`interaction: passed` |
| `git diff --check` | 0 | 无输出 |

真实 MySQL 使用一次性 Docker 容器和专用数据库 `macula_settings_it_mac6`；测试完成后容器已停止并由 `--rm` 删除。迁移测试只清理该专用数据库内对象，不触碰其他数据库。

## Proof 对照

1. 数据边界：V6 在真实 MySQL 验证绑定表只含 `provider_type + provider_subject + username` 及审计字段，不含 `tenant_id`、`user_id`、`user_type` 或 token；username 为 64 字符、utf8mb4，并实际触发 subject 与 username 两个唯一键。身份源同类型只允许一条启用配置，历史重复数据会让迁移安全停止。
2. System 管理：Service 测试覆盖新增/更新冲突、更新自身不误判、禁用配置共存、凭据不回显及数据库唯一键并发冲突映射为 HTTP 409。
3. OAuth client 路由：用户路由测试遍历 `MEMBER`、`EMPLOYEE`、`ODC`、`SUPPLIER`，验证 tenantId/userType 只来自显式 client ID；回调重读 client，快照变化即拒绝，绑定表不能改变租户或用户类型。
4. GitHub：测试覆盖固定官方端点、最小 `read:user` scope、精确回调、token 与 `/user` 两段调用、仅接受稳定数值 `id`、拒绝 login 兜底、全局 username 绑定及上游 5xx 的稳定失败文案。
5. 企业微信：测试覆盖固定官方端点、`snsapi_base`、corpId/agentId、成员 `UserId` 直作为 username、拒绝只有 OpenId 的非成员身份及上游 5xx 的稳定失败文案；不查询绑定表。
6. 公共安全路径：state 使用 32 字节安全随机值、按会话/provider 仅保留最新值、五分钟过期、先移除后校验并拒绝重放/错 provider；配置缺失、重复、损坏和可见性查询异常均失败关闭，全局绑定要求恰好一条非空 username。
7. Spring Security 回归：真实过滤链测试覆盖匿名授权请求 SavedRequest、表单/短信兼容、CSRF、PKCE/OIDC、refresh/revocation/logout、有效 client 下第三方按钮、可信 issuer 回调、Host 注入拒绝和 client 快照。
8. 页面：8 组桌面/移动/横屏浏览器检查覆盖无横向溢出、键盘交互、第三方按钮和原有密码/短信交互；首次运行因等待异步短信按钮文本存在竞态而失败，修正测试等待条件后全部通过，未改变生产行为。

## 首轮失败与修正

- 初始迁移测试只检查元数据，没有实际触发唯一约束，也未覆盖历史重复启用数据。测试已补为真实插入冲突与 V1→V5 重复数据升级失败场景；随后在隔离 MySQL 8.4 通过。
- 初始 UI 脚本在点击发送验证码后立即断言异步文本，出现竞态。脚本改为等待 `60 秒后重试` 出现后，8/8 视口通过。
- Proof 审计发现 System 缺少“更新自身”和数据库并发冲突映射测试，以及 IAM 缺少配置失败关闭、绑定唯一性和 state 随机替换的直接证据。补齐后分别重跑定向测试及全仓测试，最终均通过。

## 边界与交接

- GitHub/企业微信 HTTP 行为由固定 URL 的 MockRestServiceServer 验证，没有使用真实第三方凭据，也没有执行真实供应商 OAuth 联调；真实应用可见范围、限流、代理和供应商故障仍需部署环境验证。
- MySQL 是隔离容器，不是生产数据演练。生产升级前仍须备份并清理同一 provider type 的重复启用记录；回滚必须恢复数据库备份与匹配应用版本。
- 页面测试使用 Chromium 模拟视口，不等同于真实移动设备、企业微信内置浏览器或 Safari 软键盘验证。
- 本功能未修改 `CLAUDE.md`、skills、hooks 或其他代理保护配置，配置 eval 不适用。工作树中的 `AGENTS.md` 和 `.agent_context/` 是用户/HiFox 运行时已有改动，未纳入本功能。
- 下一步可携带本记录进入 `sdlc-deploy`，独立执行 `REVIEW.md` 的 Bugs、Security、Compliance 检查。本轮停在 Stage 4，不自动提交、推送或部署。
