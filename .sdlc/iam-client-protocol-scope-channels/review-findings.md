# Review findings: IAM协议、Scope与具体身份源类型

## Plan review setup
- Pass: Plan only，预算最多3轮；第一轮全量，后续只看修订，不重复报告已记录同类缺陷。
- REVIEW.md未指定Stage3 reviewer；无codex-plan-review已安装技能，使用sdlc-build要求的独立sdlc-reviewer fallback。
- spec已由Rain接受，plan仍draft。当前只写设计/计划文档，尚无本轮源码修改。

## Plan Round 1 — 2026-11-06
- 独立fallback reviewer：review_client_plan，仅Plan pass，实际1轮，预算最多3轮。
- Important：plan.md:131 — 现代公共扩展允许refresh且默认reuse=true，缺少重放保护；框架公共授权码当前不签发refresh，不能仅改配置宣称支持。
- Fix：逐grant明确能力矩阵；显式现代公共客户端禁止refresh grant和签发，System/IAM/模板同策，真实Token测试验证code/sms/email/external_code无refresh；机密授权码保留真实refresh，现代模板不复用，legacy原行为不自动改写。未引入不完整轮换机制。
- Proof：新增现代公共非法组合与所有实际响应无refresh、机密授权码实际刷新及legacy刷新回归；明确转换差异与SSO续登说明。
- 修订中计划仍draft；未写源码。

## Plan Round 2 — 2026-11-06
- 仅审Round 1修订及连带影响，未重复既有问题，未执行源码Bugs/Security/Compliance passes。
- Result: NO_FINDINGS。
- 本次实际1轮，总计2轮，预算最多3轮。
- Human gate: 规格已由Rain以“对”接受；待Rain确认plan.md及两轮评审结果，计划仍draft，未修改本轮源码。

## Scope revision — 2026-11-06
- Rain取消历史兼容，并新增身份源自动绑定开关、平台可用匹配依据及手动绑定页面。
- spec.md修订为draft；原计划标记superseded，前两轮Plan审查仅作历史记录，不适用于修订后的范围。
- 修订规格接受后重新编写和评审计划；尚未按修订范围修改源码。

## Revised scope acceptance — 2026-11-06
- Rain接受spec revision2，并明确企业身份源旧自动绑定约定作废，所有来源统一在身份源界面显式配置。
- 新计划Status:draft，重新启动Plan审查预算最多3轮；旧2轮不适用于本轮范围。

## Revised Plan Round 1 — 2026-11-06
- 独立fallback reviewer: review_client_plan，Plan pass，实际1轮，新预算最多3轮。
- Important — 绑定证明若依赖Client托管登录开关，仅第三方应用首次绑定无可用方式。Fix：保留现有密码/短信/会话归属证明，独立于托管按钮开关，仅有效已核验pending可使用；证明不单独签发Token，SDK不创建SSO；明确供人工接受该决策。
- Important — SDK绑定页无SavedRequest，原/login短信接口不能取得票据Client。Fix：在已列ThirdPartyBindingController增加pending限定的绑定专属发码，按原Client及绑定专用用途码桶发送/消费，模板同步；新增无SavedRequest成功和跨Client/用途拒绝证据。
- 尚未修改源码，计划保持draft。

## Revised Plan Round 2 — 2026-11-06
- 仅审修订及连带影响，实际1轮，新版累计2轮。前轮2项已解决。
- Important — SmsVerificationCodeStore仍只支持clientId，服务层显式用途无法落到实际存储。Fix：补存储实现/测试精确路径，issue/activate/consume增加显式用途，Redis key及HMAC摘要绑定用途/Client；覆盖真实Redis跨用途拒绝及共享手机号/IP限流。
- 计划仍draft，未写源码。

## Revised Plan Round 3 — 2026-11-06
- 仅审存储用途契约修订及连带影响，实际1轮，新版累计3轮，达到预算。
- Result: NO_FINDINGS。前轮Important已落实到计划和Proof。此结论仅为Plan评审，不代表实现/测试完成。
- Human gate: spec accepted，plan draft，等待Rain接受实施顺序、文件清单、绑定证明独立策略及风险；本轮未修改源码。

## UI / multiple-protocol Plan Round 1 — 2026-11-06
- 新规格已由Rain以YES接受；本轮计划draft，新审查预算最多3轮。REVIEW.md未指定Stage3 reviewer，未发现codex-plan-review工具/技能，按sdlc-build使用独立sdlc-reviewer fallback：review_ui_multi_protocol_plan。仅Plan pass，实际1轮。
- Important — plan.md:84：纯OIDC可能配置client_credentials或external_code，但Token运行时必拒绝，形成可启用而不可使用的配置。
- Fix：System/协议选项/来源添加器统一grant与协议矩阵；client_credentials及sms/email/external_code直接Token仅OAuth2.1，托管短信经code仍可OIDC；OIDC-only禁止这些grant/SDK能力，双启用支持；有依赖时关闭OAuth2.1明确拒绝且不静默删除。示例禁止扩展请求携带openid/profile。补相应配置及实际Token回归。
- 源码未修改，计划待复审与人工接受。

## UI / multiple-protocol Plan Round 2 — 2026-11-06
- 独立review_ui_multi_protocol_plan仅检查Round1修订及连带影响，实际1轮，本轮累计2轮，预算最多3轮。
- Result: NO_FINDINGS。前轮协议/grant矩阵问题已解决。
- 此结论仅证明Plan复审；未进行源码Bugs/Security/Compliance审查，未修改本轮应用源码或执行功能测试。
- Human gate: spec accepted，plan draft，待Rain接受具体文件、实施顺序、风险和Proof。
