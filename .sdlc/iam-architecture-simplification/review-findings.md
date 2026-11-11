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
