# Plan: IAM 企业微信与 GitHub 第三方身份认证 (from spec.md 2026-10-18)

## Files that change
- SDLC 与基线：保留 `.sdlc/iam-third-party-authentication/intent.md`、`spec.md`，新增本 `plan.md`。Phase B 开始前先把当前临时工作分支安全刷新到远端 `main`，保留已提交 SDLC 工件以及用户已有的 `AGENTS.md`、`.agent_context/`，不把运行时文件纳入提交。
- System 数据库：新增 `macula-cloud-system/src/main/resources/db/migration/V6__iam_third_party_authentication.sql`。创建不含租户、用户 ID 和用户类型的 `sys_user_identity_binding(id, provider_type, provider_subject, username, create_by, create_time, last_update_by, last_update_time)`，建立 `(provider_type, provider_subject)` 与 `(provider_type, username)` 唯一约束；为 `sys_identity_provider` 增加只约束 `enabled=1` 行的同类型唯一键，迁移前发现重复启用配置时安全停止，不自动选择或禁用记录。
- System 服务与文档：修改 `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImpl.java`，在启用配置时主动返回同类型冲突并由数据库约束兜底；修改 `macula-cloud-system/README.md`，记录绑定表语义、预置方式、启用唯一性、迁移预检与回滚边界。
- System 测试：修改 `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java` 与 `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IdentityProviderMigrationIT.java`，覆盖 Service 冲突、空库升级、历史重复安全停止、生成唯一键和绑定表双唯一约束。
- IAM 持久化：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProvider.java`、`SysUserIdentityBinding.java`，以及 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIdentityProviderMapper.java`、`SysUserIdentityBindingMapper.java`。配置查询区分不含 secret 的登录页投影与回调阶段的凭据读取；绑定只把 provider subject 映射为 username。
- IAM 公共第三方认证层：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`、`ThirdPartyLoginStateRepository.java`、`HttpSessionThirdPartyLoginStateRepository.java`、`ThirdPartyAuthenticationToken.java`、`AbstractThirdPartyAuthenticationFilter.java`、`IdentityProviderConfigService.java`、`ThirdPartyUserDetailsService.java`、`ThirdPartyAuthenticationFailureHandler.java`。公共层只负责 OAuth client 校验、tenant/user type 快照、五分钟一次性 state、SavedRequest 恢复、数据库配置与账号路由；provider 参数和响应不进入公共模型。
- IAM 用户路由：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/OAuthClientUserDetailsService.java`，修改 `SysUserDetailsServiceImpl.java`，把现有基于请求读取 client ID 的流程收敛到可显式传入已校验 `clientId + username` 的方法；该方法重新读取 `SysOAuth2Client.userType/tenantId`，复用 `UserAuthInfoService#getUserAuthInfo(userType, tenantId, username)` 与现有系统用户回退、禁用检查、角色装载，不由第三方 Filter 直接查询 `sys_user`。
- GitHub 包：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubClient.java`、`GithubAuthenticationFilter.java`、`GithubAuthenticationProvider.java`、`GithubLoginFilterConfigurer.java`。使用 Spring `RestClient` 和固定 GitHub HTTPS 端点完成授权码交换及 `GET /user`，只取稳定数值 `id`，通过全局绑定取得 username，不申请仓库或组织权限，不保存 token。
- 企业微信包：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`、`WecomAuthenticationFilter.java`、`WecomAuthenticationProvider.java`、`WecomLoginFilterConfigurer.java`。使用 Spring `RestClient` 和固定企业微信 HTTPS 端点，以 `corpId/agentId/appSecret` 完成 `snsapi_base` 网页授权，只接受成员 `UserId` 并直接作为 username，不建立绑定。
- IAM 装配与页面：修改 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`、`controller/LoginController.java`、`src/main/resources/templates/login.html`、`src/main/resources/static/iam/auth.css`，装配两个独立 configurer；只在 OAuth client 有效且配置 `enabled=true, visible=true` 时显示入口。`visible=false` 只隐藏，`enabled=false` 同时拒绝直达；失败只显示稳定中文错误，不回显第三方响应。预计无需修改 `auth.js`，若实现中确需交互改动，先在本计划记录偏差。
- IAM 配置与部署：修改 `macula-cloud-iam/src/main/resources/application.yml`、`deploy/docker-compose.yml` 与 `macula-cloud-iam/README.md`，为 IAM 记录与 System 相同的 `MACULA_CRYPTO_PASSWORD` 安全注入、受信任 issuer 回调地址、HTTP 超时和失败关闭行为。配置/解密能力缺失时仅停用第三方 provider，不影响密码、短信和小程序登录；不提交默认真实密钥。现有依赖已提供 Spring Web/MyBatis/Crypto 能力，预计不修改 `pom.xml`，不引入 `spring-security-oauth2-client` 或第三方 OAuth SDK。
- IAM 测试：新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepositoryTest.java`、`ThirdPartyUserDetailsServiceTest.java`，新增 `authentication/github/GithubAuthenticationProviderTest.java`、`GithubAuthenticationFilterTest.java`，新增 `authentication/wecom/WecomAuthenticationProviderTest.java`、`WecomAuthenticationFilterTest.java`；修改 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java` 和 `src/test/ui/check-ui.cjs`，覆盖安全链、SavedRequest、按钮/错误状态和现有登录回归。测试使用可注入的 `RestClient` 传输替身或 Spring mock server，不连接真实 GitHub/企业微信。
- 所有新增或实质修改的顶层 Java 类型使用现有 Apache 2.0 头、职责 Javadoc、非空 `@author` 与 `@since`。不修改 `macula-cloud-api`、Gateway、Vue Admin、远程契约或第三方账号绑定写入 API；如实现发现必须超出以上文件集，先把偏差、原因和证明影响写回本计划再继续。

## Order of work
1. 在不覆盖 `AGENTS.md` 和 `.agent_context/` 的前提下获取远端 refs，将已批准的 intent/spec/plan 提交安全重放到当前远端 `main` 基线；核对 `git status`、merge-base 和差异，解决 spec 中的基线 blocker 后才触碰业务源码。
2. 先增加失败测试与 V6 迁移：固定绑定表没有 `tenant_id/user_id/user_type`，验证双唯一键；对现有 `sys_identity_provider` 做重复启用预检，再增加数据库并发唯一约束。同步 System Service 的主动冲突检查和异常语义，不改已有 V1–V5。
3. 建立 IAM 只读持久化与配置服务：登录页列表查询不读取密钥，回调按 provider type 唯一读取启用配置并解密凭据；任何缺失、重复、禁用、非法 JSON 或解密失败都对该 provider 失败关闭，日志不得包含 secret、code、token 或原始响应。
4. 提取 OAuth client 用户路由：用显式 client ID 读取 `tenantId + UserTypeEnum`，把这两个值与 provider、会话、state、创建时间和 SavedRequest 关联；回调重新读取 client 并比对快照。账号查询继续走 `UserAuthInfoService(userType, tenantId, username)`/系统用户回退，不让绑定表或请求参数决定租户、用户类型或权限。
5. 实现公共 state/Filter/失败处理：使用安全随机 state，每个会话每个 provider 只保留一个、五分钟过期、先原子消费再交换 code；发起请求与回调共用 `/login/github`、`/login/wecom`，回调 URL只由受信任 issuer 生成。恢复服务端 SavedRequest，拒绝 Host/return URL/Cookie 改写目标。
6. 实现 GitHub 子包：固定官方授权、token、用户端点和最小身份范围；交换 code 后每次调用用户接口，只接受数值 `id`，按 `(GITHUB, id)` 查询全局 username 绑定，再按 OAuth client 用户类型/租户装载账号。覆盖未绑定、重复/冲突、账号不存在或禁用以及外部协议失败。
7. 实现企业微信子包：固定官方网页授权和成员身份端点，使用 `snsapi_base`，只接受成员 `UserId`；将其作为 username，再按 OAuth client 用户类型/租户装载账号。非成员、应用不可见、缺少 UserId、配置或外部协议失败均拒绝。
8. 接入默认 SecurityFilterChain 和 IAM Thymeleaf 登录页；保持现有表单、短信、小程序、CSRF、PKCE/OIDC 行为。增加 GitHub/企业微信入口与稳定失败文案，验证 `visible` 与 `enabled` 的不同语义以及无有效 OAuth client 时隐藏入口。
9. 补齐 IAM/System README、profile 配置和 Docker 环境注入说明；写明绑定仅由受控外部流程预置、第三方 token 不持久化、历史重复配置的升级前处理、企业微信应用可见范围和回滚必须恢复数据库备份/匹配版本。
10. Stage 3 运行定向单测、编译、模板/UI 检查和 `git diff --check` 作为实现反馈；记录任何实际文件或方案偏差。业务代码完成后停在 “Is the implementation complete per the plan?” 门，不以本阶段结果替代 Stage 4 正式验证。

## Risks
- Stage 5 review return（2026-10-18）：独立 Bugs/Security/Compliance 评审发现五条 Important，用户明确要求解决，Stage 3 因此重新打开。修复保持既有 spec，不扩大功能范围：把受控 OAuth SavedRequest 相对目标绑定到 state 并由专用成功处理器恢复；仅允许 `EMPLOYEE` 显式回退系统用户；调整 V6 DDL 顺序并证明 repair/rerun；原子消费 session state；移除 GitHub `read:user`。新增 `ThirdPartyAuthenticationSuccessHandler.java` 与 `ThirdPartyAuthenticationFlowTest.java`，并修改已有 state、用户路由、GitHub 和迁移测试。修复后旧 verification/review 结论作历史记录，必须重新执行 Stage 4 与 Stage 5。
- Stage 5 second review return（2026-10-18）：修复后独立复审确认前五条已关闭，但发现 client 快照校验与用户路由二次读取之间存在 TOCTOU。用户同意返回 Stage 3。修复把 state 中期望的 tenantId/userType 传入 `OAuthClientUserDetailsService`，由实现只读取一次 client、核对并使用同一快照查询账号；同步调整 `ThirdPartyUserDetailsServiceTest` 证明精确快照传递、路由变化失败关闭且 client 仅读取一次。该修复不改变接口范围或用户语义，完成后须再次执行 Stage 4 与 Stage 5。
- Phase B deviation（2026-10-18）：仓库中不存在 `CLAUDE.md`，无法执行原计划所依赖技能要求中的该项读取；实现继续以根 `AGENTS.md`、`.agents/rules/*`、accepted spec 和本计划作为项目指令。该偏差不改变文件范围、实现顺序或 Proof。
- Phase B deviation（2026-10-18）：刷新到远端 `main` 后发现消息渠道功能已经占用 Flyway `V5`；本功能迁移由计划中的 `V5__iam_third_party_authentication.sql` 顺延为 `V6__iam_third_party_authentication.sql`，迁移内容和证明目标不变。
- Phase B deviation（2026-10-18）：远端 `main` 已为 IAM 配置 `macula.crypto` 并在 Compose 注入与 System 相同的 `MACULA_CRYPTO_PASSWORD`，因此未重复修改 `application.yml` 和 `deploy/docker-compose.yml`；本功能只复用现有安全注入，README 补充第三方登录约束。现有基线在缺少该密钥时已因短信配置而启动失败，本变更不新增启动期依赖；单个 provider 的缺失、重复、非法 JSON 或读取异常仍在运行时失败关闭。
- Phase B deviation（2026-10-18）：计划中的独立 `GithubAuthenticationFilterTest`、`WecomAuthenticationFilterTest` 合并到真实 SecurityFilterChain 的 `IamProtocolTest`，以同一测试同时证明匿名放行、受信 issuer 回调、Host 注入拒绝、client 快照和登录页开关；provider 路由仍由两个独立 Provider 单测覆盖。未修改 `auth.js`。
- V6 的启用唯一约束依赖目标 MySQL 对生成列/唯一索引的支持；必须在隔离 MySQL 用真实迁移证明。历史同类型多条启用配置会使迁移按设计停止，环境负责人需先备份并选择保留记录；迁移不得自动清洗。回滚应停止 IAM/System 写入并恢复升级前数据库备份与匹配应用版本，不能只删除 Flyway 历史。
- `sys_user_identity_binding` 是全局 username 绑定，不含租户和用户类型；若同一 username 在不同用户体系代表不同主体，OAuth client 的 `user_type/tenant_id` 会决定最终解析结果。实现必须每次重新走现有用户服务，不能缓存某次租户结果或直接联查默认 `sys_user`。
- 当前 `UserAuthInfoService` 为可选扩展且现有代码存在系统用户回退。重构必须保持密码/短信行为兼容，同时证明 `MEMBER/EMPLOYEE/ODC/SUPPLIER` 的 client 选择不会被第三方流程绕过；如发现现有回退会导致跨用户类型误匹配，须暂停并记录为计划级安全偏差，不能自行扩大修复。
- IAM 直接读取 System 加密凭据要求同一外部密钥。Crypto 能力未配置、密钥错误或历史密文不兼容时，对应 provider 必须不可用但 IAM 本地认证仍可启动；初始化时不得把密文误当明文发给第三方，也不得输出诊断明文。
- 第三方回调横跨浏览器会话、反向代理和 IAM issuer。state 必须一次性、限时并绑定 client/tenant/user type，回调地址必须来自可信配置；否则存在登录 CSRF、重放、Host 注入、开放重定向或跨租户/用户类型登录风险。
- 外部 HTTP 端点有超时、限流、格式变化和临时不可用风险。固定主机、短超时、无跨请求自动重试、不记录响应体，且故障只能影响对应第三方入口；密码、短信、小程序和 OAuth/OIDC 协议链必须回归。
- 第三方 access token 和企业微信应用 token 只在当前调用栈短暂存在。异常对象、DTO `toString`、HTTP 调试日志、测试失败输出和浏览器响应都可能意外泄露，代码审查与测试须专门检查这些通道。
- 当前工作树含 HiFox 自动注入的 `AGENTS.md` 与 `.agent_context/`，基线重放、提交和最终 `hifox repo push --target-branch feat/MAC-6` 都必须排除这些用户/运行时改动；本计划不授权推送，只有用户后续明确要求才交付远端。

## Proof
- System 单元测试：`IdentityProviderServiceImplTest` 覆盖创建/更新第二条同类型启用配置返回冲突、更新自身不误判、禁用或隐藏配置允许共存、凭据不回显和数据库异常映射。
- MySQL 迁移测试：`IdentityProviderMigrationIT` 从 V1–V5 升到 V6，验证无重复时成功、重复启用时停止、启用唯一键的并发兜底、两个绑定唯一约束、username 长度/字符集及表中不存在 tenant/user ID/user type/token 字段；测试只使用命名受限的隔离数据库。
- 公共 IAM 单测：state 的随机性、provider/client/tenant/user type/会话绑定、五分钟边界、一次消费、错配与重放；OAuth client 在回调期间变更时拒绝；`enabled/visible`、缺失/重复配置和解密失败；稳定错误分类且 secret/code/token/原始响应不出现在日志或响应。
- 用户路由测试：显式 client ID 分别选择 `MEMBER`、`EMPLOYEE`、`ODC`、`SUPPLIER`，把相同 username 传给正确的 `UserAuthInfoService`，验证 tenant ID、禁用账号、空结果和系统用户回退兼容；证明绑定表不能改变 client 的用户类型或租户。
- GitHub 单元测试：授权 URI、state、精确回调，token 交换和 `GET /user`；只使用稳定数值 `id`，拒绝 login/email 兜底；全局绑定成功、未绑定、重复/非法数据、不同 client 用户类型/租户的成功或拒绝，以及 HTTP 超时/错误/非法 JSON。
- 企业微信单元测试：`snsapi_base`、corpId/agentId 与精确回调，应用 token/成员身份响应；只接受成员 `UserId` 并作为 username，覆盖应用不可见、非成员、缺失 UserId、不同 client 用户类型/租户、禁用账号和外部失败。
- `IamProtocolTest`/MockMvc：从匿名 `/oauth2/authorize` 保存请求，登录页按有效 client 与 provider 开关显示入口，完成模拟外部往返后建立认证并恢复原请求；state 错误返回稳定提示。回归表单、短信、小程序、CSRF、authorization_code + PKCE、OIDC、退出和错误处理。
- UI 检查：渲染登录页并运行 `check-ui.cjs`，覆盖桌面/移动尺寸、无横向溢出、键盘焦点、第三方按钮、隐藏/禁用和失败文案；确认现有密码/短信 Tab 与防重复提交不回归。
- Stage 3 反馈命令：`mvn -pl macula-cloud-iam -am test -Plocal`，`mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal`，必要的 `package`、隔离 MySQL 迁移测试、UI 检查及 `git diff --check`。Stage 4 重新记录实际命令、退出码、报告和未具备的外部条件，并按 `REVIEW.md` 执行 Bugs、Security、Compliance 三个独立检查。
