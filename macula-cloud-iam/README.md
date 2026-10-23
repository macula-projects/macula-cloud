# Macula Cloud IAM 认证中心

Macula Cloud IAM 为 Web、移动 APP、小程序和服务间调用提供统一认证与授权能力。应用只需要接入
OAuth 2.1 / OpenID Connect（OIDC），无需分别维护账号密码、短信验证码、第三方登录和 Token 生命周期。

IAM 负责完成用户认证、授权确认、第三方身份绑定和 Macula Token 颁发；业务应用负责维护自己的页面状态，
并在认证完成后使用 `state` 和回调地址恢复原业务页面。

## 主要能力

- OAuth 2.1 授权码登录，公共客户端强制使用 S256 PKCE。
- OIDC Discovery、JWKS、ID Token、UserInfo 和标准退出。
- 用户名、手机号或邮箱配合密码登录。
- 手机号或邮箱验证码登录。
- GitHub、企业微信、飞书、钉钉和微信等第三方身份登录与账号绑定。
- 移动 APP、微信/企微小程序以及飞书/钉钉端内网页把已经取得的第三方短期凭证交给 IAM，
  一步换取 Macula access token。
- `client_credentials` 服务身份认证，以及 access token 刷新、撤销和内省。
- 按 OAuth Client 隔离租户、用户类型、回调地址、scope 和允许使用的第三方凭证类型。

访问令牌默认使用 reference（opaque）格式；有明确需要的 Client 可以配置为 JWT。ID Token 始终是签名
JWT，不能代替 access token 调用业务 API。

## 如何选择接入方式

| 接入场景 | 推荐方式 | 适用说明 |
| --- | --- | --- |
| 浏览器网站、管理后台、普通 H5 | `/oauth2/authorize` + authorization code + S256 PKCE | 由 IAM 展示登录页、处理登录和授权，再回到应用登记的页面 |
| 原生 APP，希望由 IAM 统一展示登录方式 | 系统浏览器打开 `/oauth2/authorize` + S256 PKCE | 通过 Universal Link、App Link 或自定义 Scheme 回到 APP |
| 企业微信端内网页 | `/oauth2/authorize` + authorization code + S256 PKCE | IAM 识别企业微信环境后续转企业微信网页 OAuth，认证完成后恢复原授权请求 |
| 微信公众号内网页 | `/oauth2/authorize` + authorization code + S256 PKCE | IAM 续转公众号网页授权，认证完成后继续原来的 OAuth 授权请求 |
| APP 已通过 AppLink 或第三方 SDK 取得 code/token | `external_code` | 把第三方短期凭证直接提交给 IAM；仅适用于下文明确支持的类型 |
| 微信、企微小程序 | 小程序端先取得平台 code，再使用 `external_code` | 不打开 IAM 登录页，也不从 `/oauth2/authorize` 发起认证 |
| 飞书、钉钉端内网页 | JSSDK 取得 code 后使用 `external_code` | JSSDK 由业务前端集成，IAM 只负责服务端核验和登录 |
| 后端服务调用 | `client_credentials` | 代表应用自身，不代表最终用户 |

同一个 APP OAuth Client 可以同时启用 `authorization_code` 与 `external_code`：前者适合需要 IAM
统一登录体验的场景，后者适合端侧已经取得第三方凭证的场景。微信和企微小程序只使用后者，第三方 code
与 IAM authorization code 不能混用。

## 接入前准备

在 System 管理端创建 OAuth Client，并根据使用场景配置：

1. 设置唯一的 `client_id`、允许的 grant、scope、租户和用户类型。
2. 使用 `/oauth2/authorize` 时，登记完整且精确的 HTTPS 回调地址。
3. 浏览器、APP 和小程序等公共客户端将认证方式设为 `none`，并启用 S256 PKCE。
4. 在管理端先选择终端类型（浏览器、APP、微信小程序、企业微信小程序或端内网页），再选择该终端允许的登录
   方式。管理端会把业务选择映射为对应的网页登录提供商或 `code_type`，无需直接填写协议枚举。
5. 使用 `/oauth2/authorize` 时，如需一键直达，可从已允许的网页登录方式中选择一个直接登录提供商；留空则
   由用户在登录页选择。
6. 浏览器需要直接调用验证码或 Token 端点时，登记精确的 Origin；生产环境不要配置通配来源。

未配置的 grant、回调地址、scope、Origin、第三方 `code_type` 或网页登录提供商默认禁止使用。旧 Client
升级后网页登录提供商列表为空，只显示本地账号、手机和邮箱认证，不会自动继承平台全局启用的第三方方式。

管理端的终端类型只用于组织配置，不是新的 OAuth2 协议字段。同一个 Client 可以依次配置多个终端；企业微信
和微信的普通浏览器、端内网页共享各自的网页认证能力。`code_type` 仍是端侧调用 `/oauth2/token` 时必须提交的
协议参数，由接入文档和 SDK 使用，管理员通常不需要在 Client 表单中直接选择它。只有兼容特殊历史接入时，
才需要展开高级协议配置查看底层值。

## 标准授权码登录

浏览器网站、需要 IAM 统一登录体验的 APP，以及企业微信或微信公众号内网页，应从
`/oauth2/authorize` 开始认证。IAM 会保存授权请求，在用户完成账号密码、验证码或第三方登录后继续原请求，
并跳回 OAuth Client 登记的 `redirect_uri`。微信和企微小程序不使用本流程，而是在小程序端取得平台 code
后调用 `external_code`。

应用首先生成随机 `code_verifier`，再使用 SHA-256 和 Base64URL（无填充）生成 `code_challenge`：

```text
GET /oauth2/authorize
    ?response_type=code
    &client_id=<client_id>
    &redirect_uri=<registered_redirect_uri>
    &scope=openid%20profile
    &state=<random_state>
    &nonce=<random_nonce>
    &code_challenge=<s256_challenge>
    &code_challenge_method=S256
```

回调收到 `code` 后，调用 Token 端点：

```text
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&client_id=<client_id>
&code=<iam_authorization_code>
&redirect_uri=<registered_redirect_uri>
&code_verifier=<original_code_verifier>
```

应用必须校验回调中的 `state`。请求了 `openid` 时，还应校验 ID Token 的签名、issuer、audience、有效期
和 `nonce`。业务 API 始终使用 access token。

### 应用如何恢复登录前页面

业务应用在发起授权前，将当前路由或业务上下文保存在自己的安全存储中，并使用不可预测的 `state` 建立
关联。IAM 认证完成后只回到登记的 `redirect_uri`；应用验证 `state` 后，再恢复登录前页面。不要把任意
回跳 URL 直接放入未签名、未校验的参数中。

## 已取得第三方凭证时登录

当飞书 AppLink、其他 APP SDK、飞书/钉钉端内 JSSDK、小程序 API 或受支持的第三方授权页面已经返回
短期 code/token 时，公共 OAuth Client 可以调用 `/oauth2/token`：

```text
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:macula:params:oauth:grant-type:external_code
&client_id=<client_id>
&code_type=<external_code_type>
&code=<provider_code_or_token>
&scope=<requested_scopes>
```

`external_code` 是端侧已取得第三方凭证后的一步登录方式，不是 IAM authorization code 流程，因此不携带
`code_verifier`。它只签发 access token，不签发 refresh token 或 ID Token，也不接受 `openid` scope。

每个第三方凭证都是短期、一次性的 bearer credential。端侧应在取得后立即通过 HTTPS 提交给 IAM，不要
写入日志、URL、埋点或持久化存储；失败后重新向平台申请凭证，不要重复提交旧值。

## 第三方平台接入矩阵

下表列出当前支持的第三方接入方式。端侧已经取得凭证的场景使用对应 `code_type` 调用 `external_code`；
GitHub、企业微信和微信公众号等由 IAM 管理浏览器认证闭环的场景从 `/oauth2/authorize` 进入，因此没有
`code_type`。不同场景不能混用应用的 App ID、Secret、回调地址或 code。

| 终端产品 | 平台与场景 | IAM 接入方式 | 第三方凭证来源 | IAM 认可的账号标识 | 官方文档 |
| --- | --- | --- | --- | --- | --- |
| 原生 APP | 飞书登录 | `FEISHU_APP_CODE` | 通过飞书 AppLink 拉起授权并取得 code | `employee_no` | [移动应用登录流程](https://open.feishu.cn/document/uAjLw4CM/uYjL24iN/mobile-app/mobile-app-overview) |
| 原生 APP | 企业微信登录 | `WECOM_APP_CODE` | 企业微信登录/分享 SDK | 成员 `userid` | [企业微信终端 SDK：通过 code 获取用户信息](https://developer.work.weixin.qq.com/document/path/91193#%E9%80%9A%E8%BF%87code%E8%8E%B7%E5%8F%96%E7%94%A8%E6%88%B7%E4%BF%A1%E6%81%AF) |
| 原生 APP | 钉钉登录 | `DINGTALK_APP_CODE` | 钉钉授权登录 SDK | 组织成员 `userId` | [iOS 应用授权登录接入流程](https://open.dingtalk.com/document/dingstart/mini-app-procedures-for-authorized-logon-to-ios-applications#title-nkm-iuj-ieo) |
| 原生 APP | 微信开放平台登录 | `WECHAT_OPEN_APP_CODE` | 微信移动应用 SDK | `unionid` | [移动应用微信登录](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html) |
| 原生 APP | 运营商一键登录 | `ALIYUN_MOBILE_TOKEN` | 阿里云号码认证 SDK | 已核验手机号 | [Android 客户端接入](https://help.aliyun.com/zh/pnvs/developer-reference/the-android-client-access) |
| 小程序 | 企业微信账号登录 | `WECOM_MINIAPP_CODE` | `wx.qy.login()` | 成员 `userid` | [`wx.qy.login()`](https://developer.work.weixin.qq.com/document/path/91506) |
| 小程序 | 微信账号登录 | `WECHAT_MINIAPP_LOGIN_CODE` | `wx.login()` | `unionid` | [`wx.login`](https://developers.weixin.qq.com/miniprogram/dev/api/open-api/login/wx.login.html) |
| 小程序 | 微信手机号登录 | `WECHAT_MINIAPP_PHONE_CODE` | 手机号授权组件返回的独立 code | 已核验手机号 | [手机号快速验证](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html) |
| 端内网页 | 飞书网页应用 | `FEISHU_WEBVIEW_CODE` | 飞书网页应用增量授权取得 code | `employee_no` | [网页应用增量授权接入指南](https://open.feishu.cn/document/sso/web-application-end-user-consent/webapp-incremental-authorization-access-guide) |
| 端内网页 | 钉钉企业内部应用 | `DINGTALK_WEBVIEW_CODE` | 企业内部网页应用免登取得授权码 | 组织成员 `userId` | [企业内部网页应用免登](https://open.dingtalk.com/document/development/enterprise-internal-application-logon-free) |
| 端内网页 | 企业微信网页应用 | `/oauth2/authorize`（无 `code_type`） | IAM 续转企业微信网页 OAuth | 成员 `userid` | [构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91022) |
| 端内网页 | 微信公众号网页 | `/oauth2/authorize`（无 `code_type`） | IAM 续转公众号网页授权 | `unionid` | [网页授权](https://developers.weixin.qq.com/doc/service/guide/h5/auth.html) |
| 普通浏览器 | GitHub 登录 | `/oauth2/authorize`（无 `code_type`） | IAM 续转 GitHub OAuth Web application flow | GitHub 数值用户 `id` | [Authorizing OAuth apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps) |
| 普通浏览器 | 飞书网站登录 | `/oauth2/authorize`（无 `code_type`） | IAM 续转飞书 OAuth 授权页 | `employee_no` | [获取授权码](https://open.feishu.cn/document/common-capabilities/sso/api/obtain-oauth-code) |
| 普通浏览器 | 钉钉第三方网站登录 | `/oauth2/authorize`（无 `code_type`） | IAM 续转钉钉第三方网站登录 | 组织成员 `userId` | [网页方式登录应用（第三方网站）](https://open.dingtalk.com/document/development/tutorial-obtaining-user-personal-information) |
| 普通浏览器 | 企业微信 Web 登录 | `/oauth2/authorize`（无 `code_type`） | IAM 续转企业微信 Web 登录 | 成员 `userid` | [Web 登录组件](https://developer.work.weixin.qq.com/document/path/98152) |
| 普通浏览器 | 微信开放平台网站登录 | `/oauth2/authorize`（无 `code_type`） | IAM 续转网站应用扫码登录 | `unionid` | [网站应用微信登录](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html) |

企微小程序当前只支持关联到企业自建应用的模式。需要 `suite_access_token` 的服务商第三方小程序属于另一套
注册和授权模型，不能使用 `WECOM_MINIAPP_CODE`。

微信和企微小程序必须先在小程序端分别通过 `wx.login()`、手机号授权组件或 `wx.qy.login()` 取得 code，
再提交给 IAM；小程序不跳转 IAM 登录页，也不使用 IAM 的 `/oauth2/authorize` 获取第三方 code。

飞书原生 APP 不再接入已废弃的授权登录 SDK，而是使用 AppLink 拉起飞书授权并取得 code。企业微信原生
SDK code 使用企业微信终端 SDK 专用的用户信息接口核验；该接口响应字段虽然写作 `UserId`，IAM 对外仍
统一表达为成员 `userid`。钉钉原生 SDK code 先换取登录用户访问凭证，再将用户映射为所配置组织内的
`userId`。飞书 AppLink code、其他 APP SDK code 与端内 JSSDK code 必须使用各自的 `code_type`，不能混用。
`FEISHU_BROWSER_CODE`、`DINGTALK_BROWSER_CODE`、`WECOM_BROWSER_CODE`、`WECHAT_OPEN_BROWSER_CODE` 仅用于
由业务前端自行取得第三方浏览器 code 后直接兑换 IAM Token 的高级兼容接入；普通浏览器的默认方案仍是从
`/oauth2/authorize` 进入，由 IAM 完成第三方跳转、回调和应用页面恢复。

GitHub 登录由 IAM 的浏览器认证闭环处理，从 `/login/github` 发起；它不使用 `external_code`，也没有
`GITHUB_BROWSER_CODE`。

## 本地账号与验证码登录

IAM 登录页允许用户输入用户名、手机号或邮箱，并配合密码登录，无需额外传入 `identifier_type`。手机号和
邮箱也可以分别使用验证码登录。账号标识必须唯一；不存在、重复、禁用或不属于当前 OAuth Client 用户范围
的账号都会被拒绝。

对于端侧新接入，账号密码登录应进入 `/oauth2/authorize`，认证完成后使用 authorization code + S256
PKCE 返回端侧。`password` grant 仅用于兼容已有受支持客户端，不应作为新的 APP 登录方案。

需要在自定义页面申请短信或邮箱验证码时，先申请一次性 CAPTCHA，再调用对应验证码接口。公开接口统一返回
已受理结果，不会透露账号是否存在。

## 第三方账号绑定

第三方身份已经绑定本地账号时，IAM 直接按当前 OAuth Client 的租户和用户类型加载账号并签发 Token。

身份尚未绑定时，Token 端点返回：

```json
{
  "error": "binding_required",
  "binding_ticket": "<short-lived-opaque-ticket>"
}
```

客户端随后让用户通过同一个 OAuth Client 完成本地账号认证，再携带该用户 access token 和
`binding_ticket` 调用 `/api/v1/external-identity-bindings`。绑定完成后重新获取第三方 code 并再次登录。

微信小程序手机号 code 和阿里云号码认证 Token 得到的是手机号：手机号唯一匹配现有账号时可以直接登录；
没有匹配时进入手机号绑定流程。系统不会根据昵称、头像、邮箱相似度自动绑定，也不会自动创建账号。

## 浏览器第三方登录入口

通过 `/oauth2/authorize` 进入 IAM 后，用户只能看到当前 OAuth Client 明确允许且当前可用的第三方方式。
Client 可以选择保留登录方式选择页，也可以配置一个直接登录提供商。企业微信内网页优先进入企业微信网页
OAuth，微信公众号内网页优先进入公众号网页授权；这两个环境路由优先于普通的直接登录配置。认证失败返回
登录页时不会再次自动跳转，以便显示错误原因并允许用户改用本地认证。以下 `/login/...` 路径是 IAM 认证
闭环内部使用或排障时使用的明确入口，同样受当前 OAuth Client 允许列表约束，不是微信或企微小程序的接入地址：

| 平台 | 自动选择 | 普通浏览器 | 平台端内网页 | 回调地址 |
| --- | --- | --- | --- | --- |
| GitHub | `/login/github` | `/login/github` | — | `<issuer>/login/github` |
| 企业微信 | `/login/wecom` | `/login/wecom/browser` | `/login/wecom/webview` | `<issuer>/login/wecom` |
| 飞书 | `/login/feishu` | `/login/feishu/browser` | — | `<issuer>/login/feishu` |
| 钉钉 | `/login/dingtalk` | `/login/dingtalk/browser` | — | `<issuer>/login/dingtalk` |
| 微信 | `/login/wechat` | `/login/wechat/browser` | `/login/wechat/webview` | `<issuer>/login/wechat` |

飞书和钉钉端内网页由业务前端通过 JSSDK 取得 code，再直接使用 `FEISHU_WEBVIEW_CODE` 或
`DINGTALK_WEBVIEW_CODE` 调用 `external_code`；IAM 不提供对应的 `/login/.../webview` 入口，也不注入
JSSDK 或配置桥接页。企业微信和微信公众号端内网页仍使用官方 URL OAuth 跳转，不依赖 JSSDK 获取登录
code。

## 常用端点

| 路径 | 用途 |
| --- | --- |
| `/login` | IAM 登录页 |
| `/oauth2/authorize` | OAuth 2.1 / OIDC 授权入口 |
| `/oauth2/token` | authorization code、external code、刷新及兼容 grant 换取 Token |
| `/oauth2/introspect` | 查询 Token 状态 |
| `/oauth2/revoke` | 撤销 access token 或 refresh token |
| `/.well-known/openid-configuration` | OIDC Discovery |
| `/oauth2/jwks` | JWT 验签公钥集合 |
| `/userinfo` | 查询当前用户已授权的信息 |
| `/connect/logout` | OIDC 退出 |
| `/api/v1/oauth2/login-challenges/captcha` | 申请验证码登录所需的一次性 CAPTCHA |
| `/api/v1/oauth2/sms-codes` | 申请短信验证码 |
| `/api/v1/oauth2/email-codes` | 申请邮箱验证码 |
| `/api/v1/external-identity-bindings` | 完成第三方身份或手机号绑定 |

## 部署配置

| 环境变量 | 说明 |
| --- | --- |
| `IAM_ISSUER_URI` | 外部应用和第三方平台可以访问的稳定 issuer；反向代理在 `/iam` 时应包含该前缀 |
| `IAM_TOKEN_CORS_ALLOWED_ORIGINS` | 允许跨域调用 Token 端点的精确来源，多个值使用英文逗号分隔；留空表示禁用 |
| `IAM_SIGNING_KEY_LOCATION` | 签名 keystore 的 Spring Resource 地址 |
| `IAM_SIGNING_KEY_ALIAS` | 签名密钥 alias，默认 `jose` |
| `IAM_SIGNING_KEY_PASSWORD` | keystore 密码，由部署环境注入 |
| `MACULA_CRYPTO_PASSWORD` | IAM 与 System 共享的身份源秘密字段加密密码 |

生产环境必须使用独立签名密钥和 HTTPS。反向代理应覆盖客户端提交的 `Forwarded`、`X-Forwarded-*`，并
传递真实协议、主机和路径前缀。第三方平台后台登记的回调地址必须与公开 issuer 完全一致。

## 开发验证

在仓库根目录执行 IAM 及其依赖模块测试：

```bash
TEST_REDIS_SERVER=/path/to/redis-server mvn -pl macula-cloud-iam -am test -Plocal
```

测试结果只能证明本地协议和代码行为。第三方平台的应用审核、权限、可见范围、回调域名以及真实设备登录，
仍需使用各平台测试应用完成联调验收。
