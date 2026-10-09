# IAM 正式验证记录

日期：2026-10-09。Jackson 3 修正后，用户在明确询问补齐 intent 并进入正式测试后回复“继续”。本轮重新执行正式验证，结果如下；旧记录仅作历史参考。未提交、推送或部署。

## Jackson 3 修正后正式验证（本轮截至 19:00）

**Verification is green**，仅指约定的隔离自动化验证范围，不代表生产设施联调或评审已通过。

| 实际命令 | 退出码 | 实际输出摘要 |
| --- | --- | --- |
| `mvn -B -ntp test -Plocal`（清理残留后完整重跑） | 0 | `BUILD SUCCESS`，11 模块，合计 69 项，0 失败/错误/跳过 |
| `mvn -B -ntp -pl macula-cloud-iam -am package -Plocal` | 0 | `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| `mvn -B -ntp -pl macula-cloud-tinyid -am clean test -Plocal` | 0 | `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| 管理端 `npm run test:unit -- --run` | 0 | `Test Files  2 passed (2)`；`Tests  10 passed (10)` |
| 管理端 `npm run build` | 0 | 生成 dist；存在大于 500 kB 的 chunk 提示，非构建失败 |
| 管理端 `npm run test:e2e:ci` | 0 | `All specs passed!`，8/8，无失败/跳过 |
| `PLAYWRIGHT_MODULE=/Users/Rain/.npm/_npx/9833c18b2d85bc59/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs` | 0 | 320/390/844/1440px × 登录/授权，共 8 组，均 `"overflow": false, "interaction": "passed"` |
| `git diff --check` | 0 | 无输出 |

首次全后端测试确实失败：TinyID 5 个集成测试报 `Could not resolve type alias 'dev.macula.cloud.tinyid.pojo.bo.TinyIdApplicationBO'`。只在 `target/classes/mapper/TinyIdTokenMapper.xml` 找到引用，源码中已无对应 Mapper，判定为旧生成产物残留。执行目标模块 clean 后 33 项通过，随后全 reactor 重跑成功；未修改 TinyID 源码。clean 删除的仅是目标 reactor 的 target 生成物，可通过构建恢复。当前仓库测试数量已变化，以本轮日志为准，不沿用此前 72 项的数量。

### Proof 与需求对应

1. Proof 1 / Requirements 1–2：存储测试 12 项覆盖七类查询、类型隔离、TTL/state、轮换撤销墓碑、CAS、旧记录及故障传播；客户端测试 3 项覆盖不存在、五分钟默认值及 PKCE 策略。
2. Proof 2 / Requirements 3、6：协议回归覆盖 password/sms 原参数、错误映射、默认验证码 true、opaque/JWT 及兼容 openid 刷新，无额外 PKCE/ID Token 前置要求。
3. Proof 3 / Requirement 7：历史 JDK DTO fixture、Jackson 2 JSON 时间与 Principal、认证/未认证 Captcha/Weapp、Long/权限/details 往返及未知多态类型拒绝测试通过。
4. Proof 4 / Requirements 3、5：协议测试覆盖 CSRF、登录失败/成功、会话保持、匿名请求恢复、同意/拒绝及历史 consent。
5. Proof 5 / Requirements 5–6：模板渲染和八组浏览器检查覆盖移动布局、触控、键盘、网络失败/超时、防重复提交、成功跳转、权限选择和拒绝；不提供虚假的短信发送成功交互。
6. Proof 6–8 / Requirements 3–4：真实过滤链和隔离 Redis 覆盖授权码 PKCE、refresh、introspection、revocation、OIDC Discovery/JWKS/ID Token/UserInfo/Logout 及拒绝路径。

### 边界与交接

- IAM 协议测试使用真实过滤链、Redis 和模板，身份/客户端数据库服务模拟；TinyID 数据库测试使用 Testcontainers。Cypress 和 IAM 静态页面交互使用模拟 API，不是生产 E2E。
- 生产 Redis 历史数据、Nacos、真实 Gateway 部署、短信供应商和真实移动设备 Safari/软键盘未联调；这些既有非阻断限制仍保留，不宣称通过。
- 本轮未修改 AGENTS.md、CLAUDE.md、skill 或 hook；保护配置 eval 不适用，未发现相关项目 eval 套件。
- 本轮仅补录 intent/审批及验证文档，保留业务工作区和已有暂存状态；本机 18743 静态预览服务已停止。
- 本轮日志保留于 `/tmp/iam-formal-{reactor,reactor-retry,package,tinyid-clean,admin-unit,admin-build,admin-e2e,ui}.log`。下一步交给 sdlc-deploy 进行新的代码评审，不沿用 Jackson 3 迁移前结论；尚未执行新评审或远端操作。

## 08:56 补齐证据后的最终结果

本轮仅补测试与固定历史样本，没有修改运行时代码或 password/sms 兼容业务逻辑。

| 实际命令 | 退出码 | 最新输出/结果 |
| --- | --- | --- |
| `mvn -B -ntp test -Plocal` | 0 | `BUILD SUCCESS`，11 模块；Surefire 合计 70 项，0 失败/错误/跳过 |
| `mvn -B -ntp -pl macula-cloud-iam -am package -Plocal` | 0 | `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| 管理端 `npm run test:unit -- --run` | 0 | `Test Files  2 passed (2)`；`Tests  8 passed (8)` |
| 管理端 `npm run test:e2e:ci` | 0 | `All specs passed!`，8/8 通过 |
| IAM `check-ui.cjs`（同下方完整命令） | 0 | 8 组视口均为 `"overflow": false, "interaction": "passed"` |
| `git diff --check` | 0 | 无输出 |

新增证据与 Proof 对照：

1. Redis 连接故障注入与损坏 JSON 均向上传播异常，而非返回未命中。存储测试从 7 项增至 10 项。
2. 历史固定样本由修改前提交的 DTO 独立编译并 JDK 序列化生成；直接把字节写入旧 Redis 键，验证读取、迁移和删除不复活。来源/hash/构造边界见 fixtures/README.md；不是用当前 DTO 生成的旧键模拟。已有 Principal、metadata、授权请求往返测试继续通过。
3. PKCE 补缺 verifier 和错误 token-exchange redirect_uri。公共客户端缺 verifier 沿用当前 LoginUrlAuthenticationEntryPoint 返回 302 到 /login、空响应体、不签发 token；机密客户端返回 400。此处验证拒绝行为，未声称公共客户端返回标准 JSON 错误。
4. 授权码不请求 openid 时不签发 ID Token，UserInfo 返回 403；请求 openid 但不请求 profile 时不输出 nickname/preferred_username；email/email_verified 不虚构。标准/兼容刷新回归均继续通过。协议测试从 14 项增至 16 项。
5. 8 组页面回归新增真实 fetch 网络失败、AbortController 超时、按钮恢复、键盘 Tab/Space、防重复提交、成功同源跳转、同意 scope 提交。超时只在测试页面把 20 秒计时压缩为 50ms，未修改生产脚本。初次新增用例中的 approve 选择器已改为页面实际 allow 值；最终全部通过。
6. 全量后端与前端现有套件均重新执行通过；TinyID 断言红灯已解除。代理配置未改，eval 不适用。

非阻塞边界保持原 spec 约定：身份数据库模拟，Redis/MySQL 为隔离测试设施；Cypress 使用模拟后端；移动视口为 Chrome 模拟，不等于真实手机 Safari/软键盘验证；未做生产数据迁移或真实 Gateway/MySQL/Nacos 联调。历史样本不包含所有生产 Principal 实现，不能声称兼容任意未知旧缓存。上述限制不是测试失败，也不自动授权上线。临时静态预览服务已停止。

## 08:50 修正后复验

用户明确授权修正后，仅将 TinyID 创建业务测试的提示文案由“预留的数据库实例容量，默认 10”改为现有页面的“预留数据库实例容量，默认 10”，不修改业务页面。

- `npm run test:unit -- --run`：退出码 0，`Tests  8 passed (8)`。
- `npm run test:e2e:ci`：退出码 0，`All specs passed!`，8 项全部通过，0 失败/跳过；TinyID 用例 `5 passing (3s)`。这是模拟后端的浏览器回归，不是真实服务端 E2E。
- `mvn -B -ntp test -Plocal`：退出码 0，`BUILD SUCCESS`，11 模块 reactor 成功；65 项通过，0 失败/错误/跳过。
- `git diff --check`：退出码 0。

本轮只修改一行测试断言及 SDLC 记录，未改应用源码、配置、skill 或 hook，eval 不适用。IAM 浏览器脚本在上一轮通过，本轮未重复执行。下文的首次失败保留为历史证据；Cypress 红灯已解除，Proof 表中的其他测试覆盖缺口尚未补齐，按 sdlc-test 不宣告完整 Verification is green。

## 首轮真实执行结果（修正前）

| 命令 | 退出码 | 实际结果 |
| --- | --- | --- |
| `mvn -B -ntp -pl macula-cloud-iam -am package -Plocal` | 0 | IAM 27 项测试通过，打包成功 |
| `mvn -B -ntp test -Plocal` | 0 | 11 模块 reactor 成功；Surefire 报告合计 65 项，失败/错误/跳过均为 0，含 TinyID 隔离 MySQL Testcontainers |
| 管理端 `npm run test:unit -- --run` | 0 | 2 个测试文件、8 项通过 |
| 管理端 `npm run build` | 0 | 构建成功，仍有大 chunk 提示 |
| 管理端 `npm run test:e2e:ci` | 1 | 8 项中 7 通过、1 失败 |
| `PLAYWRIGHT_MODULE=/Users/Rain/.npm/_npx/9833c18b2d85bc59/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs` | 0 | 登录/授权页面各 4 个视口，8 组通过 |
| `git diff --check` | 0 | 无空白错误 |

关键原始输出：

```text
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Test Files  2 passed (2)
Tests  8 passed (8)
✖  1 of 2 failed (50%)                      00:07        8        7        1        -        -
AssertionError: Timed out retrying after 4000ms: Expected to find content: '预留的数据库实例容量，默认 10' but never did.
```

IAM 浏览器实际输出每项均为 `"overflow": false, "interaction": "passed"`；视口为 1440×960、390×844、320×640、844×390。使用本轮 Thymeleaf 渲染产物及本地静态预览，临时预览进程已停止。

## 失败定位与边界

失败用例为 `tinyid-management.cy.js` 的 `creates a business with server-assigned datasource fields and reports capacity failures`。创建对话框实际提示为“预留数据库实例容量，默认 10，不得小于当前数据源数量”，测试预期多了“的”。管理端本轮无源代码 diff；未为通过测试而修改 TinyID 页面或断言。该问题超出 IAM 范围，等待用户决定是否授权修复。

## 首轮对照计划 Proof（缺口已由上方最终结果补齐）

| Proof | 已有证据 | 限制/缺口 |
| --- | --- | --- |
| 1 存储/TTL/轮换/并发 | 7 项 Redis 存储测试、3 项客户端仓库测试；七类型、未知类型、过期、CAS、状态不续期 | Redis 断连与反序列化故障传播未有专门注入测试 |
| 2 password/sms | 真实过滤链请求；参数、错误码、opaque/JWT、openid 刷新、复用/轮换、撤销、跨客户端与扩权拒绝 | 身份数据库模拟，非真实手机号身份查询或短信供应商联调 |
| 3 序列化 | 当前 DTO、用户 Principal、授权请求往返，旧键结构读取及防复活 | 旧记录由当前 DTO 转换构造，不是历史版本二进制 golden fixture |
| 4 登录/会话/授权/CSRF | 真实过滤链及 Thymeleaf；成功、失败、CSRF、会话、原授权请求恢复、拒绝及已有 consent | 非真实 Gateway/MySQL/Nacos 全链路 |
| 5 页面 | 8 组视口、无溢出、触控尺寸、密码显示、401 恢复、拒绝不提交 scope | 网络中断/超时、键盘导航、防重复提交、真实设备软键盘未有完整自动化证据 |
| 6 项目测试 | 全量 Maven、IAM 打包、Vitest、Cypress、IAM Playwright 均已执行 | Cypress 有 1 项红灯，不能宣称所有项目测试通过 |
| 7 PKCE | 公共/机密客户端 S256，缺失 challenge、错误 verifier、plain 降级与重放拒绝 | 缺失 verifier、错误 redirect_uri 尚未有独立断言 |
| 8 OIDC | Discovery/JWKS、ID Token 校验、opaque/JWT UserInfo、撤销、退出及错误回调、标准 OIDC 刷新仍有 ID Token | 非 openid 授权码不签发 ID Token、profile/email 隔离尚需更明确的专项断言 |

## 配置、eval 与状态

本轮没有修改 CLAUDE.md、AGENTS.md、skills 或 hooks，配置 eval 不适用。应用自身的安全配置与 POM 改动已由本轮编译、协议测试及打包覆盖，不等于代理配置 eval。

首轮因失败和证据缺口暂停；当前按 sdlc-test 完成补测并重新运行全项目测试，Verification is green。下一步可携带本记录进入 sdlc-deploy 评审阶段；本轮停在展示测试结果，不自动提交、推送或部署。
