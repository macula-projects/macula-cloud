# IAM统一认证与第三方接入核对

更新日期：2026-11-09。本地实现按已接受的[统一认证补齐计划](../../.sdlc/iam-unified-auth-provider-completion/plan.md)推进；以下明确区分本次修订、初审记录与未完成能力，不代表部署或真实平台联调完成。

## 现状、缺口、实施范围

现状：保留标准浏览器授权与`external_code`两条链路，复用现有认证、账号解析、绑定、Client准入和Token签发。核心结构已具备，不应重建IAM或另建平台登录Token接口。

本次已修订：钉钉Token请求字段、明确flow版本、消费状态与防重放、加密的应用Token缓存、组织/通讯录准入及后台刷新、绑定事务边界、Client/IP限流、管理端具体能力选择与MFA发布前门禁。复用既有Verifier/Registry/AccountResolver/Binding/Issuer；没有增加另一组登录Token接口。

尚未完成：飞书AppLink与requestAccess的客户端版本合同/真实兼容性，四平台真实企业与设备联调；标准浏览器自定义主认证进入OIDC后的auth_time适配另有待确认补充设计。未核实的飞书flow保留禁用配置，不能将禁用当作能力完成。Access Token格式、资源aud、公共客户端Refresh策略及既有OIDC扩展保持原合同。

## 初审时的仓库与工作区（2026-11-08历史快照）

| 项目 | 本轮核对结果 |
| --- | --- |
| 远端 | `git@github.com:macula-projects/macula-cloud.git` |
| 分支 | `main` |
| HEAD、origin/main、只读ls-remote查询 | 均为`3142653c0b371d6c62a1e7a42467c20a375813f4` |
| 基线提交 | Merge pull request #30 from macula-projects/feat/application-apikey-management |
| 核对开始的工作区 | `git status --short`为275项；包括已修改、删除和未跟踪文件，不能视为本轮新增 |
| 初审源码操作 | 当时无；后续实施另见本文件与增量基线，不将用户已有改动算作本次新增 |
| 本轮Git/环境操作 | 没有提交、推送、部署或业务数据库迁移；未fetch/checkout/reset |

远端已有：`external_code`及Registry/Verifier/ReplayStore/AccountResolver/Issuer，四平台Client和适配器，密码/短信托管认证，标准授权码/OIDC，原有Client与来源管理。

本地已有改动：`IdentitySourceType`和registration稳定userId绑定、统一来源绑定策略、单协议/资源用途/授权版本、Admin OIDC授权码、OIDC `external_code`、V14—V19相关材料等。部分迁移在Java目录；远端资源目录最高为V13。这些能力不能当作本轮新增，也不能声称已发布到远端。

已读AGENTS、目标模块README及架构/后端/前端/测试规则。旧的双协议、旧绑定及“所有直接登录都无ID Token”的材料已被后续接受的单协议和OIDC修订取代。优先复用：

- [具体来源与统一绑定计划](../../.sdlc/iam-client-protocol-scope-channels/plan.accepted-unified-binding-20261106.md)。
- [资源边界及单协议计划](../../.sdlc/iam-resource-access-boundaries/plan.md)。
- [已接受的OIDC第三方Code补充计划](../../.sdlc/iam-resource-access-boundaries/oidc-external-code-plan.md)。

这些增量后来由统一认证补齐计划接受；历史接受记录保留，不自行批准后续补充设计。

## 已具备的认证边界

| 要求 | 当前实现及核对结论 |
| --- | --- |
| 两条链路 | `ProtocolOAuth2Configuration`保留托管认证/原生授权端点及现有扩展；无按平台独立签Token的新体系 |
| Client/grant/Scope | 请求级RegisteredClient按唯一有效协议投影；草稿、非法grant/Scope及机密Client的external_code被拒绝 |
| 具体registration | `MaculaRegisteredClientRepository`读取Client关联表；Verifier读取确切ID及SourceType，非全局同厂商择一 |
| 可信身份 | `VerifiedExternalIdentity`区分registration、SourceType、capability、identityKind；微信openid不强求unionid，手机号证明不伪装永久主体 |
| 本地账号 | 浏览器和SDK复用`RegistrationIdentityBindingService`、`IdentitySourceBindingPolicyService`；稳定userId、当前账号状态、可信租户和Client范围重校验 |
| 自动绑定 | 默认关闭；按来源明确开启及选择凭据；属性缺失/零候选/多候选走受控手动绑定，不按昵称或展示邮箱合并 |
| 绑定异常分支 | `binding_required/binding_uri/binding_ticket`；Token API不302；密码/SMS证明用途隔离；完成绑定后重新取厂商Code |
| OIDC扩展 | `none + external_code + openid`返回ID Token与Access Token；可选nonce只关联请求响应；复用真实签名器，sub为本地稳定身份，ID Token aud为client_id |
| OAuth2.1 | 不允许openid，不签ID Token；支持目录内profile；不恢复password grant |
| PKCE | 原生授权码采用S256；external_code只有配置requireProofKey，未执行IAM challenge/verifier校验，不能声称有PKCE保护 |
| 资源边界 | Access Token aud来自服务端Scope映射；ID Token不能作API凭据；Gateway内省身份独立；API沿用原Starter角色/租户链路，不增加Cloud资源拦截或System二次内省 |
| 凭据 | 平台Secret、用户Token、session_key没有被当作IAM Token下发；当前适配器白名单日志与异常脱敏继续保留 |

上述为实现和本地验证证据，不是生产运行或真实厂商验收。

## 初审发现记录（历史问题；修订结果见下节）

本节保留发现背景，以下“当前／尚无／建议”描述2026-11-08初审时点，不能作为本次实现状态。

### 1. 钉钉请求体字段错误，现有Mock未拦住

定位：`authentication/dingtalk/DingtalkClient.java:158`与`:169`。

- `/v1.0/oauth2/userAccessToken`当前发送`grant_type`，官方SDK发送`grantType`；`clientId/clientSecret/code`应保留其对应含义。
- `/v1.0/oauth2/accessToken`当前发送`clientId/clientSecret`，官方SDK发送`appKey/appSecret`。
- `DingtalkClientTest`核验了请求URL、模拟响应和最终成员结果，没有断言以上请求体，所以绿灯不能证明真实平台合同正确。

本轮独立读取官方发布的`@alicloud/dingtalk@2.2.58`包，`dist/oauth2_1_0/client.js`中`getAccessTokenWithOptions`与`getUserTokenWithOptions`明确映射字段和对应URL。参考：[官方SDK发布元数据](https://registry.npmjs.org/@alicloud%2fdingtalk/2.2.58)、[官方SDK发布包](https://registry.npmjs.org/@alicloud/dingtalk/-/dingtalk-2.2.58.tgz)。临时提取文件只用于核对，不作为项目新依赖。

建议：先按官方合同修复并增加请求体精确断言，覆盖APP/BROWSER/WEBVIEW，防止把OAuth、企业免登、旧SNS code混用。

### 2. 飞书AppLink尚不具备完整关联材料合同

定位：`authentication/feishu/FeishuClient.java:52`、`:77`、`:93`，`FeishuExternalCodeVerifier`及现有Converter白名单。

- 浏览器授权URL使用`app_id`；应与所选官方授权接口的`client_id`合同重新核对，不能以旧Mock固化错误。
- APP分支调用`oauthAccessToken(config, code, null)`，没有redirect URI；固定采用`authen/v2/oauth/token`。
- WEBVIEW采用`app_access_token/internal → authen/v1/access_token → user_info`，没有显式flow版本。
- external_code白名单没有第三方redirect URI/code_verifier，内部Verifier接口也没有这类受控材料；不存在能证明飞书移动PKCE已校验的执行链。
- 因此不能把`FEISHU_APP_CODE`可选或`requireProofKey=true`当作AppLink完整支持。

建议：核实requestAccess、浏览器授权及AppLink分别适配的Token版本；明确来源flow及配置合同，必要时对现有扩展做最小、受控的关联材料补充。不能直接接收任意上游URL/Secret，不能失败后盲试另一版本，也不能静默删除已有来源配置。v3文档链接本轮无可读正文，具体参数必填性仍待核实。

### 3. 防重放没有区分平台兑换结果不确定

定位：`OAuth2ExternalCodeAuthenticationProvider.java:77`和各平台Client的`RestClientException/5xx`处理。

- 现有原子SET-NX、HMAC摘要、owner compare-and-delete均保留。
- 目前平台网络错误/5xx统一抛`ExternalCodeInfrastructureException`，Provider随即释放占用。
- 飞书还可能在Code已换得用户Token、后续user_info失败时释放；读取超时也无法证明平台没有消费Code。
- 重新占用并重试兑换不等于必然重复签发，但现有语义依赖上游一次性保证，不能作为“结果不确定时安全重试”的证据。

建议：区分未提交凭据、兑换结果不确定、已兑换后的资料查询失败。默认保留不确定/已消费占用，仅在证明尚未向上游提交Code时允许受控释放；先修订旧计划中统一网络/5xx释放的约定，不直接修改批准记录。

摘要当前是`code_type + code`，全Client共享，可保守防止同类型Code跨Client复用；不能为“作用域优化”随意加入client_id从而放宽重放。默认TTL为10分钟，需按各Code/阿里云Token真实有效期核实覆盖范围及配置上下界。未在external_code路径发现独立Client/IP限流；短信/CAPTCHA的限流不能证明Token换码入口也受保护，需补证或最小补齐。

### 4. 缺少第三方应用Token缓存

定位：`WecomClient.fetchAccessToken`、`DingtalkClient.appAccessToken`、`FeishuClient.webviewAccessToken`、`WechatClient.fetchMiniappMobile`。

每次登录都重新获取应用Token，未看到按registration/应用/企业隔离的缓存、到期提前刷新、并发合并或凭据轮换失效机制。`stable_token`是所调微信接口，不等于IAM已实现Token缓存。当前用户Token仅在调用内短暂使用，不应为了缓存设计而持久化全部用户Token。

建议：复用现有服务端缓存组件，按具体来源及版本隔离应用Token，轮换后失效；仅确有后续调用需要时保留用户Token并加密隔离，不下发业务客户端。

### 5. 通讯录和外部组织准入策略未建成

来源目前配置凭据、具体类型、自动绑定及规则；没有“通讯录必需/可选/后台同步”配置或执行链。

- 钉钉有unionId→目标应用成员userid的转换；缺成员、权限错误等最终仍统一失败，没有完整领域结果区分。
- 企微当前取得userid后即进入本地绑定，不调用成员详情或敏感信息接口；这不等于缺少身份验证，但不能宣称做了通讯录状态/组织规则检查。
- 飞书取user_info的open_id，employee_no允许缺失并转手动绑定；没有通讯录tenant_access_token查询，也没有来源级目标tenant_key等外部组织准入合同。
- 本地Client租户、账号启停及权限检查已经存在，不应删掉，也不能当作外部企业成员状态校验的替代品。
- 当前绑定服务仍明确限制EMPLOYEE目录；其他目录不能因页面存在选项就声称已接通第三方账号绑定。

建议：先区分认证身份字段与可选资料，按来源配置必需/可选/同步策略；只有需要的成员准入依赖通讯录结果。不要把所有手机号/邮箱/头像视为必返字段。若需要新列，追加迁移；不改V15/V19、不猜填历史组织信息。

### 6. 接入指南仍缺少平台逐步骤与能力选择

当前指南生成IAM请求，SDK分支只有通用获取Code说明。`requestExample`从`externalCodeTypes[0]`选能力，用户无法在快速开始明确选择已关联的另一个Code Type。微信小程序登录Code/手机号Code尤其不能混成一个示例。

建议：在现有快速开始增加已关联能力选择、端侧获取方式、固定registration提示、对应平台权限和故障说明；按实际协议/Scope目录生成请求，不新增平行配置页面，不把未完成的飞书flow显示为可直接接入。保留现有GitHub、阿里云能力及所有SDLC参考材料。

## 按平台与终端核对的步骤

“已有代码”表示本地存在路径，不代表已经真实联调；下表中的目标接口来自需求参考，官方正文未读到的项目仍须核实版本、权限与必填性。

| 平台/终端 | 取得凭据与服务器调用步骤 | 当前状态 |
| --- | --- | --- |
| 微信公众号H5 | ①`connect/oauth2/authorize`，appid/redirect_uri/response_type/scope/state及`#wechat_redirect`；②`/sns/oauth2/access_token`，appid/secret/code/grant_type；③需要资料且权限允许时`/sns/userinfo`，用户Token/openid/lang | 有WEBVIEW托管链；scope固定snsapi_base，未配置snsapi_userinfo/资料策略 |
| 微信网站 | ①`connect/qrconnect`，snsapi_login及state；②`/sns/oauth2/access_token`；③按需`/sns/userinfo` | 有BROWSER路径；当前只取openid，unionid可缺，未查资料 |
| 微信移动SDK | ①OpenSDK取得Code；②移动应用专属AppID/Secret换用户Token/openid；③按需资料；④`WECHAT_OPEN_APP_CODE`进入既有扩展 | 已有路径，仍需真实SDK/平台验收 |
| 微信小程序登录 | ①`wx.login()`；②`/sns/jscode2session`，appid/secret/js_code/grant_type；③openid识别，session_key不下发、unionid可缺 | `WECHAT_MINIAPP_LOGIN_CODE`已有；不是wx.checkSession换身份 |
| 微信小程序手机号 | ①用户主动getPhoneNumber取得独立PHONE_CODE；②对应小程序应用Token；③POST`/wxa/business/getuserphonenumber`，JSON code；④手机号认证/绑定策略 | `WECHAT_MINIAPP_PHONE_CODE`已有，采用stable_token及按来源版本加密缓存；不伪造永久微信subject |
| 企微端内网页 | ①`connect/oauth2/authorize`，CORPID/agentid/scope/state；②`/cgi-bin/gettoken`；③`/cgi-bin/auth/getuserinfo`；④按需`/cgi-bin/user/get`；⑤适用user_ticket时`/cgi-bin/auth/getuserdetail` | 托管路径保留；已补成员目录策略；敏感user_ticket资料接口本轮未实现 |
| 企微浏览器 | ①`wwlogin/sso/login`，CorpApp/CORPID/agentid/redirect_uri/state；②应用Token；③`auth/getuserinfo`；④按需成员详情 | 有自建应用路径；第三方应用/服务商flow未实现，不混用 |
| 企微移动SDK | ①SDK SSO code；②对应应用Token；③`/cgi-bin/user/getuserinfo`，注意UserId大小写；④按需成员详情 | `WECOM_APP_CODE`已有，端点与网页区分；需实际SDK验收 |
| 企微小程序 | ①`wx.qy.login()`；②CORPID及关联企微应用Secret取Token；③`/cgi-bin/miniprogram/jscode2session`，access_token/js_code/grant_type；④userid/适用corpid，session_key保密；⑤按需成员详情 | `WECOM_MINIAPP_CODE`已有，未套用普通微信AppID换码 |
| 飞书端内H5 | ①`tt.requestAccess()`，按版本传appID/scopeList/state；②兼容版本用户Token接口；③Bearer用户Token调`authen/v1/user_info` | 保留来源及Code Type；flow暂不可启用，requestAccess与Token版本兼容性待核实 |
| 飞书浏览器 | ①accounts.feishu.cn授权页，client_id/redirect_uri/scope/state及适用PKCE；②相同flow用户Token；③user_info | BROWSER已对齐client_id与accounts.feishu.cn/oauth/v3/token；真实平台往返待验收 |
| 飞书AppLink | ①完整OAuth URL编码进applink.feishu.cn/client/web_url/open；②App接收code并保留PKCE关联；③可信后端向所选接口提交redirect_uri及必要verifier；④user_info | 已由官方Markdown核实并开放现有AppLink适配；要求固定回调及平台S256 PKCE，真实设备待验收 |
| 飞书通讯录 | ①目标租户应用Token，按选定接口使用client_credentials等合同；②`contact/v3/users/{user_id}?user_id_type=TYPE`，Bearer租户应用Token；③身份类型匹配 | 已实现open_id查询及tenant_key围栏；租户Token采用官方SDK仍明确支持的auth/v3/tenant_access_token/internal，未猜测v3 client_credentials响应 |
| 钉钉端内企业免登 | ①`dd.runtime.permission.requestAuthCode({corpId})`；②`oauth2/accessToken`，appKey/appSecret；③`topapi/v2/user/getuserinfo`，应用Token+code；④按需`topapi/v2/user/get` | 已修正appKey/appSecret并缓存应用Token；成员资料按目录策略查询 |
| 钉钉浏览器/新版SDK | ①浏览器oauth2/auth或移动SDK取得authCode；②`oauth2/userAccessToken`，clientId/clientSecret/code/grantType；③`contact/users/me`，x-acs-dingtalk-access-token；④目标企业应用Token；⑤getbyunionid→userid；⑥按需成员详情 | 已修正字段；unionId→目标企业userid后必须核验成员详情，再按目录策略补资料；权限不足不能冒充成员 |
| 钉钉旧SNS | ①旧SDK tmp_auth_code；②`sns/getuserinfo_bycode`，accessKey/timestamp/signature和tmp_auth_code，直接返回身份 | 当前无此独立适配；未证明兼容需求，不增建、不冒充新版APP_CODE |

飞书/钉钉其他小程序flow尚无专用Code Type，不把“同平台有WEBVIEW能力”推断为所有小程序版本都支持。GitHub/阿里云原有能力继续保留，本报告不删除或降级它们。

## 当前IAM调用示例

以下仅适用于已经启用、登记对应Scope并明确关联具体来源的Client。`profile`是当前内置资料Scope；访问System还必须在该Client获准时明确请求`system:access`。示例不自动给Client增加任何权限。

### 标准浏览器OIDC

生成随机state、nonce及PKCE verifier，S256 challenge为`BASE64URL(SHA256(verifier))`。示例值必须替换，verifier只在本次流程内安全保存：

```http
GET <IAM_ISSUER>/oauth2/authorize?response_type=code&client_id=<CLIENT_ID>&redirect_uri=<URL_ENCODED_REGISTERED_CALLBACK>&scope=openid%20profile&state=<STATE>&nonce=<NONCE>&code_challenge=<S256_CHALLENGE>&code_challenge_method=S256
```

完成密码/短信/第三方网页认证后，回调端先核对state，再交换IAM授权码；这里的code不是厂商Code：

```bash
curl --request POST "$IAM_ISSUER/oauth2/token" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "code=$IAM_AUTHORIZATION_CODE" \
  --data-urlencode "redirect_uri=$REGISTERED_CALLBACK" \
  --data-urlencode "code_verifier=$PKCE_VERIFIER"
```

公共Client认证为none，不发送Secret；按可信issuer/JWKS验证ID Token签名、iss、client_id audience、有效期及nonce。标准OAuth2.1浏览器流程使用已登记Scope且不带openid，不要求ID Token。托管页短信登录不需要额外启用sms Token grant。

### 微信小程序OAuth2.1直接登录

先在应用端调用wx.login取得新的Code，然后请求IAM：

```bash
curl --request POST "$IAM_ISSUER/oauth2/token" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=urn:macula:params:oauth:grant-type:external_code' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode 'code_type=WECHAT_MINIAPP_LOGIN_CODE' \
  --data-urlencode "code=$PROVIDER_CODE" \
  --data-urlencode 'scope=profile'
```

企微小程序先wx.qy.login，改用已关联的`WECOM_MINIAPP_CODE`。微信手机号Code必须来自独立手机号能力，不能使用wx.login的Code。调用不携带client_secret，也不发送IAM PKCE参数。

### 移动SDK的OIDC扩展

客户端完成对应微信移动SDK授权，取得新Code，且Client配置为OIDC/none并关联微信移动应用来源后：

```bash
curl --request POST "$IAM_ISSUER/oauth2/token" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=urn:macula:params:oauth:grant-type:external_code' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode 'code_type=WECHAT_OPEN_APP_CODE' \
  --data-urlencode "code=$PROVIDER_CODE" \
  --data-urlencode 'scope=openid profile' \
  --data-urlencode "nonce=$CLIENT_NONCE"
```

本地实现返回Access Token及ID Token；nonce可以省略，提供时必须单值、非空、不超过256字符。验证ID Token签名、iss、aud、iat/exp以及提供时的nonce。nonce不是厂商Code与PKCE的上游绑定证明；公开client_id也不是客户端真实性证明。只从可信SDK授权回调接收Code，不从任意URL自动登录。此扩展不是标准OIDC Authorization Code Flow，不创建浏览器SSO会话，不给公共Client签发Refresh Token。

### 绑定与资源调用

未绑定时Token API返回OAuth错误`binding_required`及短期`binding_uri/binding_ticket`，不是302。打开受控入口，以现有密码/用途隔离的短信证明账号；成功后返回应用重新取得平台Code。不要复用已消费的Code或持久化票据。

API请求使用IAM Access Token：

```http
Authorization: Bearer <IAM_ACCESS_TOKEN>
```

不能使用第三方用户/应用Token、session_key或ID Token调用业务API。Secret和平台Token只在IAM服务端按用途保管。

## 配置与迁移

身份源新增字段使用同一套System表单、能力目录和IAM运行时校验：

| 字段 | 设置及边界 |
| --- | --- |
| `identityFlow` | 明确固定版本，必须匹配Source Type。缺失不猜填，不在换码失败后尝试另一个接口 |
| `directoryPolicy` | `IDENTITY_ONLY`默认；`OPTIONAL`仅容忍明确的资料权限/网络故障；`REQUIRED`必须实时确认同一企业的有效成员；`BACKGROUND`检查已存拒绝结果，仅已绑定成功身份入队刷新 |
| `organizationId` | 可信外部企业/租户域，不能由请求tenant_id/corpId覆盖。启用目录策略时必需；飞书按employee_no自动绑定也必须指定tenant_key |
| `oauthRedirectUri` | 需要上游PKCE的flow固定HTTPS回调；请求不能传任意上游回调或Token URL |

既有自动绑定开关与规则仍在具体身份源上；默认关闭。只有实时核实且规则明确的成员标识、工号或手机号可匹配；可选资料缺失不会自动合并账号。BACKGROUND旧资料不用于自动绑定。目录适配器网络调用在绑定数据库事务外；事务内只比较原始来源版本/flow/组织域和有限期准入快照。

追加迁移为System的`V20__identity_source_flow_directory_policy.sql`，只新增四列，`directory_policy`默认IDENTITY_ONLY，其余不猜填历史值。V14—V19不改。升级前备份，已有来源须明确配置flow后启用；恢复时数据与服务版本配套，不把仅回退镜像视作完整回滚。本次只在一次性MySQL验证V19→V20，未执行业务库升级。

IAM配置（通过环境变量/配置中心注入实际密钥，不能使用示例占位值运行）：

```yaml
macula:
  cloud:
    iam:
      external-code:
        digest-key: ${IAM_EXTERNAL_CODE_DIGEST_KEY}
        replay-ttl: 10m
        rate-window: 1m
        client-rate-limit: 120
        ip-rate-limit: 300
      providers:
        token-lock-lease: 15s
        token-wait: 20s
        token-expiry-skew: 60s
        member-snapshot-ttl: 5m
        refresh-lease: 30s
        refresh-max-attempts: 3
        refresh-queue-capacity: 1000
  crypto:
    enable: true
    algorithm: AES
    encode: BASE64
    password: ${MACULA_CRYPTO_PASSWORD}
```

`crypto.encode=BASE64`是密文编码；`crypto.algorithm=BASE64/DEFAULT`不能提供保密性，被缓存拒绝。应用Token、目录快照和后台任务使用既有CryptoManager加密并按用途/来源/版本做MAC；缓存中不保存平台用户Token或session_key。运行时不能因缺少缓存Bean退回逐请求明文直连。来源凭据轮换/停用使旧版本缓存和证明失效。

重放占用使用原子claim和owner比较释放。只有确定NOT_SUBMITTED且尚未取得核验身份才允许释放；UNKNOWN/CONSUMED、HTTP超时或后续账号解析失败不释放。各Code类型保守下限10分钟；阿里云一键登录Token按最长30分钟有效期采用31分钟下限，配置可以延长但不能缩短。[阿里云GetMobile官方说明](https://help.aliyun.com/en/pnvs/developer-reference/api-dypnsapi-2017-05-25-getmobile)。不是所有平台Code本身都有效10分钟。

Client和IP额度在Redis原子计数。IP取解包后的服务器连接地址，不信任任意X-Forwarded-For；经过代理时可能共享代理地址。可信代理应由容器/服务器白名单配置，不能由调用者自行指定地址绕过额度。

### 微信小程序完整调用示例

下面的CLIENT_ID、SCOPES、IAM_ISSUER来自当前Client快速开始的实际配置，SCOPES必须是其已登记值；OIDC包含openid，OAuth2.1不带openid。没有client_secret、任意aud或上游Token URL。真实小程序还需在平台管理台配置IAM HTTPS请求域名。

```javascript
// 只在内存中处理返回结果，不打印或持久化平台Code/绑定票据。
async function loginWithWechat({ clientId, scopes, issuer, nonce }) {
  const code = await new Promise((resolve, reject) => {
    wx.login({
      success: result => result.code ? resolve(result.code) : reject(new Error('平台未返回登录凭据')),
      fail: reject,
    });
  });
  const fields = {
    grant_type: 'urn:macula:params:oauth:grant-type:external_code',
    client_id: clientId,
    code_type: 'WECHAT_MINIAPP_LOGIN_CODE',
    code,
    scope: scopes.join(' '),
  };
  if (nonce) fields.nonce = nonce;
  const form = Object.entries(fields)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
  const response = await new Promise((resolve, reject) => {
    wx.request({
      url: `${issuer}/oauth2/token`,
      method: 'POST',
      header: { 'Content-Type': 'application/x-www-form-urlencoded' },
      data: form,
      success: resolve,
      fail: reject,
    });
  });
  if (response.statusCode !== 200) {
    // binding_required的受控页面单独处理；完成绑定后重新调用wx.login。
    // HTTP超时同样重新取Code，不能盲目重发已提交的旧Code。
    const failure = new Error('IAM登录未完成');
    failure.oauth = response.data;
    throw failure;
  }
  return response.data;
}
```

OIDC调用者在采用ID Token之前仍须验证签名、可信issuer、client_id受众、有效期及本次nonce；上述HTTP函数不能替代OIDC验证器。业务API使用access_token。改为企微小程序时，将取码API换成wx.qy.login，并将code_type改为已关联的WECOM_MINIAPP_CODE，其他校验和重新取码规则相同。微信getPhoneNumber授权码不能传给此登录函数。

## MFA接入点

本轮提供可替换的`MfaCompletionPolicy` Bean，默认NoOp。它只收到`AuthenticationCompletionContext`：可信Client（独立门户可能为空）、租户、内部userId、入口、实际认证方法、registration和检查点，不接收密码、原始Code或任意客户端声明。

检查点包括BROWSER_SESSION、BINDING_SESSION、AUTHORIZATION_CODE、USER_TOKEN、REFRESH_TOKEN。策略必须幂等，可能在同一流程多次执行。需要额外认证时抛`AdditionalAuthenticationRequiredException`；其他策略运行异常也闭合拒绝。浏览器会话发布前返回安全错误，Token端点保持OAuth错误，不跳302；不发布完整会话/可用授权码/Token。现有会话和匿名pending不必删除，撤销Token及client_credentials不要求用户MFA。

```java
@Bean
MfaCompletionPolicy applicationMfaPolicy(MyMfaDecisionService decisions) {
    return context -> {
        if (decisions.requiresAdditionalProof(context)) {
            throw new AdditionalAuthenticationRequiredException();
        }
    };
}
```

这是未来策略的装配示意，`MyMfaDecisionService`由后续功能实现。本轮没有因子注册、挑战页、MFA开关或成功凭据，不把普通第三方主认证标记为完成MFA，也不新增acr/amr声明。标准OIDC自定义主认证的auth_time适配待工程师接受补充设计后实施，external_code不新增该声明。

## 接入步骤与示例的使用条件

上文浏览器、wx.login、wx.qy.login、SDK示例只使用Client已登记Scope。`profile`不是所有Client必然获准；接入应用的快速开始按当前协议/Scope生成，必须先选一个已明确关联且可用的Code Type，不能自动取数组第一项，也不能生成含Secret的公共Client代码。未选择/已停用/未核实来源没有可复制成功请求。

小程序可直接提交wx.login/wx.qy.login的Code；原生SDK只接收本次平台授权回调Code；钉钉端内H5先requestAuthCode后使用DINGTALK_WEBVIEW_CODE。飞书requestAccess/AppLink暂不提供可执行成功示例。微信/企微端内网页仍可走托管标准浏览器链，不把网页Code强塞成移动SDK Code。provider_code_verifier仅对匹配的FEISHU_APP_CODE且明确要求上游PKCE的flow开放；在该flow核实启用前不得认为已支持移动PKCE。external_code本身没有IAM challenge/verifier绑定。

## 实际验证与未完成事项

具体命令、数量、环境及日志见[本次验证记录](../../.sdlc/iam-unified-auth-provider-completion/verification.md)。已覆盖精确HTTP请求、真实安全过滤链、专用Redis并发/失租恢复、一次性MySQL迁移与绑定事务、管理端单元/构建及1920/390px浏览器场景；平台HTTP和账号素材仍为Mock。

未完成：四平台真实Code、设备、安装关系和通讯录权限联调；飞书requestAccess/AppLink版本合同；企微敏感user_ticket资料补充；飞书/钉钉未建模的小程序flow；旧钉钉SNS无已确认兼容需求。本轮不伪造这些成功证据。此段原有标准OIDC认证时间缺口已在本次第三方浏览器及绑定认证记录中修复；开发验证覆盖真实SAS换Token和绑定会话记录，不能据此宣称短信或真实厂商全链路已经验收。

## 官方参考原入口及读取状态

完整保留本次需求给出的链接。以下“待核实”表示本轮工具未取得正文，不等于链接已废弃；没有证据确认迁移就不替换原链接。官方SDK为字段补证，不能代替移动端版本、权限及真实平台验收。API地址本身不是说明文档，不携带真实Code/Secret发起探测。

### 微信开放平台（本轮未取得正文，待核实）

- [端内网页授权](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/h5.html)
- [浏览器网页授权](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/web.html)
- [移动应用授权](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/app.html)
- [Code换Token接口地址](https://api.weixin.qq.com/sns/oauth2/access_token)（接口，未执行凭据兑换）

### 微信小程序（本轮未取得正文，待核实）

- [服务端code2Session](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-login/api_code2session)
- [获取手机号](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-info/phone-number/api_getphonenumber.html)

### 企业微信小程序（本轮未取得正文，待核实）

- [前端登录](https://developer.work.weixin.qq.com/document/path/91506)
- [服务端code2Session](https://developer.work.weixin.qq.com/document/path/91507)

### 飞书

- [总体登录说明](https://open.feishu.cn/document/client-docs/build-login-free-system-)（官方检索结果读到流程概述；细节仍需对应接口正文）
- [浏览器获取授权码](https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code)（正文待核实）
- [移动端App Link](https://open.feishu.cn/document/uAjLw4CM/uYjL24iN/mobile-app/integrate-via-app-link)（正文待核实）
- [端内requestAccess](https://open.feishu.cn/document/web-app/gadget-api/open-ability/login/requestaccess)（正文待核实）
- [获取user_access_token v3](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3)（正文待核实）
- [获取登录用户信息](https://open.feishu.cn/document/server-docs/authentication-management/login-state-management/get)（正文待核实）
- [获取通讯录单个用户](https://open.feishu.cn/document/server-docs/contact-v3/user/get)（正文待核实）
- 补充：[自建应用tenant_access_token](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/tenant_access_token)（官方检索结果读到v3/client_credentials的字段摘要；页面正文待核实，不据此推断所有登录Code版本兼容）

### 企业微信（本轮未取得正文，待核实）

- [端内网页](https://developer.work.weixin.qq.com/document/path/91022)
- [浏览器网页](https://developer.work.weixin.qq.com/document/path/98171)
- [移动SDK](https://developer.work.weixin.qq.com/document/path/91193)
- [获取访问用户身份](https://developer.work.weixin.qq.com/document/path/91023)
- [获取敏感信息](https://developer.work.weixin.qq.com/document/path/95833)
- [读取通讯录成员](https://developer.work.weixin.qq.com/document/path/90196)

### 钉钉（页面返回空正文或请求失败，待核实；字段另有官方SDK补证）

- [端内网页免登](https://open.dingtalk.com/document/development/enterprise-internal-application-logon-free)
- [浏览器网页登录](https://open.dingtalk.com/document/development/tutorial-obtaining-user-personal-information)
- [移动SDK](https://open.dingtalk.com/document/dingstart/mini-app-procedures-for-authorized-logon-to-ios-applications)
- [通过免登码获取身份](https://open.dingtalk.com/document/development/obtain-the-userid-of-a-user-by-using-the-log-free)
- [获取用户Token](https://open.dingtalk.com/document/orgapp/obtain-user-token)
- [获取用户信息](https://open.dingtalk.com/document/development/dingtalk-retrieve-user-information)

不得因为正文抓取失败，改用未经核实的博客、推测参数或Mock成功作为官方合同结论。没有删除、迁移或替换任何已有第三方材料。

## 身份源简化与绑定字段（2026-11-09）

管理员不选择Flow/通讯录策略。平台代码继续负责各场景固定取Code及核验合同，不能失败后换接口；绑定层只消费服务端核验属性。企微同一自建应用网页来源覆盖浏览器扫码和端内网页授权，入口及state模式独立；本轮只用成员ID，敏感手机号/邮箱授权由用户明确暂缓。成员userid不冒充业务工号。

微信小程序前端必须按实际取得凭证声明`code_type=WECHAT_MINIAPP_LOGIN_CODE`（wx.login）或`WECHAT_MINIAPP_PHONE_CODE`（getPhoneNumber）。同一小程序来源共享AppID/Secret，后端分别走jscode2session与getuserphonenumber，不从Code字符串猜类型。手机号证明不生成OpenID绑定；前端不提交可信成员、工号、手机号或邮箱作为自动绑定依据。

飞书/钉钉受权威目录和字段权限约束，自动绑定只用明确配置的一组源字段与IAM字段，并保持账号唯一、Client目录/租户准入、来源版本和事务重查。官方证据及企微敏感资料限制见[本轮证据](../../.sdlc/identity-source-simplification-binding-mapping/provider-contract-evidence.md)，原官方参考全部保留。

## 2026-11-09 终端与网页环境约束更新

应用架构、平台运行环境和Code取码方式分别校验。Web/SPA只选通用网页能力；NATIVE仅一个SDK/AppLink能力；MINIAPP仅一个本平台小程序来源；IN_APP仅一个端内能力；SERVICE不提供用户登录。业务Client使用none不代表获得所有SDK许可。完整逐接口参数、原链接及新增官方Markdown证据见[本轮复核](../../.sdlc/iam-application-configuration/research.md#2026-11-09-原始第三方文档复核与终端约束)。

微信网站及企微浏览器入口按PC合同筛选；钉钉通用网页登录已按本轮官方PC/H5合同允许手机浏览器。Web/SPA不按容器UA自动切换端内登录。IN_APP企微/公众号走固定网页授权，飞书/钉钉走JSAPI Code。错误回调不重复拉起；真实设备验收仍独立进行。

AppLink：业务App生成并保留state和平台PKCE verifier，用来源固定回调及S256 challenge构造完整授权URL，编码后交飞书AppLink；回调核对state后，将新Code和provider_code_verifier提交既有external_code。IAM使用该来源平台Secret、固定redirect_uri和verifier调用v3 Token接口；平台Secret不进入业务App。上游PKCE与IAM授权码PKCE互不替代，AppLink也不建立IAM浏览器SSO会话。

旧Client关联不会自动删除；不兼容能力在页面标记并要求显式修正，服务端对已明确架构执行相同拒绝。目录新增browserApplicationTypes / browserEnvironments及Code的applicationTypes；无新Schema或Token端点。真实平台/设备验证仍需独立完成。

## 2026-11-10 飞书端内JSAPI Code纠正与补全

用户指出飞书端内同样是Code。之前用标准网页OAuth替代端内免登，不代表FEISHU_WEBVIEW_CODE已接通；该缺口现按官方原文补齐。

证据链：
1. [官方免登步骤](https://open.feishu.cn/document/client-docs/h5/development-guide/step-3.md)及其流程图：requestAccess取得Code后交服务端核验。
2. [官方免登示例](https://open.feishu.cn/document/home/quickly-create-a-login-free-web-app/introduction-to-sample-code.md)将JSAPI Code交给v1用户Token接口。
3. [v1接口原文](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/access_token/create.md)明确历史版本，并指向v2。
4. [v2迁移说明](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token.md)明确继续迁移到[v3 Token](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3.md)。

实现：新增内部FEISHU_WEBVIEW_V3，FEISHU_WEBVIEW_CODE固定调用POST https://accounts.feishu.cn/oauth/v3/token，JSON grant_type=authorization_code / client_id / client_secret / code，再用Bearer用户Token GET https://open.feishu.cn/open-apis/authen/v1/user_info取得open_id。平台Secret只在IAM。JSAPI路径不发送浏览器redirect_uri或AppLink provider_code_verifier，不失败后尝试旧Token端点。显式旧FEISHU_WEBVIEW_V1仍不可启用，不自动升级旧记录；新建来源服务端默认唯一可用v3。

业务H5在飞书内调用tt.requestAccess({appID,scopeList:[]})；如传state须核对回调state。只有客户端不支持（如errno=103）时按官方指引兼容requestAuthCode，用户拒绝或其他失败不得盲切。JSAPI Code有效期3分钟且单次使用，提交IAM external_code时明确code_type=FEISHU_WEBVIEW_CODE。仅IN_APP可关联；Web/SPA、APP原生与小程序不接受该Code，直接换Token仍遵守既有公共Client（none）及固定来源约束。标准Web默认机密配置不使用此扩展。正常Code登录不经过IAM登录选择页，也不建立浏览器SSO会话；账号未绑定仍需证明已有账号。

来源配置页不再给JSAPI来源显示无用的IAM浏览器回调地址；隐藏Flow后按服务端唯一可用默认合同显示必要关联参数。网页OAuth及AppLink能力继续独立存在，不与JSAPI Code互换。本机证据：/private/tmp/terminal-doc-audit/feishu-webview-example.md、feishu-webview-token.md、feishu-incremental-token.md、feishu-webview-incremental.md及官方流程图feishu-webview-flow.jpg（已视检）。未取得真实飞书Code或执行企业设备授权，合同/Mock验证不能替代真实联调。


## 2026-11-10 统一登录与平台Code双入口（当前规则）
Rain明确说明小程序/移动客户端均可直接提交平台Code，也可打开IAM统一登录页，并在完整模型展示后回复“是的”。本补充保留内置统一登录与直接平台Code并存；第三方网页来源边界以文末六类矩阵为准。

- IAM统一登录与平台Code是两条独立入口。同一公共Client允许authorization_code与external_code并存；小程序可仅统一登录、仅本平台Code或两者并存。SERVICE继续禁止用户登录。公共授权码使用S256 PKCE，沿用已登记回调、state及OIDC nonce约束。
- 网页第三方来源仅用于Web/SPA。移动/小程序打开IAM页面时不显示第三方网页来源；业务方仍需处理系统浏览器或web-view的回调桥接。平台Code继续按已保存架构、平台、具体registration校验，拒绝客户端自报终端绕过。
- 小程序主动打开IAM登录页仅提供内置密码/短信，不因微信UA跳走，也不执行第三方网页直登。端内网页/JSAPI独立归入IN_APP。
- 新小程序的hostedLoginMethods为null继承平台策略；已有显式[]仍表示关闭，不重置已有配置或身份绑定。启停统一登录不删除已有Code关联；有关联的网页来源时须先移除关联才可关闭。
- 小程序业务方负责web-view业务域名、回调页面与授权码返回桥接；IAM不把Access Token放入回跳URL。本轮未实现业务小程序SDK或桥接，也未完成真机/真实第三方联调。

验证补充：覆盖小程序三种入口组合、开关保留Code与显式空策略、错误平台拒绝，以及微信UA打开IAM页后密码登录→授权码→PKCE换Token。沿用原Proof中隔离数据库迁移和并发证据；本轮无Schema变化，不重复宣称执行迁移。


## 2026-11-10 身份源按真实应用注册与多登录能力配置

一条身份源对应一个真实第三方应用注册及其凭据：

| 应用类型 | 可配置能力 | 凭据边界 |
| --- | --- | --- |
| 企业微信应用 | PC网页扫码、企微端内网页授权、移动SDK、关联小程序登录 | 同一CorpID、AgentID和应用Secret；小程序必须实际关联此应用 |
| 飞书应用 | 网页OAuth、AppLink、端内JSAPI Code | 同一App ID/Secret；AppLink仍需要登记的上游HTTPS回调及对应PKCE |
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


## 2026-11-10 钉钉移动SDK统一应用合同纠正

用户指出的[移动SDK接入流程](https://open.dingtalk.com/document/dingstart/mini-app-procedures-for-authorized-logon-to-ios-applications#bcf06fd872q6e)已取得[官方Markdown正文](https://open.dingtalk.com/document/dingstart/mini-app-procedures-for-authorized-logon-to-ios-applications.md)。此前只抓HTML页面壳就将SDK注册标为未核实、禁止新建能力，属于证据读取不足导致的错误产品限制，现已撤销。官方页面自身脚本提供`.md`复制入口，正文不是不可取得。

| 阶段 | 官方参数/接口 | 归属 |
| --- | --- | --- |
| 选择应用 | 应用开发→选择接入登录的应用→凭证与基础信息的Client ID | 与网页授权同一个实际钉钉应用 |
| iOS注册SDK | registerApp(appId,identifier)；appId=Client ID/AppKey；identifier=Bundle ID，URL Scheme使用Client ID | 原生App配置，不新增IAM Secret |
| Android/HarmonyOS | appId=同一Client ID/AppKey；配置对应平台包名/签名或Identifier及回跳信息 | 原生App配置，不新增移动端专属凭据 |
| SDK请求授权 | redirectUri与开发者后台一致；responseType=code；scope=openid或openid corpid；state应由App核对 | redirectUri用于平台域名校验，授权结果通过SDK回调返回authCode |
| 换取用户Token | POST https://api.dingtalk.com/v1.0/oauth2/userAccessToken；clientId=原AppKey、clientSecret=原AppSecret、code=authCode、grantType=authorization_code | Secret留在IAM，调用既有固定端点 |
| 获取个人身份 | GET https://api.dingtalk.com/v1.0/contact/users/me；x-acs-dingtalk-access-token=用户Token | 与网页用户Token链相同，取得unionId等 |
| 企业成员解析 | 既有应用Token→getbyunionid→目标企业userid→成员有效性检查 | 继续使用同一来源AppKey/Secret与固定企业，不把个人unionId直接当成员ID |

[用户Token接口](https://open.dingtalk.com/document/development/obtain-user-token.md)明确企业内部应用使用AppKey/AppSecret；[用户信息接口](https://open.dingtalk.com/document/development/dingtalk-retrieve-user-information.md)明确路径unionId可以使用me查询当前授权人。[网页登录文档](https://open.dingtalk.com/document/development/tutorial-obtaining-user-personal-information.md)也明确client_id为同一应用Client ID/AppKey。

实现：DINGTALK_SDK显示为“移动SDK授权登录”，按已实现的DINGTALK_OAUTH_V1发布可用性；一条DINGTALK_APPLICATION可同时启用网页、移动SDK、端内Code三项能力，共用AppKey/AppSecret/CorpID。现有DINGTALK_APP_CODE、实际换码HTTP合同、成员准入和registration绑定不改变，不新增Schema、不自动为旧来源开启能力，也不要求再次确认“是否统一应用”。现有旧SNS流程不是这份新版SDK，仍不新增其独立适配；它不再成为新版SDK的禁用理由。

本次原文保留在/private/tmp/dingtalk-mobile-official.md及dingtalk-mobile-token/user/web/h5.md。文档合同已核实；本地回归与真实设备授权分别记录，不以设备联调尚未执行否定文档明确的应用模型。


## 飞书AppLink运行时回调与多条账号映射（2026-11-10增量）

身份源不登记回调。接入代码生成state和S256 PKCE，并把业务App回调作为飞书授权`redirect_uri`；地址同时登记于飞书后台及当前IAM Client协议配置的回调白名单。回调验证state后，沿既有Token端点提交：

```text
grant_type=urn:macula:params:oauth:grant-type:external_code
client_id=<IAM_CLIENT_ID>
code_type=FEISHU_APP_CODE
code=<NEW_FEISHU_AUTHORIZATION_CODE>
provider_redirect_uri=https://app.example/callback
provider_code_verifier=<ORIGINAL_PLATFORM_PKCE_VERIFIER>
scope=<ACTUAL_ALLOWED_IAM_SCOPE>
```

IAM先确认已认证Client、架构/固定来源/能力、精确回调白名单及PKCE材料，再占用一次性Code并向固定飞书v3 Token端点发送同一redirect_uri和code_verifier；不会读取来源旧回调补齐，也不会向参数地址发HTTP请求。仅FEISHU_APP_CODE支持这两个专用参数，JSAPI不传。旧调用缺少provider_redirect_uri须升级，不能把平台PKCE材料混入IAM授权码参数。其他厂商交换合同不变。

身份源新增bindingMappings数组，每项为稳定小写sourceField/targetField，可同时mobile→mobile、email→email。首次未绑定身份执行全部可用规则：至少一条命中且所有命中同一用户才自动绑定；任一歧义/冲突走原账号证明。绑定事务内再次核对完整规则与当前候选，已有绑定不被重新映射，不自动补授权或覆盖用户资料。V23及配套部署/旧请求兼容约束见各模块README。

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
