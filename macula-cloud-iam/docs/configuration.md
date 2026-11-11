# IAM 配置指南

本文介绍当前运行合同；逐平台接口及 Code 参数见[第三方接入说明](third-party-integration.md)
，模块概览见[IAM README](../README.md)。

## 应用与协议

业务应用从 `WEB_SERVER`、`SPA`、`NATIVE`、`MINIAPP`、`IN_APP`、`SERVICE` 中选择终端类型，保存后类型固定。每个业务 Client
只配置一个协议：`OIDC` 或 `OAUTH2_1`。新应用为 `DRAFT`，完成所需参数后才能启用。

| 应用       | 认证与入口                                                                            |
|------------|---------------------------------------------------------------------------------------|
| WEB_SERVER | 机密认证、授权码 + S256 PKCE，可关联多个通用网页登录来源                              |
| SPA        | `none`、授权码 + S256 PKCE，回调和 Origin 精确登记                                    |
| NATIVE     | 可选一个 SDK/AppLink 来源；可独立启用 IAM 内置密码/短信/邮箱入口                      |
| MINIAPP    | 固定本平台小程序来源，可配置登录 Code/手机号授权 Code；统一登录回传桥接由业务应用完成 |
| IN_APP     | 固定一个端内能力；网页授权与 JSAPI Code 使用各自合同                                  |
| SERVICE    | `client_credentials` 与业务 Scope，无用户登录来源或用户目录                           |

客户端认证支持 `none`、`client_secret_basic`、`client_secret_post`、`private_key_jwt`。公共客户端不使用 Secret，也不支持
Refresh Token 或服务凭据 grant。机密授权码客户端可配置 Refresh Token，默认不复用。

`private_key_jwt` 使用客户端自己保管的私钥和 HTTPS JWKS URL；该 URL 与 IAM 发布签名公钥的 `/oauth2/jwks` 不同。客户端断言必须满足
IAM 的签名、audience、时效与重放规则。

## 配置类归属

装配入口按职责分包，端点行为实现留在协议包：

```text
config/
  DefaultSecurityConfiguration
  oauth2/
    ProtocolOAuth2Configuration
    OAuth2ProtocolExtensionConfiguration
    OAuth2PersistenceConfiguration
    OAuth2TokenConfiguration
    JwtConfiguration
  MfaExtensionConfiguration
  PasswordEncoderConfig
```

MFA发布策略和密码编码器由多条流程共用，放在根包。OAuth2的CORS规则由
`protocol.oauth2.endpoint.OAuth2CorsConfigurationSource`实现，协议配置只负责注入和装配。子包用于区分职责，不改变安全链优先级或Spring
Bean名称。

## 登录控制

`hostedLoginMethods` 控制 `PASSWORD` / `SMS` / `EMAIL`：`null` 继承平台配置，`[]` 关闭全部本地方式，全局关闭优先。该策略控制新托管认证，不撤销已有
SSO 会话。验证码发送与消费固定到同一个可信 Client；绑定短信使用独立的 `IDENTITY_BINDING` 用途。

Web/SPA 显示兼容的第三方网页入口。移动应用与小程序的 IAM 页面仅提供启用的内置密码/短信/邮箱；平台 SDK
登录与内置登录开关独立。端内应用按已登记能力发起对应平台授权，不能仅凭 UA 或请求自报平台获得身份。

登录 Provider 与协议 grant Provider 分别注册到所属安全链。`JwtConfiguration` 只装配密钥、JWK 和解码器；账号密码、短信、邮箱与第三方网页登录不在
JWT 配置中声明。

邮箱开关为 `spring.security.oauth2.server.login.email.enabled`，默认开启；验证码参数位于
`macula.cloud.iam.email-verification`，与 SMS 默认值一致，摘要密钥通过 `IAM_EMAIL_DIGEST_KEY` 配置。邮件使用服务端 `MAIL`
渠道与 `MAIL_LOGIN` 模板，不接受浏览器指定收件配置。

V25 是一次性迁移：具备托管能力的 Client 合法非空显式列表追加 `EMAIL`，`null` 继承默认，空列表保持全关。迁移后管理员取消
`EMAIL` 不会被读取逻辑重新开启。旧版本无法解释新增枚举值，回滚需同时处理显式配置。此说明不代表已对业务数据库执行迁移。

## 身份源与绑定

一条身份源对应一个真实第三方应用注册及其凭据。微信网站、移动应用、公众号/服务号、小程序必须分别建源；企业微信、飞书、钉钉也只在同一实际注册内共享凭据。同一厂商可建多个来源，Client
明确关联 registration 和登录能力，运行时不按厂商全局择源。

来源身份域保存后固定；Secret 可轮换。来源能力、版本与 Client 授权版本在发起、回调、目录、绑定和发布结果时复核。已存在绑定按
registration 和稳定 userId 关联，用户名改变不导致跨源串号。

自动绑定默认关闭。启用时配置 `bindingMappings`，例如：

```json
[
  {"sourceField":"mobile","targetField":"mobile"},
  {"sourceField":"email","targetField":"email"}
]
```

仅服务端核实且该能力允许的字段参与匹配。至少一条命中、所有命中指向同一个有效本地账号才可自动绑定；多账号、跨规则冲突、缺字段或无候选进入账号证明。普通社交显示邮箱不能作为已核实企业邮箱。手机号证明能力仅允许手机号匹配，不修改本地手机号或自动合并用户。

浏览器使用短期 pending；SDK 使用 opaque ticket。可用的账号证明为密码、绑定专用短信或合格会话；Token 绑定 API 只接受同 Client
的授权码 Token 证明。待绑定的外部 Code 不能证明本地账号归属。目录请求在事务外执行，绑定在锁内复核来源与快照，提交后才消费状态。

自动匹配需要 MySQL `REPEATABLE READ` 或 `SERIALIZABLE`；现有账号读取及手动证明不以此改变原流程。企业成员资料受平台授权限制，缺失字段不会按显示资料补成可信身份。

## Scope 与资源

System 的 `GET /api/v1/oauth2-clients/configuration-catalog` 提供可配置协议、Scope 和来源能力。内置 `openid`、`profile`、
`system:access`；额外业务 Scope 由资源服务登记并实际校验：

```yaml
macula:
  iam:
    scopes:
      business:
        - id: orders.read
          resource: urn:example:orders
          name: 读取订单
          description: 订单资源服务校验的只读权限
          application-types: [SERVICE, WEB_SERVER, SPA, NATIVE, MINIAPP]
          grants: []
```

OIDC 请求包含 `openid`，可选 `profile` 及业务 Scope；OAuth2.1 不请求 `openid`，不签发 ID Token。Access Token audience 由已授予
Scope 的资源目录推导；ID Token audience 为 client_id。服务令牌不提供用户 profile。

资源服务器使用独立的 `purpose=RESOURCE_SERVER` 机密身份调用内省。此身份不配置业务协议、用户目录、回调或
`client_credentials` grant，也不与 Admin 公共 Client 共用凭据。

## 密钥与扩展

| 配置/接口                                               | 职责                                                              |
|---------------------------------------------------------|-------------------------------------------------------------------|
| `macula.cloud.iam.signing-key.*` / `SigningKeyProvider` | JKS 签名来源，可替换密钥加载实现                                  |
| `ClientAccountDetailsService`                           | 按显式 Client、租户与用户类型快照加载账号，不读取当前 HTTP 请求   |
| `UserAuthInfoService`                                   | 可选外部用户目录                                                  |
| `ThirdPartyProviderAdapter`                             | 平台网页授权 URL 与身份核验；Registry 拒绝重复厂商键              |
| `IdentityDirectoryService` / `ProviderDirectoryAdapter` | 公共目录准入端口与各平台成员查询适配                              |
| `AccessTokenClaimsService`                              | 协议层 JWT/opaque 受控声明规则                                    |
| `MfaCompletionPolicy`                                   | Session、授权码、用户 Token 与 Refresh 发布前策略；默认无额外因子 |

平台应用 Token 缓存与目录快照须有真实加密配置。敏感值不写入日志；Code 兑换结果不确定时保留防重放占用。默认 MFA
扩展不实现因子注册、挑战或可信 `acr` / `amr` 声明。

## 配置更新与运行

保存及密钥重置使用当前 `configurationVersion` 做并发保护，过期返回 409，需重新加载。`authorizationVersion`
由服务端维护；协议、权限或密钥变化按原授权版本规则使旧授权失效。身份源变更递增版本，旧证明不能继续发布新结果。

System 从受信服务器配置读取协议元数据；管理 API 不接受任意抓取 URL。平台 Admin 使用独立 OIDC/SPA/none/授权码
Client；Gateway 使用独立内省身份。初始化通过 `IAM_PLATFORM_CLIENTS_ENABLED`、`IAM_ADMIN_CLIENT_ID`、
`IAM_INTROSPECTION_CLIENT_ID` 明确认领已有身份，不猜测或自动轮换凭据。

## 授权支持范围

当前 grant 为 `authorization_code`、`refresh_token`、`client_credentials` 及
`urn:macula:params:oauth:grant-type:external_code`，同时受协议、应用类型、认证方式与来源能力限制。

`password` / `sms` / `email` grant 不提供新 Token，旧此类授权的刷新返回 `invalid_grant`；既有 Access Token
继续执行原到期、撤销和配置版本校验。历史配置保留原值并要求管理员显式整改，不能自动改成
external_code。邮箱属性匹配、托管密码/短信/邮箱认证与这些退役 grant 分属不同职责。当前 Scope 目录保留 `email` 标识，但未开放该
Scope，不能据此要求 UserInfo 发布邮箱。

`/api/v1/oauth2/sms-codes`、`/api/v1/oauth2/email-codes`、`/api/v1/oauth2/login-challenges/captcha` 返回 HTTP 410
`endpoint_retired`。托管 `/api/v1/sms-code`、`/api/v1/email-code` 与绑定 `/login/bind/sms-code` 维持各自用途和保护规则。
