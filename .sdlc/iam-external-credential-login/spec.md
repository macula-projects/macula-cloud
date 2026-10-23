# Spec: IAM 外部凭证登录 (from intent.md 2026-10-22)
Status: accepted.
Author: Rain.

## Source intent
[已接受的 IAM 外部凭证登录 intent](./intent.md)：IAM 只接收并核验调用方已取得的第三方短期凭证，在 OAuth Client 边界内定位现有业务员账号并颁发 Macula Token；客户端 SDK 不在范围内。

## Governing policies

- 根 `AGENTS.md`：认证、授权、租户、OAuth Client 和身份源是跨服务安全契约；不得覆盖用户未提交改动，不得泄露凭据，数据库及管理端变化必须同步。
- `.agents/rules/architecture.md`：IAM 负责认证授权，System 负责管理与共享表迁移；IAM、System、Gateway、System API 和 Admin 调用链必须一起核对。
- `.agents/rules/backend-development.md`：OAuth 2.1、grant 和安全过滤链属于高风险变更；默认拒绝不可信身份和租户参数，Service 承载事务规则，使用常量或枚举代替协议字符串。
- `.agents/rules/testing.md`：认证变更必须覆盖允许与拒绝路径；外部 HTTP、Redis 和 MySQL 证据与单元测试分开报告。
- `.agents/rules/dependencies-release.md`：新依赖放在最窄模块；System 拥有 Flyway；不提交真实 AK/SK、App Secret、Token 或环境地址。
- 根 `REVIEW.md`：实现评审必须分别执行 Bugs、Security、Compliance 三个 pass，Important 问题在修复或负责人明确接受前阻塞合并。
- 根 `bands.yaml`：当前只有占位 SLO，没有可用于本变更的已批准绝对阈值；本规格不虚构性能验收线。

## Requirements

1. IAM 必须在现有 `/oauth2/token` 上提供业务名称为 `external_code`、协议值为 `urn:macula:params:oauth:grant-type:external_code` 的独立扩展 grant，接收第三方短期 code 并返回现有格式的 access token；首期不得为该 grant 签发 refresh token，也不得把该流程伪装成标准 OIDC 登录。
2. 扩展 grant 首期只允许客户端认证方式为 `none` 且 `ClientSettings.requireProofKey=true` 的公共 OAuth Client。`requireProofKey` 继续强制该 Client 的标准 authorization_code 流程使用 S256 PKCE，但 `external_code` 没有 IAM authorization code，不能宣称自身受到 PKCE 保护；它是端侧已取得第三方一次性 code 后的一步登录例外。同一个 APP 或微信小程序 Client 可以同时启用 `authorization_code` 与 `external_code`：前者进入 IAM 认证闭环并经精确 HTTPS redirect URI 返回，后者直接消费端侧已取得的第三方 code，两类 code 不得混用。
3. 每个 OAuth Client 必须同时显式启用扩展 grant，并配置非空的允许外部 code type 集合；任一条件不满足均返回 `unauthorized_client`。旧 Client 的集合默认为空，因此升级后不会自动获得第三方登录能力。
4. 外部 code 类型必须由 `ExternalCodeType` 枚举表达并映射到固定 provider、固定凭证语义和唯一服务端校验器；协议参数名固定为 `code_type`，code 值使用标准 `code` 参数并在代码中引用 `OAuth2ParameterNames.CODE`。请求不得提交第三方 URL、App ID、密钥、租户、组织、用户标识或换码模式覆盖配置。
5. 首期必须分别覆盖原生 APP、小程序、协作客户端内嵌网页和普通浏览器网页四类来源。即使最终使用相同第三方接口，不同来源也使用独立 `ExternalCodeType`，以便 OAuth Client 精确授权、选择固定应用配置并独立审计；禁止按失败结果猜测来源或依次尝试多个端点。
6. 飞书成功结果必须包含非空 `employee_no`；钉钉必须解析为配置组织内的成员 `userId`；企业微信必须解析为配置企业和应用内的成员 `userid`（小写 `i`）；微信普通登录和小程序登录必须得到 `unionid`，缺失时不得降级到 `openid`。
7. 微信小程序登录 code 与手机号 code 必须独立校验：前者只产生微信 `unionid` 身份，后者只产生服务端核验的手机号。两种凭证不得互相代替，也不得要求调用方同时提交。
8. 阿里云号码认证 Token 必须由 IAM 服务端调用官方取号接口得到手机号；请求携带的手机号、掩码号或运营商信息不得参与账号认证。
9. 已核验的 provider subject 必须先查找 `sys_user_identity_binding`。已绑定时仍须按当前 OAuth Client 的 `tenantId` 和 `userType` 重新加载并校验用户、状态和权限；不能仅凭全局 username 直接颁发 Token。
10. 未绑定的飞书、钉钉、企业微信或微信身份必须返回受控的 `binding_required` 协议结果和一次性 opaque binding ticket，随后复用现有绑定业务规则完成绑定；不得静默注册、按昵称或资料相似度绑定，也不得把 provider subject 返回给调用方。
11. 微信小程序手机号 code 或阿里云号码认证 Token 得到的手机号应先在有效本地用户中唯一匹配。唯一匹配且通过 Client 用户路由时可以登录；零匹配时返回手机号 binding ticket；多匹配、字段异常、账号禁用或 Client 用户路由不允许时失败关闭。
12. 协议层绑定不得依赖 HTTP Session。调用方必须先通过同一个 OAuth Client 的本地用户认证获得 access token，再携带该 Token 和 binding ticket 调用绑定接口；只有用户型本地 grant 产生的 Token 才能作为账号归属证明。
13. 外部身份绑定必须原子维护现有 `(provider_type, provider_subject)` 与 `(provider_type, username)` 唯一性。手机号绑定只允许给当前手机号为空或已等于待绑定手机号的账号设置手机号；不得覆盖另一个非空手机号。
14. binding ticket 必须随机、不可预测、服务端存储、最长五分钟有效、限制失败次数、与 `clientId`、`tenantId`、`userType`、绑定类型及已核验身份绑定，并以原子操作消费。Ticket 不得包含可由客户端解码的 subject 或手机号。
15. IAM 必须对外部 code 执行并发安全的防重放控制。Redis 只能保存带服务端密钥的 code 摘要和短期状态，不得保存原始 code、Token、手机号或第三方响应。
16. 本地 password grant 不增加 `identifier_type`，继续只接收现有 `username` 和 `password` 参数。IAM 必须在服务端将 `username` 输入同时作为用户名、规范化手机号和规范化邮箱候选进行解析：零命中统一认证失败，只命中一个不同用户时校验该用户密码，命中多个不同用户时失败关闭；不得用客户端参数或固定优先级选择其中一个账号。解析到唯一用户后再按 OAuth Client 用户路由加载用户。
    端侧向用户提供账号密码登录时必须进入 `/oauth2/authorize` 浏览器认证闭环，并使用 authorization_code + S256 PKCE 返回端侧；不得把 password grant 作为新的端侧登录接入方式。现有 password grant 仅为兼容已有受支持调用方保留。
17. 现有 SMS grant 必须修复为按手机号唯一解析并按 Client 路由用户；`OAuth2ResourceOwnerSmsAuthenticationProvider` 继续只负责协议参数到认证 Token 的适配，手机号解析、唯一性校验和 Client 用户路由不得散落在 grant Provider 中。新增邮件验证码 grant，按邮箱唯一解析用户。短信和邮件验证码均必须一次性消费、限时、限次、限流，并使用通用失败响应避免暴露账号是否存在。
18. 短信和邮件验证码申请接口属于 IAM 内部认证能力。调用方必须提供已登记的公共 OAuth Client ID，Client 必须是 `none + requireProofKey=true` 并启用对应本地 grant；接口统一返回已接受结果，通过 CAPTCHA、Origin、IP/Client/目标限流控制滥用，不向调用方泄露手机号或邮箱是否存在、用户状态或发送供应商原始错误。浏览器预检不包含自定义 header 的具体值，因此 OPTIONS 只验证 Origin 至少属于一个 Client 及必要 header 声明；实际 POST 必须严格核对 Origin、单值 `X-OAuth-Client-Id` 与 body `client_id` 属于同一 Client。
19. `sys_user.username`、`mobile`、`email` 都是全局唯一登录标识：字段为 `NULL` 时可以存在多条，字段非空时必须由数据库唯一约束和服务层校验共同保证唯一；空字符串或仅空白内容必须在写入前规范化为 `NULL`。字段重复、格式无效、用户被删除或禁用、或不属于 Client 配置的租户/用户类型时均不得登录。身份比较必须使用服务端统一规范化规则，不能由调用方选择大小写或格式规则。
20. 扩展 grant 只能签发 Client 已注册 scope 的子集，并沿用该 Client 的 access token 格式、TTL 和撤销策略。首期不为该 grant 签发 refresh token 或 OIDC ID Token；请求 `openid` 时返回 `invalid_scope`。
21. 提供商禁用、配置缺失、code type 未允许、换码失败、超时、非成功 HTTP、3xx、畸形响应、身份字段缺失或绑定冲突都必须失败关闭；不得回退到其他 provider、其他换码模式、本地资料猜测或较弱身份字段。
22. 日志只能记录 provider、code type、本地 stage、HTTP 状态、允许的数值错误码、本地失败类别、Client ID 的不可逆摘要和异常类名；禁止记录 code、Token、binding ticket、手机号、邮箱、subject、第三方响应、URI 查询串、异常消息或堆栈。
23. System 和 Admin 必须允许管理员为 OAuth Client 选择允许的外部 code type，并明确显示“未配置即禁止全部第三方 code”；服务端必须验证枚举、grant、客户端认证方式和身份源配置的一致性，不能只依赖前端校验。
24. 升级必须保持现有 `/oauth2/authorize`、浏览器登录、GitHub/企业微信绑定、authorization_code + PKCE、password、SMS、refresh token、OIDC 和旧 OAuth Client 行为兼容；本变更不得覆盖当前网页认证实现。

## Non-goals

- 不集成或管理 App SDK、JSSDK、原生 App、业务网页、小程序前端、Universal Link、URL Scheme 或客户端回调。
- 不把 `external_code` 描述成 PKCE grant，也不接受同一次请求携带的 `code_challenge` / `code_verifier` 作为安全证明；没有预先绑定时这种参数组合不构成 PKCE。
- 不自动创建用户、自动补齐用户资料、自动跨租户匹配、用 `openid` 替代 `unionid`，也不按第三方昵称、邮箱或手机号相似度推断绑定。
- 不改变 `/oauth2/authorize` 的 Client 直达第三方登录策略；该策略属于独立的浏览器授权入口设计。
- 不把自定义 grant 宣称为 OAuth 或 OIDC 标准 grant，不为其签发 ID Token。
- 不在本期为 `MEMBER`、`ODC`、`SUPPLIER` 等非 `SysUser` 身份源强行实现手机号或邮箱查询；没有明确解析器的用户类型必须失败关闭。
- 不修改 Gateway 的 Token 校验语义、资源服务器权限模型或业务服务 API。

## Design

### 1. 协议入口和 Token 生成

在 Spring Authorization Server 的 Token Endpoint 中按官方扩展 grant 方式注册独立的 `AuthenticationConverter`、认证 Token 和 `AuthenticationProvider`。实现不得继承当前会把授权记录硬编码为 `password` 的 `OAuth2ResourceOwnerBaseAuthenticationProvider`；应共享小粒度的 scope 校验和 Token 生成组件，并始终把授权记录的 grant type 保存为真实的 `urn:macula:params:oauth:grant-type:external_code`。

请求参数如下，标准参数优先使用 `OAuth2ParameterNames`，扩展参数集中放在常量类中：

```text
grant_type=urn:macula:params:oauth:grant-type:external_code
client_id=<公共OAuth Client ID>
code_type=<ExternalCodeType稳定值>
code=<第三方短期code或Token>
scope=<空格分隔的scope>
```

`code_type` 和 `code` 必须各出现一次，去除首尾空白后非空并受长度上限约束；阿里云号码认证 SDK 返回的短期 Token 也作为一次性外部 code 放入 `code`，由 `ALIYUN_MOBILE_TOKEN` 明确其语义。未知参数不得改变 provider 选择、应用凭据或用户路由。Provider 的处理顺序为：验证 Client → 验证 grant 与 code type allowlist → 验证 scope → 原子占用 code 摘要 → 服务端换码 → 解析可信身份 → 查绑定/账号 → 按 Client 路由加载用户 → 使用真实 grant type 生成并保存 Token。

扩展 grant 的协议实现参考 Spring Authorization Server 官方扩展 grant 指南：<https://docs.spring.io/spring-authorization-server/reference/guides/how-to-ext-grant-type.html>。

### 2. 外部 code type 和提供商校验器

新增 `ExternalCodeType` 枚举，每个值至少携带 `providerType`、来源场景、code 类别、绑定结果类别和所需配置能力。首期验证矩阵如下：

| 场景 | `ExternalCodeType` | 端侧来源及传入 `code` | IAM 服务端验证 | 可信身份键 | 本地处理 |
|---|---|---|---|---|---|
| 原生 APP | `FEISHU_APP_CODE` | 飞书授权登录 SDK 返回的 code；AppLink 仅在该接入方案中承担唤起或回跳时使用 | 使用该 Client 固定绑定的飞书移动应用配置换码并读取用户资料 | `FEISHU + employee_no` | 查外部身份绑定；未绑定返回 binding ticket |
| 原生 APP | `WECOM_APP_CODE` | 企业微信 SDK 返回的 code | 使用固定企业及应用配置换取企业成员身份 | `WECOM + userid` | 查外部身份绑定；未绑定返回 binding ticket |
| 原生 APP | `DINGTALK_APP_CODE` | 钉钉授权登录 SDK 返回的 code | 使用固定钉钉应用及组织配置换码并解析组织成员 | `DINGTALK + userId` | 查外部身份绑定；未绑定返回 binding ticket |
| 原生 APP | `WECHAT_OPEN_APP_CODE` | 微信开放平台移动应用 SDK 返回的 code | 使用固定移动应用 AppID/Secret 换码 | `WECHAT + unionid` | 缺少 `unionid` 失败；未绑定返回 binding ticket |
| 原生 APP | `ALIYUN_MOBILE_TOKEN` | 阿里云号码认证 SDK 返回的取号 Token | IAM 调用阿里云服务端取号接口 | 规范化手机号 | 唯一匹配即登录；无匹配返回手机号 binding ticket，不创建第三方身份绑定 |
| 微信小程序 | `WECHAT_MINIAPP_LOGIN_CODE` | `wx.login` 返回的 code | 使用固定小程序 AppID/Secret 调用服务端登录换码 | `WECHAT + unionid` | 缺少 `unionid` 失败；未绑定返回 binding ticket |
| 微信小程序 | `WECHAT_MINIAPP_PHONE_CODE` | 手机号授权能力返回的独立 phone code | 使用固定小程序配置调用服务端手机号接口 | 规范化手机号 | 唯一匹配即登录；无匹配返回手机号 binding ticket；不要求也不补绑 `unionid` |
| 企业微信小程序 | `WECOM_MINIAPP_CODE` | 关联到企业自建应用的小程序通过 `wx.qy.login()` 返回的 code | 使用 Client 固定的企业自建应用 `access_token` 调用 `miniprogram/jscode2session` | `WECOM + userid` | 只认小写 `userid`；未绑定返回 binding ticket；不返回或持久化 `session_key` |
| 内嵌网页 | `WECOM_WEBVIEW_CODE` | 企业微信内网页通过 JSSDK / OAuth 得到的 code | 使用固定企业及应用配置换取成员身份 | `WECOM + userid` | 查外部身份绑定；未绑定返回 binding ticket |
| 内嵌网页 | `FEISHU_WEBVIEW_CODE` | 飞书内网页通过 JSSDK 得到的 code | 使用固定飞书网页应用配置换码并读取用户资料 | `FEISHU + employee_no` | 查外部身份绑定；未绑定返回 binding ticket |
| 内嵌网页 | `DINGTALK_WEBVIEW_CODE` | 钉钉内网页通过 JSSDK 得到的 authCode | 使用固定钉钉应用及组织配置换取成员身份 | `DINGTALK + userId` | 查外部身份绑定；未绑定返回 binding ticket |
| 浏览器网页 | `FEISHU_BROWSER_CODE` | 飞书 OAuth / 扫码登录回调 code | 使用固定飞书网页应用配置和已登记回调地址换码 | `FEISHU + employee_no` | 查外部身份绑定；未绑定返回 binding ticket |
| 浏览器网页 | `DINGTALK_BROWSER_CODE` | 钉钉 OAuth / 扫码登录回调 code | 换取用户身份后解析为目标组织成员 | `DINGTALK + userId` | 查外部身份绑定；未绑定返回 binding ticket |
| 浏览器网页 | `WECOM_BROWSER_CODE` | 企业微信网页 / 扫码登录回调 code | 使用固定企业及应用配置换取成员身份 | `WECOM + userid` | 查外部身份绑定；未绑定返回 binding ticket |
| 浏览器网页 | `WECHAT_OPEN_BROWSER_CODE` | 微信开放平台网站应用扫码登录回调 code | 使用固定网站应用 AppID/Secret 换码 | `WECHAT + unionid` | 缺少 `unionid` 失败；未绑定返回 binding ticket |

每个平台继续在自身包内实现换码、响应校验和主体提取；公共 registry 只按 `ExternalCodeType` 查找唯一 verifier，不包含 provider 条件分支。不同 code type 可以在确认官方合同完全一致后共享底层 verifier，但不能合并 Client allowlist、应用配置选择或审计语义。

微信公众号内网页不定义 `ExternalCodeType`：业务网页先进入 IAM `/oauth2/authorize`，IAM 在浏览器 Session 认证链中根据已验证的 OAuth Client 策略和微信容器提示自动发起公众号网页 OAuth，回调后恢复原始 authorization request 并签发受 PKCE 保护的 Macula authorization code。该自适应 `/authorize` 路由属于独立变更；本规格只确保企微、飞书、钉钉内嵌 WebView 的 JSSDK code 可以直接进入 `external_code` grant。

所有 verifier 使用固定 HTTPS 端点、连接/读取超时、禁用自动跟随 3xx、最小响应 DTO 和白名单日志。身份源配置来自 System 管理的唯一启用配置；微信增加移动应用和小程序的公开 App ID/秘密字段，阿里云号码认证增加独立 provider 类型及服务端 AK/SK 等必要配置。真实字段以实现时复核的官方服务端合同为准，不能让请求提供。

企业微信小程序首期只支持关联到企业自建应用的模式，复用该 registration 的 `corpId` 与 `appSecret`；服务商第三方小程序使用 `/cgi-bin/service/miniprogram/jscode2session` 和 `suite_access_token`，属于不同的应用注册、授权与租户模型，不得使用 `WECOM_MINIAPP_CODE` 猜测或回退调用。

### 3. Client 策略

`sys_oauth2_client` 新增 `external_code_types`，保存规范化、排序、逗号分隔的枚举值。IAM 读取后写入 `RegisteredClient.getClientSettings()` 的受控自定义 setting，Token Provider 只从已认证的 `RegisteredClient` 读取，不重新信任请求中的 `client_id`。

System 保存时执行以下一致性校验：

- 集合为空：禁止 `external_code` grant；已有本地 grant 不受影响。
- 集合非空：必须包含 `external_code` grant，客户端认证方式必须是 `none`，并且 `requireProofKey` 必须为 `true`。
- 每个类型必须是已知枚举并有对应启用身份源配置能力；运行时配置随后被禁用时，登录仍失败关闭。
- 公共 Client 的 authorization_code 流程继续只允许 S256 PKCE；`external_code` 是明确配置的一步登录例外，不接收无意义的 PKCE 参数，也不签发 refresh token。

Admin 使用分组多选框展示 provider 和 code 语义，不允许自由输入协议字符串；列表显示外部 code 能力摘要，不显示任何秘密。

### 4. 身份解析与 Client 用户路由

新增 Client-aware 的用户解析服务，以已认证 `RegisteredClient` 快照中的 `clientId`、`tenantId`、`userType` 为边界。解析外部绑定得到 username 后，必须调用现有 `OAuthClientUserDetailsService.loadUserByClient(...)` 或等价服务重新加载权限，不复用只按全局 username 构造主体的浏览器辅助服务。

本地密码身份解析不接受调用方指定类型。服务端对同一个 `username` 输入执行以下候选查询：

- 按规范化 username 精确查询；
- 输入符合手机号格式时，按统一规范化手机号查询；
- 输入符合邮箱格式时，按 `trim + lowercase` 规范化邮箱查询；
- 将候选按用户主键去重；零用户或多个不同用户均使用相同认证失败，只有唯一用户才能继续校验密码；
- 找到唯一用户后，再按 Client tenant/userType 加载权限，路由不允许时认证失败。

因此同一个用户的 username 恰好等于自己的手机号或邮箱时可以正常去重；如果输入同时命中 A 用户的 username 和 B 用户的 mobile/email，则必须拒绝，不能以 username、手机号或邮箱优先级消除歧义。SMS grant 使用专用手机号解析器；`OAuth2ResourceOwnerSmsAuthenticationProvider` 只构造 `SmsAuthenticationToken`，实际用户解析统一进入 Client-aware 手机号解析器。新增 `email` grant 使用专用邮箱解析器。非 `EMPLOYEE` 用户类型只有在对应 `UserAuthInfoService` 明确实现相同标识解析能力后才允许，否则失败关闭。

### 5. 无 Session 绑定闭环

把现有 `ThirdPartyBindingService` 中“验证本地账号归属后原子写绑定”的领域规则提取为不依赖 Servlet 的服务；浏览器继续使用 Session adapter，外部凭证协议使用 Redis ticket adapter。

未绑定外部身份或未匹配手机号时，Token Endpoint 返回：

```json
{
  "error": "binding_required",
  "error_description": "需要绑定已有账号",
  "binding_ticket": "不可解析的一次性随机值"
}
```

错误响应不得包含 provider subject、手机号、邮箱、原始凭证或第三方消息。Ticket 服务端记录：随机 ID 摘要、Client 快照、绑定类型、provider/subject 或规范化手机号、创建时间、过期时间和失败次数；原始第三方凭证在完成核验后不再保存。

调用方随后使用同一个 Client，通过 `/oauth2/authorize` 的本地账号认证取得 authorization_code + S256 PKCE access token，或通过已允许的 SMS/email 本地 grant 取得用户 access token，再调用：

```http
POST /api/v1/external-identity-bindings
Authorization: Bearer <local-user-access-token>
Content-Type: application/json

{"bindingTicket":"..."}
```

绑定接口必须从 `MaculaOAuth2AuthorizationService` 查回 access token 对应的授权，验证它来自同一个 Client 的 authorization_code 用户登录或允许的 SMS/email 用户型本地 grant，并验证 principal、tenantId、userType 和 ticket 快照一致。client_credentials、external_code、refresh_token 或不能证明本地账号交互认证的 Token 不得作为绑定依据。外部身份 ticket 插入 `sys_user_identity_binding`；手机号 ticket 在账号手机号为空或相同时绑定手机号。事务成功后原子消费 ticket 并返回 `204 No Content`。调用方已经持有本地 access token，无需让普通 REST Controller 额外签发 Token；后续外部登录使用新的第三方凭证即可直接成功。

### 6. 验证码登录

账号密码、短信验证码和邮件验证码均属于 IAM 内部认证，不进入 `ExternalCodeType`，也不依赖第三方身份绑定：

| IAM 内部认证 | grant | 请求标识 | IAM 验证 | 用户定位 |
|---|---|---|---|---|
| 用户名 + 密码 | `password` | `username` + `password` | 服务端自动查询 username/mobile/email 候选，唯一确定用户后校验密码 | 命中 `sys_user.username` |
| 手机号 + 密码 | `password` | `username=<mobile>` + `password` | 服务端自动查询 username/mobile/email 候选，唯一确定用户后校验密码 | 命中 `sys_user.mobile` |
| 邮箱 + 密码 | `password` | `username=<email>` + `password` | 服务端自动查询 username/mobile/email 候选，唯一确定用户后校验密码 | 命中规范化 `sys_user.email` |
| 手机号 + 验证码 | `sms` | `phone` + `code` | 校验 IAM 签发并一次性消费的短信验证码 | `sys_user.mobile` 非空全局唯一后按 Client 路由 |
| 邮箱 + 验证码 | `email` | `email` + `code` | 校验 IAM 签发并一次性消费的邮件验证码 | `sys_user.email` 非空全局唯一后按 Client 路由 |

保留现有短信验证码摘要存储和原子消费语义，修复手机号到 Client 用户的解析。新增邮件验证码 Store、Service 和 IAM 邮件投递器，沿用 System 共享的 `MAIL_LOGIN` 配置和模板；IAM 只读映射补齐 SMTP endpoint、标题、正文等必要字段，并在 IAM 最窄模块引入邮件依赖。

新增面向已登记公共 Client 的验证码申请接口，分别接收手机号或邮箱。Client 必须为 `none + requireProofKey=true` 并启用对应 grant；由于公共 Client ID 不是秘密，接口还必须校验登记 Origin、执行 CAPTCHA 及 IP/Client/目标限流。申请接口无论目标是否存在都返回 `202 Accepted`；只有目标唯一、用户启用并符合 Client 路由时才实际发送。Redis key 使用目标摘要和 Client ID 摘要，执行发送间隔、TTL、最大尝试次数和一次性消费。邮件正文使用固定变量白名单转义，禁止模板注入。

### 7. 重放、并发和错误

`ExternalCodeReplayStore` 使用服务端 HMAC 对 `code_type + code` 生成摘要，以 Redis `SET NX` 原子占用。成功换码、无效 code 或产生 binding ticket 后保留至该凭证最大有效窗口；明确的本地基础设施瞬时失败可以释放 processing 标记，但不得在第三方已确认消费后释放。并发请求只能有一个进入换码。

标准失败优先使用 `invalid_request`、`invalid_client`、`unauthorized_client`、`invalid_grant`、`invalid_scope`；只有已核验但尚未绑定的身份返回扩展错误 `binding_required`。所有对外描述均为受控固定文本，不透传异常或提供商内容。

## Data and interfaces

### 数据库

由 System 拥有的新 Flyway 迁移完成：

1. `sys_oauth2_client.external_code_types VARCHAR(1024) NOT NULL DEFAULT ''`。
2. 为 `sys_user.mobile` 和规范化后的 `sys_user.email` 建立全局唯一约束。MySQL 唯一键允许多个 `NULL`，但任何非 `NULL` 值都必须唯一；写入链路把空字符串和仅空白内容规范化为 `NULL`。迁移前必须检测历史重复值及空字符串，发现重复时明确失败，不能自动选择、合并或删除业务记录。
3. `sys_user_identity_binding` 继续使用现有两个唯一键，不存储 code、Token 或 ticket。
4. 微信新增的移动应用/小程序秘密继续使用当前 V8 引入的加密附加秘密列；阿里云号码认证秘密使用同一受控秘密存储，不新增明文列。

IAM 和 System 实体同步增加 Client 字段；System Form/VO 和 Admin 同步读写。IAM 不创建 Flyway 文件。

### OAuth Token 接口

新增扩展 grant 和 `email` grant；password grant 保持现有 `username`、`password` 协议参数不变，由服务端自动解析用户名、手机号或邮箱。扩展字段统一定义常量，枚举值作为稳定协议，不在业务代码散落字符串。

外部凭证成功响应继续使用 Spring Authorization Server 的 OAuth2 Token Response。`binding_required` 是 Macula 扩展错误，额外只允许 `binding_ticket`；其他错误不返回扩展身份数据。

### 绑定接口

新增 `POST /api/v1/external-identity-bindings`，只接受 Bearer 用户 Token 和 JSON binding ticket，成功返回 204。Controller 只做协议适配；ticket 校验、授权回查、唯一性判断、用户资料更新和绑定写入由事务 Service 完成。

### 验证码申请接口

新增面向已登记公共 OAuth Client 的 SMS/MAIL 登录验证码申请接口，统一返回 202，并强制 Client/grant 校验、登记 Origin、CAPTCHA 及多维限流。现有浏览器同源 `/api/v1/sms-code` 保持兼容且不放宽 CORS/CSRF；协议层接口使用独立路径和安全链，不能复用浏览器 Cookie 推断 Client。

### 配置与管理端

- System `OAuth2ClientForm` / `OAuth2ClientVO`：增加 `externalCodeTypes`。
- IAM/System `SysOAuth2Client` 映射：增加 `externalCodeTypes`。
- System `IdentityProviderType`：补齐微信移动应用、小程序字段和阿里云号码认证 provider。
- Admin OAuth Client 页面：增加外部凭证类型多选、默认禁止提示和 grant 一致性校验反馈。
- Admin 身份源页面继续由目录动态渲染，不硬编码秘密值。
- IAM README、System README 和 Admin 管理说明同步记录参数、错误、配置、数据迁移和真实平台验收条件。

## Flagged concerns

- 公共 OAuth Client：仅凭公开 `client_id` 无法证明哪个 App 在提交第三方 bearer code，也不能阻止第三方 code 在到达 IAM 前被窃取后抢先兑换。规格接受公共 Client 直接使用 `external_code` 以满足端侧一步登录，但要求 code 与固定第三方 App 配置绑定、服务端换码、一次性防重放、Client/code type allowlist、限流，并在平台支持时叠加 App Attestation 或第三方原生 PKCE；这是已知剩余风险，non-blocking。
- 手机号和邮箱存量数据：唯一性规则已经确定为“`NULL` 可重复、非空全局唯一”。当前数据库尚未施加该约束，历史 mobile/email 可能重复或为空字符串；迁移必须先生成只读冲突清单并在发现冲突时停止，存量数据由业务负责人处置后才能部署，但不再作为规格决策项，non-blocking for implementation、blocking for rollout。
- V8 实现顺序：当前工作区的多身份源网页登录正在引入加密附加秘密列，微信多应用秘密和阿里云秘密依赖该持久化能力；V8 未被接受并验证前不能实施依赖它的外部凭证配置，blocking。
- 非 EMPLOYEE 用户源：`MEMBER`、`ODC`、`SUPPLIER` 当前没有统一的 mobile/email 唯一解析契约；首期在没有显式 resolver 时拒绝这些标识登录，不影响 EMPLOYEE 业务员交付，non-blocking。
- 自定义 grant 的 OIDC 与续期能力：首期只颁发 OAuth access token，拒绝 refresh token 和 `openid`；需要续期或 ID Token 的调用方必须重新取得第三方 code，或继续走 authorization_code + PKCE，non-blocking。
- 真实供应商合同：Mock 测试不能证明飞书、钉钉、企业微信、微信和阿里云真实 code/Token、应用审核、权限和错误行为；缺少测试应用与真实设备不阻塞代码评审，但阻塞相应凭证类型上线，non-blocking。
- 供应商 SDK/服务端依赖：除阿里云服务端接口可能需要官方 SDK 外，优先复用现有受控 HTTP 客户端；如必须新增 SDK，需单独完成版本、许可证、传递依赖和漏洞评审，non-blocking。
- 性能基线：`bands.yaml` 尚无可执行的 IAM Token Endpoint 基线，实施后只能报告负载测试观测值，不能宣称满足未定义 SLO，non-blocking。

## Verification strategy

1. Converter 单元测试固定 grant URI、参数常量、单值和长度规则，覆盖缺失、重复、未知 code type、额外 provider 参数及 scope 解析。
2. Token Provider 测试覆盖缺少 `client_id`、机密 Client、`requireProofKey=false`、grant 未启用、allowlist 为空/不包含、scope 越权、provider 禁用和用户类型无 resolver，并断言均不调用第三方；另覆盖 `none + requireProofKey=true` 公共 Client 的允许路径。
3. 每个 provider verifier 使用本地 Mock HTTP Server 覆盖成功换码、固定 HTTPS 端点、禁用 3xx、超时、非成功状态、数值错误码、畸形响应和关键字段缺失；不得访问真实外网。
4. 身份测试分别证明飞书 `employee_no`、钉钉组织成员 `userId`、企业微信成员 `userid`、微信普通/小程序 `unionid`、微信小程序手机号和阿里云手机号；微信缺少 `unionid` 必须失败且不查询 `openid` 绑定。
5. 防重放测试并发提交相同凭证，断言最多一次进入 provider verifier；覆盖成功、binding required、无效凭证、基础设施瞬时失败和 TTL 后行为，Redis 中不得出现原始凭证。
6. Client-aware 用户解析测试覆盖 username/mobile/email 的唯一、空、重复、大小写/格式规范化、删除、禁用、租户不符、用户类型不符和 Client 配置变化。
7. password 兼容测试证明现有 `username + password` 请求不变，并覆盖输入只命中 username、mobile、email，同一用户跨字段重复命中，以及输入分别命中多个不同用户时失败关闭；SMS 和 email grant 覆盖验证码成功、过期、重放、错误次数、限流和账号枚举防护。
8. binding ticket 测试覆盖随机性、TTL、失败次数、Client/tenant/userType/绑定类型错配、并发消费、Token grant 来源限制，以及响应和 Redis key 不泄露 subject/mobile。
9. 绑定事务测试覆盖外部身份两个唯一键、已被他人绑定、同 provider 重复账号、手机号为空/相同/不同、手机号已被他人占用和事务回滚；失败后不得消费可重试 ticket，成功后必须消费。
10. Token 生成测试断言授权记录保存真实 `urn:macula:params:oauth:grant-type:external_code` grant、scope 不扩权、access token TTL/格式沿用 Client、不签发 refresh token、`openid` 被拒绝，并验证撤销、内省和 `findByToken(token, null)` 兼容；标准 authorization_code 的刷新行为单独回归且不得变化。
11. 真实 `SecurityFilterChain` MVC 测试覆盖 `/oauth2/token`、绑定 API、协议验证码申请接口、客户端认证和 CORS/CSRF 边界，同时回归 authorization_code + PKCE、OIDC、password、SMS、浏览器登录及 GitHub/企微绑定。
12. 捕获日志测试给 code、Token、ticket、手机号、邮箱、subject、provider 响应和异常消息注入独特标记，断言日志与错误响应均不包含这些标记或堆栈。
13. System 服务测试覆盖外部凭证枚举、grant/认证方式一致性、空集合默认拒绝、未知值拒绝、缓存提交后失效及 VO 不回显秘密；Admin 单测覆盖多选、空态、校验和保存回显。
14. Flyway 集成测试必须在命名受控的隔离 MySQL 上分别执行历史 schema → 新版本和干净库初始化，覆盖多个 NULL 可保存、任意非空 mobile/email 重复均失败、空字符串写入前归一化、历史重复数据使迁移明确失败、唯一键和旧 Client 默认空集合；不能修改或清洗业务数据库。
15. 依赖检查记录 Spring Authorization Server、邮件和可能的阿里云服务端 SDK 的实际解析版本、许可证和漏洞结果；新增依赖后执行 IAM `-am test` 与 package。
16. Stage 4 至少执行 IAM 目标测试、System/API 组合测试、Admin 相关 Vitest 与 build、根 `mvn test -Plocal`、`git diff --check`；任何外部环境缺失必须单列未验证范围，不能以 Mock 结果冒充真实平台通过。
17. 真实验收为每个 code type 使用独立测试应用和账号验证：成功、取消/无效、重放、未绑定、绑定后重试、禁用账号、错误 Client 和错误租户。凭据、手机号和主体不得写入验收文档或提交仓库。
