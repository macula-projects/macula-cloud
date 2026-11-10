# 第三方合同证据记录

核对日期：2026-11-08—09。当前仍在实施，不代表所有能力已验收。

## 可读取的官方实现证据

- 钉钉官方 Node SDK `2.2.58` 的 OAuth 客户端：应用 Token 请求体为 `appKey`、`appSecret`；用户 Token 为 `clientId`、`clientSecret`、`code`、`grantType`。本轮修正应用 Token 及 grant 字段，并以精确 HTTP 请求体测试验证。SDK 只能证明服务端封装合同，不能证明真实企业权限、安装关系或真实 Code 联调。
- 飞书官方包 `@larksuiteoapi/node-sdk` `1.76.0`：[版本元数据](https://registry.npmjs.org/@larksuiteoapi%2fnode-sdk/1.76.0)。只下载读取源码，没有安装或执行包。`AccessToken.retrieveByAuthorizationCode` 使用 `/oauth/v3/token`，中国区 OAuth 域名为 `https://accounts.feishu.cn`；请求含 `grant_type`、`client_id`、`client_secret`、`code`，并可传 `redirect_uri`、`code_verifier`。据此修正 Browser 换码端点。官方 SDK 仍包含旧版 tenant token 封装，不能据此推断 v3 client_credentials 的全部响应字段。

## 尚未取得足够证据的合同

下列官方页面原地址保留，但本次网页工具没有可读取正文；浏览器尝试也未成功加载。不能把需求参考参数、旧 Mock 或其他 flow 的 SDK 方法视为这些客户端合同的证据。

- [飞书 v3 用户 Token](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3)：通过上述 SDK 补证服务端授权码端点；应用类型、权限及真实返回字段仍须联调。
- [requestAccess](https://open.feishu.cn/document/web-app/gadget-api/open-ability/login/requestaccess)：JSAPI Code 与选用 Token 版本的兼容关系待核实。
- [App Link](https://open.feishu.cn/document/uAjLw4CM/uYjL24iN/mobile-app/integrate-via-app-link)：完整回调/PKCE 材料合同待核实。保留 FEISHU_APP_CODE 和既有来源记录，未核实的 flow 不能启用，也不能生成可直接复制的成功示例。
- [通讯录用户](https://open.feishu.cn/document/server-docs/contact-v3/user/get)：SDK 确认路径封装存在，不代表已确认目标租户、字段权限或非成员错误码全集。

完整逐步骤接入清单及用户指定的全部官方参考链接保留在 [第三方接入核查](../../macula-cloud-iam/docs/third-party-integration.md)，不得用平台首页替代。

## 证据层级

- HTTP Mock：仅证明固定 URL、请求参数及响应映射。
- 专用 Redis：证明真实并发、TTL、加密信封、owner 发布栅栏；不连接业务实例。
- SecurityFilterChain：证明真实 Spring Security/OIDC 签名和发布前拒绝；账号和上游平台仍为测试素材。
- 真实平台：本轮尚未进行微信、企微、飞书、钉钉真实设备/企业凭据联调。不得由上述测试推断真实平台接入已验收。

## 2026-11-09追加可读证据

- [飞书获取单个用户信息原入口](https://open.feishu.cn/document/server-docs/contact-v3/user/get)保留；同一文档的[官方Lark域可读正文](https://open.larkoffice.com/document/server-docs/contact-v3/user/get?appId=cli_a80d82191ff1500d)明确HTTP 400/41050为无用户权限，而不是非成员；tenant_access_token查询范围及敏感字段Scope另有要求。[通用错误码](https://open.feishu.cn/document/server-docs/api-call-guide/generic-error-code)搜索可读正文作交叉补证。没有把原地址静默替换或声称链接迁移。
- [飞书自建应用tenant_access_token当前官方说明](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/tenant_access_token)搜索已取得部分正文：accounts.feishu.cn/oauth/v3/token、client_credentials及client_id认证说明。当前实现固定保留已由官方SDK核实的internal租户Token合同，不据部分片段猜完整响应或在失败后切换接口。
- [应用授权登录概述](https://open.feishu.cn/document/client-docs/build-login-free-system-)与[端内免登步骤](https://open.feishu.cn/document/client-docs/h5/development-guide/step-3)搜索正文确认requestAccess获取预授权码及appID/scopeList用法，但没有给出足以证明现有flow与所选Token版本完整兼容的材料，原待核实结论保留。
- [阿里云GetMobile](https://help.aliyun.com/en/pnvs/developer-reference/api-dypnsapi-2017-05-25-getmobile)可读正文说明电信10分钟、联通30分钟、移动2分钟且单次使用。本实现据此为ALIYUN_MOBILE_TOKEN设31分钟防重放保守下限；不能据通用10分钟TTL覆盖最长30分钟有效凭证。
