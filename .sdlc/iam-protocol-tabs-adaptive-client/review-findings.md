# Plan review: IAM协议分区与接入适配

## Setup
按sdlc-build强制执行独立Plan评审。REVIEW.md未配置Stage3专用reviewer，未发现codex-plan-review可调用工具，使用sdlc-reviewer fallback代理review_adaptive_protocol_plan。总预算最多3轮，仅Plan，后续只看修订及连带影响，不重复已记录缺陷。

## Plan Round 1
实际1轮，无测试/应用源码修改。
- Important：plan.md:125 — 授权记录恢复可能findByToken→请求仓库findById→findByToken递归。Fix：基础仓库显式Qualifier隔离，不解析请求不回查授权；恢复后才构造协议视图，并补真实Redis恢复链验证。
- Important：plan.md:127 — 双协议Client管理端点缺少无歧义的认证配置选择，尤其同private_key_jwt不同JWKS。Fix：固定合法机密OAuth2.1优先、合法机密OIDC其次、否则拒绝；不读默认/目标Token/未验证载荷，不尝试另一配置；只读说明及真实跨协议内省/撤销测试。
- 同时澄清关闭协议保留的配置仍可能依赖共享Secret，不能因只统计启用协议而删除。
- 以上修订待第2轮复核，plan仍draft。

## Plan Round 2
实际第2轮，仅审Round1修订及连带影响，累计2轮，未超3轮预算。Result: NO_FINDINGS。独立评审确认授权恢复隔离和管理端点固定认证选择已落实，未发现剩余实质歧义。无源码修改或测试执行；计划仍待Rain人工确认，不代表实现/安全源码评审完成。
