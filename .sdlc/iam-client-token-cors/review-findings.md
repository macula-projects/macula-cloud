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

## Code review

### Round 1 — Code — `d4e4fd99872272af9763b75cbbd270c6e115c039 + uncommitted workspace`

- Reviewer: `sdlc-reviewer`；Depth: `Standard`（仓库 `REVIEW.md` 未显式声明 depth，按完整 Bugs、Security、Compliance passes 执行）。
- Scope: 相对基线 `d4e4fd99872272af9763b75cbbd270c6e115c039` 的全部已跟踪 diff 和本变更新增文件。
- Passes run: Bugs、Security、Compliance；Compliance 未发现适用的新增缺陷。
- Earlier findings suppressed: 0。
- Verifier rejected: 0；独立 verifier 确认以下 3 条均真实发生。
- Findings:
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilter.java:65 — Important — 过滤器要求 SecurityContext 中已有已认证 OAuth2ClientAuthenticationToken，但默认 public-client converter 只认证带 code_verifier 的 authorization_code 请求，仓库未为 sms、email、external_code 或 public refresh 注册 Client 认证转换器；这些受支持的 public Client 浏览器 grant 会在进入既有 grant Provider 前返回 invalid_request，当前真实过滤链测试只覆盖 confidential Client 的成功路径 — 为受支持的 public 自定义 grant 建立可可靠取得 RegisteredClient 的既有认证路径，并增加 sms、email、external_code、public authorization_code/refresh 的匹配 Origin 可达性测试 — Response: fixed and verified 2026-10-28`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSource.java:113 — Important — Token 预检强制要求 Access-Control-Request-Headers 声明 content-type，但 application/x-www-form-urlencoded 的 Content-Type 是 CORS safelisted；浏览器因 Authorization 或 tenantId 触发预检时通常不会声明它，合法请求会被 403 拒绝 — 不要求预检声明 safelisted Content-Type，只校验浏览器实际声明的 Header 均在 allowlist 中，并覆盖 authorization、tenantId 及二者组合 — Response: fixed and verified 2026-10-28`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSource.java:76 — Minor — 每个跨域 Token POST/OPTIONS 都在认证前调用 hasAllowedOrigin，其 FIND_IN_SET 条件无法利用现有 client_id 索引，匿名请求可持续触发 OAuth Client 表全表计数并放大数据库与连接池压力 — 使用随 OAuth Client 配置失效的规范化 Origin 缓存或可索引的独立 Origin 映射，补充更新和失效验证 — Response: fixed and verified 2026-10-28`

### Round 2 — Code — `d4e4fd99872272af9763b75cbbd270c6e115c039 + uncommitted remediation workspace`

- Reviewer: `sdlc-reviewer`；Depth: `Standard`；项目 `REVIEW.md` 的 Bugs、Security、Compliance 三个 pass 均执行。
- Scope: 只审 Code Round 1 remediation 增量：public Token Client converter/provider、public refresh 签发与归属、Token 预检 Header 修正、固定键 Origin 传输缓存、权威 Client Origin 查询、提交后缓存清理及 Stage 4 随后修正。
- Earlier findings suppressed: 7（Code Round 1 三项和 remediation plan review 四项均不重复报告）。
- Verifier rejected: 0；全新上下文 verifier 确认以下 2 条均真实发生。
- Compliance: 未发现适用于本轮增量的新增合规缺陷。
- Findings:
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverter.java:52 — Important / Security — converter 注册在 Spring Authorization Server 共享 Client 认证过滤器中，却未限制为 POST /oauth2/token；Spring Security 7.0.7 同一过滤器还覆盖 introspection、revocation、device authorization 和 PAR，攻击者可在这些端点提交 public client_id 与受支持 grant_type，仅凭公开标识建立已认证 Client，其中 introspection Provider 不校验请求 Client 是否拥有目标 Token，因而可探测任意有效 Token 的状态与 claims — 将 converter 显式绑定 AuthorizationServerSettings 的 POST Token endpoint matcher，并增加 introspection、revocation、device authorization、PAR 的负向测试 — Response: fixed and verified 2026-10-28`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImpl.java:52 — Minor / Bugs — 固定缓存键 __allowed_origins__ 与 MaculaRegisteredClientRepository 的任意 client_id 共用 OAuth Client cache region；System 允许合法 Client ID 取该值，因此两类缓存项会使用同一字符串键，先写入的一方可能让另一方收到 Set 或 RegisteredClient 的错误类型并中断 Client 认证/CORS — 为 Origin 索引使用独立 cache region 或不会与 Client ID 冲突的键类型，并覆盖该保留样式 Client ID — Response: fixed and verified 2026-10-28`

### Round 3 — Code — `d4e4fd99872272af9763b75cbbd270c6e115c039 + uncommitted Code Round 2 remediation workspace`

- Reviewer: `sdlc-reviewer`；Depth: `Standard`；项目 `REVIEW.md` 的 Bugs、Security、Compliance 三个 pass 均执行。
- Scope: 只审 Code Round 2 remediation 增量：public converter 的严格 POST Token endpoint matcher、共享 System API cache region 常量、独立 Origin 索引 region、双 region 事务提交后失效、缺失/异常处理及对应单元、真实安全链、缓存碰撞和 Stage 4 证据。
- Earlier findings suppressed: 9；不重复 Code Round 1/2 code finding 或 remediation plan finding。
- Findings: `NO_FINDINGS`。
- Verifier: 全新 verify-mode 上下文收到空候选列表，返回 `NO_CANDIDATES_TO_VERIFY`；rejected: 0。
- Nits: 0。
- Compliance: 未发现适用于本轮增量的新增合规要求违反。
- Disposition: Code Round 3 无新增 finding；等待人工 PR 审批，不由 agent 自行批准、合并、推送或部署。
- Human approval: Rain 于 2026-10-28 明确回复 `LGTM`；Stage 5 人工审批 gate 通过。该审批不扩大为 commit、push、PR 创建、merge 或生产部署授权。

### Pre-existing

无。

### Recurring

扫描 `.sdlc/*/review-findings.md` 后，未发现上述 finding class 已在两个或更多 change 中出现；当前无需要编码的 recurring class。

### Code round summary

| Round | Stage | Findings posted | Suppressed as duplicates | Verifier rejected |
| --- | --- | ---: | ---: | ---: |
| 1 | Code | 3 | 0 | 0 |
| 2 | Code | 2 | 7 | 0 |
| 3 | Code | 0 | 9 | 0 |

## Code Round 1 remediation plan review

### Round 1 — Plan — `uncommitted`

- Reviewer: `sdlc-reviewer`；Passes run: Plan。
- Scope: accepted spec revision 2026-10-28 and `plan.md` Code Round 1 remediation addendum only。
- Earlier findings suppressed: 3。
- Verifier rejected: 0；独立 verifier 确认以下 4 条均真实发生。
- Findings:
  - `.sdlc/iam-client-token-cors/plan.md:79 — Important — 仅在事务提交后清理缓存时，清理异常无法回滚数据库且可能让 IAM 继续使用已撤销 Origin — 最终 Origin 授权改为按已认证 client_id 查询权威数据库；缓存只提供传输许可，陈旧缓存不能签发 Token，并增加清理失败测试 — Response: fixed`
  - `.sdlc/iam-client-token-cors/plan.md:89 — Important — public refresh proof 未固定跨 Client refresh token 归属拒绝 — 增加 Client B 使用 Client A refresh token 返回 invalid_grant 且 Token Generator 无调用的断言，并保留 confidential compatibility 回归 — Response: fixed`
  - `.sdlc/iam-client-token-cors/plan.md:91 — Minor — 固定键缓存 proof 未覆盖并发 cache miss，匿名并发仍可能重复全表加载 — 规定同步 single-flight，并发测试断言一次加载、共享结果和异常全部失败关闭 — Response: fixed`
  - `.sdlc/iam-client-token-cors/plan.md:80 — Important — remediation 要更新 verification.md 但 Additional files 未列该路径 — 已加入精确路径并限定只追加 Stage 4 重跑证据 — Response: fixed`

### Round 2 — Plan — `uncommitted`

- Reviewer: `sdlc-reviewer`；Passes run: Plan。
- Scope: 只审 Round 1 四条 finding 对应的 remediation plan 修订。
- Findings: `NO_FINDINGS`。
- Earlier findings suppressed: 4。
- Verifier rejected: 0（无候选 finding 需要二次验证）。
- Disposition: 修复计划评审通过；等待工程师明确批准后进入实现。

## Code Round 2 remediation plan review

### Round 1 — Plan — `uncommitted`

- Reviewer: `sdlc-reviewer`；Pass: Plan only。
- Scope: `plan.md` 的 `## Code Round 2 remediation` 新增部分；检查 converter Token endpoint 限定、独立 Origin cache region、双 region 事务后失效、精确文件范围和重新验证证据。
- Earlier findings suppressed: 9；不重复 Code Round 1/2 finding 或上轮 remediation plan finding。
- Findings: `NO_FINDINGS`。
- Disposition: Code Round 2 修复计划首轮评审通过，等待工程师明确批准后实施。

## Runtime remediation plan review

### Round 1 — Plan — `e9f41e6 + uncommitted runtime remediation plan`

- Reviewer: `sdlc-reviewer` fallback；Pass: Plan only。
- Scope: `plan.md` 的 `## Runtime remediation: 历史 confidential 验证码 Client 可维护性` 新增部分；检查历史豁免安全边界、并发、错误信息泄露、精确文件范围和证明。
- Earlier findings suppressed: 9；不重复既有 Plan/Code finding。
- Findings:
  - `.sdlc/iam-client-token-cors/plan.md:159 — Important — 历史豁免依赖先读旧 auth/grant 再无条件 updateById，但计划未处理并发；若另一请求已把 Client 整改为合法 public 配置，基于旧快照的保存仍可把 confidential+sms 历史组合写回，绕过“目标状态变化重新严格校验”的边界 — 在同一事务内对 Client 行加锁，或用旧 auth/grant 作为条件更新并在不匹配时冲突失败；补充可证明并发整改不会被旧表单回写覆盖的测试 — Response: fixed in plan with conditional auth/grant update, conflict failure and concurrency proof`
  - `.sdlc/iam-client-token-cors/plan.md:167 — Minor — 风险段写成“不得把 confidential 浏览器 Client 转成 public”，与第 160 行及 Proof 中“管理员显式整改为完整 public PKCE 配置允许”矛盾，实施者可能据此错误阻止安全整改 — 改成“不得自动转换；仅管理员显式提交且完整满足 public PKCE 约束时允许整改”，并保持对应成功测试 — Response: fixed`
- Disposition: 两项接受并修订；Round 2 只复审对应变化。

### Round 2 — Plan — `e9f41e6 + uncommitted runtime remediation plan revision`

- Reviewer: `sdlc-reviewer` fallback；Pass: Plan only。
- Scope: 只复审 Round 1 两项 finding 对应的条件更新并发保护、显式 public PKCE 整改边界及证明变化。
- Earlier findings suppressed: 11。
- Findings: `NO_FINDINGS`。
- Disposition: Runtime remediation 计划评审通过；等待工程师明确批准后实施。
