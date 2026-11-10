# Spec: IAM协议配置分区与接入自动适配 (from intent.md 2026-11-07)
Status: accepted

## Source intent
[已接受意图](intent.md)：Rain已明确Admin继续OIDC，不同协议配置分别保存。

## Requirements
1. 默认协议放顶部，单选候选仅包含已启用协议。关闭默认协议时自动选定剩余协议并提示；无启用协议则清空默认值，允许保存草稿但不能启用应用。
2. OIDC、OAuth2.1分别一个Tab，内部各有启用开关。切换Tab不改变默认协议或启用状态；关闭协议保留配置，再次启用重新校验。
3. 各协议分别保存认证方式、授权方式、回调、Origin、Scope、PKCE、授权确认、客户端JWKS认证参数及令牌策略；修改一个协议不改变另一个。应用Client ID、Secret及轮换、类型、租户/用户目录、身份源与托管登录方式仍属应用级，不复制身份或秘密材料；当前Client ID/Secret共享规则仅适用于OAuth家族，不要求未来CAS/SAML使用OAuth凭据。
4. 按应用类型与协议联动：公共应用固定none，禁止client_credentials/refresh_token；授权码固定S256；OIDC补齐必需openid/授权码，profile可选；纯OAuth不使用openid/profile，不要求ID Token。Macula直接Token扩展仅在OAuth2.1中按能力提供。自动移除已有选择时明确反馈；不能删除另一协议仍需使用的密钥。后台重复执行完整校验。
5. 每个Tab底部独立折叠端点。OIDC与OAuth分别获取真实openid-configuration、oauth-authorization-server；遵循各自带路径issuer的发现URL规则。真实共享端点可相同；OAuth不混列OIDC UserInfo/SSO退出，不伪造独立地址。可信来源、issuer校验、超时/大小限制保持，失败单独重试，不跨协议降级。
6. 快速开始与示例按所选协议自动匹配发现、Scope与响应处理，默认展示默认协议。OIDC包含nonce与ID Token验证；纯OAuth使用Access Token与约定业务接口，不冒充OIDC UserInfo。Admin自身保持现有OIDC流程、回调/会话/F12修复；不因默认协议改变而切换或降级。
7. IAM按原始请求语义选协议：openid对应OIDC，否则OAuth2.1；直接Token授权只取OAuth2.1。默认协议不偷偷改变请求Scope。授权码、授权记录、refresh绑定原协议；兑换/刷新重新校验当前启用状态及对应认证、grant、Scope、Origin和策略，不能借另一协议并集越权或缩小Scope转换协议。令牌格式/有效期与授权码策略取对应协议。
8. 保持事务、并发校验和提交后缓存驱逐；更新不能回写陈旧的另一协议。内省、撤销、UserInfo及原权限claims保持相应授权边界。预检可使用启用协议Origin并集，实际Token请求绑定本次协议。

9. 为未来CAS、SAML预留协议注册、配置类型、展示、端点与校验扩展点。本轮只注册实际支持的OIDC/OAuth2.1；CAS/SAML不出现可启用Tab或默认候选，不允许API伪造启用。协议通用层不强制要求Scope、grant、PKCE或ID Token。
## Non-goals
本轮不实现CAS/SAML，仅预留扩展点；不新增MFA/password/implicit；不扩展Admin为多协议登录SDK；不虚构业务Scope；不在失败后自动降级；不自动部署、提交或推送。

## Design
保留满幅详情及外层五分区；协议分区采用顶部默认协议和两个内层Tab，各有独立表单与能力联动。应用主记录保留共同身份和默认协议，协议子记录以应用内部ID+protocol唯一标识；启用状态和配置存于子记录，禁用不删除。
IAM从权威子记录构造本次协议的有效配置；RegisteredClient即使为框架装配聚合能力，也不能把并集当作某个协议的授权依据。授权码与refresh不得按新默认值重新选协议。元数据分别获取与缓存，示例自动适配；无需新增匿名应用配置引导接口。

协议扩展契约分别承载协议代码/可用性、类型化配置及版本、配置校验、UI展示与端点描述。通用层只管理应用归属、启停、默认和保存状态；OIDC/OAuth复用OAuth家族能力，不让所有协议继承OAuth字段。协议配置载荷采用按协议注册的类型化、带版本结构，持久化可用受限JSON载荷；服务端按白名单绑定、限制大小并校验，不接受任意配置透传。
未来CAS的service/service ticket校验与SAML的entityID/ACS/绑定/签名证书和XML元数据具有独立语义；此处只要求能容纳，不定义或实现完整协议。端点展示不能假设所有协议只有JSON Discovery；将来SAML元数据必须作为XML能力处理。公共平台地址来自服务器可信配置，不能为未来协议引入任意URL抓取或跳过签名验证。

## Data and interfaces
追加迁移，不修改已执行脚本。协议标识使用稳定代码与服务端支持列表，数据库不以仅包含两个值的枚举限制未来扩展；当前未知协议仍严格拒绝。Form/VO/System API改为独立协议对象，IAM/System/前端及Admin/Gateway初始化工具同步；新运行时不回退旧共享列，使用新缓存版本。删除应用时事务删除协议子记录及原关联。
单协议记录按明确协议精确迁移；双协议记录按能力校验拆分，不复制不适用的grant/Scope。无法形成合法配置的启用应用转DRAFT，保留原值恢复依据，不猜Scope或扩权；既有Admin OIDC、Gateway OAuth必须迁移后仍有效。实际切换需另行授权，不能与旧IAM混跑。

## Flagged concerns
- 同Client的协议隔离：认证/授权能力聚合可能放宽校验，须逐次绑定和验证原协议，non-blocking，纳入真实Security链测试。
- 凭据共享：认证选择与参数独立，但Client ID/Secret只有一份；轮换影响所有使用该密钥的协议，需界面明确，non-blocking，随本规格确认。
- 迁移影响：非法旧双协议可能转DRAFT，部署前须列出受影响记录并保留恢复依据，不能直接在业务库试迁移，non-blocking，随本规格确认该策略。
- OAuth业务Scope可能为空：授权码仍需真实业务Scope；提示登记并允许草稿，不悄悄取消现有约束，non-blocking。
- 治理依据：AGENTS.md、REVIEW.md、architecture/frontend-development/backend-development/testing/dependencies-release及前序已接受规格；未发现额外组织品牌/合规技能。本设计替代旧规格共享OAuth基础设置的部分，其余安全边界保留，non-blocking。

- 扩展边界：CAS/SAML本轮仅设计扩展点，不宣称协议已支持；不能借扩展载荷绕过类型校验或把未注册协议启用，non-blocking。

## Verification strategy
Vitest/Cypress验证独立编辑保存重载、Tab/启停/默认联动、草稿与非法组合、联动清理反馈、元数据错误/重试、协议端点/示例和390px；回归Admin真实OIDC登录、回调、F12、刷新与退出。
真实Security链验证同Client的认证/grant/Scope/Origin隔离、不同TTL/Token格式、OIDC nonce/ID Token及纯OAuth无ID Token；授权码/refresh原协议绑定、禁用、默认切换、伪造参数、并集绕过及旧缓存拒绝。
隔离MySQL/Redis验证追加迁移、Admin/Gateway保持、非法双协议转DRAFT、唯一键、事务回滚、并发保存/密钥轮换及提交后缓存；其他应用/身份源不误改。执行全仓Maven、前端单测/build/Cypress、Checkstyle/无fix ESLint/diff检查；区分Mock、隔离集成、真实部署及未做第三方真机证据。

扩展性验证还需覆盖：未知/CAS/SAML启用请求拒绝、默认协议仅来自真实已启用能力、类型/版本不匹配与超大载荷拒绝；检视通用接口未强制OAuth字段，不为未实现协议编造联调或安全验证结果。

## Acceptance
Rain以YES接受本规格及CAS/SAML扩展边界、全部non-blocking concerns。后续实施计划仍待独立评审与人工确认；未授权提交或部署。
