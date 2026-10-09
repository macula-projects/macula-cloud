# Plan: IAM OAuth2 升级兼容与登录授权界面
Status: accepted

## Scope and approval
用户已明确要求执行上一轮兼容性修复建议，并补齐专业化登录、授权界面。现有 admin-oauth-configuration 的 accepted spec/plan 明确排除 IAM，不能作为本次代码变更合同。2026-10-08 用户在 Redis、PKCE、password 兼容和 OIDC 范围全部补充后明确回复“确认”，接受本独立方案，开始实施；该确认不代表 Build 完成或 Test/Review 通过。

2026-10-08 范围补充：用户明确要求 `findByToken` 在 `tokenType == null` 时查询各 token 类型，并同时修复 Redis 问题。以下专项契约补充到原方案，登录、授权界面及 TokenGenerator 工作仍保留。

2026-10-08 协议补充：用户明确要求补齐授权码 PKCE、保留仅供兼容的 password grant 扩展，并支持 OIDC。这三项为本次实施要求，不得在重构生成器或升级框架时删除 password 扩展。

## Files that change
以下路径均相对 macula-cloud-iam/：
- src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationService.java：修正空值返回、多类型查询、剩余 TTL，以及刷新轮换与撤销时的授权状态一致性。
- src/main/java/dev/macula/cloud/iam/pojo/dto/Authorization.java：补齐 device_code/user_code 的持久化字段，保持历史字段可读；增加缺失的版权头。
- src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java：保留用户已有五分钟修复，补充客户端不存在的契约处理。
- src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java、OAuth2ConfigurerUtils.java：构建阶段注册扩展 Provider，统一令牌生成配置。
- src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2ResourceOwnerBaseAuthenticationProvider.java：保留原兼容 grant 标记与异常映射，只移除认证对象调试输出。
- src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/CustomeOAuth2AccessTokenGenerator.java：保留现有 opaque token 格式与公共类名；联合标准 JwtGenerator 支持已配置的 self-contained 客户端。
- src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizer.java：核对各授权流程的客户端与用户 claims。
- src/main/java/dev/macula/cloud/iam/config/JwtConfiguration.java、src/main/resources/application.yml：核对 OIDC 签名、解码与 issuer；签名密钥支持外部配置，仓库演示密钥仅用于本地开发，不生成或提交生产密钥。
- src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOidcTokenCustomizer.java（按实现需要新增）：按授权 scope 提供真实的用户 claims，区分 ID Token 与 access token。
- src/main/java/org/springframework/security/config/annotation/web/configurers/AbstractLoginFilterConfigurer.java：补齐认证上下文保存。
- src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java：页面资源放行与表单安全配置。
- src/main/java/dev/macula/cloud/iam/controller/LoginController.java、protocol/oauth2/endpoint/AuthorizationConsentController.java：模板模型、身份与客户端校验、可用登录方式。
- src/main/java/dev/macula/cloud/iam/service/userdetails/CaptchaServiceImpl.java：按用户纠正保留原有默认返回 true 的占位行为，不修改该文件。
- src/main/resources/templates/login.html、oauth2/consent.html、index.html：统一中文品牌与完整交互。
- src/main/resources/static/iam/auth.css、auth.js（新增）：本地共享样式与页面交互，不依赖外部 CDN。
- README.md：依赖基线、支持模式、缓存兼容、登录入口及验证说明。
- src/test/java/dev/macula/cloud/iam/ 下新增授权存储、客户端仓库、grant、SecurityFilterChain、页面渲染与会话测试；优先沿用 JUnit/Mockito，以隔离设施进行必要集成验证。
- 本目录 spec.md、plan.md：经人审确认后记录合同与实际偏差。

## Order of work
1. 确认独立范围与合同；保留工作区用户修改。当前已创建 fix/iam-oauth2-login-consent 分支，未修改业务代码。
2. 先补授权存储失败用例，再修正缺失 token、无类型查找、剩余 TTL、轮换与撤销一致性；保留既有 Redis 键兼容，若需改变数据结构先明确迁移方案。
3. 调整 Provider 注册时机，保留兼容 grant 原有业务语义，统一 token 生成器；默认保持 reference token 与 Gateway introspection，JWT 仅服务显式配置的客户端，不切换平台默认认证模式。
4. 验证 Jackson 2 缓存往返及自定义认证对象。优先保留可验证的兼容配置；不直接替换共享 Macula Boot 序列化框架，不清空 Redis。若必须迁移 Jackson 3，先记录跨版本数据读取方案与影响。
5. 完善登录态保存、登录回跳和表单安全；短信登录入口保留默认开启，后端校验占位行为保持不变。
6. 重做 Thymeleaf 页面：桌面为深蓝品牌区与白色表单卡片，移动端单列；中文标题、清晰标签、键盘焦点、密码显隐、允许粘贴、错误反馈与防重复提交。授权页展示经服务端查询的应用名、当前用户、新请求权限与已授权权限；提供清晰的允许与拒绝按钮，未知 scope 保留原始标识，不虚构含义。
7. 更新文档并完成实现阶段的必要检查；记录偏差，由用户确认 Build 完成后进入正式 sdlc-test。

## Risks
- 认证变更需同时检查允许和拒绝路径。浏览器页面不展示 token、客户端 secret 或演示密码。
- 当前短信页面模拟发送成功，校验实现无条件通过；本次移除该误导交互，按用户纠正保留后端占位行为，不引入短信供应商。
- Jackson 3 是新版默认，但现有 Jackson 2 并非必然失效；避免无依据批量迁移与既有会话失效。
- 授权状态多键存储可能出现旧 token 残留；需验证刷新轮换与撤销一致性，不能仅修空指针。
- 保留 password/sms 扩展契约与既有 opaque token 格式；不把 password 描述为 OAuth2.1 标准能力。
- 本次页面为 IAM /login、/oauth2/consent 及登录后承接页，不重做 Vue Admin 登录页面，不增加 CAS/SAML、注册或找回密码服务。
- 不部署、不推送、不提交真实凭据、不自动批准任何 SDLC gate。

## Redis and token lookup contract
1. `tokenType == null` 依次查找 access_token、refresh_token、code、id_token、device_code、user_code、state；命中有效的对应记录即返回，全部未命中返回 null。state 虽非令牌实体，也必须支持框架授权请求状态查找。所有类型由一处定义驱动查找、保存、清理与测试，避免只扩展查询却没有对应存储。
2. 指定类型时仅按该类型查询；未知类型返回 null，不降级为 access_token。不使用 Redis KEYS/全库 SCAN。无效参数仍按接口契约拒绝，Redis 连接或反序列化故障不得伪装成“没有令牌”。
3. 当前 DTO 虽保存 id_token，却没有相应 Redis 索引；device_code/user_code 连 DTO 字段也缺失。需要同步补齐序列化与索引生命周期，不通过返回另一种 token 对应记录来假装支持。
4. TTL 使用 expiresAt 减当前时间，以毫秒存储；已过期时清理而非执行零/负 TTL 写入。同一授权再次保存不得重置完整生命周期。state 使用固定到期时间，不在重复保存时重新续十分钟。
5. 补齐 findById。采用授权主记录与 token 索引关联，读取时核对主记录中的实际 token，避免轮换后读到旧副本；保存时清理被替换索引，删除时清理主记录和所有索引。保留失效 metadata 供框架判断，不把撤销当成重新签发。
6. 多键更新必须考虑并发与部分失败；具体原子写入方式应适配当前 Redis 部署。仅有 MULTI/EXEC 不能声称解决并发 refresh 请求重复消费问题，该项需独立拒绝路径验证。
7. 升级兼容：读取旧 token 键中的 Authorization DTO；新主记录存在时必须以主记录为准，旧索引不得恢复已撤销或被替换 token。删除后的旧数据回退也不得复活授权。实施前明确新旧键迁移与回滚限制，不清空全库或无条件双写旧副本。
8. CustomeOAuth2AccessTokenGenerator 只负责令牌生成，不承担 Redis 查询。客户端标识从 RegisteredClient 获取，避免依赖可空 authorizationGrant；保留现有 Base64 字段布局，联合标准生成器处理其他格式。

## Authorization code, PKCE and OIDC contract
1. 标准交互流程为 authorization_code + PKCE；使用 S256，验证 code_challenge 与 code_verifier，拒绝缺失、错误、降级挑战以及重复使用授权码。公共客户端使用认证方式 none，不要求浏览器保存 client secret；机密客户端仍验证其客户端身份，PKCE 不替代客户端认证。
2. 当前 requireProofKey 已由数据库映射，但字段存在不等于 PKCE 端到端已验证。公共客户端强制 PKCE；机密客户端显式启用 requireProofKey 时同样强制，现存未开启 PKCE 的机密客户端需在接入文档明确迁移，不静默批量改写数据库。标准接入示例全部采用 S256。
3. password grant 永久保留在本次兼容范围：保留 converter、provider、客户端 grant 白名单检查、请求与返回契约。PKCE 校验只作用于授权码流程，不错误要求 password 请求携带 code_verifier。不将 password 声称为标准 OIDC 登录流程，也不因请求 openid 就给 password 扩展自动签发 ID Token。
4. OIDC 核心范围：Discovery、JWKS、携带 openid 的授权码流程签发 ID Token、UserInfo、标准退出端点。ID Token 为签名 JWT，验证 iss/sub/aud/exp/iat 及请求携带时的 nonce；必须有稳定的用户 subject，不以可能改变的昵称代替身份。
5. profile/email 等用户信息按实际授权 scope 返回，只暴露身份源中真实存在的字段，不虚构邮箱或 email_verified。UserInfo 的 sub 与 ID Token 一致；没有 openid、过期/撤销 token 或把 ID Token 当 access token 的请求必须拒绝。
6. 现有代码已经启用 oidc(withDefaults)、配置 JWKSource 和 JwtDecoder；在此基础上修复并验证，不重复造协议端点。明确校验当前默认 opaque access token 的 UserInfo 路径，不能只验证 JWT 客户端；ID Token 为 JWT 不意味着强制所有 access token 改为 JWT。
7. 登录与同意页必须保持框架保存的原始授权请求，使 state、nonce、PKCE challenge 与 redirect_uri 在回跳后仍绑定同一次请求；页面不能用请求参数绕过已注册回调校验，拒绝授权按协议返回 access_denied。
8. 标准退出流程校验 ID Token hint 和会话关系，不接受任意退出回调地址。当前客户端实体没有 post_logout_redirect_uris，本次不臆用普通 redirectUris 代替；暂不增加退出后外部跳转，若需该能力则明确补充 System 所有的数据迁移和客户端管理契约。
9. 不默认开启 OIDC 动态客户端注册、隐式流程或混合流程；不将 Vue Admin 的既有 password 接入强制迁移为授权码，本次补齐 IAM 能力及标准接入示例。

## Proof
1. 单元测试：参数化覆盖 null 类型查询全部七种索引、显式类型隔离、未知类型/未命中、错误传播；id_token/device_code/user_code 保存读取删除往返；TTL 以剩余时间计算且 state 不续期；旧 refresh token 轮换后不可复用，撤销/删除后旧索引不能恢复授权；findById、客户端不存在与五分钟默认值。
2. 授权测试：password/sms 原有参数、校验、grant 存储标记与异常映射兼容；reference 与显式 self-contained 各自能生成令牌；允许/拒绝 scope。
3. 序列化测试：历史结构 fixture 和新增授权对象往返，覆盖 Principal、自定义用户属性、scope、token metadata。
4. Security/Web 测试：登录成功后下一请求保持认证，错误凭据失败，匿名授权请求回到登录，登录完成恢复授权请求，拒绝授权不签发 code；表单 CSRF 按实际安全配置验证。
5. 页面验证：桌面与移动端渲染、无外部资源依赖、键盘操作、校验/加载/网络错误/成功跳转、权限勾选与拒绝；无可用短信服务时不显示发送入口。
6. 正式 Test 阶段执行 mvn -pl macula-cloud-iam -am test -Plocal；可用隔离环境再验证 authorization_code + PKCE、refresh、introspection、revocation 与 Gateway 调用。明确区分单元、集成和浏览器证据，设施缺失时不声称联调通过。
7. PKCE 测试：公共/机密客户端的 S256 成功；缺失或错误 verifier、缺失 challenge、plain 降级、错误 redirect_uri、授权码重放失败；password 兼容请求不受 PKCE 影响。
8. OIDC 测试：Discovery/JWKS、签名 ID Token、nonce 与 audience、scope 控制的 UserInfo、opaque/JWT access token 两种路径、token 撤销后 UserInfo 拒绝、退出端点与非法回调拒绝；非 openid 请求不签发 ID Token，password 不伪装成 OIDC 授权码登录。

## Implementation decisions and deviations

- 2026-10-09 用户指出 Jackson 2 未升级并明确回复“改”，授权修正为 Jackson 3 完整链：迁移授权服务的两个 mapper、jackson2 包的 module/mixin/deserializer、SysUserDetails databind 注解和 IAM HTTP JSON mapper；保留共享 Jackson annotation 包及 Nacos 所需 Jackson 2 依赖。采用 SecurityJacksonModules 显式类型许可，验证旧 JDK DTO/旧 JSON 时间与认证属性可读。不修改 password/sms 业务。此前测试/评审仅适用于迁移前版本，本次回到 Build，补齐跨版本回归证据后重新确认完成。

- 2026-10-09 根据用户“继续”补齐验证证据：仅扩展 IamProtocolTest、MaculaOAuth2AuthorizationServiceTest、src/test/ui/check-ui.cjs，新增 src/test/resources/fixtures/legacy-authorization.base64 及来源说明。覆盖 Redis 故障、历史二进制 DTO、PKCE 参数缺失/回调不匹配、OIDC scope 隔离和页面网络/超时/键盘/重复提交/跳转。不改 password/sms 或其他运行时代码。公共客户端缺 verifier 沿用现有认证入口返回登录重定向，测试明确断言未发令牌；机密客户端返回 400。

- 2026-10-09 用户授权修正正式验证发现的 TinyID 测试文案断言：仅修改 `macula-cloud-admin/cypress/e2e/tinyid-management.cy.js`，使预期与现有创建对话框提示一致，不修改 TinyID 业务页面或接口。

- 2026-10-09 用户确认继续修复兼容 grant 携带 openid 的刷新异常。新增 `src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/CompatibilityRefreshTokenAuthenticationProvider.java`，仅适配原授权类型 password/sms、含 openid 且没有 ID Token 的刷新；其他请求委托原框架 Provider。保留授权 scope、原 grant 标记、客户端绑定、有效性、scope 子集、刷新复用/轮换及 Redis CAS；不生成 ID Token、不修改原 password/sms 验证。注册位置为 ProtocolOAuth2Configuration，新增 IamProtocolTest 协议回归。该兼容分支仅处理已有的机密客户端 Bearer 刷新，不将 sender-constrained token 降级。

- 2026-10-09 用户澄清指的是 `/oauth2/token` 自定义 password/sms 扩展内容，不是页面开关。本轮恢复公共 Provider 原有 PASSWORD_GRANT_TYPE 存储标记与异常映射；只保留认证对象日志移除。补齐 token endpoint 独立管理器的 CaptchaAuthenticationProvider 注册，复用原验证码与用户服务，不改变业务实现。新增直接协议测试，身份源用测试数据模拟，默认验证码 true 保持真实实现。
- 新回归发现兼容 grant 请求 openid 后刷新会进入框架 ID Token 刷新路径，因原扩展不签发 ID Token 而异常；后经用户确认按上述独立刷新适配修复，不改变兼容 scope 语义。

- 2026-10-09 用户再次明确 SMS/password 均为兼容能力：恢复短信登录入口默认开启，保留原端点、参数、converter/provider；PKCE/OIDC 不作为兼容 grant 的前置条件。同步恢复页面回归的入口断言。

- 2026-10-09 用户纠正：手机号验证码默认返回 true 是需保留的原有行为，撤回将其改为 false 的变更；真实短信验证不在本次范围内。
- 用户进一步要求登录页支持移动端；已在原移动布局范围内明确 320/390px、横屏与桌面验证。
- 新增 LocalAccessTokenIntrospector.java，使用 Security 7 JWT resource-server configurer 的显式 AuthenticationManager 扩展点，统一以本地存储验证 opaque/JWT access token；默认 OIDC 自动 JWT 过滤器无法直接处理现有 opaque token。
- SysUserDetails.java 与 SysUserDetailsServiceImpl.java 增加 userId/tenantId，以稳定 ID 形成 OIDC sub；认证后清除密码，将 authorities 规范为 Jackson Security 已支持的 ArrayList，不扩大反序列化 allowlist。
- CaptchaLoginFilterConfigurer.java 删除重复安装过滤器的一行；通用登录 configurer 将过滤器放到 CSRF 之后并显式保存 SecurityContext，避免重复认证与绕过表单保护。
- 授权拒绝通过框架 consent customizer 处理，已校验 state/principal 后取消当前授权；不能只清空 scope，因为框架会合并历史 consent，导致点击拒绝仍获准。保留此前 consent。
- Redis v2 采用同一 {iam-oauth2} hash slot 的 Lua CAS 原子更新；新增 DTO 序列化 UID 固定为原类型计算值。旧记录读取保留，禁止新旧节点混写；回滚限制写入 README。
- OIDC Logout 按 7.0.7 Provider 契约允许已过期 ID Token hint，因此 ID Token 索引保留到授权主记录到期；其他 token 查找仍拒绝过期值。
- 迁移前历史记录（已由 2026-10-09 Jackson 3 决策替代）：当时保留显式 Jackson 2 Security 模块。开发测试发现 ID Token claims 取错位置和不可变 List 类型问题，已修正；未批量迁移共享 Macula Boot 序列化设施。当前 IAM 已使用 Jackson 3。
- 新增测试依赖 spring-security-test（父 BOM 管理版本），新增 src/test/ui/check-ui.cjs；协议测试使用隔离真实 Redis 与真实过滤链，数据库身份/客户端服务模拟。未运行全平台真实 Gateway/MySQL/Nacos 联调。
- OAuth2ConfigurerUtils.java 无运行时调用，保留原文件避免无关删除；统一 tokenGenerator bean 被标准与兼容 grant 共用。
- 现存客户端实体无邮箱，OIDC 不虚构 email/email_verified；仅 profile 提供真实姓名标识字段。CAS/SAML、短信发送和动态客户端注册不扩展。

## Build evidence
Jackson 3 修正后，本轮明确询问“实现完成、补齐 intent 并进入正式测试”，用户回复“继续”；据此补录需求并进入正式 Test。未授权提交、推送、部署或自动批准评审。
2026-10-09 用户对 Build 完成 gate 回复 YES，确认本轮实现完成；进入正式 Test。此确认不代表测试或评审通过。
开发阶段已执行目标模块 test/package 与浏览器回归；实际结果与限制见 build-notes.md。未自动推进 Test/Review gate，未提交或部署。
