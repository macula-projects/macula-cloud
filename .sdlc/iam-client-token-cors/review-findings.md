# Review findings: IAM Client 级 Token CORS

## Plan

### Round 1

- Reviewer: `sdlc-reviewer` fallback；`REVIEW.md` 未配置 Stage 3 reviewer，默认 `codex-plan-review` 在当前环境不可用。
- Budget: Plan pass only；最多三轮；Round 2 起只审上一轮后发生的变化；不重复已记录 finding；同类缺陷只报告一次；格式为 `path:line — Severity — failure — fix`。
- Findings:
  - `.sdlc/iam-client-token-cors/plan.md:34 — Important — 计划移除整个 verificationCodeGrant 耦合会同时放宽短信/邮箱 grant 必须使用 public PKCE Client 的既有安全约束，违反 Spec“不改变 Client 认证和 grant 校验”边界 — 只移除 Origin 非空/非验证码 grant 禁配限制，保留并回归测试短信/邮箱 grant 的 public PKCE 约束；对当前 confidential+sms 历史 Client 单独制定不扩大权限的迁移办法。`
  - `.sdlc/iam-client-token-cors/plan.md:36 — Important — 计划要求调用方新增 X-OAuth-Client-Id 后再切换 IAM，但旧 IAM 的 Token CORS 不允许该 Header，而新 IAM 又拒绝未带 Header 的旧调用方，Admin 与仓库外系统不存在无中断的先后部署顺序 — 增加明确的两阶段兼容发布，或经工程师确认停机切换。`
- Disposition: 第 1 项接受，将保留 public PKCE 约束；第 2 项暴露 Spec 中 Header 方案的兼容性缺陷，暂停 Plan，建议回到 Design 改为认证后按 `Origin + authenticated Client` 绑定，待人工确认。

### Round 2

- Reviewer: `sdlc-reviewer` fallback。
- Scope: 只审修订后的 Spec 和相对 Round 1 变化；不重复已记录 finding。
- Findings:
  - `.sdlc/iam-client-token-cors/plan.md:34 — Important — “旧 IAM 运行期间先配置每个浏览器 Client”仍未给当前内置 Admin Client 提供可执行路径：该 Client 是 confidential+sms 且 allowed_origins 为空，修订后保留的 public-PKCE 校验会继续拒绝 System API/Admin 的整表保存，直接切换新 IAM 会将其锁死 — 在计划和部署文档中增加受控的历史 Client 迁移办法，例如切换前经审核 SQL 仅更新 allowed_origins 并校验影响行/原值，或提供不放宽 grant 约束的窄更新接口；补充针对该内置历史记录的迁移证明。`
- Disposition: 接受；选择不扩大公开 API 的受控 DBA SQL 路径，要求事务、原值和单行影响校验，并由隔离 MySQL 测试证明只改变 `allowed_origins`，不放宽 public PKCE 或其他 Client 字段。

### Round 3

- Reviewer: `sdlc-reviewer` fallback。
- Scope: 只审 Round 2 finding 的修订；不重复前两轮 finding。
- Findings: `NO_FINDINGS`。
- Disposition: Plan review 在第三轮结束；前两轮 Important 均已修订，最终轮无新增 finding。
