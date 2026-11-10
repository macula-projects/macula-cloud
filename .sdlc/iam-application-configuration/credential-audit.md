# 第三方身份源凭据与登录能力核对

核对时间：2026-11-10。范围：用户给出的微信网页/移动/网站、小程序、企业微信、飞书、钉钉文档，以及当前IAM调用实现。本报告是分析结果，不是已实施的新模型，不构成真实厂商联调证明。

## 归类依据与结论

先识别每步接口实际使用的应用注册及密钥，再归类能力。相同字段名不代表相同凭据；不同字段名也可能引用同一应用凭据。同一开放平台账号、企业ID或共同UnionID，不足以证明应用Secret共用。

| 配置主体 | 可共用的基础凭据 | 可挂载能力 | 必须保留的区别 |
| --- | --- | --- | --- |
| 企微具体自建应用 | CorpID + AgentID + 该应用Secret | PC网页、企微端内网页、移动SDK、已关联小程序 | 小程序取Token明确使用关联企微应用Secret；SDK注册的CorpID/AgentID必须匹配，不凭同CorpID合并不同AgentID |
| 微信网站、移动、公众号/服务号注册（分别建源） | 各自的AppID/Secret | 分别为PC网站扫码、移动OpenSDK、微信端内网页 | 微信管理端独立创建的注册在IAM也独立，不建立聚合源 |
| 微信小程序 | 该小程序AppID + Secret | wx.login登录、getPhoneNumber手机号授权 | 两种Code不同，登录换session与手机号接口不同；不与企微wx.qy.login混同 |
| 飞书具体应用 | 同一App ID + App Secret | 网页OAuth、AppLink、端内JSAPI Code | 授权发起、回调和PKCE合同不同；用户Token与应用Token分别获取和缓存 |
| 钉钉具体应用（新版OAuth/企业内部能力） | 当前实现将同一AppKey/AppSecret分别映射为clientId/clientSecret和appKey/appSecret | 网页OAuth、移动SDK授权、企业内免登 | 官方移动SDK与Token正文已证明新版统一应用的Client ID/AppKey和Secret；旧SNS单列，不与新版混码 |

用户随后明确要求微信各注册独立建源，实施已遵循该决定：网站、移动、公众号/服务号、小程序分别创建来源，不建立微信开放平台聚合分组。前述凭据接口事实保持。

## 企业微信：同一应用凭据，四条获取Code的路径

以下限定企业自建应用，不把服务商SuiteID/SuiteSecret/授权企业凭据混入同一模型。

| 步骤/能力 | 接口与方法 | 应用标识、凭据及关键参数 | 返回/后续身份 |
| --- | --- | --- | --- |
| PC网页获取Code | 浏览器GET `https://login.work.weixin.qq.com/wwlogin/sso/login` | login_type=CorpApp；appid=CorpID；agentid=AgentID；redirect_uri/state；前端无Secret | 回调code/state |
| 企微端内网页获取Code | 浏览器GET `https://open.weixin.qq.com/connect/oauth2/authorize` | appid=CorpID；agentid=AgentID；response_type=code；scope/redirect_uri/state；非小程序AppID | 回调code/state |
| 移动SDK获取Code | WWKApi注册并发起SSO | 注册使用CorpID/AgentID及回跳Scheme；不下发Secret；仅企业自己的App场景 | SDK回调一次性code |
| 企微小程序获取Code | `wx.qy.login()` | 在已关联企微应用的小程序内调用；无Secret | code |
| 获取应用Token（以上服务端共用） | GET `https://qyapi.weixin.qq.com/cgi-bin/gettoken` | corpid=CorpID；corpsecret=该企微应用Secret | 应用access_token；按具体应用及凭据版本隔离缓存 |
| 网页Code获取身份 | GET `https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo` | access_token=应用Token；code=网页Code | 成员userid；适用scope下可返回user_ticket；非成员不能当作本企业员工 |
| SDK Code获取身份 | GET `https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo` | access_token=对应应用Token；code=SDK Code | UserId；注意与网页端点及字段大小写不同 |
| 小程序Code获取身份 | GET `https://qyapi.weixin.qq.com/cgi-bin/miniprogram/jscode2session` | access_token必须由该小程序关联企微应用Secret取得；js_code；grant_type=authorization_code | corpid、userid、session_key；session_key不下发 |
| 成员资料 | GET `https://qyapi.weixin.qq.com/cgi-bin/user/get` | 同一应用Token；userid；可见范围/字段权限约束 | 成员资料；不能承诺新应用直接取得手机号/邮箱 |
| 需本人授权的敏感资料 | POST `https://qyapi.weixin.qq.com/cgi-bin/auth/getuserdetail` | 应用Token + 网页私密授权所得user_ticket | 授权范围内的敏感资料；用户已选择本轮暂保留成员ID绑定，不新增此链 |

结论：企微小程序归入关联企微应用，复用那套应用凭据；不再为了登录方式不同重复录入同一Secret。但各Code保留独立处理器和协议类型。SDK若在平台实际注册成另一个AgentID，应使用该注册对应凭据，不能仅因CorpID相同强行复用。

官方依据：[PC网页](https://developer.work.weixin.qq.com/document/path/98152)、[端内网页](https://developer.work.weixin.qq.com/document/path/91022)、[移动SDK](https://developer.work.weixin.qq.com/document/path/91193)、[应用Token](https://developer.work.weixin.qq.com/document/path/91039)、[网页身份](https://developer.work.weixin.qq.com/document/path/91023)、[小程序换码](https://developer.work.weixin.qq.com/document/path/91507)、[读取成员](https://developer.work.weixin.qq.com/document/path/90196)、[敏感资料](https://developer.work.weixin.qq.com/document/path/95833)。本次已读取前七项对应正文（含成员）；敏感资料权限边界沿用已核实的网页身份/读取成员文档及先前用户决策，本次未实施/调用敏感接口。

## 微信：Token接口相同，不代表应用凭据相同

| 能力/步骤 | 接口与方法 | 实际使用的凭据 | 返回身份与限制 |
| --- | --- | --- | --- |
| PC网站取Code | 浏览器GET `https://open.weixin.qq.com/connect/qrconnect` | appid=审核通过的网站应用AppID；scope=snsapi_login；redirect_uri/state | 网站回调Code |
| 原生移动取Code | OpenSDK注册及授权 | appid=审核通过的移动应用AppID；scope=snsapi_userinfo；SDK回跳配置 | 移动应用Code；不是在手机网页上照搬PC二维码 |
| 微信端内网页取Code | 浏览器GET `https://open.weixin.qq.com/connect/oauth2/authorize` | appid=服务号AppID；scope=snsapi_base或适用snsapi_userinfo；redirect_uri/state | 服务号网页Code |
| 上述三类Code换用户Token | GET `https://api.weixin.qq.com/sns/oauth2/access_token` | appid/secret必须与产生该Code的网站应用、移动应用或服务号分别对应；code/grant_type | 用户access_token、openid；适用时unionid |
| 按需用户资料 | GET `https://api.weixin.qq.com/sns/userinfo` | 用户access_token + openid；scope允许 | 授权资料；不是企业员工通讯录 |
| 微信小程序取登录Code | `wx.login()` | 当前小程序身份；前端不使用Secret | 登录Code |
| 小程序登录Code换身份 | GET `https://api.weixin.qq.com/sns/jscode2session` | 小程序appid/secret、js_code、grant_type=authorization_code | openid、session_key，满足条件时unionid |
| 小程序取手机号Code | 用户主动触发getPhoneNumber能力 | 当前小程序；取得独立的手机号Code | 不是wx.login产生的Code |
| 小程序获取应用Token | 当前实现POST `https://api.weixin.qq.com/cgi-bin/stable_token` | 同一小程序appid/secret、grant_type=client_credential | 小程序应用access_token；不是网页用户Token |
| 手机号Code换手机号 | POST `https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=…` | 同一小程序应用Token；body.code=手机号Code；新版可选openid校验绑定 | phone_info；不能凭手机号响应虚构永久openid |

证据：H5文档参数明确为服务号AppID/Secret，错误码10016明确拒绝开放平台AppID并要求服务号AppID；网站/移动文档分别要求已审核的网站应用/移动应用及其AppID/Secret。即使三类都调用sns/oauth2/access_token，也不能用同一Secret替代。

官方依据（均本次读取正文）：[H5](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/h5.html)、[网站](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/web.html)、[移动](https://developers.weixin.qq.com/doc/oplatform/developers/dev/auth/app.html)、[小程序登录](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-login/api_code2session)、[手机号](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-info/phone-number/api_getphonenumber.html)。stable_token行描述当前源码实现，并未把读取手机号接口等同于核验所有Token版本。

## 飞书：同一应用凭据，区分用户授权与应用身份

| 步骤/能力 | 接口与方法 | 应用标识/凭据 | 返回/用途 |
| --- | --- | --- | --- |
| 网页OAuth取Code | 浏览器GET `https://accounts.feishu.cn/open-apis/authen/v1/authorize` | client_id=应用App ID；redirect_uri/state/scope及适用PKCE | 授权码 |
| AppLink取Code | `https://applink.feishu.cn/client/web_url/open`包裹完整OAuth URL | 内层client_id仍为该应用App ID；回调、state、S256 PKCE | 回调Code；AppLink不是另一套Secret，也不冒充SDK本地免登 |
| 端内JSAPI取Code | `tt.requestAccess({appID,scopeList,state})` | 网页应用传同一App ID；无Secret；旧客户端兼容requestAuthCode需按官方条件 | 临时Code；服务端换码路径按官方迁移链核验 |
| Code换用户Token | POST `https://accounts.feishu.cn/oauth/v3/token` | client_id=App ID；client_secret=App Secret；grant_type=authorization_code；code；网页/AppLink带匹配redirect_uri/适用verifier | 用户access_token；普通开放平台应用须保管Secret于服务端 |
| 登录身份/资料 | GET `https://open.feishu.cn/open-apis/authen/v1/user_info` | Bearer用户Token | open_id、union_id、tenant_key，user_id/工号/手机/邮箱视字段权限返回 |
| 应用身份Token（当前官方新版） | POST `https://accounts.feishu.cn/oauth/v3/token` | 同一App ID/Secret；grant_type=client_credentials；按注册认证方式使用Basic或请求体 | access_token字段承载tenant_access_token；与用户Token分开缓存 |
| 目标成员详情 | GET `https://open.feishu.cn/open-apis/contact/v3/users/:user_id` | Bearer应用Token或接口支持的用户Token；user_id_type与传入ID一致；应用权限/通讯录范围 | user_id、employee_no、mobile、email等按权限返回 |

结论：同一飞书应用可提供上述多能力，基础凭据填一次；端内Code、网页Code、AppLink的回调与PKCE规则不因此合并。对员工身份最终选用的ID必须明确类型和租户，不能把open_id当工号，也不能把user_id与employee_no当同义字段。

当前源码的登录换码已经使用v3；FeishuDirectoryClient仍调用旧自建应用tenant_access_token/internal接口及旧返回字段。此次文档已发布新版应用Token合同，这是后续实现需单独核对的版本差异，不把文档结论写成代码已升级。

官方依据（本次通过官方Markdown取得正文）：[取授权码](https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code)、[JSAPI](https://open.feishu.cn/document/web-app/gadget-api/open-ability/login/requestaccess)、[AppLink](https://open.feishu.cn/document/uAjLw4CM/uYjL24iN/mobile-app/integrate-via-app-link)、[用户Token](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3)、[登录用户资料](https://open.feishu.cn/document/server-docs/authentication-management/login-state-management/get)、[应用Token](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/tenant_access_token)、[通讯录成员](https://open.feishu.cn/document/server-docs/contact-v3/user/get)。JSAPI到v3的版本迁移链证据保留于research.md，不用旧v1示例覆盖新版实现。

## 钉钉：新版字段合同已核实，历史注册不能凭名称归并

| 步骤/能力 | 接口与方法 | 凭据/关键参数 | 返回/用途 |
| --- | --- | --- | --- |
| 网页取授权码 | 浏览器GET `https://login.dingtalk.com/oauth2/auth` | 当前实现client_id=AppKey；redirect_uri/state/scope=openid | OAuth Code |
| 企业端内取免登码 | `dd.runtime.permission.requestAuthCode({corpId})` | corpId选择企业上下文，前端无Secret；corpId本身不能定位唯一应用凭据 | 免登Code |
| 获取应用Token | POST `https://api.dingtalk.com/v1.0/oauth2/accessToken` | JSON appKey/appSecret（官方SDK已确认字段） | 企业应用accessToken |
| 端内Code获取成员 | POST `https://oapi.dingtalk.com/topapi/v2/user/getuserinfo?access_token=…` | 应用Token；body.code | result.userid |
| 新版OAuth Code换用户Token | POST `https://api.dingtalk.com/v1.0/oauth2/userAccessToken` | JSON clientId/clientSecret/code/grantType=authorization_code（官方SDK已确认字段） | 用户accessToken |
| 用户信息 | GET `https://api.dingtalk.com/v1.0/contact/users/me` | x-acs-dingtalk-access-token=用户Token | openId/unionId等；不能把此处unionId直接当企业userid |
| 企业成员映射 | POST `https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=…` | 目标企业应用Token；body.unionid | userid；再核验成员属于目标企业及有效状态 |
| 成员详情 | POST `https://oapi.dingtalk.com/topapi/v2/user/get?access_token=…` | 同一目标企业应用Token；body.userid | userid、job_number、mobile、email等按权限返回 |
| 历史移动SNS | SDK tmp_auth_code → `sns/getuserinfo_bycode` | 旧应用accessKey、timestamp/signature等独立合同 | 与新版OAuth Code不混用；当前未实现该独立适配 |

证据更新：初次只取得HTML标题；本次已通过官方Markdown读取移动SDK、用户Token、用户信息、网页登录和端内免登正文。新版移动SDK使用同一应用Client ID/AppKey及AppSecret，详见本文末纠正记录。此前据HTML空壳阻止移动能力新增的限制已移除。官方npm SDK字段证据继续保留。

归类结论：该官方新版移动SDK与网页授权使用同一应用注册和凭据，在一条身份源中提供多能力；SDK返回authCode并进入既有用户Token链。无需另建移动来源或填写移动专属Secret。历史独立注册不自动合并，但不影响该新版合同开放。

原始文档：[端内免登](https://open.dingtalk.com/document/development/enterprise-internal-application-logon-free)、[网页登录](https://open.dingtalk.com/document/development/tutorial-obtaining-user-personal-information)、[移动SDK](https://open.dingtalk.com/document/dingstart/mini-app-procedures-for-authorized-logon-to-ios-applications)、[用户Token](https://open.dingtalk.com/document/orgapp/obtain-user-token)。字段补证：[官方SDK包](https://registry.npmjs.org/@alicloud/dingtalk/-/dingtalk-2.2.58.tgz)。其余成员查询行列示当前源码使用的接口，不能冒充本次已读取对应官方网页正文。

## 由凭据推导IAM模型

1. 身份源主体：平台、名称、具体应用/企业身份、身份映射。微信四类应用独立建源，不新增聚合分组。
2. 凭据配置：标明注册类型、AppID/AppKey或CorpID+AgentID、Secret及版本。同一真实注册只存一次，多能力可引用；不同注册不因平台相同去重。
3. 登录能力：持有明确的凭据引用、获取Code方式、兑换合同、必要的回调/Scheme/关联小程序信息、运行环境。用户只选择业务能力，不暴露内部flow/version。
4. 身份归属：保留应用/registration范围内openid、飞书open_id及企业成员ID的真实命名空间。共同UnionID仅在范围已验证且实际返回时作为跨注册关联依据，不自动改写已有绑定。
5. 资料获取：继承具体应用的Token、权限及可见范围，不要求用户选择通讯录“获取方式”；页面仅允许选择已具备且可信的字段绑定到IAM字段。userId、员工工号、手机、邮箱不是固定都会返回的同一组字段。

应用匹配顺序：已配置且实现的能力 → 业务应用类型与所选入口 → 实际浏览器/容器 → 凭据/回调/授权权限。标准Web/SPA可使用网页授权或对应端内能力；原生App可使用对应移动能力及系统浏览器IAM入口；小程序可使用本平台Code及显式IAM网页入口。IAM Client认证/PKCE与第三方应用Secret是两套边界，不能因业务Client为none就删除厂商Secret。

## 本轮证据与未执行事项

本次重新请求23个官方文档入口，其中微信/企微/飞书19项取得正文，钉钉4项仅标题；另从官方npm下载并读取钉钉SDK。原文与清单存/private/tmp/credential-audit/manifest.json及同目录文件；旧证据保留/private/tmp/terminal-doc-audit/。本报告保留原始接口及版本边界，未以绿色Mock测试替代官方合同证明。

当前IdentitySourceType仍把应用注册与场景固化到单值sourceType，不能仅换成多选控件。后续源代码调整需要基于以上事实更新规格/实施计划，并保留旧registrationId、凭据与绑定，不自动迁移不同注册的身份。本轮只写分析文档，没有改代码、运行配置或镜像，没有获取真实Code、Token、凭据或用户资料，没有执行测试或真实平台联调。


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
