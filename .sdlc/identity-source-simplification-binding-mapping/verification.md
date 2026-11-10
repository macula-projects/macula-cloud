# 身份源简化与绑定映射：Build 开发验证记录

日期：2026-11-09。Build 完成门已由 Rain 以 YES 确认；下文保留 Build 反馈，并追加本轮重新执行的 Stage 4 正式测试证据。不代表 PR 审查或部署批准。

## 已实现及明确边界

- Admin 隐藏平台接口版本、Flow、通讯录策略；服务端选择已核实的默认合同，保留历史显式配置及更强准入策略。自动绑定提供一组源字段和 IAM 目标字段，按来源可信能力收窄可选项。
- 新增 binding_source_field / binding_target_field，三种确定旧规则兼容转换；省略、显式 null 和有效值分开处理，继续采用来源版本 CAS。V21 只追加映射列及确定回填，不修改既有用户、凭据或绑定。
- 首次候选与事务内重查复用规范化固定 SQL；多匹配拒绝，MOBILE_PROOF 仍核对本地手机号。真实事务仅允许 REPEATABLE READ / SERIALIZABLE；READ COMMITTED 拒绝自动匹配。非 ASCII 邮箱暂不自动匹配，避免未经证明的 Java/MySQL 大小写差异。
- 用户明确选择“暂保留成员 ID 绑定，敏感字段后续处理”。企业微信本轮仅 member_id → username；不实施 snsapi_privateinfo / user_ticket。AUTO 使用固定企业基础授权已核实的成员 ID；原更强策略继续执行。
- 企业微信双网页入口保留不同取 Code / state 路径，使用同一应用凭据。微信小程序继续区分登录 Code 与手机号 Code 的 code_type，IAM 分别核验，手机号证明不生成 OpenID。
- 钉钉与飞书仅使用证据确认的权威成员资料；字段仍受实际应用权限及平台返回约束。官方接口证据及限制见 provider-contract-evidence.md。

## 实际执行结果

| 范围 | 结果 | 本机证据 |
| --- | --- | --- |
| Cloud 全后端 `mvn test -Plocal`，最终源码快照 | 556 项总计，555 通过、1 跳过、0 失败、0 错误 | `/private/tmp/identity-binding-complete-backend.log` |
| Admin `npm run test:unit -- --run` | 21 文件、199 项通过 | `/private/tmp/identity-binding-admin-tests.log` |
| Admin Docker 模式构建 | 成功；存在 Sass / chunk 体积提示 | `/private/tmp/identity-binding-admin-build.log` |
| Admin 无 fix ESLint | 通过，无错误输出 | `/private/tmp/identity-binding-eslint.log` |
| Cypress 两个身份源 / 应用设置相关 spec | 20 / 20 通过 | `/private/tmp/identity-binding-final-cypress.log` |
| 隔离 MySQL 的 IdentityBindingMappingMigrationIT | 1 项通过；V20 → V21、三种旧规则及未知规则、原字段摘要不变、重复迁移无新增 | `/private/tmp/identity-binding-migration-it.log` |
| 隔离 MySQL / Redis 的 IdentitySourceBindingIT | 15 项通过，包含真实规范化歧义、锁内相同谓词、READ COMMITTED 拒绝及原有绑定 / 票据用例 | `/private/tmp/identity-binding-final-it.log` |
| 变更 Java 主代码及测试 Checkstyle | 35 文件，XML 报告 0 错误 | `/private/tmp/identity-binding-checkstyle-all.log`、`/private/tmp/identity-binding-checkstyle-all.xml` |
| `git diff --check` | 通过 | 最终工作区直接执行 |

后端跳过项为受显式环境开关控制的 SysUserUsernameUniqueMigrationTest；本任务 V21 迁移及身份绑定集成测试已另外在独立测试资源中实际运行。

Checkstyle 使用临时 POM 指定六个主代码 / 测试目录和本次精确文件名，沿用仓库配置与许可证头；未修改项目 POM、未格式化源码。临时 POM 的 failOnViolation 不充当通过依据，已解析 XML 确认 35 文件、0 错误。

桌面 1280px 与手机 390px 的新建身份源截图已实际查看：无 Flow / 策略选择器，无横向溢出，页脚可见；成员 ID 限制提示可见。截图位于 `macula-cloud-admin/cypress/screenshots/identity-provider-policy.cy.js/identity-source-create-1280px.png` 与 `identity-source-create-390px.png`。截图只验证模拟界面，不能证明真实平台登录。

## 调整及运行条件

- 新固定 Mapper 查询使原 Mock 夹具需要同步；飞书固定组织夹具、JSON 显式字段与重复字段检查已补齐，最终完整回归重新执行。
- 首次隔离绑定 IT 缺少专用 Redis 环境参数；补建独立 Redis 并设置测试运行标记后重跑通过，不把环境缺失记作产品代码失败。
- Checkstyle 最初 CLI 未包含测试目录，已改用临时 POM 覆盖主代码与测试；报告以最终 35 文件为准。
- 仅创建 `macula-identity-mapping-it-20261109`（MySQL 8.4.6、tmpfs）与 `macula-identity-mapping-redis-it-20261109`（Redis 7.4.5、禁用持久化），均使用回环端口和独立测试库 / 标记。完成后已删除这两个容器、临时卷及本机临时凭据文件；未接入业务库或业务 Redis。

## 未验证与未执行

- 未进行真实企业应用权限、用户敏感授权、真实设备或厂商端到端联调；Mock / HTTP 合同及隔离 IT 不替代上述验证。
- 企业微信手机号、邮箱及工号不在本次交付能力内，敏感授权由用户明确延后。
- 未更新运行镜像、未在业务数据库执行 V21、未提交 / 推送 / 发布。部署前仍须确认目标数据库版本及隔离级别，并遵守新映射的回滚兼容限制。
- 原有大量未提交工作保留；修改前基线位于 `/private/tmp/identity-binding-build-baseline`，Boot 工作区检查仍为空。

## 后续人工门

Rain 已以 YES 确认 `Is the implementation complete per the plan?`，进入 sdlc-test。正式测试结果见下节。

## Stage 4 正式测试（2026-11-09）

本轮在工程师完成确认后重新执行下列命令，不以 Build 的历史日志冒充本轮测试。全部命令退出码 0。

| 命令 | 结果 | 本轮日志 |
| --- | --- | --- |
| Cloud 根目录 `mvn test -Plocal` | 总计 556：555 通过、1 跳过、0 失败 / 错误 | `/private/tmp/identity-binding-stage4-backend.log` |
| Admin `npm run test:unit -- --run` | 21 文件、199 项通过 | `/private/tmp/identity-binding-stage4-unit.log` |
| Admin `npm run build -- --mode docker` | 成功；原有 Sass / chunk 提示 | `/private/tmp/identity-binding-stage4-build.log` |
| Admin `npm run test:e2e:ci` | 全部 8 个 spec，56 / 56 通过 | `/private/tmp/identity-binding-stage4-cypress.log` |
| `mvn -pl macula-cloud-system -am verify -Plocal -Dtest=IdentityBindingMappingTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IdentityBindingMappingMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false` | 迁移 IT 1 项通过；另执行共享映射单元 2 项 | `/private/tmp/identity-binding-stage4-migration-it.log` |
| 上述命令将模块换为 `macula-cloud-iam`、IT 换为 `IdentitySourceBindingIT` | 绑定 IT 15 项通过；另执行共享映射单元 2 项 | `/private/tmp/identity-binding-stage4-binding-it.log` |
| `npx eslint`，明确列出本次 7 个 Vue / locale / unit / Cypress 文件，无 `--fix` | 0 errors、994 warnings（Prettier 格式建议），退出 0 | `/private/tmp/identity-binding-stage4-eslint.log` |
| `mvn -f /private/tmp/identity-binding-checkstyle-pom.xml checkstyle:check` | 35 个变更 Java 主代码 / 测试文件，解析 XML 为 0 errors | `/private/tmp/identity-binding-stage4-checkstyle.log` |
| `git diff --check` | 通过 | 工作区直接执行 |

ESLint 精确文件为：src/views/system/identityProvider/index.vue、src/views/system/oauth2Client/components/ClientGuide.vue、src/locales/lang/zh-cn.js、src/locales/lang/en.js、tests/unit/identity-provider-policy.test.js、cypress/e2e/identity-provider-policy.cy.js、cypress/e2e/admin-identity-security-settings.cy.js。未执行自动格式化或覆盖原有混合改动。

### 实际输出摘录

后端最终 Reactor：
```text
[INFO] BUILD SUCCESS
[INFO] Total time:  48.094 s
```

Admin 单元：
```text
 Test Files  21 passed (21)
      Tests  199 passed (199)
```

浏览器全套：
```text
    ✔  All specs passed!                        01:09       56       56        -        -        -
```

隔离集成：
```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.447 s -- in dev.macula.cloud.system.integration.IdentityBindingMappingMigrationIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.748 s -- in dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceBindingIT
```

### Proof 与 Verification strategy 对照

| 计划证明项 / Spec 条目 | 实际证据与边界 |
| --- | --- |
| 共享白名单及来源能力；策略 1 / 3 | IdentityBindingMappingTest 2 项（循环覆盖组合和来源能力）、IdentityBindingMatcherTest 2 项；证明型手机号不能映射 username，旧规则等价 / 冲突均校验。 |
| 默认 Flow、保留旧合同、CONFIGURATION 例外；策略 1 | 实际覆盖位于 IdentityProviderServiceImplTest 的 20 项内，包括默认飞书合同、编辑保留、逐提供方保存 / 支付宝凭据场景。计划中命名的独立 IdentitySourceFlowTest 并不存在；不把该类记作通过，记录实际由服务测试覆盖。 |
| JSON、CAS、新旧写兼容；策略 1 / 3 | SettingsModelJsonTest 3 项、IdentityProviderServiceImplTest 20 项及目录测试：省略 / null / 值、重复 / 未知字段、半组及不等价规则、旧客户端、版本冲突、敏感凭据不回显。 |
| V20→V21、已知 / 未知回填及原值保留；策略 5 | 真实 MySQL IdentityBindingMappingMigrationIT 1 项；从空库到 V20 再 V21、原字段摘要不变、重复启动 0 次迁移。未连接业务库。 |
| 匹配、锁内重查、并发与账号证明；策略 3 / 5 | Matcher 2、BindingPolicy 6、RegistrationBinding 6、真实 BindingIT 15 项，包含规范化歧义及 READ COMMITTED 拒绝。真实 Redis 票据验证与 Mock 用户范围服务边界保留，不声称完整真实租户权限集成。 |
| 三厂商权威资料、准入与缓存；策略 2 / 5 | 三个 DirectoryClient 各 2 项、DirectoryPolicy 8 项、Refresh 3 项；Mock HTTP 合同和组织 / 成员 / 缺失 / 故障处理。官方证据见 provider-contract-evidence.md；用户已明确将企微敏感资料暂缓，真实平台不在本地验收范围。 |
| 企微双网页入口、微信双 Code；策略 4 | WecomClient 4、WecomLoginMode 2、WechatClient 10、IamProtocol 40、ExternalCodeAccountResolver 3 项；不将服务端合同测试当作真实设备授权。 |
| UI、映射联动、双 Code 指南、布局；策略 6 | Vitest 全 199、Cypress 全 56 项，其中身份源 6、应用 / 身份设置 14。本轮生成的 1280 / 390px 截图及断言继续覆盖，390px 截图重新视检；应用设置另有 1440 / 390px 单列与间距用例。 |
| README / 权限前提 / 回滚边界 | 三模块 README、接入文档、平台证据文档均已维护；保留旧服务回滚限制、实际事务隔离要求及未完成真实平台联调声明。 |

### 运行条件、跳过与配置评估

- 全后端唯一跳过项仍为环境开关控制的 SysUserUsernameUniqueMigrationTest，属于既有用户名唯一索引迁移测试，不是本变更 V21；本计划两个 IT 均已明确启用并实际执行，未跳过。其他历史外部 IT 不在本计划验收声明内。
- 本轮重新创建专用 tmpfs MySQL / 无持久化 Redis。旧 helper 使用 mysqladmin ping 时过早返回，随后认证建库失败；改为等待认证 SELECT 后成功完成初始化。此环境准备失败不算测试通过，也未掩盖为代码成功；正式两个 IT 的退出码均为 0。两个临时容器、临时卷与私有环境文件均已删除。
- 本次变更未修改 CLAUDE.md、技能、hook、REVIEW.md 或 reviewer 定义；工作区这些受保护配置无 diff。本仓库未发现 `.sdlc/evals`，无适用配置 eval，不虚构 eval 结果。
- 新建 / 修改范围保留原有未提交工作；本轮测试未更改产品源码或弱化断言 / 检查。未更新运行镜像、业务数据库、远程 Git。

### 正式测试结论

Verification is green。结论仅覆盖接受后的本地实现范围与已列命令，包含明确的 1 项既有环境门跳过及格式警告；不包括用户暂缓的企微敏感授权和真实企业平台 / 设备联调。可以将本报告、plan.md 和代码交给 sdlc-deploy 作 PR 审查准备；尚未进行 Stage 5 审查，也不代表部署批准。

## 用户授权的本地镜像更新（2026-11-09）

正式测试后，Rain 明确要求“好，更新镜像”，授权本次相关服务的本地部署及正常启动迁移。本节更新先前“尚未更新”的时间点状态；未进行远程 Git 或 PR 操作，未将运行部署冒充 Stage 5 PR 审查。

- 目标：现有 `macula-cloud-dev` Compose 项目的 System、IAM、Admin，镜像标签 `6.1.0-SNAPSHOT`。按当前工作区构建，保留已有混合源码；不清理或覆盖其他工作。
- 构建：`docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps build macula-cloud-system macula-cloud-iam macula-cloud-admin`，退出 0；日志 `/private/tmp/identity-binding-image-build.log`。
- 启动：先对 System 执行 `up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180`，健康后再对 IAM、Admin 执行同样命令，均退出 0。日志 `/private/tmp/identity-binding-system-up.log`、`/private/tmp/identity-binding-iam-admin-up.log`。
- 启动前只读确认数据库 V20、隔离级别 REPEATABLE-READ；System 启动日志：`Successfully applied 1 migration to schema macula-system, now at version v21`。启动后再次查询 V21 success=1，binding_source_field / binding_target_field 两列存在。
- 三个服务最终均 healthy；运行 image ID 与构建标签 ID 相同：System `1a8e35e1e1b6f2655259079cec9d34158c90b723478d5560a70d5851f6a4d14c`、IAM `64a3bc846601eaf87b3928261372e071afef4e6dd4ec6e6b61b1a89f21bc2320`、Admin `84e5e82d7a0f45b7ada834f6633932f7d0486f94e9dacaec52d7ed87b17b2754`。
- HTTP 冒烟：Admin `http://127.0.0.1:5900/` 200；IAM Discovery 200、issuer 为 `http://127.0.0.1:9010`；Admin `/iam/.well-known/openid-configuration` 200；System 无凭据 `/api/v1/menus/routes` 401（正常认证边界）。TCP / HTTP 健康不代表真实平台授权联调。
- 基线比对确认其余 11 个运行容器 ID 不变，包括 MySQL、Redis、Nacos、RocketMQ。未重建 Gateway 等其他应用；未手动修改历史数据 / migration checksum。

## 部署后目录兼容回归（2026-11-09）

- 用户截图是应用“添加身份源”空列表。数据库只读检查有两个 enabled 来源（GITHUB_OAUTH / WECOM_WEB），都有附加密钥存储值；不读取或输出凭据原文。代码排查发现目录与 IAM 对解密后的空附加密钥判定不一致。
- 新增 OAuth2ClientConfigurationCatalogServiceImplTest.emptyAdditionalSecretsRemainAvailableWithoutAcceptingMixedCredentials。原实现真实失败：`empty additional secrets: null ==> expected: <true> but was: <false>`；日志 `/private/tmp/identity-picker-regression-red.log`。
- 修复只接受历史 JSON null / 空对象，继续拒绝非空混合对象、数组、字符串、损坏 JSON。未放宽来源 / 能力 / 账号准入，不修改前端安全过滤。
- 修复后 `mvn test -Plocal` 退出 0：557 总计、556 通过、1 原有环境门跳过，0 失败 / 错误；日志 `/private/tmp/identity-picker-backend.log`。全量包含目录 6 项。
- Checkstyle 临时 POM 补入本次测试类，36 文件 XML 0 错误，退出 0；`git diff --check` 通过。日志 `/private/tmp/identity-picker-checkstyle.log`。无前端源码变化，本轮不重复前端套件。
- 沿用本轮用户“更新镜像”授权，仅构建 / 重建 System，命令使用既有 Compose env-file、apps、`--no-deps --no-build --force-recreate --wait`。日志 `/private/tmp/identity-picker-image-build.log`、`/private/tmp/identity-picker-system-up.log`。构建和重建均退出 0；System 最终 healthy，运行 image ID 与新镜像 `072582c2ed4ecd8e230f1c5bd9ed8fb294670126d2679ca9a36468cbe958fcbb` 相同。Admin HTTP 200、IAM Discovery 200、System 无认证边界 401。未进行登录后的真实选择器人工操作，刷新后的真实选择结果由用户当前会话验证。
