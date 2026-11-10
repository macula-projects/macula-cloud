# Spec: AppLink回调由接入代码处理
Status: accepted
Author: Rain

## Source intent
沿用已接受intent.md的身份源与应用接入范围。Rain明确纠正：飞书回调不应在身份源填写，应在代码处理。本增量取代上一轮仅修改字段提示的方案；原spec/plan其他已接受范围不变。

## Requirements
1. 身份源页面删除AppLink回调输入及必填条件；System保存、IAM加载及能力选择不再因为来源缺少oauthRedirectUri而拒绝AppLink能力。
2. 业务App代码决定授权时使用的redirect_uri，并在飞书后台登记。提交FEISHU_APP_CODE的external_code请求时，用专用provider_redirect_uri传递同一地址及provider_code_verifier；不混用IAM授权码流程的redirect_uri和code_verifier。
3. IAM仅允许FEISHU_APP_CODE接收此参数，限制单值、长度及合法地址；按已认证IAM Client现有登记回调集合精确匹配，拒绝通配、前缀匹配和其他Client的地址。飞书登记由平台校验，IAM不能声称自行读取过后台配置。
4. 校验完成后将原地址与专用PKCE证明传入飞书v3换码请求；不静默改写地址，不使用请求Host推导业务App回调，不让参数改变平台端点或身份源。
5. 网页OAuth仍由IAM代码生成自身回调；JSAPI不接收AppLink回调或PKCE参数。已有反重放、固定来源、应用类型、账号绑定及组织准入保持。

## Non-goals
不新增身份源回调配置或独立Token接口，不移除PKCE，多条绑定按同期已接受multi-binding-spec.md独立落实，不改变其规则，不自动修改飞书后台或业务App。

## Design
Admin删除输入与对应校验，接入指南给出App代码参数示例。System移除来源回调必填；IAM converter/provider负责专用参数及Client白名单验证，Feishu verifier/client接收通过校验的回调，禁止从来源配置回退。其他平台适配器默认拒绝专用参数。

## Data and interfaces
external_code增加provider_redirect_uri条件参数。旧oauth_redirect_uri列暂保留以避免破坏旧数据，但不再作为AppLink有效来源；旧调用未传新参数返回明确invalid_request，需同步升级调用方。无需数据迁移或清理凭据。Client沿用现有协议配置中的回调白名单，不新增身份源输入。

## Flagged concerns
- 原已接受计划明确固定来源回调；本增量涉及Token请求及可信输入边界，必须经规格/计划接受后实施，不能仅删页面掩盖运行失败：[blocking]。
- 业务App回调须在飞书后台登记，并匹配IAM该Client已有回调白名单；这两处登记不等同于要求在身份源重复填写。需接受此校验及旧调用升级要求：[blocking]。
- 适用AGENTS.md及架构、后端、前端、测试规则；未发现额外组织策略技能。真机回调需接入方环境验证：[non-blocking]。

## Verification strategy
验证不填写来源回调可保存/选择能力；请求参数缺失、重复、非法、跨Client及跨平台均拒绝；正确AppLink地址和PKCE精确传入厂商换码；网页和JSAPI不受影响。执行converter/provider/verifier/client和System单测、前端组件/浏览器回归与构建，更新相关README和接入示例。Mock不替代真机测试。


2026-11-10 接受记录：Rain在回调增量方案展示后回复“YES，再加上 身份绑定是可以多个的”，明确接受本回调与多条绑定增量。多条绑定采用任一命中且所有命中一致，冲突转账号证明。此确认接受规格，不自批尚未展示的实施计划。原blocking规格决策已解决；计划接受门仍待后续人工确认。
