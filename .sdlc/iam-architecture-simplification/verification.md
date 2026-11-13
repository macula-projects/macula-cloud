# IAM 架构精简本地验证记录

日期：2026-11-11。状态：Build内已实际执行的验证证据；等待工程师确认实施完整性，未自行批准正式Test/PR/部署阶段。

## 结果

| 验证 | 实际结果 | 范围 |
| --- | --- | --- |
| 全仓 `mvn clean test -Plocal` | BUILD SUCCESS；625用例，0失败/错误，1跳过 | 最终包整理、共享账号和目录端口后执行；清除旧class，11模块构建成功 |
| IAM安全链与单元 | 369通过 | 包含登录/协议链分离、五家网页登录、PKCE/OIDC、external_code、旧grant拒绝、绑定、Claims、签名/JWK、序列化兼容等 |
| MFA与平台缓存IT | 16通过 | MfaProtocolIT 12、ProviderApplicationTokenCacheIT 4；真实隔离Redis缓存/owner和发布拒绝 |
| 身份绑定IT | 17通过 | IdentitySourceBindingIT，独立MySQL8.4与Redis，真实事务/锁/并发与来源/目录约束 |
| 身份映射迁移IT | 1通过 | IdentityBindingMappingsMigrationIT，在独立新库执行原迁移并验证V23；没有改写迁移SQL |
| Admin单元 | 23文件、212通过 | 当前协议目录、保存校验、示例、退役值显式处置 |
| Admin生产构建 | 成功 | 存在原有大于500KB chunk提示；未在本轮开展无关分包 |
| Admin Cypress | 43通过 | OAuth配置35、身份策略8；含桌面与390px历史grant保留/显式移除 |
| Checkstyle | 207个现存变更Java文件通过 | 使用仓库`.config/checkstyle.xml`和实际header；CLI Checkstyle9.3，无项目依赖变更 |
| 范围/文档 | `git diff --check`通过；实际文件均在计划清单内 | README本地链接与PNG/SVG检查；无schema、业务配置或凭据改动 |

全仓唯一跳过项为既有SysUserUsernameUniqueMigrationTest，其`SYSTEM_MIGRATION_IT=true`环境门控未开启。这不等于本轮身份绑定/映射IT跳过：后两项已在单独的新建隔离环境中明确开启并通过。

## 实际命令

在仓库根执行：

```bash
mvn clean test -Plocal
mvn -pl macula-cloud-iam -am verify -Plocal \
  -Dit.test=MfaProtocolIT,ProviderApplicationTokenCacheIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

隔离执行器初始化独立MySQL数据目录与随机回环端口，创建专用`macula_identity_binding_it_*`库，启动独立Redis并设置测试标记。只给该子进程注入`IAM_SOURCE_IT_URL/USER/PASSWORD/REDIS_PORT/RUN_ID`，开启`IDENTITY_BINDING_MAPPINGS_IT`、`IAM_SOURCE_BINDING_IT`后顺序运行：

```bash
mvn -pl macula-cloud-system -am verify -Plocal \
  -Dit.test=IdentityBindingMappingsMigrationIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
mvn -pl macula-cloud-iam -am verify -Plocal \
  -Dit.test=IdentitySourceBindingIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

两个进程在执行器finally中关闭，仅保留临时诊断文件。没有连接业务库、复用业务Redis或使用真实平台凭据。

Admin执行：

```bash
npm run test:unit -- --run
npm run build
./node_modules/.bin/start-server-and-test preview http://localhost:4173/ \
  './node_modules/.bin/cypress run --e2e --spec cypress/e2e/oauth-client-configuration.cy.js,cypress/e2e/identity-provider-policy.cy.js'
```

## 关键证据边界

- 原email认证主体的Jackson2/3合成夹具来自删除前的实际序列化器。用当前安全读取器恢复完整授权，证明有效Access Token可读、不写回历史记录、撤销后不可读；非法结构、错误位置、未认证或带凭据的主体拒绝。到期、版本和原子撤销另由原存储/协议回归覆盖；未宣称已扫描所有线上历史格式。
- 最终安全链检查登录链有五家入口、没有Token端点Filter，协议链有Token端点Filter、没有网页登录Filter，且无全局网页登录/external_code Provider Bean。
- 所有旧grant和标准流程的结果均经过当前实际安全链；厂商HTTP与身份材料为Mock，不能当成真实第三方Code验收。
- 目录端口、账号加载拆分及搬包后的clean构建通过，原序列化身份/状态类型保留全限定名。根包保留兼容模型不是遗漏搬迁。

## 修正与记录

实施中曾修正组合Configurer同类覆盖、绑定测试事务生命周期及显式可空Mockito参数；拆分共享账号服务后给隔离Test App补齐Import，并补足合成用户必需dataScope。MFA合成Client补齐实际平台/能力关联以到达原拒绝边界。所有对应拒绝断言保留，未通过放宽生产校验取得绿色结果。

初期全量tests未清理旧target，后续已执行最终clean全仓回归，排除了已删除类残留掩盖引用的可能。统计使用当前运行日志，未将过期Surefire XML累计为本次用例数。

## 交付规模与未执行项

IAM生产Java：216文件/19,633行 → 216文件/18,052行，净减1,581行（约8.1%，含注释/版权头）。剔除package-info后的功能类型文件：211 → 199，净减12个；新增包说明用于解释职责和兼容边界。

未执行真实厂商/真机/业务小程序回跳验证、业务库升级、配置整改、提交推送、镜像更新、部署及SLO观测。正式Test阶段及后续审查按SDLC门继续，不以本地结果代替人工批准。

## 本地日志索引

临时证据目录：`/private/tmp/macula-iam-review-20261111/`。

- `architecture-clean-tests.log`：最终全仓clean回归。
- `architecture-verify.log`：最终MFA和平台缓存IT。
- `isolated-run-final.log`、`isolated-migration-it.log`、`isolated-binding-it.log`：最终隔离迁移和绑定。
- `admin-tests-final.log`、`admin-build-final.log`、`cypress-final.log`：Admin结果。
- `checkstyle-final.log`：源码规范检查。

日志保存在本地临时目录，未提交生成物、业务数据或秘密。

## 配置归类后的补充验证

`mvn -pl macula-cloud-iam -am clean verify -Plocal -Dit.test=MfaProtocolIT,ProviderApplicationTokenCacheIT -Dfailsafe.failIfNoSpecifiedTests=false`通过，IAM单元/安全链369项、MFA及缓存IT16项均无失败/错误/跳过，且完成Boot可执行包构建。日志：`config-verify.log`。Checkstyle207文件通过，计划文件范围及`git diff --check`通过。本轮仅配置/CORS类与测试搬包，没有再修改绑定或数据库算法，因此不重复独立数据库迁移测试。


## Stage 4 正式复验（2026-11-11，推送前最终代码）

用户已用YES确认Build完成，并明确授权推送合并。已重新执行验证，不将之前的Build运行冒充本轮结果。

Verification is green。

| 本轮命令/证据 | 实际结果 |
| --- | --- |
| `mvn clean test -Plocal`，`pr-backend-tests3.log` | `BUILD SUCCESS`，643项，0失败/错误，1项既有环境门控跳过；642项实际通过。 |
| 补类型职责注释后的`mvn test -Plocal`，`pr-backend-final.log` | `BUILD SUCCESS`；同一完整reactor通过。 |
| `npm run test:unit -- --run`，`pr-admin-final-unit.log` | `Test Files 23 passed (23)` / `Tests 213 passed (213)`。 |
| `npm run build`，`pr-admin-build.log` | `built in 5.78s`；既有chunk大小提示保留。 |
| `npx start-server-and-test preview http://localhost:4173 'cypress run --expose IAM_PREVIEW_URL=http://127.0.0.1:4176/login.html --config screenshotsFolder=/private/tmp/macula-iam-review-20261111/pr-final-screenshots,video=false'` | `All specs passed!`，83项，0失败/待执行/跳过，含5项真实Thymeleaf页面场景；`pr-admin-browser-final.log`。 |
| IdentityBindingMappingsMigrationIT + IdentitySourceBindingIT | 1+17项通过，新建隔离MySQL/Redis；`pr-binding-run.log`及isolated相关日志。 |
| HostedEmailLoginMigrationIT | 1项通过，新建隔离MySQL从V24到V25；`pr-migration-run.log`及`email-migration-it.log`。 |
| OAuth2ClientApplicationConcurrencyIT | 1项通过，新建隔离MySQL内EMAIL/PASSWORD竞争，409及唯一缓存失效；`pr-concurrency-run.log`。 |
| MfaProtocolIT + ProviderApplicationTokenCacheIT | `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`；`pr-mfa-cache.log`。 |
| 实际全部暂存Java差异Checkstyle（包含重命名目的文件） | `Java files: 311 Checkstyle exit: 0`；`checkstyle-final.log`。 |
| 其他 | `git diff --cached --check`通过。暂存无target/dist/node_modules/.env；未变更CLAUDE.md、AGENTS.md、技能、hooks或REVIEW.md，无适用配置eval变更。 |

唯一后端跳过项为既有SysUserUsernameUniqueMigrationTest的独立数据库环境门；本次不修改用户名唯一性迁移，相关账号歧义拒绝由本次账号回归及真实绑定IT覆盖。不能把该跳过项计为通过。真实厂商、设备及邮件送达仍是单独验收范围。

本轮初始失败及修复：JSON夹具被排版后，拒绝测试的紧凑字符串替换没有修改样本，改为结构化JSON变异；不削弱生产兼容读取器。真实模板输出改为模块target，Cypress16使用expose公开预览配置，未启用时显式跳过，最终完整运行已启用并通过。Checkstyle按暂存+重命名完整集合扫描，补既有排版类型的Javadoc及since；未修改校验规则。所有最终命令退出码均0。

完整原始日志仅保存在本地临时目录，不提交日志或私有库备份。本节证明本地测试，不代替第三方联调或人工PR风险接受。


## Stage 4 — 显式import后续整理（2026-11-11）

范围仅IAM service下8个Java文件，以及计划/验证记录；不涉及协议合同、配置、数据库或前端变动。用户已要求提交合并，并已明确授权公开仓库发布。

- 逐文件解析各自import，将类型解析回全限定名后比较Java token：`Resolved Java token streams identical: 8 files`；字符串及序列化类名未改变。比较基线是已合并main `78624af241091d543eb121b09fd5f94537feb333`，含用户先前同类import整理。
- `mvn test -Plocal`实际重新执行：`BUILD SUCCESS`，643项，0失败/错误，1项既有SysUserUsernameUniqueMigrationTest环境门控跳过，实际642项通过；IAM387项和API21项全部通过。日志：`/private/tmp/macula-iam-review-20261111/import-cleanup/merge-tests.log`，退出码0。
- 8个变更Java文件Checkstyle退出码0；`git diff --check`通过。
- 未修改CLAUDE.md、AGENTS.md、REVIEW.md、技能或hooks，无适用配置eval变化；既有前端/数据库/厂商能力代码不变，不把原有外部验证成绩重新算作本轮执行。

Verification is green。独立审查仅覆盖本轮差异，不重报此前已关闭的Plan问题；此结论不构成人工风险接受。


## IAM全模块显式import整理自检（2026-11-11）

用户明确要求扫描整个macula-cloud-iam，将全限定类型尽量改为import。扫描src下317个生产/测试Java文件，修改135文件（生产62、测试73），替换1536处类型/静态方法引用；不改变注释、字符串或序列化标识。保留原有AbstractThirdPartyAuthenticationFilter未提交修改，写入前核对各文件摘要。

- 全部135个文件按各自显式/同包/Java隐式导入还原类型和静态方法后，Java token流与本轮写入前快照一致（135/135）；所有注释和字符串片段逐项相同。
- 最终不限制包名前缀的词法扫描只剩1处代码限定：MaculaOAuth2AuthorizationServiceTest内tools.jackson.databind.ObjectMapper，与该测试用于旧格式夹具的com.fasterxml.jackson.databind.ObjectMapper同名，保留必要限定。生产代码剩余0处，注释和字符串中的包名不计入。
- `mvn -pl macula-cloud-iam -am test -Plocal`：`BUILD SUCCESS`；API21项、IAM387项，408项全部通过，无失败/错误/跳过，退出码0。
- 135个修改Java文件Checkstyle退出码0，`git diff --check`通过；没有新增package-info.java或新类型/依赖/接口。

本轮原始快照、扫描清单、等同性检查及测试/规范日志仅存本地`/private/tmp/macula-iam-review-20261111/module-import-cleanup/`，不提交生成物。此处为本地整理自检，不代表新增远端发布或部署。
