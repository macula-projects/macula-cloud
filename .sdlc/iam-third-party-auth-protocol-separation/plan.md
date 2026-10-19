# Plan: IAM 第三方身份认证与协议解耦及企业微信双场景登录 (from spec.md 2026-10-19)

## Files that change
- IAM 第三方发起、回调、认证与 state：
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationToken.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepository.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationSuccessHandler.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFailureHandler.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyUserDetailsService.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionService.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/PendingThirdPartyBindingRepository.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`
- 提供商适配：
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProvider.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubLoginFilterConfigurer.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationFilter.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProvider.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginFilterConfigurer.java`
  - `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`
  - 新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginMode.java`
  `GithubClient.java` 仅在现有稳定 subject 提取测试发现缺口时变更，届时先在计划记录偏差。
- 本地账号及短信证明：`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/SysUserDetailsServiceImpl.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysUserService.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysUserServiceImpl.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysUserMapper.java`、`macula-cloud-iam/src/main/resources/mapper/SysUserMapper.xml`。复用 `SmsService` 和现有验证码存储/发送实现，不改变独立短信登录或 token grant。
- 安全链、页面及文档：`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`、新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ThirdPartyBindingController.java`、`macula-cloud-iam/src/main/resources/templates/login.html`、新增 `macula-cloud-iam/src/main/resources/templates/third-party-bind.html`、`macula-cloud-iam/src/main/resources/static/admin/css/other/login.css`、`macula-cloud-iam/README.md`。
- System 数据约束：新增 `macula-cloud-system/src/main/resources/db/migration/V7__sys_user_username_unique.sql`；修改 `macula-cloud-system/README.md` 说明 V7 预检、失败恢复和回滚边界。不改写 V1/V6 历史迁移，不自动清理重复用户。
- 定向测试：
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyUserDetailsServiceTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProviderTest.java`
  - `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProviderTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingServiceTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginModeTest.java`
  - 新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ThirdPartyBindingControllerTest.java`
  - 新增 `macula-cloud-system/src/test/java/dev/macula/cloud/system/migration/SysUserUsernameUniqueMigrationTest.java`
  新增测试若仓库现有集成测试夹具不支持 Flyway/MySQL，则在实施前记录验证替代方式，不伪称已做真实迁移测试。
- 本计划及审查记录：`.sdlc/iam-third-party-auth-protocol-separation/plan.md`、`.sdlc/iam-third-party-auth-protocol-separation/review-findings.md`。不修改 eIAM/AGPL 参考仓库、不引入新运行时依赖、不触及 Gateway 或管理端源码。

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
- `ThirdPartyBindingServiceTest`、`ThirdPartyBindingControllerTest`：密码/短信/会话证明、目标账号校验、重复手机号、禁用账号、CSRF、超时、换会话、错误尝试、并发绑定、重放及失败后无本地权限；验证码和密钥不落库/日志。
- 账号与数据库测试：`ThirdPartyUserDetailsServiceTest` 验证无租户登录、按受信客户端租户装载角色且跨租户不串权；`SysUserUsernameUniqueMigrationTest` 或隔离 MySQL 验证已有唯一数据可迁移、重复数据失败，不默默重写；验证旧密码/SMS 与 OAuth2 独立入口可用。
- IAM README 与 System README 的 V7 预检/失败恢复/回滚说明检查；登录/绑定模板手动渲染、不同 context path 和浏览器宽度检查；运行上述 Maven 定向及可用的集成测试、`git diff --check` 和敏感信息扫描。真实第三方平台、真实 MySQL 和外部服务不可用时逐项注明未验证，不把 mock 结果当作联调成功。
