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

短信入口保留原有默认开启行为，`spring.security.oauth2.server.login.sms.enabled` 控制展示，默认 true。

GitHub 与企业微信网页登录配置直接读取共享 System 数据库的 `sys_identity_provider`。只有有效
OAuth client（已配置 `tenant_id` 与 `user_type`）且对应 provider 同时 `enabled=true`、
`visible=true` 时登录页才展示入口；`visible=false` 只隐藏入口，`enabled=false` 还会拒绝直接访问。
OAuth client 决定最终租户与用户类型。回调账户路由只读取一次 client 配置，将该快照与 state
绑定值核对后使用同一快照查询账号；回调期间若其配置发生变化会拒绝登录。

GitHub OAuth 不申请额外 scope，只接受 `/user` 返回的公开稳定数值 `id`，并通过 `sys_user_identity_binding` 的
`(provider_type='GITHUB', provider_subject) -> username` 全局绑定解析账号，不使用 login 或 email
兜底。绑定表不含 tenant ID、user ID 或 user type，同一 provider 的 subject 与 username 都唯一；
当前不提供公开写入接口，须由受控数据库/运维流程预置。企业微信使用 `snsapi_base`，只接受企业
成员 `UserId` 并直接作为 username，不写绑定表。两者随后均按 OAuth client 的 `user_type` 和
`tenant_id` 调用现有用户身份路由。只有 `EMPLOYEE` 明确复用现有 System 用户源；其他用户类型
必须由 `UserAuthInfoService` 返回匹配账号，空结果不会回退到同名系统用户。

第三方回调固定由 `macula.cloud.iam.issuer-uri` 生成，state 与浏览器会话、provider、client、租户、
用户类型和原始 OAuth 授权请求的受控相对目标绑定，五分钟内仅可原子消费一次。成功后只恢复该
state 自带的服务端目标，不使用会话中可能被其他标签页覆盖的 SavedRequest。第三方授权码和 token
不持久化，也不写日志。部署时 IAM 与
System 必须共享 `MACULA_CRYPTO_PASSWORD`；GitHub 回调为 `<issuer>/login/github`，企业微信回调为
`<issuer>/login/wecom`，应在各开放平台精确登记。企业微信自建应用还需把实际登录成员纳入应用可见范围。
登录页通过同源、CSRF 保护的 `POST /api/v1/sms-code` 请求验证码。IAM 只读共享 System 数据库中的
唯一启用短信供应商和 `SMS_LOGIN` 中文模板，使用阿里云或腾讯云 SDK 提交；配置缺失、重复、
不受支持或无法解密时失败关闭。验证码使用安全随机数生成，只以 HMAC-SHA256 摘要存入 Redis，
默认有效 5 分钟、最多尝试 5 次，手机号 60 秒内仅可申请一次，来源 IP 每个 60 秒窗口最多申请
20 次，并在供应商受理后
才激活可验证记录。成功验证原子删除记录，错误达到上限或过期后不能认证。

标准 `/iam/` 反向代理部署必须传递 `X-Forwarded-For` 与 `X-Forwarded-Proto`；IAM 使用容器原生的
受信代理处理恢复客户端地址，且不会信任来自非内部代理的转发头。验证码页面使用相对 URL，确保
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
| IAM_ISSUER_URI | 客户端可访问且稳定的外部 issuer；反向代理场景必须显式配置 |
| IAM_SIGNING_KEY_LOCATION | Spring Resource 地址，例如 file:/run/secrets/iam.jks |
| IAM_SIGNING_KEY_ALIAS | keystore alias，默认 jose |
| IAM_SIGNING_KEY_PASSWORD | 外部注入的 keystore 密码 |

仅 local/docker 可回退到仓库已有的公开演示 keystore。共享/生产环境必须提供独立密钥；未配置时启动失败。Docker 默认内部 issuer 仅供本地开发，浏览器接入时应覆盖为可访问的外部地址。不提交真实签名密钥或密码。

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
