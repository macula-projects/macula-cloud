# 身份与安全设置管理正式验证

日期：2026-10-14 初次正式验证；2026-10-16 完整复验。当前状态：**Verification is green**（限已接受规格的自动化验证范围；真实供应商投递和在线容器联调不在该结论内）。独立 PR 审查仍待 Stage 5 完成。

## 实际执行证据

| 命令/检查 | 退出码 | 原始输出摘要 |
| --- | --- | --- |
| `mvn test -Plocal -q`（补测后全 reactor 重跑） | 0 | `IdentityProviderServiceImplTest` 的 Surefire 记录：`tests="6" errors="0" skipped="0" failures="0"`；其余模块命令退出码 0 |
| `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am package -Plocal -q` | 0 | System/API 测试与打包完成 |
| `SYSTEM_SETTINGS_IT=true ... mvn -pl macula-cloud-system -am -Dtest=SettingsPersistenceIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal -q` | 0 | Surefire：`tests="5" errors="0" skipped="0" failures="0"`；使用 `macula_settings_it_identity_20261014` 和隔离 Redis database 15 |
| `SYSTEM_MIGRATION_IT=true ... mvn -pl macula-cloud-system -am -Dtest=IdentityProviderMigrationIT -Dsurefire.failIfNoSpecifiedTests=false clean test -Plocal -q` | 0 | `Successfully applied 2 migrations ... now at version v2`；`Successfully applied 1 migration ... now at version v3`；Surefire：`tests="1" errors="0" skipped="0" failures="0"` |
| Admin `npm run test:unit -- --run` | 0 | `Test Files  3 passed (3)`；`Tests  13 passed (13)` |
| Admin `npm run build` | 0 | Vite 构建完成；仅有大于 500 kB 的 chunk 提示 |
| Admin `npm run test:e2e:ci` | 0 | `All specs passed!`，20/20（身份与安全 12、登录 3、TinyID 5） |
| `git diff --check`、`git diff --cached --check` | 0 | 无输出 |
| 变更范围前端 `npx eslint ... --no-fix --quiet` | 0 | 无输出、无 error |

隔离 MySQL 中直接顺序执行 V1/V2/V3 的两条路径也各自退出码 0；在 V2 阶段插入的 `legacy-fixture-identity` 于 V3 后仍保留。应用依赖中的真实 Flyway 随后在**全新空库**复核了带 `flyway_schema_history` 的 V1→V2→V3：版本 1、2、3 均为成功，旧行和 `app_secret=TEXT`、`remark=VARCHAR` 均经断言。三个本轮专用测试库已核对名称并删除；`macula-system` 未被测试或清理。

第一次单独运行 Flyway Maven 插件退出码 1，原因是插件类路径缺少 MySQL 数据库插件；改由应用依赖执行迁移。第一次应用集成测试也退出码 1，发现旧分支 `target/classes/db/migration/V2__iam_client_identity_provider.sql` 与当前 V2 同号；仅通过 Maven `clean` 清理生成产物后重跑成功，未更改历史 V1/V2 源文件。

## Proof 与规格对照

1. 身份源六类字段、分类/类型拒绝、未知/缺失字段、凭据拆分和留空保留、旧 JSON 拒绝：System 单测 6 项；独立 MySQL 加密往返及长支付宝私钥另由 `SettingsPersistenceIT` 验证。ROOT 授权、全局设置、密码边界、OAuth 租户与 PKCE/OIDC 相关 System/IAM 单测在全 reactor 中通过。
2. 数据库和跨缓存：真实 Flyway V1→V2→V3、V2→V3、旧身份源行保留；隔离库的 `@CryptoField` 密文/明文往返、长私钥列与 `config_json` 脱敏；隔离 Redis 中第二个 Boot 缓存实例收到提交后的客户端缓存驱逐。未在运行中的 IAM 容器或真实业务库进行联调。
3. 网络适配：现有测试中的环回 TLS SMTP AUTH、阿里/腾讯 SDK 本地 HTTP 服务、自定义 HTTPS POST 及拒绝/超时/参数映射均在全 reactor 测试中通过。未提供真实供应商测试凭据，故不声称实网投递或最终收件成功。
4. Admin：Cypress 覆盖两类页签、六种专属字段、编辑凭据不回填、企业微信对象型 `config` 请求、双击保存只发一次请求，以及其他管理页的租户选择、模板临时测试等流程；Vitest、构建和差异检查均通过。Cypress 的后端响应被拦截模拟，不是实服务端 E2E。

## 额外质量检查与边界

- 仓库级 `npx eslint . --ext .vue,.js,.jsx,.cjs,.mjs --no-fix --ignore-path .gitignore --quiet` 退出码 **1**，输出 `269 problems (269 errors, 0 warnings)`；报错在未修改的旧配置/资源/页面。它不属于本计划指定的测试或必需检查；本功能变更范围的 lint 退出码 0。未执行带 `--fix` 的 `npm run lint`，以免批量改写用户的其他文件。仓库全量 lint 债务仍需单独处理。
- Bugs 检查发现身份源、通用安全及 OAuth 表单在异步校验后才上 `saving` 锁，快速双击可能重复提交；已将锁移到校验前，Cypress 用双击断言证明身份源只发一次请求，并重跑前端构建、Vitest、全量 Cypress 和后端全 reactor。Security/Compliance 未发现本轮未接受的阻断问题。`@CryptoField` 加密异常可能被 Boot 拦截器吞掉并继续写库，是规格已接受的剩余风险；本轮未修改 Boot，也不宣称失败即阻止写入。未复制 eIAM AGPL 代码或真实凭据。
- 本轮未修改 `CLAUDE.md`、skill、hook 或其他受保护代理配置；配置 eval 不适用，未发现需运行的对应 eval 套件。System 应用配置和 POM 变更已由隔离测试与打包覆盖。

## 2026-10-14 回调地址移除后的增量复验

Rain 确认构建阶段完成后，按已更新的 spec/plan 重新执行正式验证。身份源表单、请求 Form、响应 VO 均不含 `callbackUrl`，但历史数据库列和值保留。`SettingsPersistenceIT.blankUpdateRetainsEncryptedIdentityAndMailCredentials` 新增真实 MySQL 断言：先写入旧 `callback_url`，修改身份源其他配置后该列保持原值。

| 命令/检查 | 退出码 | 实际输出 |
| --- | --- | --- |
| `mvn test -Plocal -q`（新增数据库断言后全 reactor 重跑） | 0 | 全 reactor 命令退出码 0；System 后续 Surefire：`IdentityProviderServiceImplTest tests=6 errors=0 skipped=0 failures=0` |
| `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am package -Plocal -q` | 0 | System/API 测试与打包命令退出码 0 |
| 隔离空库 `SYSTEM_MIGRATION_IT=true ... IdentityProviderMigrationIT ... clean test -Plocal -q` | 0 | `Successfully applied 2 migrations ... now at version v2`；`Successfully applied 1 migration ... now at version v3`；Surefire：`tests=1 errors=0 skipped=0 failures=0` |
| 已迁移隔离库 `SYSTEM_SETTINGS_IT=true ... SettingsPersistenceIT ... test -Plocal -q` | 0 | Surefire：`tests=5 errors=0 skipped=0 failures=0`；包括旧回调列值不被覆盖的新增断言 |
| Admin `npm run test:unit -- --run` | 0 | `Test Files  3 passed (3)`；`Tests  13 passed (13)` |
| Admin `npm run test:e2e:ci` | 0 | `All specs passed!`；`20` 项通过，其中身份源表单不显示回调地址且创建请求不含该字段 |
| Admin `npm run build` | 0 | `✓ 2256 modules transformed.`；仅保留超过 500 kB 的 chunk 提示 |
| 变更范围 `npx eslint ... --quiet`、`git diff --check`、`git diff --cached --check` | 0 | 均无输出 |

对应 Proof：六类身份源和 ROOT/拒绝边界由全 reactor、前端单测和 Cypress 复验；V1→V2→V3、旧行保留、加密凭据与缓存由两个隔离 MySQL/Redis 集成测试复验；邮件和短信本地模拟测试由全 reactor 复验。隔离测试库 `macula_settings_it_callback_20261014`、`macula_settings_it_callback_migration_20261014` 及对应授权已核实后移除，查询剩余库数量为 `0`。未使用真实供应商凭据，未验证实网收件；Cypress 使用模拟 API 响应，未对当前运行中的 System/Admin 容器做登录后端到端验证。本次代码变更尚未重新部署 Compose。

本次未修改 `CLAUDE.md`、skill 或 hook，不适用配置 eval。Bugs 检查重点验证移除字段后旧列不丢失；Security 检查确认该字段不再由管理表单传入且未增加授权入口；Compliance 没有新增适用要求。旧仓库全量 ESLint 的 269 个既存错误仍是独立债务，不能把变更范围 lint 退出码 0 误述为全仓 lint 通过。**Verification is green**（限上述自动化范围及已说明的外部联调边界）。

## 2026-10-14 官方图标与紧凑列表后的增量复验

Rain 确认 Stage 3 实施完成后，按已更新的 spec/plan 重新执行 Stage 4。六家官网 favicon 打包为 Admin 本地数据资源，列表沿角色管理的顶栏、分类标签和无内边距表格布局；Cypress 逐类检查图标 `naturalWidth > 0`，并断言顶栏与表格间距小于 75px。页面继续使用模拟 API 响应，不能据此声称已部署容器验收。

| 命令/检查 | 退出码 | 实际输出摘录 |
| --- | --- | --- |
| `mvn test -Plocal -q` | 0 | 全 reactor 命令退出码 0；System 测试中的 `AdminSettingsAuthorizationTest` 和业务单测正常执行 |
| `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am package -Plocal -q` | 0 | System/API 测试与打包命令退出码 0 |
| 隔离库 `SYSTEM_MIGRATION_IT=true ... IdentityProviderMigrationIT ... clean test -Plocal -q` | 0 | `Successfully applied 2 migrations ... now at version v2`、`Successfully applied 1 migration ... now at version v3`；Surefire：`tests="1" errors="0" skipped="0" failures="0"` |
| 隔离库 `SYSTEM_SETTINGS_IT=true ... SettingsPersistenceIT ... test -Plocal -q` | 0 | Surefire：`tests="5" errors="0" skipped="0" failures="0"`，验证加密往返、旧行及缓存驱逐 |
| Admin `npm run test:unit -- --run` | 0 | `Test Files  3 passed (3)`、`Tests  14 passed (14)` |
| Admin `npm run build` | 0 | `✓ 2257 modules transformed.`；仅有原有的大 chunk 提示 |
| Admin `npm run test:e2e:ci` | 0 | `All specs passed!`，`20` 项通过（身份与安全 12、登录 3、TinyID 5） |
| 变更范围 ESLint、`git diff --check` 与 `git diff --cached --check` | 0 | 均无输出、无 error |

与本次变更直接相关的规格要求 2 和计划偏差由六类图标加载、分类列表及顶部间距断言覆盖；既有身份配置和其余管理功能由全量测试保留回归证据。Bugs 检查未见列表查询/分页行为退化；Security 检查确认图标从本地资源加载、没有运行时第三方请求或凭据显示；Compliance 检查未发现新增适用要求，品牌资源仍须遵守各提供商商标使用要求。未修改 `CLAUDE.md`、skill 或 hook，配置 eval 不适用，未发现对应 eval 套件。没有真实供应商凭据，因此实网投递仍未验证；未对运行中的 Admin 容器做登录后实服务端 E2E。

测试仅创建 `macula_settings_it_brand_20261014` 隔离库并授权现有本地测试用户；运行后核对库名和 V1/V2/V3 成功记录，再删除该测试库及对应授权，随后查询该库数量为 `0`。未清理业务库或 Redis DB。**Verification is green**（限上述自动化范围）。

## 2026-10-16 新增变更的交付前预检

- 当前工作树执行 `mvn test -Plocal`，11 个 Maven reactor 模块均成功；System/API 执行 `mvn -pl macula-cloud-system -am package -Plocal -DskipTests`，4 个模块打包成功。后者跳过测试，不能代替前者。
- Admin 执行 `npm run test:unit -- --run`，3 个测试文件、14 个用例通过；`npm run build` 成功；`npm run test:e2e:ci` 的 22 个用例通过，使用模拟 API 响应，不代表真实后端联调。
- 当前变更范围执行只读 ESLint `--no-fix --quiet` 及 `git diff --check HEAD`，退出码均为 0。未运行会自动改写文件的 `npm run lint`。
- 权限需求以 Rain 后续明确要求为准：移除 `RootAdminGuard`，通过既有 Gateway URL 权限配置授权，V2 默认仅赋予 ROOT。`AdminSettingsAuthorizationTest` 采用独立 Controller 测试，仅证明 Controller 不硬编码 ROOT，不证明网关授权或服务直连安全；直接暴露 System 端口会绕过网关 URL 检查，部署时必须限制直连。
- 本轮已在隔离 MySQL/Redis 重新执行下述集成测试；Stage 5 独立审查与真实供应商投递仍未由自动化测试覆盖。

## 2026-10-16 Stage 4 正式复验

| 命令/检查 | 退出码 | 实际输出摘录 |
| --- | --- | --- |
| `mvn test -Plocal` | 0 | `Reactor Summary` 中 11 个模块均 `SUCCESS`，末尾 `BUILD SUCCESS`，`Total time: 47.156 s` |
| `mvn -pl macula-cloud-system -am package -Plocal -DskipTests` | 0 | System 及 API 共 4 个模块 `SUCCESS`，末尾 `BUILD SUCCESS`；此项跳过测试，仅用于打包检查 |
| `SYSTEM_MIGRATION_IT=true ... IdentityProviderMigrationIT ... clean test -Plocal -q` | 0 | `Successfully applied 2 migrations ... now at version v2`；`Successfully applied 1 migration ... now at version v3`；Surefire `tests="1" errors="0" skipped="0" failures="0"` |
| `SYSTEM_SETTINGS_IT=true ... SettingsPersistenceIT ... test -Plocal -q` | 0 | Surefire `tests="5" errors="0" skipped="0" failures="0"`；使用独立 MySQL 测试库与 Redis database 15 |
| Admin `npm run test:unit -- --run` | 0 | `Test Files 3 passed (3)`、`Tests 14 passed (14)` |
| Admin `npm run build` | 0 | `✓ 2262 modules transformed.`；仅提示部分 chunk 超过 500 kB |
| Admin `npm run test:e2e:ci` | 0 | `All specs passed!`，`22` 项通过（身份与安全 14、登录 3、TinyID 5） |
| 变更范围 `npx eslint ... --no-fix --quiet`、`git diff --check HEAD` | 0 | 均无输出；未运行带 `--fix` 的全仓 lint |

Proof 对照：六类身份源字段、分类、JSON 与敏感字段拆分由 System 单测、MySQL 密文往返及 Admin 14 项浏览器测试覆盖；V1→V2→V3、旧 V2 行保留由独立 Flyway 集成测试覆盖；OAuth 租户、PKCE/OIDC 配置、模板默认/覆盖、SMTP/HTTP 本地模拟和 Redis 缓存失效由全量 Maven 测试及 5 项持久化集成测试覆盖。Cypress 拦截并模拟 API，不是实时后端 E2E；没有真实供应商测试凭据，不能声称实网投递或最终收件成功。`CLAUDE.md`、skill 与 hook 均未改动，不适用配置 eval；仓库中未发现需运行的相应 eval 套件。

本轮仅创建 `macula_settings_it_final_20261016` 与 `macula_settings_it_migration_final_20261016` 两套隔离库；测试后逐名核对并删除，撤销仅针对这两个库的临时授权。测试数据已删除，不可从这些临时库恢复；`macula-system` 业务库未触碰。Stage 4 **Verification is green**，其含义仅限上述证据及边界。

## 2026-10-16 独立审查修复后的复验

Stage 5 的首次 Bugs/Security/Compliance 审查提出两项 Important：自定义短信固定请求头的密钥名变体可能落入普通 JSON，以及常量模板未校验场景必需变量。修复后返回 Build/Stage 4；复验输出如下。

| 命令/检查 | 退出码 | 实际输出摘录 |
| --- | --- | --- |
| 定向 `MessageSettingsServiceImplTest,TemplateRendererTest` | 0 | Maven 测试命令退出码 0；新增 `X-API-KEY`、`access_key`、鉴权前缀与常量短信模板缺变量拒绝用例 |
| `mvn test -Plocal` | 0 | 11 个模块均 `SUCCESS`，末尾 `BUILD SUCCESS`，`Total time: 50.682 s` |
| 隔离库 `SettingsPersistenceIT` | 0 | Surefire `tests="5" errors="0" skipped="0" failures="0"`；使用独立 MySQL 库及 Redis database 15 |
| Admin `npm run test:unit -- --run` | 0 | `Test Files 3 passed (3)`、`Tests 14 passed (14)` |
| Admin `npm run build` | 0 | Vite 构建退出码 0；仅有大 chunk 提示 |
| Admin `npm run test:e2e:ci` | 0 | `All specs passed!`，`22` 项通过 |
| 变更范围 ESLint `--no-fix --quiet`、`git diff --check HEAD` | 0 | 均无输出 |

受影响的持久化测试仅创建 `macula_settings_it_review_20261016`，通过后按准确库名删除并撤销授权；该库只有临时测试数据，已不可恢复。V1→V2→V3 迁移脚本未因审查修复而改变，先前本轮 1/1 的迁移测试仍适用。未修改 `CLAUDE.md`、skill 或 hook，无适用配置 eval。真实供应商投递和真实 System/Admin 联调仍未验证。修复后的 Stage 4 **Verification is green**（仅限上述自动化范围）。

## 2026-10-16 固定头旁路修复后的最终复验

二次独立复审指出嵌套 `authHeader` 与 `X-Auth` 仍可能绕过原过滤。再次修复后，定向 `MessageSettingsServiceImplTest,TemplateRendererTest,CustomSmsTestSenderTest` 退出码 0；全量 `mvn test -Plocal` 的 11 个模块均 `SUCCESS`，末尾 `BUILD SUCCESS`，`Total time: 46.114 s`。隔离库 `SettingsPersistenceIT` 退出码 0，Surefire `tests="5" errors="0" skipped="0" failures="0"`；Admin 单测为 3 文件 14 用例通过，构建退出码 0，Cypress `All specs passed!`、22/22 通过。该隔离测试仅使用 `macula_settings_it_review2_20261016`，通过后删除并撤销对应授权；只有测试数据被删除，业务库未触碰。独立复审确认原 Security Important 和模板变量 Bugs Important 均已解决。

独立审查另发现 OAuth 客户端更新的 `afterCommit` 缓存失效在 Redis 不可用时无可靠补偿。测试绿色只证明正常路径；数据库已提交但缓存仍旧的故障路径尚未解决，列为 **Important 合并阻断项**，须责任人明确接受或另行设计可靠失效机制。Stage 4 自动化 **Verification is green**，但不等于 Stage 5 审查通过，也不授权合并。
