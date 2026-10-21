# Macula Cloud IAM 认证中心

基于 Java 17、Spring Security 7 Authorization Server 的统一身份服务。依赖版本由 Macula Boot 父 POM 与 Spring Boot BOM 管理，不在 IAM 单独固定版本。

## 协议与兼容范围

- 标准交互登录：authorization_code + PKCE（S256）。公共客户端使用 client authentication method `none`，强制 PKCE，不在浏览器保存 client secret。
- 机密客户端在 `requireProofKey=true` 时强制 PKCE，仍需完成客户端认证。旧机密客户端不会被静默修改数据库；新接入均应启用 PKCE。
- 保留 password、sms 扩展及 client_credentials、refresh_token。password 仅供已有客户端兼容，不是标准 OIDC 登录；请求 openid 也不会自动得到 ID Token。
- OIDC：Discovery、JWKS、ID Token、UserInfo、RP-Initiated Logout。动态客户端注册不默认开放。
- 访问令牌默认使用 reference（opaque）；显式 self-contained 客户端使用 JWT。ID Token 始终为签名 JWT，与访问令牌格式相互独立。
- Gateway 的默认 opaque token introspection 链路保持不变。

主要端点：

| 路径 | 用途 |
| --- | --- |
| /login | IAM 登录页；POST 表单认证 |
| /oauth2/authorize | 授权码与授权确认处理 |
| /oauth2/consent | 自定义权限确认页面 |
| /oauth2/token | 换取、刷新及兼容扩展令牌 |
| /oauth2/introspect | 令牌状态查询 |
| /oauth2/revoke | 撤销 access/refresh token |
| /.well-known/openid-configuration | OIDC 发现文档 |
| /oauth2/jwks | 公钥集合 |
| /userinfo | 使用 access token 查询已授权用户信息 |
| /connect/logout | OIDC 退出；校验 ID Token hint 与会话 |

## 授权码与 OIDC 接入

兼容扩展说明：`grant_type=password` / `sms` 保留原参数、认证业务逻辑及异常映射；SMS 仍通过 `phone` / `code` 调用原验证码与身份服务。公共 Provider 原有授权记录标记（包括 SMS 记录为 password）保持不变。兼容 grant 携带 openid 时，可正常刷新 access/refresh token，保留原 scope，不新增 ID Token；标准授权码 OIDC 刷新仍由框架处理。适配保留客户端绑定、有效期与撤销校验、scope 不扩权、刷新令牌复用/轮换和 Redis CAS。此路径只适配已有机密客户端 Bearer 令牌，不将绑定密钥的令牌降级；勿将兼容 grant 视为完整 OIDC 登录流程。

客户端每次发起授权前生成随机 `code_verifier`（43–128 个 RFC 7636 允许的字符），以 SHA-256 后 Base64URL 无填充编码生成 `code_challenge`。由客户端维护 state、nonce 并在回调验证，不能复用固定示例值。

授权请求参数：

```text
response_type=code
client_id=<已注册客户端>
redirect_uri=<已注册的精确回调地址>
scope=openid profile
state=<客户端生成的一次性随机值>
nonce=<客户端生成的一次性随机值>
code_challenge=<S256 挑战值>
code_challenge_method=S256
```

回调得到 code 后，POST form-urlencoded 到 /oauth2/token，携带 grant_type=authorization_code、code、redirect_uri、code_verifier；公共客户端附 client_id，机密客户端使用其注册的认证方式。缺失/错误 verifier、plain 降级、错误回调和授权码重放均拒绝。

客户端验证 ID Token 的签名、公钥、issuer、audience、有效期及 nonce；不得拿 ID Token 调用 UserInfo。UserInfo 同时支持本服务签发并仍有效的 opaque/JWT access token，撤销后立即拒绝。

系统身份的 OIDC sub 使用 `tenantId:userId`，不使用可变昵称。其他身份源若没有稳定 ID，框架回退到认证主体名称，接入方需保证该名称稳定且唯一。profile 仅输出真实存在的用户名/昵称；当前身份模型没有邮箱，故不虚构 email/email_verified。客户端不请求 openid 时，不签发 ID Token。

退出支持本地会话结束；客户端模型尚无 post_logout_redirect_uris，本次不允许任意外部退出回调，也不借用普通登录 redirectUris。授权记录仍存续时，过期 ID Token 可用于标准退出 hint；这不使其成为有效 access token。

## 登录和授权页面

采用本地 Thymeleaf + CSS/JavaScript，无外部 CDN。桌面为品牌区与表单，680px 以下为移动单列；包含安全区、触控按钮、16px 输入字号、密码显隐、密码管理器、错误反馈、请求超时和防重复提交。授权页可按 scope 勾选，同意与拒绝由服务端处理，拒绝当前请求不会取消此前授权。

密码、短信、小程序及第三方登录均按浏览器认证闭环处理：成功后由 Spring Security 恢复 SavedRequest 或跳转 `/`，失败后回到登录页；登录 Handler 不返回 JSON。JSON 响应只由 OAuth2/OIDC、Token、UserInfo 等协议端点及独立业务 API 提供。

短信入口保留原有默认开启行为，`spring.security.oauth2.server.login.sms.enabled` 控制展示，默认 true。

GitHub 与企业微信登录配置直接读取共享 System 数据库的 `sys_identity_provider`。登录发起、回调、
本地账号关联及登录不需要 IAM OAuth2 client，也不以租户为前置条件。provider 同时
`enabled=true`、`visible=true` 时登录页展示入口；`visible=false` 仅隐藏入口，`enabled=false`
拒绝发起和回调。OAuth2/OIDC 协议仍独立校验自身 client；客户端的租户配置用于后续按租户加载角色
权限，不决定第三方身份绑定的账号。具体 `UserAuthInfoService` 实现由接入方提供。

GitHub OAuth 不申请额外 scope，只接受 `/user` 返回的稳定数值 `id`；已有全局绑定直接登录，
未绑定时进入已有账号绑定页面，不使用 GitHub login/email 兜底。企业微信只接受企业成员
`UserId`，优先匹配全局唯一的 System `username`，其次查询已有绑定，仍未关联则进入绑定页面；
匹配与绑定冲突时拒绝。绑定表不含租户 ID。绑定只能通过已有账号密码、与目标账号登记手机号一致的
一次性短信验证码，或目标账号的已登录会话完成；手机号重复时拒绝，不在此流程注册或静默改绑。

企业微信系统浏览器入口 `/login/wecom/browser` 使用新版 `CorpApp` 自构造网页登录链接；内置
WebView 入口 `/login/wecom/webview` 使用 `snsapi_base`。`/login/wecom` 根据可识别的客户端环境选择；
无法识别时让用户在登录页选择明确入口。两种模式沿用 `<issuer>/login/wecom` 登记回调。
第三方回调固定由 `macula.cloud.iam.issuer-uri` 生成，`state` 使用 Spring Security 的 URL-safe
`Base64StringKeyGenerator`，与浏览器会话、provider、登录模式和受控相对续转目标绑定，五分钟内
仅可消费一次。续转目标取自 Spring Security 会话中保存的原请求，限制为当前应用上下文内的安全相对路径，
不限于 OAuth2 授权端点，也不在第三方认证层检查 `client_id`；目标协议返回后仍自行校验请求。成功后只恢复
该 state 自带的服务端目标；无待续转请求时转到 `/`，不使用回调参数或请求 Host 决定回跳。未绑定状态
同样限时、限次且不授本地权限。第三方授权码和 token 不持久化、
不写日志。部署时 IAM 与
System 必须共享 `MACULA_CRYPTO_PASSWORD`；GitHub 回调为 `<issuer>/login/github`，企业微信回调为
`<issuer>/login/wecom`，应在各开放平台精确登记。企业微信自建应用还需把实际登录成员纳入应用可见范围。
IAM 的 `ThirdPartyProviderType` 将已接入登录的 `GITHUB`/`/login/github`、`WECOM`/`/login/wecom`
作为内部统一映射；数据库中的 provider type 和对外回调路径保持原值。System 的身份源管理目录独立维护，
新增登录源时应同时核对两侧的类型与回调路径。
登录页通过同源、CSRF 保护的 `POST /api/v1/sms-code` 请求验证码。IAM 只读共享 System 数据库中的
唯一启用短信供应商和 `SMS_LOGIN` 中文模板，使用阿里云或腾讯云 SDK 提交；配置缺失、重复、
不受支持或无法解密时失败关闭。验证码使用安全随机数生成，只以 HMAC-SHA256 摘要存入 Redis，
默认有效 5 分钟、最多尝试 5 次，手机号 60 秒内仅可申请一次，来源 IP 每个 60 秒窗口最多申请
20 次，并在供应商受理后
才激活可验证记录。成功验证原子删除记录，错误达到上限或过期后不能认证。

标准 `/iam/` 反向代理部署必须由受信代理覆盖客户端提交的 `Forwarded`、`X-Forwarded-*`，并提供
实际外部主机、协议和 `X-Forwarded-Prefix: /iam`；IAM 使用 Spring 的 forwarded-header 处理恢复
外部上下文路径。IAM 端口只应供本机或受信网络访问，不能让任意客户端直连并提交伪造的转发头。
验证码页面使用相对 URL，确保
直接访问 `/login` 与代理访问 `/iam/login` 都命中 IAM 本身，而不是管理端 `/api/` Gateway 路由。

IAM 与 System 必须连接同一消息配置库，并注入相同的 `MACULA_CRYPTO_PASSWORD`；验证码摘要密钥
使用 `IAM_SMS_DIGEST_KEY`，未配置时回退到该加密密码。生产应单独配置强随机摘要密钥。此变更不
增加注册、找回密码、手机号绑定、欢迎通知或密码到期提醒流程。password 与 sms grant 保留已有
兼容接入，不要求旧客户端改用 PKCE/OIDC。

发送审计使用独立的 `MACULA_MESSAGE_AUDIT_HMAC_KEY` 对手机号生成 HMAC-SHA-256 摘要；该密钥
至少 32 个字符，不得复用字段加密密钥或验证码摘要密钥。轮换会切断新旧日志之间的目标关联，
应由密钥管理系统按审计保留策略执行，日志中不得记录该密钥、原手机号或验证码。

表单登录和本地退出开启 CSRF，页面自动携带 token；验证码请求依赖 Spring Security CSRF 和浏览器
同源凭据，不手工比较代理前后的端口，因此兼容默认 `5900 → 8080 → 9010` Compose 链路。自定义
表单客户端需先获取登录页。OAuth 协议端点仍使用标准协议校验。password token endpoint 的兼容参数不变。

IAM 不再通过全局 Servlet Filter 向所有端点开放 CORS。仅 `/oauth2/token` 在 OAuth2 协议安全链中
支持显式来源跨域，来源由 `macula.cloud.iam.token-cors-allowed-origins` 或环境变量
`IAM_TOKEN_CORS_ALLOWED_ORIGINS` 配置，多个来源使用英文逗号分隔；不允许 `*`，留空表示禁用跨域。
允许的方法仅为 `POST`/`OPTIONS`，且不携带浏览器 Cookie。登录、绑定、授权页面及其他端点应使用
同源 `/iam` 代理，不因 Token 端点需要跨域而扩大暴露范围。

## Redis 授权存储与升级

v2 使用授权主记录＋七类索引：access_token、refresh_token、code、id_token、device_code、user_code、state。`findByToken(token, null)` 按这七种类型查找，指定类型只查询对应类型；未命中返回 null，Redis/序列化故障向上传播，不能伪装成无效凭据。

- 新键使用 `{iam-oauth2}` hash tag 与 token/授权 ID 的 SHA-256 摘要。多键 Lua 更新位于同一 Redis Cluster slot，支持当前单实例 Redis，也避免跨 slot 事务；高吞吐场景需评估这个单 slot 的容量。
- 主记录包含递增版本。读取到的授权快照必须原样保留内部版本属性后再更新；并发覆盖被拒绝为 invalid_grant。刷新/撤销/轮换原子更新主记录与索引，不再返回旧令牌快照。
- TTL 按 expiresAt 减当前时间计算，毫秒精度，不在每次保存时续完整生命周期。state 首次存储后不续期。ID Token 索引保留至授权生命周期结束，以支持标准退出。
- 删除写入保留到授权最长到期时间的墓碑，防止残留的历史索引复活授权。主记录仍在时，旧格式快照只能作为定位依据，不能覆盖新状态。
- 保留原 DTO 的 JDK serialVersionUID，读取旧键中的 Authorization；首次更新转为 v2。主记录及授权属性均使用 Jackson 3；SecurityJacksonModules 自动加载 OAuth2AuthorizationServerJacksonModule，MaculaIamJacksonModule 注册自定义认证对象。仅显式许可 IAM 用户、验证码/小程序认证 token 和 Long 类型，不开放任意包或 Object 类型。
- Jackson 2 历史 JSON 的类型标记和数字时间戳仍可读取；测试覆盖旧 DTO、旧 JSON、自定义认证对象及未知类型拒绝。共享注解仍使用 com.fasterxml.jackson.annotation（Jackson 3 的正常用法）；不排除 Nacos 自身所需 Jackson 2 依赖，IAM 运行时代码不再使用 Jackson 2 mapper/module。
- 旧格式没有授权 ID 索引：尚未迁移的记录可按旧 token 查找，但不能凭 ID 全库扫描。新建/更新记录支持 findById。旧版本未保存的 device/user code 和缺失的 ID Token 索引无法凭空恢复。

升级应停止旧 IAM 写入后切换新节点，禁止新旧版本混写。回滚不能让旧节点继续信任残留的旧缓存；应切换隔离的认证缓存命名空间/Redis 数据库并要求重新登录，或由运维按精确认证键范围执行已批准的失效操作。不要清空共享 Redis。不存在数据库迁移。

## 签名与 issuer 配置

| 环境变量 | 说明 |
| --- | --- |
| IAM_ISSUER_URI | 浏览器和外部身份提供商可访问且稳定的公开 issuer；通过 Admin `/iam` 回调时须设为该公开地址（含 `/iam`） |
| IAM_TOKEN_CORS_ALLOWED_ORIGINS | 允许跨域调用 `/oauth2/token` 的精确浏览器来源，多个值以英文逗号分隔；留空禁用，禁止 `*` |
| IAM_SIGNING_KEY_LOCATION | Spring Resource 地址，例如 file:/run/secrets/iam.jks |
| IAM_SIGNING_KEY_ALIAS | keystore alias，默认 jose |
| IAM_SIGNING_KEY_PASSWORD | 外部注入的 keystore 密码 |

仅 local/docker 可回退到仓库已有的公开演示 keystore。共享/生产环境必须提供独立密钥；未配置时启动失败。Docker Compose 本机默认 issuer 为 `http://127.0.0.1:${IAM_HOST_PORT}`，因此直接从该端口发起 GitHub/企微登录时会回到同一地址；如果从 Admin 的 `/iam/login` 进入且希望回到该同源地址，应显式设置 `IAM_ISSUER_URI` 为 Admin 的公开 `/iam` 地址，并在 GitHub/企业微信后台精确登记对应的 `<issuer>/login/github` 与 `<issuer>/login/wecom`。跨机器、HTTPS 或反向代理部署同样必须显式配置公开 issuer；一个身份源登记的固定回调不随请求 Host 动态变化。不提交真实签名密钥或密码。

## 开发验证

```bash
# 在仓库根目录；TEST_REDIS_SERVER 指向本机 redis-server
TEST_REDIS_SERVER=/path/to/redis-server mvn -pl macula-cloud-iam -am test -Plocal
mvn -pl macula-cloud-iam -am package -Plocal -DskipTests
```

测试自行启动随机端口、无持久化的隔离 Redis，结束后关闭，不连接业务 Redis。macOS 默认尝试 /opt/homebrew/bin/redis-server；其他平台必须配置路径。协议测试使用真实 SecurityFilterChain、模板、Redis 和演示签名密钥，模拟数据库身份/客户端服务，不依赖 Nacos/MySQL。

测试同时生成 target/iam-preview 的模板渲染结果，可用仅监听本机的静态服务器检查；预览仅含测试数据，不代表真实登录后端：

```bash
python3 -m http.server 18743 --bind 127.0.0.1 --directory macula-cloud-iam/target/iam-preview
# 另一个终端；安装 Playwright 并指定其模块位置，需要本机 Chrome
PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node macula-cloud-iam/src/test/ui/check-ui.cjs
```

浏览器回归涵盖 320/390/844/1440px、无横向溢出、触控尺寸、密码显隐、错误后恢复、拒绝授权；截图位于 target/iam-preview，不纳入提交。生产数据库身份、外部代理 issuer、真实网关部署及设备 Safari/软键盘仍需环境联调。

## 扩展

身份源实现 UserAuthInfoService；协议和 grant 使用 Spring Security 的扩展点。复用框架能力应回到 Macula Boot，IAM 仅保留本服务的协议与身份编排。CAS/SAML 尚未实现。
