# Macula Cloud IAM

## 作用

`macula-cloud-iam` 是 Macula Cloud 的身份认证与授权中心，为管理端、业务应用和服务调用统一签发、校验及撤销访问令牌。

第三方接入的当前能力、逐平台流程、调用示例、实际缺口及完整官方参考见[统一认证与第三方接入核对](docs/third-party-integration.md)。该文档明确区分本地实现、远端基线与待核实能力。

## 核心功能

- OAuth 2.1 授权码模式与 OpenID Connect 登录。
- 用户名、手机号、邮箱及验证码登录。
- GitHub、企业微信、飞书、钉钉、微信等第三方身份登录与绑定。
- `client_credentials` 服务身份认证。
- access token 刷新、撤销、内省以及 JWT 公钥发布。
- 按 OAuth Client 管理回调地址、scope、租户、登录方式和浏览器 Origin。

## 使用

先在 System 管理端创建 OAuth Client，配置允许的授权方式、scope 和回调地址。浏览器与移动端公共客户端推荐使用授权码模式和 S256 PKCE：

```text
GET /oauth2/authorize
    ?response_type=code
    &client_id=<client_id>
    &redirect_uri=<registered_redirect_uri>
    &scope=openid%20profile
    &state=<random_state>
    &code_challenge=<s256_challenge>
    &code_challenge_method=S256
```

回调取得授权码后，通过 `/oauth2/token` 换取令牌。应用必须校验 `state`；请求了 `openid` 时还应校验 ID Token。

常用端点：

| 路径 | 用途 |
| --- | --- |
| `/login` | 统一登录页 |
| `/oauth2/authorize` | OAuth 2.1 / OIDC 授权入口 |
| `/oauth2/token` | 获取或刷新令牌 |
| `/oauth2/introspect` | 查询令牌状态 |
| `/oauth2/revoke` | 撤销令牌 |
| `/.well-known/openid-configuration` | OIDC Discovery |
| `/oauth2/jwks` | JWT 验签公钥 |
| `/userinfo` | 获取已授权的用户信息 |
| `/connect/logout` | OIDC 退出 |

本地开发先按[根 README](../README.md)启动 MySQL、Redis、Nacos 和 System，再在 IDE 中运行 `MaculaIamApplication`。默认访问地址为 <http://127.0.0.1:9010>。

生产环境应使用 HTTPS，并通过环境变量或配置中心提供稳定的 `IAM_ISSUER_URI`、签名密钥和加密密码。完整 Compose 配置见 [`deploy/README.md`](../deploy/README.md)。

## OAuth Client 配置与接入示例

Client 的协议配置以中文复选框选择授权方式，并显示协议值和用途。客户端认证说明应用如何向 IAM 证明身份：`none` 不使用密钥，`client_secret_basic` 使用请求头，`client_secret_post` 使用 POST 表单，`private_key_jwt` 使用客户端私钥签名。

当前策略：公共客户端使用 `none`，禁止 `client_credentials` 和 `refresh_token`；可使用明确标注的 `sms`、`email`、`external_code` 扩展。`password` grant 已移除。所有授权码使用 S256 PKCE；机密授权码客户端可选择刷新令牌，默认不复用。

通过 `/oauth2/authorize` 跳转 IAM 登录页时，用户在登录页使用短信认证无需额外勾选 `sms` grant；后者表示直接通过验证码请求 `/oauth2/token`。授权请求不携带客户端密钥。

密钥认证支持安全生成、复制、创建和重置。密钥原文及 Basic 请求头只在当前操作中暂存，关闭后清除；已保存密钥不能反查，编辑示例可临时输入已知密钥，临时值不随更新提交。重置成功后旧密钥立即失效，请同步更新接入应用。

接入示例按授权方式生成请求，默认遮蔽实际凭据；点击显示或复制时才使用本次输入。Clipboard API 不可用时手动复制。请确认示例 IAM 基地址并替换占位符；示例不会实际发送请求。

`private_key_jwt` 保持配置客户端 HTTPS JWKS URL 和 RSA 签名算法，IAM 获取公钥验签，客户端自行保管私钥。页面提供 Node.js 原生 crypto 的签名示例：显式配置客户端 ID、IAM 接受的 `JWT_AUDIENCE`、`KEY_ID` 和本地私钥文件，每次请求生成短期 `client_assertion`。此 JWKS URL 与 IAM 签发 Token 的公钥端点用途不同。


## IAM 应用配置

创建应用时选择标准Web、单页Web、移动客户端、后端服务、小程序或端内应用；微信/企微作为小程序平台，身份源作为登录渠道独立管理。Client ID 自动生成且只读，创建后应用类型/小程序平台不可修改。新建阶段切换类型或平台会清空旧登录方式、固定身份源、回调、直登与派生grant，保留名称及通用参数。详情分为应用配置、协议配置、登录控制和接入指南，共用一份草稿；切换分区不丢值，离开有未保存提示。

Web 默认 Basic 与授权码；SPA/本地应用使用 none 与 S256 PKCE；服务应用使用 client_credentials 和目录中的业务 Scope；小程序使用已关联具体来源的 external_code。协议、Scope 和来源能力均从目录选择。private_key_jwt 保持 HTTPS JWKS URL 配置，私钥由客户端保管；平台端点使用紧凑只读文本与复制。

托管登录方式`hostedLoginMethods`支持PASSWORD/SMS，null继承平台、[]关闭全部本地方式；全局关闭优先。托管页呈现、密码/SMS POST和发码使用Session受控授权请求的同一Client策略，核对授权端点、唯一client_id、code响应及精确回调。请求参数/header/cookie不能成为可信身份，验证码发送与消费绑定相同Client。策略只控制新托管认证，不撤销已有SSO会话/Token，不改变独立Token扩展grant的校验。

同厂商可配置多个具体身份源。每个 Client 为每个网页厂商明确关联一个 registration，SDK/小程序按认证能力关联具体来源；运行时不按厂商全局择一。取消应用关联不删除来源，有引用时禁止删除来源。来源类型及 AppID/CorpID 等身份域标识保存后不可替换，Secret 可轮换。

V15 追加协议、来源类型、绑定策略与稳定用户 ID 绑定表，并取消厂商全局唯一来源索引；已执行 V14 不修改。缺少新模型字段的配置需明确重新配置；旧 state/ticket 需重新登录，旧用户名绑定表不参与认证，不做历史回退或自动迁移。更新提交完整新表单，来源 version 和 Client 完整快照保护并发，冲突返回 409。回滚前停用新来源并按验证过的配置恢复，旧镜像不能直接解释新身份域。

System只读`GET /api/v1/oauth2-clients/metadata`懒加载可信Discovery，复用Client管理权限；获取地址来自IAM_DISCOVERY_URL，Issuer必须匹配IAM_ISSUER_URI，不允许请求参数覆盖URL。默认HTTPS，只有显式IAM_METADATA_ALLOW_HTTP=true才允许本地HTTP；禁用重定向、无认证请求、连接2秒/读取3秒超时、响应限256KiB、成功缓存60秒。获取失败仅影响接入信息，不影响System启动/健康，也不猜测管理端域名。只展示Discovery发布的端点，接入指南使用其实际地址。

密钥原文及派生Basic内容仅在当前操作内存中保留，离开/关闭/停用页面即清理，不写入本地持久状态。已保存密钥不能反查；无密钥记录改用密钥认证可显式初始化，已有密钥更换必须走重置接口。接入指南临时输入仅生成示例，不随普通保存提交。

## 身份源的首次账号绑定

企业身份源不默认自动绑定。新配置使用 `autoBindEnabled`（默认 false）及多条 `bindingMappings` 数组；旧单条字段仅作兼容；应用页只关联来源。下表是V21确定转换并继续兼容的三种旧 `bindingRule`，新配置的字段组合见“身份源字段映射”。

| 已实现来源属性 | 可选择的规则 | 前提 |
| --- | --- | --- |
| 企业微信、钉钉成员 UserID | `MEMBER_ID_TO_USERNAME` | 明确配置企业成员标识与本地用户名相同 |
| 飞书 employee_no | `EMPLOYEE_NO_TO_USERNAME` | 当前授权返回员工编号；本地没有单独工号列 |
| 微信小程序手机号授权、阿里云号码认证 | `VERIFIED_MOBILE_TO_MOBILE` | 平台服务端已核验手机号 |

普通微信、GitHub 当前没有安全匹配本地账号的属性，首次使用手动绑定。邮箱映射只支持已核实的企业目录属性，不使用普通社交显示邮箱。Code、Secret、前端提交资料和普通展示邮箱不作为匹配依据。

先查询当前 registration 的有效绑定，再处理首次匹配。关闭、不支持、缺属性、零候选或多候选进入绑定页；唯一启用且符合 Client 用户目录和认证范围的账号才自动建关系。绑定保存稳定 userId，用户名变化不失联；不同 registration 相同外部标识不串号。禁用、越权或冲突不能通过手动绑定绕过，也不自动创建、合并或覆盖账号。

绑定页通过账号密码、已部署的短信服务或合格本地会话证明归属。这与 Client 托管登录按钮开关独立，仅有效第三方核验 pending 内可用；SDK完成绑定不创建SSO会话或Token。绑定短信使用原Client及 `IDENTITY_BINDING` 专用用途；与 LOGIN 码互不消费，共享手机号/IP发送限流。

手机号型凭据没有伪造的永久平台主体。手动绑定要求目标账号当前已登记手机号与核验手机号一致，不在此流程修改手机号；本地手机号变化后，旧手机号关系拒绝使用。

SDK Token API 返回 `binding_required`、`binding_uri` 和 opaque `binding_ticket`，不会302。端侧打开短期入口，服务端转入无票据URL的绑定页；完成后回到应用重新取平台CODE登录，原CODE不能重放。绑定材料仅保存在服务端，页面禁止缓存/Referer传播，不支持任意returnTo。

## Scope目录与初始化

`GET /api/v1/oauth2-clients/configuration-catalog` 返回协议、实际Scope和具体来源能力。内置 `openid` / `profile` / `system:access`（当前资料为用户名、昵称）；OAuth2.1不隐式添加openid、不签发ID Token。额外业务 Scope 由资源服务显式登记，并填写 resource 标识；登记本身不替代资源端权限检查：

```yaml
macula:
  iam:
    scopes:
      business:
        - id: orders.read
          resource: urn:example:orders
          name: 读取订单
          description: 由订单资源服务实际校验的只读权限
          application-types: [SERVICE, WEB_SERVER, SPA, NATIVE, MINIAPP]
          grants: []
```

升级前需为管理端自身 Client 准备明确的新模型配置，避免管理端登录依赖未配置的旧Client。运营者根据实际管理端架构设置 protocol_mode、application_type、认证/grant/Scope、S256及精确回调/Origin；不要给所有存量Client批量猜测回填。自助管理尚不可用时，按已审核的具体配置通过数据库管理工具完成该Client初始化，再登录配置其余应用/来源。V15不自动修改任何Client的凭据或授权集合。

SDK绑定入口使用服务器配置的可信issuer；缺少可信地址时拒绝生成，不能由请求Host/Forwarded或调用方URL决定。


## 应用创建与单协议配置

创建应用使用对话框，选择应用类型并填写名称，小程序另选平台；Client ID由服务端生成，不配置独立认证域名。创建结果为`DRAFT`，允许接入参数尚未填写，但不能被IAM用于登录、换取Token或账号绑定。详情保存草稿会停用应用；“保存并启用”需要通过完整的租户、用户类型、协议、回调、Scope和身份源校验。

详情包含快速开始、应用配置、协议配置、登录控制、访问授权五个页签，无高级配置页。用户类型位于访问授权；平台端点位于协议配置最底部的折叠区，默认收起。快速开始在完成配置并启用后展示真实接入示例。

业务应用仅选择一个协议，API 使用 `protocol` 和 `settings`；不再提交多配置数组或默认协议。协议采用单选，切换确认后清空协议参数、回调和身份源关联，保存后旧 code/refresh/access 失效。创建时终端类型切换清空相关配置，保存后终端类型不可修改。

OIDC 请求包含 openid，可选 profile 及已登记资源 Scope；OAuth2.1 不允许 openid、不签发 ID Token，但可使用 profile。授权码使用 S256 PKCE；公共应用使用 none，不支持 client_credentials 或 refresh。用户 Profile 与服务权限不能混用；服务令牌禁止 profile。刷新只能保持或缩小既有授权，协议/权限变更或密钥重置后旧授权失效。

关联身份源只展示已添加项，点击“添加身份源”搜索兼容来源并明确选择网页/SDK/小程序能力；取消不修改，槽位冲突需明确更换，不默认全开。移除直登来源会清空默认直登；失效来源保留标记并阻止无效保存。来源凭据及自动绑定策略仍统一在身份源管理配置。

V16采用追加迁移：旧protocol_mode不作为新运行时回退，旧记录不自动猜协议或启用状态；新列默认DRAFT。草稿tenant_id/user_type允许为空，启用仍必填。RegisteredClient及Origin索引已升级为v5-resource 缓存命名空间。部署新模型必须协调System与IAM版本，并显式配置管理端Client；管理端自身从password迁移到授权码登录是单独的接入工作，不能用旧IAM混跑证明新模型认证已生效。

## Admin 公共客户端接入

管理控制台使用独立公共 SPA Client，通过 OIDC `authorization_code` + S256 PKCE 接入；浏览器不发送 client_secret，ID Token 由控制台验证，平台 API 使用 reference Access Token。Gateway 保持独立机密 Client 进行内省，不复用 Admin 身份。初始化时平台自有 Admin 免二次授权确认，但仍需完成 IAM 登录、PKCE、nonce 及 System 访问权限检查。

Discovery `/.well-known/openid-configuration` 与 JWKS `/oauth2/jwks` 的 GET/OPTIONS 跨域仅对已启用 Client 登记的精确 Origin 开放，无凭据、无通配符；其他方法、请求头或未登记 Origin 拒绝。Token 端点原有 Client/Origin 绑定继续生效。公开 issuer、ID Token sub 和 Access Token 权限声明保持既有语义。

Chrome DevTools的精确探测路径 `/.well-known/appspecific/com.chrome.devtools.json` 不写入登录RequestCache，避免打开F12覆盖原OAuth授权上下文。两条Security链共享同一缓存；探测路径不额外匿名放行，其他请求保留Spring默认缓存和认证行为。


## 单协议配置与授权版本

管理 API 提交 `{protocol:"OIDC"|"OAUTH2_1", settings:{kind:"oauth2",...}}`，每个业务应用仅保存一份配置。更新携带 `configurationVersion`，其他配置修改或密钥轮换后旧版本返回 409；重新加载后再保存。`authorizationVersion` 是服务端授权绑定版本，不能由调用者指定。

内省身份使用 `purpose=RESOURCE_SERVER`，没有业务协议、用户目录、回调或 client_credentials grant；业务应用使用 APPLICATION。`/oauth2/introspect` 验证机密调用方身份，目标Token可以属于其他业务Client；返回令牌原有aud/Scope及安全字段白名单，不要求配置“允许内省的资源”。无效或撤销的Token返回active:false。private_key_jwt 继续使用客户端 JWKS URL，平台不保存私钥。

元数据按协议独立获取、缓存和重试：`GET /api/v1/oauth2-clients/metadata?protocol=OIDC|OAUTH2_1`。OIDC发现为`<issuer>/.well-known/openid-configuration`；OAuth为`<origin>/.well-known/oauth-authorization-server<issuer路径>`。抓取源由服务器配置推导，不接收用户URL；OAuth发现失败不会借用OIDC数据。端点在协议配置底部默认折叠。Admin控制台自身继续固定OIDC授权码/S256，应用管理协议选择不会改变Admin登录方式。

扩展采用通用配置接口和注册白名单；当前仅实现OIDC/OAUTH2_1，CAS/SAML尚未实现。每业务应用仅一项，载荷上限32KiB，拒绝未知协议、版本、字段与任意Java类型多态。


基础RegisteredClient仓库仅负责恢复Redis授权，不能读取请求或回查授权服务。请求级仓库根据原Scope或持久化protocol绑定构造有效视图；原生授权、确认、客户端认证、code、refresh和Token生成共同使用该视图。旧记录缺少绑定的code/refresh要求重新登录；访问令牌的原claims不重写。客户端断言解码器按Client ID、协议、JWKS和算法分开缓存，继续复用Spring签名和claims校验。

## V19 资源边界与平台身份

业务 Client 的 Scope 由目录选择，服务器推导 Access Token aud；调用方传入任意 aud 不能获得新资源。当前内置资源为 `urn:macula:system` 和 `urn:macula:iam:profile`。ID Token 的 audience 仍是 client_id，不能拿 ID Token 访问 API。

平台初始化只认领已经存在且明确配置的 Admin/Gateway 身份，保留主键、client_id、Secret 哈希。通过 `IAM_PLATFORM_CLIENTS_ENABLED=true`、`IAM_ADMIN_CLIENT_ID`、`IAM_INTROSPECTION_CLIENT_ID` 显式指定；不按应用名或示例 UUID 猜测。Admin 必须先有单 OIDC/SPA/none/code 配置。Gateway使用内省认证身份，不再属于业务授权Client。平台身份不可从普通页面删除、停用或变更用途。

V19 是新增迁移，V17/V18 不改。旧双配置应用转为 DRAFT 并移除歧义配置，管理员明确选择协议后再启用；不自动给其他应用补授 System 权限。旧令牌需重新登录，新授权与缓存使用独立版本。升级前备份数据库，恢复时配套恢复数据和服务版本，不能只回退镜像。

Gateway/System恢复使用原Starter的认证、角色与租户链路及内部JWT转发；不再增加Cloud资源/Scope拦截或System二次内省。API Key/HMAC沿用Starter原有处理。

Gateway/System错误响应遵循原Starter行为，不再增加Cloud固定401/403/503分类。Admin已有的503保留会话处理保留。


密钥重置请求与删除客户端请求都须携带当前 `configurationVersion`；版本过期返回 409，应重新加载配置后操作。平台身份认领只针对明确配置的已有 Admin/Gateway，不生成或轮换 Secret，重复启动不增加授权版本。


### OIDC 第三方 Code 登录

OIDC 与 OAuth2.1 都支持“第三方Code登录”（`urn:macula:params:oauth:grant-type:external_code`）。在登录控制添加具体 SDK/小程序身份源后自动启用，移除最后一个 SDK 来源后关闭；Client 认证必须为 `none`。OIDC 可只启用此扩展，无需网页回调；小程序也可选择 OIDC。来源的自动绑定开关、绑定凭据、客户端租户和防重放校验继续生效；未绑定返回 `binding_required`，完成绑定后须重新取得厂商 Code。

OIDC 请求必须包含 `openid`，可选 `profile`，返回 Access Token 与签名 ID Token；OAuth2.1 不允许 `openid`、不返回 ID Token。可选 `nonce` 须为单个非空值、最多 256 字符，并由接入方核对 ID Token 中的值；它只关联本扩展请求与响应，不代表厂商 Code 已与 nonce 绑定。从可信厂商 SDK 获取 Code，不从 URL 自动接收 Code。接入方须验证 ID Token 签名、`iss`、`aud`、有效期及提供时的 `nonce`，API 使用 Access Token。

此能力是 Macula 的 SDK 登录扩展，不是标准 OIDC 授权码流程，不创建浏览器 SSO 会话，也不向公共客户端签发 Refresh Token。网页登录仍使用 `authorization_code` + S256 PKCE。Admin 自身保留 OIDC 授权码；显式增配 SDK 来源不会被平台初始化清除。


### MFA 扩展预留与第三方缓存

本轮预留 `MfaCompletionPolicy.check(AuthenticationCompletionContext)`。默认策略直接继续；提供自定义策略 Bean 可拒绝认证完成。策略应幂等，不应签发 Token、保存会话或相信请求自报的 `acr`、`amr`。上下文只包含可信 Client（独立门户时为空）、稳定 userId/租户、实际认证方式、来源 ID、入口和检查阶段，不包含密码、平台 Code、用户 Token 或 session_key。

检查阶段包括会话建立前、绑定后浏览器会话建立前、授权码发布前、用户 Token 和 Refresh 发布前。SDK 绑定不建立 SSO；`client_credentials` 不执行用户 MFA。策略拒绝/异常时停止发布新成果，撤销和已有合法会话不因 MFA 被清理。本轮没有因子注册、挑战协议或 MFA 成功声明，真实安全链覆盖进度见 [本轮验证](../.sdlc/iam-unified-auth-provider-completion/verification.md)。

第三方应用 Token 缓存按 registration/version/flow/用途隔离，仅保存应用或企业 Token。需要启用平台 `CryptoManager` 的保密算法（拒绝 BASE64/DEFAULT），并配置 `macula.cloud.iam.external-code.digest-key`；缺少安全材料时第三方缓存失败关闭。缓存信封绑定用途并带独立域 HMAC，失去刷新锁的请求不得覆盖或返回自己的迟到结果。`macula.cloud.iam.providers` 配置锁租约、等待上限和过期提前量；不缓存平台用户 Token 或小程序 session_key。

Code 已提交后超时不视作可安全重试：客户端重新获取平台 Code。只有明确发生在提交 Code 前的应用 Token 获取失败允许释放本次占用；核验成功后的准入/资料失败不释放 Code。


### 身份源补齐与MFA扩展

具体来源现在配置明确的`identityFlow`、`directoryPolicy`和可信`organizationId`，不能只按厂商择一兑换。应用Token缓存、目录准入与绑定版本围栏由浏览器和external_code共用。缓存需要真实CryptoManager加密与外部注入的digest-key；超时或兑换结果不确定不释放Code重放占用。

未来MFA通过可替换的`MfaCompletionPolicy` Bean，在会话、绑定会话、授权码、用户Token和Refresh发布前检查；默认NoOp，不实现因子/挑战或MFA成功声明。配置、逐平台接口、完整调用示例、当前未完成能力及标准OIDC主认证时间待确认项见[第三方接入说明](docs/third-party-integration.md)。

## 身份源字段映射

身份源界面不再选择平台接口版本或通讯录策略；服务端选择适用的已核实默认合同，更新时保留历史Flow及更强成员准入。新企业来源自动取得适用资料；企业微信本轮按用户确认仅使用基础授权核实的成员ID，不增加敏感资料授权。飞书/钉钉的成员ID、业务工号、手机号、邮箱取决于应用和字段权限，缺失字段不自动兜底。

自动绑定可同时配置多条`bindingMappings`中的`sourceField`→`targetField`：成员ID/工号匹配username，可信企业手机号可匹配mobile或username，可信企业邮箱可匹配email或username。微信手机号Code、阿里云等MOBILE_PROOF能力仅允许mobile→mobile，不能绕过本地手机号核对。只匹配已有唯一有效账号，不覆盖IAM资料或权限。已有绑定优先，修改映射不重新绑定已关联身份。

System追加V21两个nullable映射列，确定转换原三种bindingRule；未知规则保留待整改。新旧字段同时提交须等价，省略与显式null分开，CAS版本防止旧客户端清掉新映射。更新/清空必须同时提交两个字段；关闭自动绑定时显式清空。升级先迁移、再更新System/IAM、最后Admin；旧服务不理解新映射，不可直接回滚接管写入。不得在业务库试运行集成测试。

候选匹配只查所选目标。用户名按trim后精确大小写比较，手机号移除空白/横线且不猜区号，邮箱trim并小写；历史规范化多匹配拒绝自动绑定，不修改历史数据。非ASCII邮箱在Java/MySQL大小写一致性未证明前不用于自动匹配。源码与Mock/隔离测试不代表真实平台授权或设备验收。

自动匹配事务需要MySQL REPEATABLE READ或SERIALIZABLE，运行时检查实际隔离级别；READ COMMITTED不能证明规范化查询范围内没有并发新候选，因而拒绝自动绑定。已有账号绑定读取与手动账号证明流程不因此更改。

### 应用与第三方认证场景约束（2026-11-09）

网页授权、原生APP Code、端内WEBVIEW Code、小程序Code分别限定。Web/SPA仅使用通用网页能力；NATIVE仅一个APP能力；MINIAPP仅一个本平台注册；IN_APP仅一个端内能力；SERVICE无用户第三方登录。外部Code仍受原有公共Client、固定来源和协议约束，不能仅靠none认证获得跨场景能力。Admin过滤与System保存、IAM换码执行相同矩阵，不自动改写历史关联。

网页目录按实际环境筛选通用网页入口。Web/SPA默认展示IAM页，只有明确配置的通用网页直登才续转；IN_APP企微/公众号直接进入固定容器授权，飞书/钉钉由业务JSAPI取Code。UA只影响入口体验，身份仍由平台Code、一次性state及固定来源核验；错误后不循环拉起。逐接口参数、官方原链接及未完成的真机边界见第三方接入文档。

2026-11-10补正：飞书端内Code已按官方v1→v2→v3迁移说明补齐FEISHU_WEBVIEW_V3。IN_APP可关联FEISHU_WEBVIEW_CODE，由H5调用requestAccess取得Code后交IAM external_code；IAM以平台凭据调用v3 Token和user_info，不需要浏览器回调或AppLink PKCE，不建立浏览器SSO会话。平台Secret保留在IAM，直接Token模式仍要求公共Client及固定来源。旧显式v1不自动升级，真实企业/设备尚未联调；完整证据见第三方接入文档2026-11-10补正章节。


## 小程序与移动客户端的两条登录入口

应用类型不决定必须使用哪条入口。小程序可选择本平台Code直换IAM Token、IAM统一登录页的授权码+S256 PKCE，或两者并存；移动客户端同样可使用SDK Code或系统浏览器统一登录。登录控制提供独立的统一登录开关，已有Code关联不会被该开关清除。

Web/SPA的IAM网页入口按实际浏览器/容器筛选通用网页方式。移动/小程序主动打开IAM页仅提供密码/短信，不允许第三方网页直登。飞书/钉钉端内JSAPI Code与企微端内网页授权沿用各自合同。业务小程序仍需完成web-view业务域名、回调及授权码回传桥接；本地测试通过不代表第三方真机链路已验证。详见[接入核对](docs/third-party-integration.md)。


## 2026-11-10 身份源按真实应用注册与多登录能力配置

一条身份源对应一个真实第三方应用注册及其凭据：

| 应用类型 | 可配置能力 | 凭据边界 |
| --- | --- | --- |
| 企业微信应用 | PC网页扫码、企微端内网页授权、移动SDK、关联小程序登录 | 同一CorpID、AgentID和应用Secret；小程序必须实际关联此应用 |
| 飞书应用 | 网页OAuth、AppLink、端内JSAPI Code | 同一App ID/Secret；AppLink回调由接入代码通过provider_redirect_uri传递，IAM按Client白名单及平台PKCE校验 |
| 钉钉应用 | 网页OAuth、移动SDK授权、端内免登 | 同一实际注册的Client ID（AppKey）/AppSecret及固定企业；移动SDK已按官方统一应用合同开放 |
| 微信网站应用 | 网站扫码 | 该网站应用AppID/Secret |
| 微信移动应用 | OpenSDK | 该移动应用AppID/Secret |
| 微信公众号／服务号 | 微信端内网页授权 | 该服务号AppID/Secret |
| 微信小程序 | 登录Code、手机号授权Code | 该小程序AppID/Secret；两种Code分别提交、分别兑换 |

微信四种注册必须分开建源，不提供开放平台聚合源；企微、飞书、钉钉也只在同一真实应用注册内共用凭据，不按企业ID、同名或UnionID自动合并来源。

管理页面将“应用类型”单选与“登录能力”多选分开，凭据只填写一次；不显示flow、接口版本或通讯录策略。应用类型创建后固定，修改应用身份标识需新建来源；应用Secret仍可轮换，变化递增来源版本。关闭全部能力可保留来源配置，但不能用于认证。已有显式历史合同保留，不自动切换厂商接口。

应用登录控制按能力固定registrationId，微信网站来源用于Web/SPA，服务号来源用于IN_APP，不能在同一Client混用。展示、显式发起、自动直登、回调、Code换码、目录、缓存及账号证明都复查当前能力与来源版本；不能凭客户端自报终端或registration切换凭据。应用类型先筛选能力，实际浏览器/容器再筛选网页登录入口。密码/SMS与统一登录/平台Code双入口策略保持。

存储与接口：V22新增sys_identity_provider.credential_type、capabilities_json及sys_oauth2_client.browser_capability_bindings。身份源请求新增credentialType/loginCapabilities；Client新增browserCapabilityBindings（能力代码→registrationId）。来源能力JSON只保存服务器选定的合同，密钥留在原加密列。Client网页关联使用受控JSON文本，与旧关联字段保持字符串CAS语义。

升级时只等价登记原来已有能力：企微旧网页原本同时具备PC与端内网页；微信小程序原本具备两种Code。不会自动为旧企微来源添加SDK或小程序，也不会改写registrationId、AppID、密文、Client或用户绑定。旧PUT省略能力保留原值，显式空集合表示关闭，未知/重复字段拒绝；新旧网页登录关联冲突拒绝，已使用新关联的配置不能被旧页面静默覆盖。旧state缺少能力标记要求重新发起。迁移追加执行，不改V21及之前校验和；旧服务不理解新能力合同，启用新写入口后不能仅回滚单个旧镜像。

企微手机号/邮箱敏感授权继续按用户此前决定暂缓；多能力不等于增加字段读取权限。真实第三方Code、设备和业务小程序回跳桥接仍须联调。本轮代码已完成本地验证，未把V22应用到运行中业务库，也未更新运行镜像。


## 多条首次绑定与AppLink代码回调（2026-11-10增量）

身份源可同时配置手机号→手机号、邮箱→邮箱等多条规则，线格式为`bindingMappings: [{"sourceField":"mobile","targetField":"mobile"},{"sourceField":"email","targetField":"email"}]`。仅服务端核验属性参与匹配；至少一条命中且所有命中指向同一IAM用户时自动绑定。任一规则多账号或不同规则命中不同账号，转现有账号证明；缺少属性/无命中不贡献候选。已有有效绑定优先，不回写用户资料或合并账号，企微敏感资料授权继续暂缓。

V23只新增nullable `binding_mappings_json`。NULL按旧单条合同读取；显式空集合表示关闭时清空。新字段省略时更新保留原集合，null、未知/重复JSON属性或重复映射拒绝；旧客户端不能把多条规则截断为一条。启用自动绑定必须至少一条，规则必须适用于来源所有启用能力；修改继续版本CAS并使旧证明失效。配套更新System/IAM/Admin后再启用多条配置，回退前由管理员明确缩为一条或关闭自动绑定，不能混用旧IAM。

身份源页面不再填写AppLink回调。业务App代码发起飞书授权时使用飞书后台已登记的`redirect_uri`；将Code交IAM `external_code`时提交同一原地址的`provider_redirect_uri`及专用`provider_code_verifier`。该HTTPS地址须在当前IAM Client现有回调白名单中精确登记，不能使用其他Client、前缀或通配。纯external_code的AppLink应用也显示该白名单，无需开启IAM授权码登录；混合客户端可保留其他合法用途的本地回调，但它们不能作为AppLink参数。

旧来源`oauth_redirect_uri`列暂留且不再参与AppLink校验/换码；旧调用缺少新条件参数返回invalid_request，调用方需同步升级。网页OAuth仍由IAM代码生成自身回调；JSAPI及其他平台不接受AppLink专用回调/PKCE。`provider_redirect_uri`只作为固定飞书Token端点的请求参数，不是IAM请求的目标URL。此说明替代上文历史“来源固定回调/单条映射”描述；没有真实设备验证时，单元/Mock通过不代表平台联调完成。

## 应用类型与登录能力边界（2026-11-10）

身份源描述第三方后台实际创建的应用及其能力；业务应用类型决定可以关联哪种能力。同一个企微、飞书、钉钉注册可提供多种能力，业务Client仍按下表选用。

| 业务应用类型 | 第三方能力及数量 | IAM页面 |
| --- | --- | --- |
| 标准Web `WEB_SERVER` | 多个通用网页来源：微信网站、企微浏览器、飞书网页、钉钉网页、GitHub | 内置密码/短信，下方显示第三方图标；不按UA自动直登 |
| 单页Web `SPA` | 同标准Web；授权码使用S256 PKCE | 同标准Web |
| 移动客户端 `NATIVE` | 仅一个移动SDK或AppLink能力、一个固定来源 | 可另开内置密码/短信入口；无第三方网页图标 |
| 小程序 `MINIAPP` | 仅一个本平台小程序来源；微信登录Code/手机号Code可同时关联同一来源 | 可另开内置入口；无第三方网页图标，业务方负责回传桥接 |
| 端内应用 `IN_APP` | 启用时恰一个端内能力：企微/公众号网页授权，或飞书/钉钉JSAPI Code | 无标准页；企微/微信在对应容器直接进入平台授权；JSAPI由业务前端取码 |
| 后端服务 `SERVICE` | 无用户身份源；OAuth2.1仅`client_credentials`及业务Scope | 无用户登录；界面只保留客户端认证、Scope、Token参数和示例 |

直接Code均使用`urn:macula:params:oauth:grant-type:external_code`。移动与小程序选择来源不关闭内置入口；“排他”约束第三方来源及能力，不覆盖IAM内置登录。端内JSAPI使用`none`认证且不创建浏览器SSO；端内网页使用授权码与S256，可保留服务端机密认证。用户绑定、目录准入、MFA、防重放及来源版本校验继续执行。

旧配置不会自动猜测类型或删除用户绑定。不兼容渠道会显示待整改并拒绝发起/回调，移动/小程序合法内置入口仍可用。只有既有Web/SPA业务Client可通过“转换为端内应用”选择唯一能力、预览确认后提交完整CAS快照；保留Client ID、原凭据哈希和用户绑定，递增授权版本。`none`不启用保留的旧Secret认证。平台管理身份、资源服务器、跨租户/用户目录转换及反向转换均拒绝。

普通IAM会话不能直接签端内授权码：仅本次授权请求临时隔离缺失或陈旧的平台证明，不清空其他应用SSO。证明绑定当前Client及授权版本、注册/能力/来源版本/Flow和已认证用户；授权、绑定、MFA及Token发布点复核。新旧state/pending不可跨转换继续使用。未在真实平台设备联调前，本地Mock和安全链测试不代表厂商端到端验收。

后端服务无需选择用户目录，不提供访问授权中的用户类型分区；认证租户和业务Scope仍必需。原列已允许空值，无额外Schema变更，也不填入虚构的员工目录。
