# Authing 参考与 Macula 能力核对

核对日期：2026-11-06。依据用户提供的七张截图、Authing 官方文档、当前仓库源码；没有执行新的真实登录或修改运行环境。

## 官方资料与采用结论
- [快速开始](https://docs.authing.cn/v2/quickstarts/)：按标准 Web、SPA、本地客户端、后端/API 等接入架构组织教程；应用类型用于提供模板，不按微信、企微等供应商划分主要类型。
- [登录控制](https://docs.authing.cn/v2/guides/app-new/create-app/login-control.html)：将托管登录方式与身份源关联放到应用详情中；第三方身份源先独立创建，再由应用关联。
- [协议配置](https://docs.authing.cn/v2/guides/app-new/create-app/protocol-config.html)：协议配置与应用配置、登录控制分开，OIDC 是用户登录的默认方向。
- [Java SDK 配置说明](https://docs.authing.cn/v3/reference/sdk/java/install.html)：应用 ID、Secret、应用地址、回调与不同端点认证具有独立职责。Macula 当前没有各 Token 端点独立认证配置，不能照搬三个选择器。
- [微信移动端](https://docs.authing.cn/v2/guides/connections/social/wechat-mobile/)：微信平台凭据属于身份源，关联业务应用后用于相应渠道；它不是 Macula 生成的 Client ID/Secret。
- [选择 OIDC 授权模式](https://docs.authing.cn/v2/concepts/oidc/choose-flow.html)、[OIDC 配置](https://docs.authing.cn/v2/guides/federation/oidc.html)：既有授权码+PKCE说明，也仍有隐式模式等历史表述，不能机械采用全部建议。
- [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html#section-2.1)：新公共客户端使用 PKCE；新模板不加入隐式授权或密码授权。已有 password 兼容记录不因此被批量改写。

部分 Authing 创建应用/应用配置页面只返回导航壳，版式以用户截图为依据；没有把空壳页面当作已核实的功能描述，也没有推断 Authing 私有后端实现。

## 当前源码证据
| 能力 | 当前实际情况 | 设计处理 |
| --- | --- | --- |
| OAuth2/OIDC | ProtocolOAuth2Configuration 有真实配置、RegisteredClient 及端点 | 本轮对外呈现此能力 |
| CAS/SAML | ProtocolCasConfiguration、ProtocolSaml2Configuration 是空类 | 不提供可开启的配置入口 |
| 托管登录页 | login.html 只有密码和手机验证码；短信显示依赖全局开关 | 新增 Client 级托管策略必须同时改 IAM 校验，不能只加勾选框 |
| 邮箱验证码 | 有 email Token grant 与验证码基础设施；托管页没有邮箱登录表单 | 放入明确的自建登录兼容配置，不显示为已实现的托管页开关 |
| 网页第三方登录 | BrowserLoginClientPolicyService 按 Client 选择 provider 类型；IdentityProviderConfigService 要求同类型全局唯一启用配置 | 本轮保留此限制，列表显示实际身份源名称，不伪造应用级多 registration 能力 |
| SDK/小程序外部码 | externalProviderBindings 已按 Client 固定 registration ID，并有 provider/capability 校验 | 复用现有固定绑定，归入登录渠道配置 |
| Client Secret | System PasswordEncoder 单向保存，读接口只返回 secretConfigured | 新建/重置时一次性展示，不能设计成随时读取原密钥 |
| 平台地址 | IAM 配置持有 issuer；Admin 的当前 Origin 不一定是 issuer | 从受信任 Discovery 取得展示端点，不猜测平台域名 |
| 应用类型 | 上一轮 terminalType 只区分浏览器/App/小程序等，不能表达服务端 Web 与 SPA 差异 | 改为应用架构类型；保留未提交成果，重新整合未部署迁移 |
| 业务应用/API Key | System 已有独立业务应用与 AK/SK 管理 | 不与 IAM Client 合并实体或权限模型 |

关键文件：
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`
- `macula-cloud-iam/src/main/resources/templates/login.html`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`

## 后续扩展边界
真正支持不同应用选择同平台不同网页登录 registration，需要同时检查 state、回调换码及外部账号标识作用域。当前账号绑定主要按 providerType/subject 解析，不能只增加一个身份源下拉框就宣称多源隔离完成。本轮只参考信息组织，不扩大该能力。

## 2026-11-09 原始第三方文档复核与终端约束

用户明确要求重新研究前次提供的文档并继续优化代码。本节以原链接正文及其公布的 Markdown 版本为依据，不把先前“抓取失败”当成永久不支持。原有参考、历史审查与已接受安全边界继续保留。

### 读取方式与实际结果

- 微信三个原链接 HTTP 200，响应为 gzip；按响应内容解压后取得可读正文：网站约9182字符、移动约7611字符、H5约10654字符。首次未解压的输出不作为正文证据。
- 企微 98152 / 91022 / 91193 / 91507 取得可读正文，分别核对桌面网页登录、端内授权、原生移动 SSO 和小程序换码。
- 飞书 HTML 页面含官方 `rel=alternate type=text/markdown` 链接，按该链接取得 AppLink、H5步骤、requestAccess、v3 Token、浏览器授权共五份正文，不猜测私有接口；读取后纠正先前 AppLink“未取得完整合同”的状态。
- 钉钉原网页登录和移动SDK页仍只返回页面壳；现有服务端参数依据官方SDK证据保留。没有用非官方镜像或第三方文章宣称手机网页登录/旧SNS与新版SDK已验证。手机普通浏览器能力暂不发布，不宣称平台永久不支持。
- 本机只读材料保存在 `/private/tmp/terminal-doc-audit/`；原始下载脚本 `/private/tmp/terminal-doc-audit.py`、官方Markdown读取脚本 `/private/tmp/terminal-feishu-md.py`。未执行第三方登录或使用企业真实凭据。

### 平台逐步骤、参数与身份

| 平台与场景 | 端侧取得凭证 | IAM对应固定接口 / 凭据 / 结果 | 已核实的设备与处理 |
| --- | --- | --- | --- |
| 微信网站 | GET `https://open.weixin.qq.com/connect/qrconnect`，appid、redirect_uri、response_type=code、scope=snsapi_login、state | GET `https://api.weixin.qq.com/sns/oauth2/access_token`，appid / secret / code / grant_type=authorization_code，取得openid | 原文面向PC，支持二维码与已登录Windows/Mac微信的快捷确认；手机浏览器不复用此网站入口。[原文](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/web.html) |
| 微信移动应用 | OpenSDK SendAuth 拉起微信，回调取得Code | 同一Token端点，使用移动应用独立AppID/Secret，`WECHAT_OPEN_APP_CODE` | 原文明确原生方式及客户端安装条件，不宣称网站浏览器可自动转SDK。[原文](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/app.html) |
| 微信H5 | 微信内 GET `https://open.weixin.qq.com/connect/oauth2/authorize`，服务号appid / redirect_uri / scope / state | 微信用户Token端点；固定公众号来源，snsapi_base取得openid | 原文限定具备资格的服务号及微信端内；不是手机Safari/Chrome的通用授权。[原文](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/h5.html) |
| 企微桌面网页 | GET `https://login.work.weixin.qq.com/wwlogin/sso/login`，login_type=CorpApp、appid=CorpID、agentid、redirect_uri、state | GET `/cgi-bin/gettoken` → GET `/cgi-bin/auth/getuserinfo`，应用Token / code，userid | 原文将系统浏览器列为桌面端，可扫码或快速登录；手机普通浏览器不展示该入口。[原文](https://developer.work.weixin.qq.com/document/path/98152) |
| 企微端内网页 | GET `https://open.weixin.qq.com/connect/oauth2/authorize`，corpid / agentid / snsapi_base / state | 与同一自建应用网页凭据对应的 gettoken / auth/getuserinfo | 桌面及移动企微WebView均支持，保持独立模式和state；本轮仍仅成员ID，敏感资料延后。[原文](https://developer.work.weixin.qq.com/document/path/91022) |
| 企微原生SDK | 官方移动SDK SSO取得Code | 应用Token → GET `/cgi-bin/user/getuserinfo`，结果UserId；`WECOM_APP_CODE` | 端点、UserId大小写与网页区分；原文限制企业自己的App，不当成任意第三方手机网页。[原文](https://developer.work.weixin.qq.com/document/path/91193) |
| 企微小程序 | `wx.qy.login()` → Code | GET `/cgi-bin/miniprogram/jscode2session`，access_token / js_code / grant_type，userid | 与普通微信小程序AppID换码不同，session_key不下发。[原文](https://developer.work.weixin.qq.com/document/path/91507) |
| 飞书浏览器 / 飞书内网页OAuth | GET `https://accounts.feishu.cn/open-apis/authen/v1/authorize`，client_id、redirect_uri、state及申请Scope | POST `https://accounts.feishu.cn/oauth/v3/token`，grant_type / client_id / client_secret / code / 原redirect_uri → GET `/open-apis/authen/v1/user_info`，Bearer用户Token取得open_id | 官方说明端内可免确认续转；AppLink文档明确浏览器授权可作回退。允许普通桌面/手机浏览器及飞书内OAuth，不与JSAPI Code混用。[原入口](https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code) / [官方Markdown](https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code.md) |
| 飞书原生AppLink | 完整OAuth地址带S256 PKCE、state、prompt=consent，经URL编码后放入 `https://applink.feishu.cn/client/web_url/open?mode=window&url=…` | 业务App核对state；IAM收到`FEISHU_APP_CODE`与`provider_code_verifier`后向上述v3 Token端点发送固定来源redirect_uri / verifier / 平台Secret，再查user_info | 这是客户端唤起体验，核心仍为OAuth；现有预留适配本次通过精确请求测试后开放。平台Secret留在IAM，不进入App。[原入口](https://open.feishu.cn/document/uAjLw4CM/uYjL24iN/mobile-app/integrate-via-app-link) / [官方Markdown](https://open.feishu.cn/document/uAjLw4CM/uYjL24iN/mobile-app/integrate-via-app-link.md) |
| 飞书JSAPI | `tt.requestAccess({appID,scopeList})`，返回Code；旧客户端兼容说明有独立requestAuthCode | 当前预留WEBVIEW旧换码链不因读到JSAPI参数就自动开放 | 已读前端步骤，但没有将其Code与当前旧Token链兼容性混同为已证明，`FEISHU_WEBVIEW_V1`仍不可启用。可先使用上面已核实的端内OAuth直登。[步骤](https://open.feishu.cn/document/client-docs/h5/development-guide/step-3) / [JSAPI](https://open.feishu.cn/document/web-app/gadget-api/open-ability/login/requestaccess) |
| 钉钉企业免登 / 新OAuth | 端内`dd.runtime.permission.requestAuthCode({corpId})`与新OAuth authCode分别取得 | 企业应用Token / `topapi/v2/user/getuserinfo` 与 `oauth2/userAccessToken` / `contact/users/me`分别核验；unionId需在固定企业换成员userid | 保留之前官方SDK精确字段证据与原链接。原生旧SNS `tmp_auth_code`不冒充新Code；本轮不新增或宣称已验证手机浏览器唤起。 |

飞书Token原文确认兼容JSON请求体（推荐form）；代码保留现有已测JSON合同。普通开放平台应用仍需平台Secret，不能因业务IAM Client是none而省略飞书Secret。PKCE使用时code_verifier必填且43—128字符；redirect_uri必须与发起一致。固定来源不接受业务请求传入任意回调或URL。[Token原入口](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3) / [官方Markdown](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3.md)。

### 实施后的矩阵与边界

- WEB_SERVER / SPA：关联已核实网页来源，或对应WEBVIEW Code；后者仍须满足原有公共Client和协议约束，标准Web机密Client不能借此直接使用external_code。
- NATIVE：保留系统浏览器SSO，同时可关联APP Code（微信OpenSDK、企微SDK、新版钉钉OAuth、飞书AppLink、号码认证）；不能关联小程序或端内JSAPI Code。网页登录是否适合手机取决于来源发布的环境，不因NATIVE包含移动设备就显示桌面二维码。
- MINIAPP：仅本平台Code，无通用IAM登录页；手机号Code和普通登录Code独立核验。SERVICE没有用户来源。
- 新目录每个网页来源发布browserApplicationTypes / browserEnvironments，每个Code发布applicationTypes；Admin渲染、保存及IAM Token端点按同一白名单执行。旧目录前端回退只识别明确Code，不按后缀猜未知能力。
- 手机页过滤桌面入口，默认直登同样检查环境；显式发起URL也检查，拒绝时不创建state。UA只用于交互路由，不是身份/组织证明；回调不按新UA改道，继续核对服务端一次性state / 来源 / Flow / 版本。
- 微信、企微、飞书容器使用已实现的网页OAuth直接续转；失败/拒绝后展示错误，避免循环。原生SDK / 小程序直接Code路径不建立浏览器SSO会话；账号未绑定仍可能进入账号证明流程。
- 历史不兼容关联只读展示并标记待整改，不自动删除/迁移；未分类Client不猜类型，沿用既有精确授权白名单。没有新增数据库列、SDK依赖、任意回调参数或JSAPI到网页SSO桥接。

## 2026-11-10 飞书端内JSAPI Code纠正与补全

用户指出飞书端内同样是Code。之前用标准网页OAuth替代端内免登，不代表FEISHU_WEBVIEW_CODE已接通；该缺口现按官方原文补齐。

证据链：
1. [官方免登步骤](https://open.feishu.cn/document/client-docs/h5/development-guide/step-3.md)及其流程图：requestAccess取得Code后交服务端核验。
2. [官方免登示例](https://open.feishu.cn/document/home/quickly-create-a-login-free-web-app/introduction-to-sample-code.md)将JSAPI Code交给v1用户Token接口。
3. [v1接口原文](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/access_token/create.md)明确历史版本，并指向v2。
4. [v2迁移说明](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token.md)明确继续迁移到[v3 Token](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3.md)。

实现：新增内部FEISHU_WEBVIEW_V3，FEISHU_WEBVIEW_CODE固定调用POST https://accounts.feishu.cn/oauth/v3/token，JSON grant_type=authorization_code / client_id / client_secret / code，再用Bearer用户Token GET https://open.feishu.cn/open-apis/authen/v1/user_info取得open_id。平台Secret只在IAM。JSAPI路径不发送浏览器redirect_uri或AppLink provider_code_verifier，不失败后尝试旧Token端点。显式旧FEISHU_WEBVIEW_V1仍不可启用，不自动升级旧记录；新建来源服务端默认唯一可用v3。

业务H5在飞书内调用tt.requestAccess({appID,scopeList:[]})；如传state须核对回调state。只有客户端不支持（如errno=103）时按官方指引兼容requestAuthCode，用户拒绝或其他失败不得盲切。JSAPI Code有效期3分钟且单次使用，提交IAM external_code时明确code_type=FEISHU_WEBVIEW_CODE。Web/SPA可关联；APP原生与小程序不接受该Code，直接换Token仍遵守既有公共Client（none）及固定来源约束。标准Web默认机密配置不使用此扩展。正常Code登录不经过IAM登录选择页，也不建立浏览器SSO会话；账号未绑定仍需证明已有账号。

来源配置页不再给JSAPI来源显示无用的IAM浏览器回调地址；隐藏Flow后按服务端唯一可用默认合同显示必要关联参数。网页OAuth及AppLink能力继续独立存在，不与JSAPI Code互换。本机证据：/private/tmp/terminal-doc-audit/feishu-webview-example.md、feishu-webview-token.md、feishu-incremental-token.md、feishu-webview-incremental.md及官方流程图feishu-webview-flow.jpg（已视检）。未取得真实飞书Code或执行企业设备授权，合同/Mock验证不能替代真实联调。


## 2026-11-10 双入口模型与小程序网页边界
Rain明确说明小程序/移动客户端均可直接提交平台Code，也可打开IAM统一登录页，并在完整模型展示后回复“是的”。本补充替代之前按MINIAPP架构排除统一登录/网页来源的限制。

- IAM统一登录与平台Code是两条独立入口。同一公共Client允许authorization_code与external_code并存；小程序可仅统一登录、仅本平台Code或两者并存。SERVICE继续禁止用户登录。公共授权码使用S256 PKCE，沿用已登记回调、state及OIDC nonce约束。
- 网页身份源按实际浏览器/容器能力显示；允许应用关联来源不表示每个容器都能使用。不能把“手机浏览器支持”推导为“小程序内嵌web-view一定支持”。平台Code继续按已保存架构、平台、具体registration校验，拒绝客户端自报终端绕过。
- 小程序主动打开IAM登录页默认保留密码/短信和适用网页入口，不因微信UA直接跳走；明确配置直登时才自动续转。Web/SPA的企微端内自动网页授权、飞书/钉钉JSAPI Code路径保持。
- 新小程序的hostedLoginMethods为null继承平台策略；已有显式[]仍表示关闭，不重置已有配置或身份绑定。启停统一登录不删除已有Code关联；有关联的网页来源时须先移除关联才可关闭。
- 小程序业务方负责web-view业务域名、回调页面与授权码返回桥接；IAM不把Access Token放入回跳URL。本轮未实现业务小程序SDK或桥接，也未完成真机/真实第三方联调。

验证补充：覆盖小程序三种入口组合、开关保留Code与显式空策略、错误平台拒绝，以及微信UA打开IAM页后密码登录→授权码→PKCE换Token。沿用原Proof中隔离数据库迁移和并发证据；本轮无Schema变化，不重复宣称执行迁移。

官方[微信小程序web-view说明](https://developers.weixin.qq.com/miniprogram/dev/component/web-view.html)要求配置业务域名，并注明个人类型暂不支持；wx.miniProgram.postMessage消息在后退、销毁、分享等指定时机触发，不能当作即时Token回传通道。已读取正文保存在/private/tmp/miniapp-webview-official.txt。此约束不等于所有第三方OAuth都可在嵌套web-view使用，仍需逐平台真机验证。


## 2026-11-10 按接口与凭据重新核对

用户要求从获取Code、换码及获取身份实际使用的应用ID/Secret反推归类，详见[凭据与登录能力核对](credential-audit.md)。该报告替代前文把微信开放平台账号等同单一应用凭据、或把登录场景直接等同互斥身份源的表述。企微小程序复用关联企微应用凭据；微信网站/移动/服务号可统一界面管理但保留对应注册凭据；飞书同应用多能力共用凭据；钉钉区分已核实SDK字段与尚未取得的移动注册/Code合同证据。此为分析，无代码/配置/镜像变化。


## 2026-11-10 多能力模型实施边界



一条身份源对应一个真实第三方应用注册及其凭据：

| 应用类型 | 可配置能力 | 凭据边界 |
| --- | --- | --- |
| 企业微信应用 | PC网页扫码、企微端内网页授权、移动SDK、关联小程序登录 | 同一CorpID、AgentID和应用Secret；小程序必须实际关联此应用 |
| 飞书应用 | 网页OAuth、AppLink、端内JSAPI Code | 同一App ID/Secret；AppLink仍需要登记的上游HTTPS回调及对应PKCE |
| 钉钉应用 | 网页OAuth、端内免登；保留已有SDK合同 | 同一实际注册的AppKey/Secret及固定企业；未核实的新移动注册能力不能新增启用，旧SNS不新增适配 |
| 微信网站应用 | 网站扫码 | 该网站应用AppID/Secret |
| 微信移动应用 | OpenSDK | 该移动应用AppID/Secret |
| 微信公众号／服务号 | 微信端内网页授权 | 该服务号AppID/Secret |
| 微信小程序 | 登录Code、手机号授权Code | 该小程序AppID/Secret；两种Code分别提交、分别兑换 |

微信四种注册必须分开建源，不提供开放平台聚合源；企微、飞书、钉钉也只在同一真实应用注册内共用凭据，不按企业ID、同名或UnionID自动合并来源。

管理页面将“应用类型”单选与“登录能力”多选分开，凭据只填写一次；不显示flow、接口版本或通讯录策略。应用类型创建后固定，修改应用身份标识需新建来源；应用Secret仍可轮换，变化递增来源版本。关闭全部能力可保留来源配置，但不能用于认证。已有显式历史合同保留，不自动切换厂商接口。

应用登录控制按能力固定registrationId，同一个业务Client可以分别选择微信网站来源与服务号来源。展示、显式发起、自动直登、回调、Code换码、目录、缓存及账号证明都复查当前能力与来源版本；不能凭客户端自报终端或registration切换凭据。应用类型先筛选能力，实际浏览器/容器再筛选网页登录入口。密码/SMS与统一登录/平台Code双入口策略保持。

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
