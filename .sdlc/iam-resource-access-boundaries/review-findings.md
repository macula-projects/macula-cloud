# Review findings: IAM单协议接入、Scope与资源访问边界优化

## Plan — Round 1
- Reviewer: sdlc-reviewer fallback；codex-plan-review 未安装，REVIEW.md 未指定 Stage 3 reviewer。
- Budget: Plan pass only，最多三轮；每轮仅本轮变化，已记录问题不重报，同类问题一次报告。实际本轮 1 次，无额外轮次；重复抑制 0。
- Reviewed plan SHA256: 11dc2fd490d1777a8db668c51142bacb20dd2c6ef36c91d6d2596f2778ec221c。因没有提交授权，以文档内容哈希和私有临时快照记录，不创建 Git commit。
- plan.md:97 — Important — 越权内省 active:false 无法同时保证有效错误资源一律 403 — 已修订为设计澄清：不可确认有效性返回 401，成功内省后路由授权不足返回 403；待 Rain 随计划明确确认，未静默修改已接受 spec。
- plan.md:98 — Minor — Servlet/Gateway 来源标识跨模块契约和 HMAC 来源缺项 — 增列 commons SecurityConstants.java、可信来源覆盖/冲突和签名/issuer验证、对应回归。
- plan.md:95 — Minor — 共享协议类变化可能破坏历史 V18 迁移 — 明确保留旧调用契约，新增空库全历史迁移和 V18→V19 两条真实数据库证明。

## Plan — Round 2
仅 Plan pass，仅审查 round 1 后修订。实际第 2 轮，无额外轮次；已记录三项未重复报告。结果：No findings。原 Important 的修订充分，错误分类澄清仍须 Rain 随计划明确接受；源码实施 gate 尚未通过。
Reviewed plan SHA256: f7f51007fa4a631955c2db39bdb5bbecec22ec38973deb4044388e4a84eadd32

## Plan — OIDC external_code amendment — round 1
- oidc-external-code-plan.md:13 — Important — 漏列RegistrationIdentityBindingService的OAUTH2_1硬编码，Token Provider测试Mock账号resolver可能掩盖真实拒绝 — 已补充该服务、单测、真实MySQL/Redis绑定IT，以及真实账号解析链覆盖。
- 另按连带影响核实：受管Admin的初始化器要求grant仅authorization_code，新增显式SDK能力后可能启动失败；计划补齐初始化器和幂等测试，同时保持受管Admin授权码入口。
- 本轮只做Plan审查，没有修改源码或自行接受实现计划。

## Plan — OIDC external_code amendment — round 2
- 仅审本轮修改及连带影响；NO_FINDINGS，0 Important/Minor。
- round1的真实绑定链遗漏已通过服务/单测/专用V19 MySQL与Redis真实账号resolver链证明补齐；稳定userId/可信租户失败关闭、受管Admin保留授权码并允许显式SDK能力无剩余计划问题。
- 实际2轮，不声称实现/测试或人类批准已完成；补充计划仍待Rain接受。
