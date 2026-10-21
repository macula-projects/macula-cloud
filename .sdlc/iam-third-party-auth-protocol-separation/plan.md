# Plan: IAM 第三方身份认证与协议解耦及企业微信双场景登录 (from spec.md 2026-10-19)

## Files that change
- IAM 第三方发起、回调、认证与 state：
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationToken.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepository.java`
  - 删除 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationSuccessHandler.java`
  - 删除 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFailureHandler.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandler.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/AuthenticationFailureReason.java`
  - 删除实施中间产物 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingFailureHandler.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyUserDetailsService.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionService.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/PendingThirdPartyBindingRepository.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`
- 提供商适配：
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationFilter.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubClient.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProvider.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubLoginFilterConfigurer.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationFilter.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProvider.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginFilterConfigurer.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginMode.java`
- 本地账号及短信证明：`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/SysUserDetailsServiceImpl.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysUserService.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysUserServiceImpl.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysUserMapper.java`、`macula-cloud-iam/src/main/resources/mapper/SysUserMapper.xml`。复用 `SmsService` 和现有验证码存储/发送实现，不改变独立短信登录或 token grant。
- 安全链、页面及文档：`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`、删除 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/CorsFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`、新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ThirdPartyBindingController.java`、删除 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/RedirectLoginAuthenticationSuccessHandler.java`、删除 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/JsonAuthenticationEntryPoint.java`、`macula-cloud-iam/src/main/resources/templates/login.html`、`macula-cloud-iam/src/main/resources/static/iam/auth.js`、新增 `macula-cloud-iam/src/main/resources/templates/third-party-bind.html`、`macula-cloud-iam/src/main/resources/static/admin/css/other/login.css`、`macula-cloud-iam/README.md`。
- System 数据约束：新增 `macula-cloud-system/src/main/resources/db/migration/V7__sys_user_username_unique.sql`；修改 `macula-cloud-system/README.md` 说明 V7 预检、失败恢复和回滚边界。不改写 V1/V6 历史迁移，不自动清理重复用户。
- 定向测试：
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandlerTest.java`
  - 删除实施中间产物 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingFailureHandlerTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyUserDetailsServiceTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProviderTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProviderTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingServiceTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginModeTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ThirdPartyBindingControllerTest.java`
  - `macula-cloud-iam/src/test/ui/check-ui.cjs`
  - 新增 `macula-cloud-system/src/test/java/dev/macula/cloud/system/migration/SysUserUsernameUniqueMigrationTest.java`
  新增测试若仓库现有集成测试夹具不支持 Flyway/MySQL，则在实施前记录验证替代方式，不伪称已做真实迁移测试。
- 本计划及审查记录：`.sdlc/iam-third-party-auth-protocol-separation/plan.md`、`.sdlc/iam-third-party-auth-protocol-separation/review-findings.md`。不修改 eIAM/AGPL 参考仓库、不引入新运行时依赖、不触及 Gateway 或管理端源码。

实施偏差记录：`GithubAuthenticationFilter.java` 的 `authorizationUri` 覆盖方法必须随公共过滤器新增 `ThirdPartyLoginContext` 参数同步调整，原计划漏列；只调整方法签名和调用转发，不改变 GitHub 授权参数，相关 Provider/Flow 测试覆盖该点。现有真实 SecurityFilterChain 集成测试 `IamProtocolTest.java` 独立声明 Bean 并断言旧 client 行为，公共入口变更后必须同步更新该测试夹具和断言，原计划漏列；不扩大产品功能范围。用户明确自行处理 `UserAuthInfoService` 的租户角色装载实现，本次不扩展令牌定制器文件范围；角色权限的最终真实实现与联调由该接口实现方完成。

补充偏差：现有 `IdentityProviderMigrationIT.java` 会把“迁到最新版”误认为仍是 V6；新增 V7 后须将其两处目标版本显式固定为 V6，原有 V6 断言才不失效。独立的 V7 迁移测试仅在用户提供空的隔离 MySQL 库并设置 `SYSTEM_MIGRATION_IT=true` 后实际运行；常规测试执行时只编译该测试并跳过数据库操作。绑定页的真实安全链与 CSRF 还在 `IamProtocolTest.java` 验证。新增 `ThirdPartyIdentityResolutionServiceTest.java` 对企微直匹配/冲突及 GitHub 待绑定独立分流做单元验证，以免依赖 Provider 模拟掩盖服务层回归。

2026-10-20 用户修正：第三方身份认证公共层不应硬编码 `/oauth2/authorize` 或要求 `client_id` 来决定登录后续转。把已保存的请求恢复策略放宽为同一应用上下文内的单斜杠相对路径，仍拒绝协议相对 URL、跨上下文及不安全路径；目标继续由会话中的 `SavedRequest` 在发起时封入一次性 state，回调参数与 Cookie 不得决定目标。原 OAuth2 授权请求依旧由协议安全链自行校验；追加非 OAuth 受保护页面、直接登录及恶意路径的回归测试。企微账号解析查询顺序同步调整为先 `UserId=username`，再检查已有绑定与冲突，不改变结果规则。此修正只涉及原计划列出的 Filter、成功处理器、身份解析服务、测试及 IAM README。

2026-10-20 回调地址部署修正：本地 Compose 的 Docker profile 将未配置 `IAM_ISSUER_URI` 时的 issuer 错误回退到容器 DNS 名，导致 GitHub/企微收到浏览器不可访问的回调；Admin `/iam/` 代理剥离前缀后也未告知 IAM 外部路径。用户要求修复这两个实际入口，因此扩展文件范围到 IAM `application.yml`、`deploy/docker-compose.yml`、`deploy/README.md`、Admin `nginx.conf` 和相应 IAM 安全链回归测试。保留 spec 的受信 issuer 约束：默认仅面向本机直连 `127.0.0.1:${IAM_HOST_PORT}`，跨机或要求回到 Admin 同源 `/iam` 时显式设置公开 `IAM_ISSUER_URI` 并在提供商登记相同回调；不得依据任意请求 Host 自动生成回调。代理需覆盖而非透传客户端提交的 forwarded 前缀/主机头，并验证带前缀页面链接及直连第三方回调。

2026-10-20 第三方接口诊断日志补充：真实企微回调在 `WecomClient` 的身份响应校验处失败，但原企微和 GitHub 客户端均丢弃 HTTP 状态、提供商错误码及调用阶段，原公共失败处理器又直接记录任意异常全文和堆栈。诊断职责已收敛到 `GithubClient.java`、`WecomClient.java` 及其 Provider 测试：只记录本地白名单字段 provider、token/user 阶段、HTTP 状态、企业微信数字 `errcode` 和本地失败类别；企业微信 `errmsg`、GitHub `error`/`error_description` 等外部自由文本一律不落日志，也不得记录授权码、access token、client/corp secret、完整响应体或外部用户标识。浏览器 FailureHandler 不承担诊断日志职责，也不记录任何异常消息或堆栈；绑定待处理是正常分流，不记错。该补充不改变登录协议、异常对外文案、跳转或身份解析结果。

2026-10-21 用户修正：认证定位为浏览器闭环，JSON 响应属于 OAuth2/OIDC 等协议端点，不应由密码、短信、小程序或第三方浏览器登录 Handler 返回。所有浏览器认证成功统一使用 Spring Security `SavedRequestAwareAuthenticationSuccessHandler`，普通失败统一使用 `SimpleUrlAuthenticationFailureHandler` 返回登录页；第三方仅通过 `DelegatingAuthenticationFailureHandler` 为 `BindingRequiredException` 保留保存待绑定状态的薄适配器。删除两个第三方结果 Handler 和登录表单 JSON 拦截逻辑。第三方回调在认证成功前必须核对一次性 state 中的受控目标与当前 `RequestCache` 完全一致，直接登录则同时要求无缓存请求且目标为上下文根路径，之后才交给原生成功 Handler，避免多标签页或被替换 SavedRequest 绕过 state 绑定。此修正不改变协议端点响应、第三方提供商日志白名单或待绑定安全边界。

2026-10-21 FailureHandler 收敛修正：用户要求浏览器认证失败也统一为一个新类。新增 `BrowserAuthenticationFailureHandler`，由同一个实例服务表单、短信、GitHub 和企微登录；它仅对 `BindingRequiredException` 保存已核验的待绑定身份并跳转 `/login/bind`，其他异常委托 Spring Security 原生 `SimpleUrlAuthenticationFailureHandler` 统一跳转 `/login?error=authentication`。该 Handler 不输出日志；第三方失败诊断继续由 Provider 客户端按白名单记录。删除实施中间产物 `ThirdPartyBindingFailureHandler` 及 `DefaultSecurityConfiguration` 中的 `DelegatingAuthenticationFailureHandler` 组装代码；不改变 OAuth2/OIDC 协议端点错误响应、待绑定条件或异常日志白名单。

2026-10-21 失败原因展示修正：用户要求浏览器认证失败后显示原因，但不得把 `AuthenticationException#getMessage()`、第三方自由文本或内部异常直接回显。新增 `AuthenticationFailureReason` 枚举，把异常只映射为受控错误码和固定中文文案：用户名/密码/短信入口中的账号不存在、凭据错误、锁定、禁用、账号或凭据过期全部公开为同一个“认证信息无效或账号不可用”，避免通过文案差异确认账号存在；其他系统或第三方故障公开为“认证暂不可用，请稍后重试”。`BrowserAuthenticationFailureHandler` 使用 `OAuth2ParameterNames.ERROR` 携带枚举错误码，`LoginController` 只解析已知枚举并把固定文案交给模板；未知或伪造错误码回退到统一认证失败文案。待绑定仍直接进入绑定页，不显示为认证错误，内部诊断只保留既定白名单字段。

2026-10-21 Code review Round 1 修正：回调阶段复用发起阶段同一个 `SavedRequest` 受控目标解析方法，再与 state 中的相对 `path?query` 比较，避免把原始绝对 URL 与相对目标直接比较而拒绝正常续转。绑定页取消改为同源、CSRF 保护的 `POST /login/bind/cancel`，由待绑定仓库主动清除会话状态后返回登录页；安全链只为该匿名取消入口增加显式放行。补充匹配 SavedRequest 成功回调、取消 CSRF、清除后页面拒绝及绑定服务拒绝测试，不改变其他认证和协议端点行为。

实施偏差补充：Stage 4 浏览器回归发现 `macula-cloud-iam/src/test/ui/check-ui.cjs` 仍断言旧的两个第三方入口和 JSON 登录交互，原文件清单遗漏该受影响测试。将其纳入文件范围，改为断言 GitHub、企业微信自动选择、系统浏览器和 WebView 四个入口，以及浏览器原生 POST 表单提交；`IamProtocolTest` 同步导出待绑定页面，浏览器回归覆盖取消表单的 POST、CSRF 和触控尺寸。该偏差只更新已废弃的测试契约，不恢复 JSON Handler 或扩大运行时功能。

2026-10-21 Code review Round 2 修正：SavedRequest 的受控目标除既有路径规范化和 state 一致性校验外，还必须要求原始绝对 URL 的 scheme、host 与归一化端口和固定 `issuer` 同源；发起阶段不接受由请求 Host 或代理 Host 形成的站外 SavedRequest，回调阶段继续复用同一校验。端到端测试进一步发现 `ForwardedHeaderFilter` 会让相对失败地址采用请求 Host，因此统一 `BrowserAuthenticationFailureHandler` 的普通失败与待绑定跳转也改为固定 `issuer` 下的绝对地址；仍复用 Spring Security 原生失败 Handler，不回显异常。`ThirdPartyAuthenticationFlowTest` 增加恶意 Host、协议变化和非默认端口拒绝用例，`IamProtocolTest` 验证伪造 `X-Forwarded-Host` 既不能进入第三方授权，也不能决定失败 `Location`；不替换 Spring Security 原生成功 Handler。

2026-10-21 用户确认 CORS 协议边界修正：删除对全部 IAM 请求设置通配响应头并提前吞掉任意 `OPTIONS` 的全局 `CorsFilter`；仅在 OAuth2 Authorization Server 安全链为 `/oauth2/token` 启用 Spring Security 标准 CORS，允许来源由 `macula.cloud.iam.token-cors-allowed-origins`（环境变量 `IAM_TOKEN_CORS_ALLOWED_ORIGINS`）显式配置，禁止 `*`，只允许 `POST`/`OPTIONS` 及 Token 调用实际需要的请求头，不携带浏览器凭据。`IamProtocolTest` 覆盖允许来源、拒绝来源及 `/login` 不返回 CORS 头；同步 IAM/deploy 文档和 Compose 示例。该修正落实用户已确认的“浏览器认证闭环、JSON/CORS 归协议层”，不向登录、绑定或其他 IAM 端点开放跨域。

## Order of work
1. 用现有 Spring Security、MyBatis/MyBatis-Plus、Thymeleaf 与 System Flyway，不新增依赖。先梳理 OAuth2 授权续转、`SysUserDetails` 与角色装载的真实调用顺序；固定全局 `username` 账号查找与租户角色装载之间的边界。登录和发令牌不得因缺少租户或 OAuth2 client 而失败；协议自身的 client 校验保留。
2. 改造第三方公共过滤器与上下文，去掉发起及回调阶段的 `requireClient`/`validateClient` 和 client/tenant/userType 快照；只保留提供商、登录模式、受控续转目标和创建时间。`state` 改由 `Base64StringKeyGenerator(Base64.getUrlEncoder())` 生成，保留会话绑定、五分钟过期和一次性消费。恢复 SavedRequest 时只接受服务端已保存的同源 OAuth2 授权请求；直接登录回到 `/`。先完成公共层与对应测试。
3. 在提供商层分别保留 GitHub 数值 ID 和企微成员 `UserId` 的核验，增加“已绑定/唯一直接匹配/待绑定/冲突”分流。企业微信优先按唯一有效 `username=UserId` 匹配，GitHub 不按昵称或邮箱自动匹配。登录页无需有效 OAuth2 client 即展示启用且可见的身份源；`enabled=false` 时发起和回调都拒绝。
4. 实现企业微信系统浏览器 `CorpApp` 自构造登录链接与内置 WebView `snsapi_base` 两种方式。保留 `/login/wecom` 和既有回调，增加 `/login/wecom/browser`、`/login/wecom/webview` 两个显式发起地址；仅在能确认企业微信内置环境时自动走 WebView，不能确认时页面提供两种选择。模式只从已消费的服务端 state 取得，回调不能自选模式。
5. 建立待绑定会话仓库及绑定服务，定义 `GET /login/bind`、`POST /login/bind`（`method=password|sms|session`）和现有 `POST /api/v1/sms-code` 的协作。待绑定数据限时、一次性且不赋本地权限；表单受同源与 CSRF 保护。密码复用现有密码校验；短信复用验证码一次性消费，但还须校验目标账号登记手机号且发现手机号重复时拒绝；已登录会话只能绑定其自身账号。成功后在事务中写入全局绑定并完成登录，唯一键冲突、失败或重放均不得留下半绑定或已认证会话。账号注册、静默改绑不在范围内。
6. 在 System 新增 `sys_user.username` 唯一约束迁移；迁移前以 SQL 检查非空重复并明确失败，不清理数据，不改历史 V1/V6。IAM 查询仍按唯一性和状态失败关闭，以防数据不一致。同步 IAM README 的入口、绑定方式、配置和无租户登录边界，并在 System README 说明 V7 预检、失败恢复和回滚；旧 token/sub/state 不作兼容迁移。
7. 补齐服务、控制器、过滤器、模板和迁移测试，按 `mvn -pl macula-cloud-iam -am test -Plocal`、涉及 System 的定向测试以及可用时的根 reactor 测试验证。检查安全边界、受控跳转、角色租户隔离、`git diff --check` 与凭据扫描；记录外部 GitHub/企微往返和真实 MySQL 迁移若未执行的限制。

## Risks
- 身份已核验但尚未绑定的浏览器状态不能被 Spring Security 误认为已认证；会话固定、CSRF、并发回调、重放、跨标签页及失败清理必须失败关闭。不能把第三方 code、token、短信码或密码写入日志与绑定表。
- `sys_user.username` 目前仅有普通索引；新增唯一约束可能被历史重复值或 `NULL` 行影响。先只读检查、迁移遇冲突停止；不删除或改写已有用户。已接受的实施范围只覆盖 System `sys_user`，其他用户体系不通过同名回退获得本次绑定权限。
- 手机号不唯一，收到短信码不能证明任意 `username`；目标账号手机号核对、重复手机号拒绝和验证码一次性消费均不可省略。短信通道缺失时不启用此绑定方式。
- `loadUserByClient` 当前把查账号和按租户装载角色放在一起；调整时避免改变密码/SMS 入口或 OAuth2 协议校验。租户只在登录后装载角色，不能用 `client_id` Cookie 或默认租户决定绑定目标。
- 企业微信模式、URL 参数和桌面端环境识别依赖真实客户端；显式双入口是识别不确定时的回退。外部认证平台往返需环境联调，不能由模拟测试替代。参考 AGPL 项目仅用于确认 Spring Security 公共 API，不复制其实现。
- 本服务尚未上线，不做旧 state/token/sub 数据兼容；但保留需求明确指定的 `/login/wecom`、登记回调与受控 redirect。回滚以恢复本分支代码及单独评估 V7 唯一约束为界，不自动删除生产数据。

## Proof
- `HttpSessionThirdPartyLoginStateRepositoryTest` 和 `ThirdPartyAuthenticationFlowTest`：URL-safe `Base64StringKeyGenerator`、会话/提供商/模式绑定、五分钟过期、一次性消费；无 client 发起/回调、回跳目标校验和直接登录 `/`，原协议无效 client 仍拒绝。
- `GithubAuthenticationProviderTest`、`WecomAuthenticationProviderTest`、`WecomLoginModeTest`：稳定 subject、GitHub 已绑/待绑、企微 `UserId` 优先唯一匹配及冲突拒绝、CorpApp 与 WebView 参数、同一登记回调、环境识别不确定时的显式入口。
- `GithubAuthenticationProviderTest`、`WecomAuthenticationProviderTest` 的日志捕获断言：企业微信数字错误码、调用阶段、HTTP 状态及本地失败类别可诊断，授权码、token、secret、第三方自由文本、完整响应体和外部 subject 不进入日志；对外仍只返回统一认证失败消息。
- `BrowserAuthenticationFailureHandlerTest`：同一 Handler 对 `BindingRequiredException` 保存待绑定状态并跳转绑定页，对普通失败委托原生 Handler 并携带受控枚举错误码；覆盖不存在账号、错误凭据、锁定、禁用、账号/凭据过期的公开响应完全一致，未知异常走“认证暂不可用”，伪造值回退安全，并捕获输出证明绑定分流不记错、原始异常消息与堆栈不进入日志。`IamProtocolTest` 证明表单、短信和第三方配置复用同一浏览器失败语义，登录页只展示枚举固定文案。`ThirdPartyAuthenticationFlowTest` 与 `IamProtocolTest` 验证 state 目标和 `RequestCache` 一致后由原生成功 Handler 恢复请求，直接登录回到 `/`，密码和短信登录不再返回 JSON。
- `ThirdPartyBindingServiceTest`、`ThirdPartyBindingControllerTest`：密码/短信/会话证明、目标账号校验、重复手机号、禁用账号、CSRF、超时、换会话、错误尝试、并发绑定、重放及失败后无本地权限；验证码和密钥不落库/日志。
- 账号与数据库测试：`ThirdPartyUserDetailsServiceTest` 验证无租户加载全局账号且初始不带角色；由用户负责的 `UserAuthInfoService` 后续实现另行验证按受信客户端租户装载角色且跨租户不串权。`SysUserUsernameUniqueMigrationTest` 或隔离 MySQL 验证已有唯一数据可迁移、重复数据失败，不默默重写；验证旧密码/SMS 与 OAuth2 独立入口可用。
- IAM README 与 System README 的 V7 预检/失败恢复/回滚说明检查；登录/绑定模板手动渲染、不同 context path 和浏览器宽度检查；运行上述 Maven 定向及可用的集成测试、`git diff --check` 和敏感信息扫描。真实第三方平台、真实 MySQL 和外部服务不可用时逐项注明未验证，不把 mock 结果当作联调成功。
