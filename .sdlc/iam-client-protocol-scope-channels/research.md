# 依据与现状核对

## 当前源码
- MaculaRegisteredClientRepository在注册Client时无条件builder.scope(OidcScopes.OPENID)，所以协议不能只在前端分组。
- CustomOidcTokenCustomizer只在profile授权时添加preferred_username、存在的nickname；没有email/phone/address声明映射。默认UserInfo基于ID Token可用声明；授权页标签不是实现证明。
- Client scopes现为任意非空CSV；没有统一业务scope登记目录。基线message.read/message.write/userinfo属于历史值，不据此宣称对应资源权限已经实现。scope不是角色、URL权限或数据权限，不能相互猜测生成。
- HostedLoginClientPolicyService中null继承PASSWORD/SMS全集，再与IAM全局login.password.enabled/login.sms.enabled取交集；空集合关闭本地方式。继承不包含外部身份源、grant或SSO撤销；当前没有平台默认配置的管理页面。
- IdentityProviderType.WECHAT将网站、公众号、移动、小程序凭据放在同一registration不同字段；企业微信/飞书等也非一个独立的“网页源/SDK源”模型。原external-code-catalog只给原始codeTypes与少量capability标记，Admin直接渲染枚举。
- ExternalCodeType已包含providerType、APP/MINIAPP/WEBVIEW场景、EXTERNAL_IDENTITY/MOBILE结果及凭据能力；足以生成有业务名称的接入方式目录，但不能直接把全部CODE展示为选择。
- 浏览器第三方仍全局同平台唯一启用源；SDK/小程序固定registration绑定。不能借UI支持多registration而改变现有providerType+subject身份域。

## 规范
- [OIDC Core](https://openid.net/specs/openid-connect-core-1_0.html)：openid识别OIDC，profile/email/phone/address有各自资料语义；offline_access具有离线授权语义，不能等同refresh_token grant开关。
- [OAuth2.1当前IETF文档](https://datatracker.ietf.org/doc/draft-ietf-oauth-v2-1/)：授权码PKCE等安全要求，排除implicit与密码授权；本次产品模式不宣称通过完整协议一致性认证。

本轮未修改运行配置或业务数据，也未开始新一轮源码实现。

## 来源类型补充
当前第三方绑定主要按providerType+subject解析；仅将WECHAT等枚举拆为新名称会影响旧绑定或造成跨registration身份混淆。来源类型需要同时约束凭据加载、回调与固定绑定；厂商、来源类型、registration和身份域不得混为一个字段。旧来源不自动拆分，来源类型变更不自动改写已有账号绑定。

## 公共刷新能力核对
独立Plan评审核对当前Spring Security 7.0.7 OAuth2RefreshTokenGenerator：公共授权码不签发refresh；项目external_code/email也不签发，但sms继承allowRefreshToken=true且默认reuse=true。按[RFC9700刷新令牌重放保护](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2)，本轮现代公共模式禁止refresh组合，不以一个UI轮换开关冒充完整支持；legacy行为保留，机密授权码刷新用真实链测试。

## 自动绑定现状
- WecomClient取得成员userid；DingtalkClient由unionId解析企业userid；FeishuClient当前取得employee_no；GithubClient当前只保留数值id；WechatClient取得UnionID，手机号授权另取得核验phoneNumber；阿里云号码认证返回核验mobile。未发现已实现的通用verified email获取。
- 本地SysUser有稳定id、username、mobile、email，没有独立employeeNo列。企业UserID/employeeNo只能在明确策略下与username匹配，不能声称有工号字段。
- 现有浏览器企微存在写死的subject→username自动匹配；外部码MOBILE也有固定手机号快捷匹配。这些均需改为来源策略控制。
- SDK当前binding_required仅携带ticket，经Bearer本地身份完成API绑定，尚无浏览器可直接打开的统一SDK绑定入口；本轮必须补齐页面桥接，不仅返回“需要绑定”。
