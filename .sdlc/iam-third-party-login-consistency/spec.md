# Spec: IAM 第三方登录类型与路径一致性 (from intent.md 2026-10-19)
Status: accepted

## Source intent
[已接受的意图](./intent.md)：统一 IAM 中 GitHub、企业微信身份源类型与登录路径的引用，保持既有数据库值、外部 URL 和认证安全行为不变。

## Requirements
1. IAM 对当前支持登录的 GitHub、企业微信各保留一组明确对应的身份源类型值与登录路径；外部可观察值分别保持 `GITHUB`/`/login/github`、`WECOM`/`/login/wecom`，大小写与路径完全不变。
2. 两种身份源的 Filter、Configurer、Provider、登录页可见性判断、Security 匿名放行规则与登录页链接使用同一组类型/路径定义，不再各自维护运行时字符串副本。
3. IAM 与数据库、HTTP、会话 state、认证 token 等既有边界继续传递原有字符串值；本次不改变序列化格式、账号绑定查询、第三方平台登记的回调地址或任何外部请求参数。
4. GitHub 与企业微信的登录发起、回调、Provider 路由、`enabled`/`visible` 行为，以及 OAuth client、租户、用户类型、state、SavedRequest 和失败关闭规则保持不变。错误的 provider 不得被另一 Provider 接受。
5. 自动化验证固定两组外部类型/路径值，并覆盖实际渲染链接、匿名入口和回调、Provider 选择与错误 provider 拒绝；现有表单、短信、小程序和 OAuth/OIDC 流程不得回归。

## Non-goals
- 不新增第三方身份源，也不把 System 目录中尚未接入 IAM 登录的支付宝、微信、飞书、钉钉加入 IAM 枚举。
- 不修改数据库结构、既有数据、System 身份源管理 API、`macula-cloud-api`、Gateway、管理端或部署配置。
- 不改第三方 OAuth/企业微信协议实现、账号绑定与用户权限路由，也不将字符串持久化字段整体迁移为 Java 枚举。

## Design
约束来源：项目根 `AGENTS.md`、`.agents/rules/architecture.md`、`backend-development.md`、`testing.md`、`dependencies-release.md`、`REVIEW.md`，以及已接受的 IAM 第三方认证 spec。现有可用技能中未发现额外的组织级品牌、合规或数据处理技能；`bands.yaml` 仅约束部署后监控，不改变本次设计。

在 `dev.macula.cloud.iam.enums` 放置仅描述当前 IAM 第三方登录源的枚举，显式声明 GitHub、企业微信各自的稳定 `providerType` 和 `loginPath`。不根据枚举名、大小写转换或请求数据推导外部值。两个具体 Filter 和 Configurer 使用该定义配置匹配路径；两个 Provider 使用对应类型值校验 token 与读取配置。LoginController 使用对应类型值判断可见性，并向 Thymeleaf 提供对应路径；登录模板保留现有 `client_id` 参数语义。默认 SecurityFilterChain 的匿名放行路径也使用同一来源。

公共 Filter、认证 token、state、实体和配置服务保留既有字符串边界，避免改变持久化或会话契约。登录回调仍由受信任 issuer 加上对应路径构造，不采用请求 Host。System 的 `IdentityProviderType` 是独立服务的六类管理目录；当前 `callbackUrl()` 已产生相同两条路径，不为了复用而让 IAM 依赖 System 实现。本次以 IAM 对外值的独立断言和 System 现有实现核对兼容性，不宣称建立跨服务的共享类型。

## Data and interfaces
- 浏览器接口保持 `GET /login/github`、`GET /login/wecom`；登录页链接仍携带现有 `client_id`，仅在满足原有有效客户端及 `enabled`/`visible` 条件时展示。
- `sys_identity_provider.provider_type`、`sys_user_identity_binding.provider_type` 继续使用原字符串；不增加 Flyway 迁移或配置键。
- IAM 与 System、Gateway、管理端之间无新增或变更的 REST/Feign 契约；System 目录对外的 callback URL 仍为原值。
- IAM README 说明两条稳定类型/路径及其内部单一映射约束，不改变部署登记要求。

## Flagged concerns
- System 仍有独立的六类身份源目录：它与 IAM 的两类登录枚举不能直接共享服务实现；当前两组 code/callback URL 一致，设计选择不改 System，但未来扩展可能漂移，需由 Reviewer 确认本次 IAM-only 范围，non-blocking。
- Thymeleaf 链接改为由 IAM 提供路径：模板 URL 表达式必须继续正确处理 `client_id`、上下文路径与编码；需要渲染测试证明，不可仅靠字符串搜索，non-blocking。
- 认证链属于高风险路径：即使只是值提取，Filter 匹配和匿名放行不一致也会中断登录或误开放入口；必须覆盖允许与拒绝路径并保留回调的 state 校验，non-blocking。
- 意图中是否存在其他必须解决的问题仍未得到单独回答：当前已批准的主要目标足以限定本次设计；如提出额外目标，需回到意图/规格重新确认，non-blocking。

## Verification strategy
1. 枚举单元测试逐字断言两组 `providerType` 和 `loginPath`，不从实现反推期望值；同时检查枚举仅含本期两种 IAM 登录源。
2. 扩充 IAM Provider 单测，验证各自接受正确类型、拒绝另一类型，并保留配置/绑定查询的原字符串参数断言。
3. 扩充 `IamProtocolTest` 或等价的 Security/MockMvc 测试，验证匿名 GET 登录发起与回调仍命中对应 Filter、登录页实际渲染链接及 `client_id` 参数、隐藏/禁用时行为、受信 issuer 构造的精确回调，并回归表单/短信/小程序/OAuth/OIDC 关键路径。
4. 运行 IAM 及依赖模块的定向测试 `mvn -pl macula-cloud-iam -am test -Plocal`；若环境服务缺失，区分环境失败与代码失败。静态核对 System 的 `IdentityProviderType.callbackUrl()` 对当前两类仍生成原路径；跨模块源码确有变更时再扩大测试范围。
5. 检查 `git diff --check` 和受影响路径的引用搜索，确认无外部值、schema、配置、API、部署材料的意外变化；记录未验证的真实第三方平台往返。
