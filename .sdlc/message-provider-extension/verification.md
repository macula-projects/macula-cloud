# Verification: 邮件服务扩展、自定义短信与消息包结构整理
Author: Rain
Stage: Test
Result: Verification is green (review fix reverified)
Date: 2026-11-13

## Scope
- [spec.md](spec.md)、[plan.md](plan.md) 均 accepted，用户已确认实施完成并授权继续。
- 基线提交 `3be078a068b725e61fc72cac764a00a425ff97fa`，当前变更尚未提交；源码/测试/README 共 60 个变更路径的工作区指纹为 `bf635c9753691a4571e2925a34fb7a10bad7da1d3a715ddb88f2147bcf2bdabc`，验证期间未改变。
- 临时日志仅保存在 /private/tmp，不含真实投递凭据。

## Actual commands and output
所有下列命令 exit status 均为 0。

### 后端
`mvn -o test -Plocal`

```text
macula-cloud-iam ................................... SUCCESS
macula-cloud-system ................................ SUCCESS
BUILD SUCCESS
```

Surefire 汇总：710 项，709 通过、0 failures、0 errors、1 skipped。唯一跳过项为既有 `SysUserUsernameUniqueMigrationTest#duplicateUsernameStopsV7UntilDataIsFixed`，原因 `Environment variable [SYSTEM_MIGRATION_IT] does not exist`。本次无 schema/迁移变更，该数据库专项不属于本次必要验证；不把跳过记作通过。未连接或修改业务数据库。

### 管理端单元测试及构建
`npm run test:unit -- --run`

```text
Test Files  23 passed (23)
Tests  223 passed (223)
```

`npm run build`

```text
✓ built in 8.01s
```

现有 chunk 体积与 Sass 弃用提示不改变退出码，未为此修改无关样式或依赖。

### 完整浏览器套件
`npm run test:e2e:ci`

```text
All specs passed!  01:44  100  95  -  5  -
```

此命令自动启动并关闭本地 Admin 预览；95 项通过，5 项 IAM 模板预览用例因未提供 IAM_PREVIEW_URL 初始 pending，未当作通过。按 IAM README 启动后端测试生成的 `target/iam-preview` 隔离页面后补跑：

```sh
python3 -m http.server 4175 --bind 127.0.0.1 --directory macula-cloud-iam/target/iam-preview
npx cypress run --spec cypress/e2e/iam-hosted-login.cy.js --expose IAM_PREVIEW_URL=http://127.0.0.1:4175/login.html --config baseUrl=http://127.0.0.1:4175
```

```text
All specs passed!  00:02  5  5  -  -  -
```

合计全部 100 项 Cypress 用例已执行并通过，无剩余浏览器 pending。

### 静态检查
- 项目 Checkstyle，临时检查 POM 指定实际变更的 42 个 Java 路径及 IAM/System 的 main/test 根目录：`mvn -o -f /private/tmp/macula-message-style-pom.xml checkstyle:check`。

```text
You have 0 Checkstyle violations.
BUILD SUCCESS
```

- 无自动修复的 scoped ESLint，覆盖本次修改的页面、两语言文件及相关 unit/Cypress 文件：

```text
868 problems (0 errors, 868 warnings)
```

退出码 0；全部 warning 为 prettier/prettier 格式建议。按 REVIEW.md 不将格式化器负责的事项作为审查发现，不执行全文件自动格式化。
- `git diff --check`：exit 0。

## Proof mapping
| 规格要求/计划验证 | 实际证据 |
| --- | --- |
| 新邮箱类型、默认参数与覆盖 | MessageSettingsServiceImplTest 邮箱预设实际保存测试及新增 Cypress Exchange/163/QQ 流程 |
| STARTTLS 与证书边界 | 两服务隔离 SMTP 的升级成功、缺少 STARTTLS、证书不受信、主机名不匹配、原 NONE/SSL 回归测试 |
| POST/认证 Header 与凭据保护 | 两服务 CustomSmsOptionsTest、System 配置保留/替换/切换隔离/不回显测试及实际浏览器 Header 编辑流程 |
| 固定短信模板契约、失败与有界调用 | 两服务 CustomHttpSmsSenderTest 捕获真实回环 POST/JSON/Header；业务失败、非 JSON、重定向、超大响应和慢正文总超时拒绝 |
| 接口实现分包与装配回归 | 全后端编译测试，重复策略拒绝、原阿里/腾讯 SDK、登录/身份绑定及端到端后端流程测试 |
| 管理端加载/失败/成功和旧功能 | 223 项单元测试及 100 项浏览器用例，含模板保存、服务配置、Header 清理、错误重试和 IAM 登录页防重复发送/提交 |

## Configuration and evals
AGENTS.md、CLAUDE.md、技能、Hook、REVIEW.md 和审查器定义均未修改，故无适用的受保护配置 eval。仓库未发现 `.sdlc/evals` 套件；不虚构评测结果。

## Limits
- 本地 SMTP/HTTP 为隔离协议测试，浏览器为合成账号/mock API 或后端真实模板预览。
- 企业自建 Exchange 的真实账号/CA/发送权限和自定义短信服务联调仍按 accepted spec 列为 non-blocking，不宣称第三方投递已验证。
- 先前用户授权的本地镜像健康和 HTTP 冒烟证据保留在 plan.md，不能替代上述测试。

## Handoff
Verification is green。可进入 sdlc-deploy 的 Bugs、Security、Compliance 独立审查；不代表提交/推送/创建 PR/合并已获授权。

## Review round 1 follow-up
发现并确认停用服务受未完成 Header 编辑阻塞，修复后须重新验证，前述 green 对新快照暂不适用。

## Current verification after review fix
- 当前快照指纹：`3e606da6dcce26a70f11053110e894f7b53b700d8de440bbd2c1f8b2fbc0d07c`；相对首轮仅修改消息设置页面和对应 Cypress 文件。
- `mvn -o test -Plocal`：exit 0，真实输出 `BUILD SUCCESS`，710 项、709 通过、0 failures/errors、同一既有数据库专项 1 条件跳过。
- `npm run test:unit -- --run`：exit 0，`Test Files 23 passed (23)`、`Tests 223 passed (223)`。
- `npm run build`：exit 0，`✓ built in 4.91s`。
- 启动隔离 IAM 预览后，执行 `npx start-server-and-test preview http://localhost:4173/ "cypress run --expose IAM_PREVIEW_URL=http://127.0.0.1:4175/login.html"`：exit 0，`All specs passed! 01:47 101 101 - - -`，所有浏览器用例执行，无 pending。新增用例真实验证未完成 Header 和无效新 URL 不阻止停用已保存服务。
- 修复涉及的两个前端文件 scoped ESLint JSON 汇总：exit 0、0 errors、246 prettier 格式 warnings，不自动重排。
- 未改 Java，首轮同一 42 路径的 Checkstyle 0 违规证据仍适用；`git diff --check` exit 0；受保护配置仍未改变，eval 不适用。
- 首轮 Minor 已按 accepted spec 修复并完成全量重验；Verification is green，可对修复差异进入第 2 轮独立代码审查。第三方联调边界及既有条件数据库跳过说明保持。
