# IAM 架构精简审阅记录

## Stage: Plan

审阅方式：REVIEW.md 未指定 Stage 3 reviewer；可用技能和工具没有 codex-plan-review，按 sdlc-build fallback 使用独立 `sdlc_reviewer` 子代理执行 Plan pass。预算最多三轮；只审 Plan，后续仅审变化，已记录问题不重复，同类问题合并报告。当前没有进行源码 Bugs/Security/Compliance 审阅或测试签核。

### Round 1

实际审阅轮数：1。审阅者只读，未改文件、未运行测试。

`.sdlc/iam-architecture-simplification/plan.md:29 — Important — 计划直接删除 EmailAuthenticationToken，但现有 EmailAuthenticationProvider 返回该类型，OAuth2UserGrantTokenIssuer:132 又把它直接写入授权 attributes；当前授权存储仅规范化 ThirdPartyAuthenticationToken，计划未覆盖旧 email 授权的类型恢复，无法证明“旧 Access Token 不主动失效”的已接受合同 — 删除前用旧版本实际序列化格式建立恢复/内省/撤销夹具；若记录引用该类型，保留受限的只读兼容类型或明确的解码适配，并将所需生产存储/Jackson文件加入名单，禁止扩大反序列化白名单或重写业务缓存。`

处置：已修订 plan.md 的文件名单、步骤 3、Risks 和 R4 Proof。新增 LegacyEmailPrincipalReader 及旧序列化夹具，MaculaOAuth2AuthorizationService 只增加 Principal attributes 的固定类型兼容读取；不放宽多态 validator，不改写缓存。实现阶段必须以修改前真实序列化器和合成数据证明格式，不能猜测 JSON；无法无损恢复时停止删除并记录偏差。状态：Round 2 复核通过；仅在计划层闭合，尚无实现验证。

父代理自检修正：原列表中两个不存在的厂商 FilterTest 改为新增统一 ThirdPartyLoginFilterConfigurerTest；Files that change 已展开为完整路径且使用粗体分组，适配 artifact-gate 的逐行解析。这两项不计为独立审阅发现或额外审阅轮数。

### Round 2

结果：NO_FINDINGS。实际累计审阅轮数：2；本轮只复核相对 Round 1 的变化。旧邮箱主体恢复缺口已在计划层闭合，仍须通过实施后的旧格式夹具、恢复/内省/撤销测试证明实际兼容性。审阅者未修改文件、未运行测试。

Plan review 状态：完成，等待工程师批准 plan.md；未自行批准工程计划，未开始源码实施。


## Stage 5 — Standard — Round 1

- Reviewed SHA: `0e24a881a3cacbd778979e9297c559af03b49eae`，base `8baee0a4cd132da935ed5aec46fdd61760589efe`，整个429文件差异；后续仅审阅记录元数据，不改变已审源码。
- Reviewer: iam_pr_review，fresh context，只读实际diff和固定SHA源码。REVIEW.md适用；Bugs、Security、Compliance三个独立pass，实际完成1轮，预算最多3轮，Nit上限5。
- Bugs: NO_FINDINGS。核对标准授权/退役刷新、邮箱认证、目录一致性、原子验证码、绑定事务、V25与Admin合同。
- Security: NO_FINDINGS。核对可信Client/CSRF顺序、来源/state/版本、隔离与消费、账号证明、MFA栅栏、精确序列化白名单、审计脱敏。
- Compliance: NO_FINDINGS。按项目凭据/个人信息、权限/租户、审计、迁移和可访问性控制核对；未发现额外适用监管/留存要求，不推定新要求。
- Findings: Important 0 / Minor 0 / Nit 0 / Pre-existing 0；Posted 0。
- Suppressed: 1项历史Plan缺口（旧邮箱主体兼容）已通过受限读取器及真实旧格式夹具闭合，不重复发布；本轮重复发现抑制0。
- Verifier: iam_pr_findings_verify，独立fresh context verify mode，幸存发现为空，逐项核验0，驳回0；未新增发现或重跑review。
- 两个slug共用此次完整代码审查，不重复累计轮数；没有让审查者或verifier批准合并或接受风险。

### Recurring

已扫描`.sdlc/*/review-findings.md`。本轮无新发现类别；未确认旧邮箱主体这个具体缺陷类别已在多个change重复，不把通用序列化/缓存主题视为重复缺陷。该历史缺口由LegacyEmailPrincipalReader的精确字段/类型限制、旧Jackson2/3夹具与恢复/内省/撤销测试编码。

### Remote publication checkpoint

用户已要求“推送合并”。本地提交和完整审查已完成，`git push`尚未执行成功：自动审批判定目标macula-projects/macula-cloud为公开仓库，要求明确确认将本次源码/配置/文档公开披露。未换接口绕过拒绝、未创建远端PR、未合并。待公开发布授权后，推送并发布包含上述证据和发现计数的PR，按实际分支保护执行。


## Stage 5 — Standard — Round 2：显式import增量

- Base `78624af241091d543eb121b09fd5f94537feb333`（PR #33已合并）；reviewed SHA `e4666f4099ee60fa768c055dbce1f4d2c22a5430`，8个IAM Java及计划/验证记录共10文件。后续仅补此审查记录，不改变已审源代码。
- Reviewer `iam_imports_review`，fresh context、只读实际固定SHA差异。REVIEW.md适用，Bugs、Security、Compliance均NO_FINDINGS：类型/泛型/方法引用/异常/接口/事务及注入注解解析相同，字符串及序列化标识不变，未改变权限、身份来源、事务隔离、凭据清除或审计行为。
- Important 0 / Minor 0 / Nit 0 / Pre-existing 0，发布0，重复发现抑制0；历史Plan邮箱兼容问题沿用原闭环，不重复计入本轮。
- Verifier `iam_imports_verify`，独立fresh-context verify mode：空发现输入，逐项核验0，驳回0，未新增发现或重新review。
- 本次实际1轮增量审查，Stage5累计2轮，未超过3轮；未由代理接受风险或批准合并。
- Recurring：本轮无发现类别，没有新增跨change重复缺陷；既有编码及测试保持。
- 人工授权：用户已明确YES同意向公开仓库macula-projects/macula-cloud发布，本轮再次明确要求“提交合并”。前轮公开披露检查点已解除；按实际分支保护合并，不绕过检查。不包含部署或分支删除。
