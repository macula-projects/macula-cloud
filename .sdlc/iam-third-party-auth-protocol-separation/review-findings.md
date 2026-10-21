# Review findings: IAM 第三方身份认证与协议解耦

## Plan

Reviewer: `sdlc-reviewer` fallback；`REVIEW.md` 未指定 Stage 3 reviewer，默认 `codex-plan-review` 未安装。仅执行 Plan pass，不执行源码 Bugs、Security 或 Compliance pass。

### FailureHandler 收敛 Round 1（本轮实际 1 轮）

- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:60` — Important — 新 Handler 的职责和 Proof 未闭环说明删除旧公共失败处理器后的安全诊断日志归属 — 已明确第三方客户端只记录白名单诊断，新浏览器 Handler 完全不记录异常；测试将捕获输出，证明绑定分流不记错且普通异常消息、堆栈不泄漏。

Round 1 未修改源码或运行测试；以上修订待增量复核。

### FailureHandler 收敛 Round 2（本轮实际 1 轮，累计实际 2 轮）

仅复核 FailureHandler Round 1 后的修订：第三方客户端白名单诊断、新浏览器 Handler 无日志边界及对应输出捕获 Proof 已明确。上一轮 finding 已闭环，本轮无新发现；Plan pass 通过。审查者未修改源码或运行测试。

### FailureHandler 原因展示 Round 3（本轮实际 1 轮，累计实际 3 轮）

- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:63` — Important — “账号当前不可用”与“凭据无效”两类公开文案仍可用于确认账号存在 — 已将不存在账号、错误凭据、锁定、禁用、账号/凭据过期收敛为同一公开错误码和固定文案；Proof 增加这些场景响应完全一致的防枚举断言。

本轮达到 Plan pass 三轮上限，不再发起自动复核；修订后的公开错误分类与测试边界交由工程师确认。审查者未修改源码或运行测试。

### Round 1（实际 1 轮）

- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:4` — Important — 多个拟改 Java 与测试文件只写 basename/“同目录”，artifact gate 无法逐项确认目标 — 已改为每个目标的仓库根相对完整路径。
- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:8` — Minor — 新增 `sys_user.username` 唯一约束但遗漏 System README 迁移说明 — 已将 `macula-cloud-system/README.md` 加入文件清单、实施顺序和 Proof，明确 V7 预检、失败恢复与回滚说明。

Round 1 未修改源码或运行测试；以上修订待增量复核。

### 诊断日志补充 Round 2（本轮实际 1 次，补充累计实际 2 轮）

仅复核诊断日志 Round 1 后的修订：`GithubClient.java` 已以完整路径进入文件清单并移除旧条件；日志内容已收紧为白名单结构化字段、企业微信数字 `errcode` 与本地失败类别，第三方自由文本、任意异常消息和堆栈均不落日志；新增 `ThirdPartyAuthenticationFailureHandlerTest.java` 已进入文件清单与 Proof，覆盖恶意异常和待绑定分流。本轮无新发现，诊断日志 Plan pass 通过。审查者未修改源码或运行测试。

### Round 2（本轮实际 1 轮，累计实际 2 轮）

仅复核 Round 1 后的改动：所有拟变更文件已列为仓库根相对完整路径，`macula-cloud-system/README.md` 已进入文件清单、实施步骤和 Proof。上一轮两项均已修订，本轮无新发现；Plan pass 通过。审查者未修改文件或运行测试。

### 诊断日志补充 Round 1（实际 1 轮）

- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:52` — Important — 已确定修改 `GithubClient.java`，但文件清单仍将其列为有条件变更 — 已用完整路径明确列入并移除旧条件。
- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:52` — Important — 第三方自由文本错误描述可能回显 code、token、secret 或用户标识 — 已收紧为只记录本地白名单字段、企业微信数字 `errcode` 与本地失败类别，所有第三方自由文本不落日志。
- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:74` — Minor — Proof 未闭环验证公共失败处理器不再打印异常全文/堆栈及待绑定不记错 — 已加入独立 `ThirdPartyAuthenticationFailureHandlerTest.java` 和对应 Proof。

Round 1 未修改源码或运行测试；以上修订待增量复核。

## Code review

- 评审者：`sdlc-reviewer`（Review 与独立 Verify 模式）。深度：Standard（技能默认；仓库 `REVIEW.md` 未另设深度）。
- 审查基线：`f23008f81bdc4f6b78b6ab4af8f643a9396ba510` 加当前 staged、unstaged 与 untracked 工作区差异。
- Passes run：Bugs、Security、Compliance，遵照仓库 `REVIEW.md`。
- Stage 4 证据：全量后端测试通过；IAM 98 项通过；System 48 项通过、1 项常规环境跳过；两项隔离 MySQL 8.4 迁移测试分别通过；IAM Checkstyle、`git diff --check`、`git diff --cached --check` 与高置信凭据扫描通过。真实 GitHub/企业微信平台往返未验证。

### Round 1 — Code — `f23008f` 加未提交工作区差异（2026-10-21）

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java:113` — Bugs — Important — 发起阶段把 `SavedRequest#getRedirectUrl()` 规范化为相对 `path?query` 保存到 state，上游回调校验却把原始绝对 redirect URL 与相对目标直接比较；存在 SavedRequest 的 GitHub/企业微信回调会被判定为“登录续转目标无效” — 回调使用与发起阶段相同的规范化逻辑后再比较，并增加带真实 SavedRequest 的成功回调测试。
- `macula-cloud-iam/src/main/resources/templates/third-party-bind.html:43` — Security — Important — “取消并返回登录”仅 GET `/login`，未清除会话中的待绑定外部身份；同一会话在五分钟内仍可重新进入或提交绑定，不满足规格要求的取消后失败关闭 — 增加同源、CSRF 保护的取消动作并清除 pending，测试取消后页面访问和提交均失败。

Compliance pass 未发现本次差异适用的额外控制问题。以上两项经独立 verifier 确认控制流可达。

以下两个候选的代码机制虽经 verifier 确认，但不构成相对已接受规格的缺陷，未发布为 finding：

- `V7__sys_user_username_unique.sql` 对逻辑删除记录同样保持用户名唯一，是规格第 10、11 条及 Plan 第 6 步明确要求的全局身份唯一约束；迁移遇历史重复值停止也是既定失败关闭行为。
- 禁用的企业微信同名账号阻止回退到另一条绑定，是规格第 3 条明确要求的“账号无效时必须拒绝”，不能把无效直接匹配视为“查不到”后改用其他账号。

Already reported in an earlier round, not repeated here：7。

Verifier rejected：0；规格对照后未发布的候选：2。

### Round 2 — Code — `f23008f` 加 Round 1 后未提交修复（2026-10-21）

- Scope：仅复核 Round 1 的 SavedRequest 规范化修复、取消待绑定状态修复，以及由 Stage 4 补充的绑定页浏览器回归；未重新审查其余工作区差异。
- Passes run：Bugs、Security、Compliance，深度 Standard，遵照仓库 `REVIEW.md`。
- Stage 4 证据：根 reactor `mvn test -Plocal` 的 11 个模块全部成功，IAM 101 项通过，System 48 项通过、1 项常规环境跳过；两项隔离 MySQL 8.4 迁移测试通过；管理端 46 项单测、25 项 E2E 与构建通过；IAM 登录、绑定、授权页面共 12 个视图/宽度组合通过；本次修改的 Java 主代码 Checkstyle 0 项，高置信凭据扫描及差异检查通过。真实 GitHub/企业微信平台往返未验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java:117`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java:69` — Security — Important — `resolveSavedRequestTarget` 只校验并保存 `SavedRequest` 的路径与查询参数，未验证原始绝对 URL 的 scheme、host、port 是否与受信 `issuer` 同源；state 校验可通过，但原生 `SavedRequestAwareAuthenticationSuccessHandler` 最终仍按缓存中的绝对 URL 跳转，攻击者控制请求 Host 或代理 Host 时可造成站外重定向，违反规格第 8 条“请求 Host 不能决定最终跳转” — 保存及回调时要求 SavedRequest origin 与固定 `issuer` 完全一致，或让成功处理器只使用已验证的相对目标；补充恶意 Host、`X-Forwarded-Host` 与非默认端口测试 — Response: fixed。

Round 1 已发布的两项问题均已修复，未重复报告。独立 verifier 确认上述失败控制流实际可达。

Already reported in an earlier round, not repeated here：0。

Verifier rejected：0。

### Round 3 — Code — `f23008f` 加 Round 2 后未提交修复（2026-10-21）

- Scope：仅审查 Round 2 后的 SavedRequest 与固定 issuer 同源校验、统一浏览器失败跳转的固定 issuer 修复，以及删除全局 `CorsFilter`、仅为 `/oauth2/token` 开放显式来源 CORS 的增量；不重新审查其余工作区差异。
- Passes run：Bugs、Security、Compliance，深度 Standard，遵照仓库 `REVIEW.md`。
- Stage 4 证据：根 reactor `mvn test -Plocal` 的 11 个模块全部成功，IAM 104 项通过，System 48 项通过、1 项常规环境跳过；两项隔离 MySQL 8.4 迁移测试分别通过；管理端 46 项单测、25 项 E2E 与构建通过；IAM 登录、绑定、授权页面共 12 个视图/宽度组合通过；全部变更 IAM 主代码按仓库规则 Checkstyle 0 项，Compose 配置、差异检查和高置信凭据扫描通过。真实 GitHub/企业微信平台往返未验证。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandler.java:58,71` — Security — Minor — 普通失败把原始 `AuthenticationException` 委托给 Spring Security `SimpleUrlAuthenticationFailureHandler`；当前 7.0.7 默认会把同一异常对象保存到 HTTP Session 的 `SPRING_SECURITY_LAST_EXCEPTION`，使原始消息、cause 或内部诊断可能进入会话存储。现有测试只证明日志不含原始 marker，未检查 Session — 继续复用原生重定向 Handler，但只传入不含原始消息/cause 的受控异常，或在委托后清除 `WebAttributes.AUTHENTICATION_EXCEPTION`；增加 Session 不包含原始 marker 的断言。

Bugs pass 未发现新的可达缺陷；Compliance pass 未发现本次增量适用的额外控制问题。独立 verifier 依据 Spring Security 7.0.7 的实际实现与当前委托路径确认上述失败实际发生。

Already reported in an earlier round, not repeated here：3（Round 1 两项、Round 2 一项）。

Verifier rejected：0。

## Pre-existing

本轮范围未识别到需单列的既有问题。

## Recurring

已扫描 `.sdlc/*/review-findings.md`；当前无在两个及以上变更中重复出现的发现类别。

## Round summary

| Round | Stage | Findings posted | Suppressed as duplicates | Verifier rejected | Spec-adjudicated candidates |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | Code | 2 | 7 | 0 | 2 |
| 2 | Code | 1 | 0 | 0 | 0 |
| 3 | Code | 1 | 3 | 0 | 0 |
