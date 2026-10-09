# 验证报告：IAM 接入指南与交互演示

2026-10-09，作者 Rain，分支 `feat/iam-protocol-playground`。
Rain 在 Build 完成门禁回复 YES 后执行正式 sdlc-test。spec/plan 均已 accepted；没有自批评审、提交或部署。

后续评审发现 I1：默认 Macula Boot advice 在字段校验失败时记录原始凭据。Rain 回复“继续”授权修复后，已局部处理校验/JSON 解析异常并完成以下重新验证；当前 Test 通过，I1 已经独立复审确认关闭，尚未获人审合并批准。原始发现及复审结论保留在 [review.md](review.md)。

## I1 修复后的最新验证

- `PlaygroundApiController` 局部处理 `MethodArgumentNotValidException`、`HttpMessageNotReadableException`，返回固定 HTTP 400，不输出异常/原因/被拒绝字段值；原 password/sms Provider、Captcha 默认 true、全局框架行为不变。
- 新增 `PlaygroundInputSafetyTest`，使用实际 Macula Boot `ControllerExceptionAdvice` 和真实 MVC 参数绑定，捕获 root 日志正文及 throwable 堆栈；2 个测试覆盖 16 组输入（密码、验证码、用户名/手机号、state/nonce/challenge、code/verifier，以及非法枚举、截断 JSON、类型错误、未知字段）。同时断言不泄露输入、固定错误响应、HTTP 400、没有调用业务服务。
- 先加测试再修复：`mvn -pl macula-cloud-iam -am test -Plocal -Dtest=PlaygroundInputSafetyTest -Dsurefire.failIfNoSpecifiedTests=false` 退出 1，真实输出 `Tests run: 2, Failures: 2, Errors: 0, Skipped: 0`，失败断言为 `Sensitive input must not enter log messages or exception stacks`。修复后 2 项通过。
- 真实 HTTP fixture 也已导入实际 `ControllerExceptionAdvice`，原有协议和拒绝路径重新通过，不再以缺少全局异常处理器的 fixture 证明部署行为。

修复后以下命令均实际重跑，最终退出 0：

| 命令 | 本次输出 |
| --- | --- |
| `mvn -pl macula-cloud-iam -am test -Plocal` | `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| `mvn -pl macula-cloud-iam -am package -Plocal` | `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| `mvn test -Plocal` | IAM 72、TinyID 33、RocketMQ 1、Docs 1，共 107；全部无失败/错误/跳过；`BUILD SUCCESS` |
| 管理端 `npm run test:unit -- --run` | `Test Files  2 passed (2)`；`Tests  10 passed (10)` |
| 管理端 `npm run build` | `✓ 2241 modules transformed.` |
| 管理端 `npm run test:e2e:ci` | `All specs passed!`；8 项通过 |
| 原登录/授权页 `check-ui.cjs` | 8 组 `interaction: passed` |
| 新演示页 `check-playground.cjs` | 9 组 `passed: true`，连接含实际全局异常处理器的隔离 IAM |
| `git diff --check` | 无输出，退出 0 |

本轮日志前缀 `/tmp/iam-input-`，包括 `before`、`target`、`full`、`package`、`admin-unit`、`admin-build`、`admin-e2e`、`browser`、`old-ui`。原 Proof 1–8 的分层映射仍适用，Proof 6/R7 增加上述实际 advice 日志断言；无额外配置/CLAUDE/skill/hook 修改，eval 不适用。环境及 mock 边界不变。

## I1 修复前的历史执行结果

以下最终命令均退出 0，没有跳过测试。测试报告在模块 `target/surefire-reports`，浏览器产物在 IAM `target/playground-browser` 和 `target/iam-preview`，不提交这些生成文件。

| 命令 | 实际输出摘录 |
| --- | --- |
| `mvn -pl macula-cloud-iam -am test -Plocal` | `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| `mvn -pl macula-cloud-iam -am package -Plocal` | `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| `mvn test -Plocal` | IAM 70、TinyID 33、RocketMQ 1、Docs 1，共 105 项；各模块 `Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| 管理端 `npm run test:unit -- --run` | `Test Files  2 passed (2)`；`Tests  10 passed (10)` |
| 管理端 `npm run build` | `✓ 2241 modules transformed.`；退出 0 |
| 管理端 `npm run test:e2e:ci` | `All specs passed!`；`8 8 - - -`（8 项通过，无失败/待定/跳过） |
| `node macula-cloud-iam/src/test/ui/check-ui.cjs` | 8 个视图/视口组合均输出 `"overflow": false, "interaction": "passed"` |
| `node macula-cloud-iam/src/test/ui/check-playground.cjs` | 9 组均输出 `"passed": true`，包含 `real-two-context-device-approval-poll` 和 `mock-timeout-loading-duplicate-submit` |
| 两个新增 JS 的 `node --check`、`git diff --check` | 无输出，退出 0 |

本次机器原始日志：`/tmp/iam-playground-{target-test,full-test,package,admin-unit-run,admin-build,admin-e2e,browser,old-ui}.log`。日志是临时诊断材料，不是可长期依赖的仓库工件。

浏览器使用系统 Chrome 与已有 Playwright，环境变量 `PLAYWRIGHT_MODULE=/Users/Rain/.npm/_npx/9833c18b2d85bc59/node_modules/playwright`。新页面连接 `PlaygroundHttpIntegrationTest.main` 随机本机 HTTP 地址（通过 `PLAYGROUND_TEST_URL` 传入）；旧页面使用 `python3 -m http.server 18743 --bind 127.0.0.1 --directory macula-cloud-iam/target/iam-preview`。测试服务器已停止。

## Proof 与规格映射

| 计划证明 / 需求 | 本次证据与层级 | 结果 |
| --- | --- | --- |
| Proof 1 / R1、R7 | 4 个视口 320/390/844/1440 × 5 场景，无横溢出；按钮至少 44px；键盘/占位符复制；真实 OIDC 后显式揭示/隐藏；加载 disabled/aria-busy、重复 submit 只发一次、真实 15 秒 AbortController 超时与恢复 | 通过 |
| Proof 2 / R2 | 真 HTTP codeFlow 验证 state/verifier、会话登录轮换、验签结果、UserInfo、退出及回调重放；新增过期授权码注入隔离 Redis 后真实 HTTP 拒绝换码。错误 redirect 和框架 PKCE 拒绝由既有 MockMvc 真过滤链覆盖；签名/iss/aud/nonce/exp 异常由签名 JWT + 独立 HTTP JWKS 验证器测试覆盖，不声称恶意 ID Token 来自正常 IAM | 通过（分层证据） |
| Proof 3 / R3 | 真 HTTP + 隔离 Redis：pending、允许、拒绝、重放、错误客户端、未知 user_code、未登录和 CSRF。新增设备码到期后 token 请求拒绝（存储删除过期索引，返回 invalid_grant）；两独立浏览器上下文完成用户批准及设备轮询取 token | 通过；slow_down 见下方已记录调整 |
| Proof 4 / R4、R5 | 真 HTTP 机密 code/refresh、credentials、introspection/revoke；公共 code 无 refresh；原 password/sms 参数与无 ID Token 行为；既有 IAM 过滤链 scope 扩权、参数缺失、错误映射、Captcha 默认 true 回归均通过 | 通过 |
| Proof 5 / R6 | Policy 默认关闭/allowlist/生产否决；仓库冲突、缺 secret、业务委托；真 HTTP enabled=false、prd、production、local+prd、local+production 时页面/回调/Device 页/JS/CSS/API 均 404，已发 access UserInfo 与 refresh 均 401，匿名普通 login 仍 200；隔离 Redis 7 种索引包括 code/device/user_code/state 关闭后全部拒绝 | 通过（运行时切换及单元矩阵，不冒充生产部署验收） |
| Proof 6 / R6、R7 | 框架 introspection provider 演示/业务/不同演示客户端矩阵；ROOT 主体无业务 claims；跨 session reveal/操作拒绝；CSRF/Origin、未知 issuer/任意 token 字段拒绝；会话限额、到期、失效、并发；敏感 toString/响应脱敏、HTTP 不跟随重定向/响应体上限 | 通过 |
| Proof 7 | IAM test/package、完整 Maven reactor、管理端 unit/build/e2e、新旧 IAM 浏览器均本轮执行，最终代码后完整复跑 | 通过 |
| Proof 8 | IAM 真 HTTP 使用正常过滤链和独立 Redis，身份/业务客户端服务是测试替身；TinyID 使用 Docker/Testcontainers 双 MySQL；无生产连接。浏览器错误注入、管理端 Cypress mock 与真实协议路径分开列示 | 通过，边界如下 |

## 证据边界与计划调整

- Security 7.0.7 Device Provider 不产生 slow_down。依 plan 已记录的调整，使用独立真实 HTTP 响应验证 slow_down 后加 5 秒及禁止提前/重叠轮询；不声称该结果来自 IAM。正常 IAM 的 pending、批准、拒绝等由真实端点证明。
- 异常签名/issuer/audience/nonce 使用受控 JWKS HTTP 服务及真实签名 JWT，redirect 错误使用 MockMvc 真实过滤链，均不是浏览器制造的正常 IAM 成功响应。初次 OIDC 及刷新验签、UserInfo、退出由真实 IAM HTTP 验证。
- 过期测试调整隔离授权的时间戳，再请求真实协议端点，不等待数分钟，不向业务 Redis 写入。
- UI 网络失败与超时使用 Playwright 拦截；成功 PKCE、Device、credentials、introspection、撤销仍真实调用测试 IAM。旧登录页脚本及管理端 Cypress 使用响应 mock，不是外部业务后端 E2E。
- 未验证真实手机深链、生产身份源、真实短信供应商、反向代理部署或跨节点并发。第一版只支持同节点会话并发保证/会话粘性，不宣称共享会话跨节点一致性。

## 本轮失败及修正记录

1. 初次 `npm run test:unit` 默认进入 watch：输出 10 passed 后仍等待。停止该 watcher，使用 `-- --run` 重跑并取得退出 0。
2. 新增环境矩阵初次断言登录页 200，但已登录会话返回正常 302。改为独立匿名 HTTP 客户端检查登录页；没有修改登录实现，矩阵重跑通过。
3. 新增授权码到期测试最初局部变量重名导致 testCompile 失败；重命名后 IAM 70 项、全后端 105 项及完整前端/浏览器套件重新通过。
4. Admin 构建有已有大 chunk 警告；Cypress 有 `/dev/tty` 诊断，最终退出 0 且所有测试通过。未为消除无关警告改管理端代码。

## 配置与 eval

功能修改 `application.yml`，增加默认关闭及外部注入的演示配置；不改 password/sms Provider 或 Captcha 默认 true。没有修改 `CLAUDE.md`、skill 或 hook，配置保护 eval 不适用，没有虚构 eval 结果。未改 Gateway/System/Admin 源码。

I1 修复与完整复验后：Verification is green。此为测试结论，不替代 I1 独立复审和人审批准。

此结果只完成 Test 阶段，停止在评审前；不代表 PR 已审查、代码已提交或服务已部署。
