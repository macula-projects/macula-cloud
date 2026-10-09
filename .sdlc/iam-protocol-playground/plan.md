# Plan: IAM 接入指南与交互演示 (from spec.md 2026-10-09)
Status: accepted

Rain 在完整计划展示后回复 YES，批准按此顺序实施；不代表实现完成、测试或评审通过。

## Files that change
以下相对 `macula-cloud-iam/`，新增文件按职责拆分，不修改共享 Macula Boot 框架：

1. `src/main/java/dev/macula/cloud/iam/playground/` 新增：
   - `PlaygroundProperties.java`、`PlaygroundAccessPolicy.java`、`PlaygroundConfiguration.java`：配置、非生产门禁、装配和专用资源映射。
   - `PlaygroundRegisteredClientRepository.java`：装饰现有数据库客户端仓库，隔离演示客户端及缓存，保留业务客户端语义。
   - `PlaygroundPageController.java`、`PlaygroundApiController.java`：指南/回调/设备页面和固定动作 API。
   - `PlaygroundFlowService.java`、`PlaygroundProtocolClient.java`、`PlaygroundOidcVerifier.java`：会话流程、真实 HTTP 调用、ID Token 验证。
   - `PlaygroundSessionStore.java`、`PlaygroundFlow.java`、`PlaygroundRequest.java`、`PlaygroundResponse.java`：有界短期会话数据、输入/输出及脱敏，不存密码/验证码/secret。
   - `DevicePublicClientAuthenticationConverter.java`、`DevicePublicClientAuthenticationProvider.java`：仅演示 Device 客户端的公共客户端认证适配。
   - `PlaygroundTokenIntrospectionAuthenticationProvider.java`：隔离演示与业务 introspection，非演示请求委托原实现。
2. 修改 `config/ProtocolOAuth2Configuration.java`、`config/DefaultSecurityConfiguration.java`：装配仓库/协议扩展、页面放行与统一关闭门禁；辅助 API 保留 CSRF，不放宽原协议或业务页面。
3. 修改 `protocol/oauth2/CustomOAuth2TokenCustomizer.java`、`protocol/oauth2/LocalAccessTokenIntrospector.java`、`service/oauth2/MaculaOAuth2AuthorizationService.java`：演示 token 不携带业务角色/数据权限；关闭或配置移除后拒绝演示遗留授权，业务授权行为不变。
4. 新增 `src/main/resources/templates/playground/{index,callback,device}.html`、`src/main/resources/playground/{playground.css,playground.js}`：场景式指南、步骤/请求/结果和独立设备验证页；资源不放入无条件公开的 static 路径。
5. 修改 `src/main/resources/application.yml`、`README.md`，新增 `docs/playground.md`：默认关闭配置、环境许可、客户端/测试身份准备、示例与限制。入口直接访问 `/playground`，不改现有首页和 Vue Admin。
6. 新增 `src/test/java/dev/macula/cloud/iam/playground/` 下的 `PlaygroundAccessPolicyTest.java`、`PlaygroundClientRepositoryTest.java`、`PlaygroundSessionStoreTest.java`、`PlaygroundProtocolTest.java`、`PlaygroundHttpIntegrationTest.java`，以及 `src/test/ui/check-playground.cjs`；必要时补充现有 `config/IamProtocolTest.java` 和授权存储测试的回归断言。
7. 本变更的 `plan.md`、后续 `build-notes.md`、`verification.md`、`review.md`；不修改已合并 IAM 变更的历史证据。

采用已存在的 Java 17、Spring Security 7、Jackson 3、Thymeleaf、原生 JavaScript/Web Crypto、JUnit/MockMvc 和 Playwright。优先使用 JDK HTTP 客户端及现有 JOSE 能力，不升级依赖或引入前端构建工程；若确认缺少直接依赖，先记录计划偏差再改目标模块 POM。

## Order of work
1. 先建立安全门禁及测试：`enabled=false` 为默认，仅显式允许的非生产 profile 可启用；prd/production 优先拒绝；覆盖页面、资源、辅助 API、Device 演示适配和客户端查询。业务端点保持原行为。
2. 添加专用客户端与令牌隔离：公共 PKCE、机密客户端、公共 Device 使用保留 ID 前缀和稳定注册 ID；scope 固定为 openid/profile/playground.read 的所需子集。拒绝前缀碰撞，不写数据库、不复用业务缓存；缺少外部 secret 时只禁用依赖它的演示并说明原因。
3. 实际演示 access token 固定 opaque，ID Token 仍为 JWT；指南说明 JWT access token 差异，不为演示发行可被业务 JWT 资源服务器直接接受的 access token。演示 token 不携带业务角色/数据权限。标准 introspection 中，演示查询方只能查询自己客户端的令牌，演示/业务跨边界查询返回 inactive；非演示客户端之间继续原框架行为。关闭演示后已有 access/refresh/device/code 授权和 UserInfo 均拒绝。无需修改 Gateway/System 配置，验证现有 introspection 调用被正确隔离。
4. 实现每会话最多 5 流程、最长 10 分钟的短期状态；会话轮换仍保留流程、销毁则清理，令牌自身先到期则提前终止。第一版跟随既有 HttpSession 部署模型，不新增共享会话基础设施；多实例需会话粘性或既有共享会话配置。
5. 实现固定目的地 HTTP 调用和 API：配置确定 issuer/回调，禁止任意 URL/client/token 输入和跨主机重定向，连接 3 秒/请求 10 秒超时。副作用 API 要求 CSRF 与同源，返回请求方法、路径、脱敏参数、HTTP 状态及协议结果；当前会话令牌引用供后续操作使用。
6. 完成公共 PKCE/OIDC：浏览器生成 state/nonce/verifier，以短期 sessionStorage 保留回调绑定，服务端保存对应会话关联；真实跳转登录授权、验证绑定并换码。回调后立即移除 URL 中 code/state；JWT 先验签并校验 iss/aud/exp/nonce，再标记验证通过。示例明确浏览器操作和为会话归属校验使用的演示辅助调用，不能冒充完全无后端 SPA。
7. 增加机密客户端授权码/刷新、客户端凭据及兼容 password/sms 的固定动作；密码/验证码仅瞬时转发，不保存、不记录。不修改原 password/sms Provider 或 CaptchaService 默认 true。公共授权码不出现虚假的刷新按钮；设备流不请求 openid，以 OAuth Device access token 流程为范围，避免暗示已支持设备 OIDC 登录。
8. 完成 Device：复用框架发码、验证、设备码换令牌；适配仅演示客户端且严格区分端点/授权类型。浏览器用户经原 IAM 登录，再验证 user_code 并明确允许/拒绝，不通过页面直接改存储；验证提交保留框架绑定并补足 CSRF。设备按 interval 轮询、slow_down 增加等待、网络超时退避，终态/离页/取消停止，不并发轮询。
9. 完成中文场景页：移动端、H5、Device、服务端、兼容接入；每组都有职责、准备、真实步骤、可复制示例、结果解释和失败路径。敏感字段默认遮罩，secret/password 不显示；示例复制使用占位符，查看本次 token 需主动操作，所有结果 no-store，界面 reset 清理本会话数据。
10. 更新配置与接入说明；运行开发回归并记录实际输出和偏差。由 Rain 确认实现完成后进入正式 sdlc-test；不自动提交源代码、推送、部署或批准下一阶段。

## Risks
### 已记录的实施调整
- 2026-10-09：已核对本地 Security 7.0.7 源码，Device Provider 明确不产生 slow_down。真实 IAM 测试覆盖 pending/批准/拒绝/重放；slow_down 使用独立 HTTP 测试端点验证客户端退避，不谎称该响应来自 IAM，也不擅自新增全平台限流机制。正式验证报告须单列此证据差异。
- 2026-10-09：Device 使用只匹配演示授权的独立框架 DeviceVerification filter 和 ProviderManager，业务 Device 不改用演示确认页。独立 CSRF filter 补足 AS 默认忽略 CSRF 的行为，每次设备连接强制确认。页面采用 strict-origin Referrer-Policy，避免真实浏览器原生 POST 在 no-referrer 下产生 Origin: null，同时不发送代码/状态所在的完整 URL。
- 2026-10-09：真实 HTTP 集成测试放在 `src/test/java/dev/macula/cloud/iam/config/PlaygroundHttpIntegrationTest.java`，复用同包既有 IAM 测试身份/隔离 Redis 配置，使用已存在的嵌入式 Tomcat 启动真实过滤链；避免复制测试身份实现或公开测试内部类。不新增依赖。
- 2026-10-09：现有 `config/CorsFilter.java` 在 Security 之前无条件返回 OPTIONS 200 并设置跨域 `*`，因此增加该文件的窄范围修改：仅演示路径不应用此规则，交给演示过滤链。增加拒绝跨域及关闭时 OPTIONS 的测试；其他业务 CORS 行为不变。
- 2026-10-09：门禁采用独立且更高优先级的 SecurityFilterChain，无需扩大 `DefaultSecurityConfiguration` 的 permitAll；存量令牌在授权存储读取处统一拒绝，`LocalAccessTokenIntrospector` 继续使用原存储接口，无需重复修改。

- Gateway 现有默认 opaque introspection 不能单靠 scope 保证隔离；本计划增加 IAM 侧演示客户端边界，并测试有业务角色的用户登录演示后仍无法获得业务访问能力。测试失败必须阻断，不仅用无权限账号掩盖问题。
- 演示禁用包括存量令牌，不能只撤掉导航；客户端查询缓存、直接 UserInfo、刷新和 Device 端点都需要拒绝路径。正常客户端记录不受影响，不清空共享 Redis。
- 公共授权码没有 refresh token；机密客户端另行演示刷新。Device 仅补齐 OAuth 设备授权，不默认宣称 OIDC Device 或公共 refresh 可用。
- 演示匿名开放但只部署到隔离的非生产环境；正确标注 profile、测试身份和非生产配置由部署方负责。每会话限额不能替代环境级防滥用策略。
- HTTP 自调用依赖配置的 IAM 地址从服务端可达；HTTPS 为常规前提，仅明确的本机开发允许 HTTP。外部 issuer、精确回调及浏览器/服务端连通性必须在准备页说明。
- 第一版无需数据库迁移。回滚可关闭演示并回退本功能代码，但不得把关闭前的演示令牌重新暴露给业务资源；临时会话随重启或失效丢弃，用户重新开始。
- 没有原生设备/技术栈要求；只验证浏览器可运行协议示例，保留真机验证边界。真实 SMS 身份源不在范围内。

## Proof
1. R1/R7：页面包含五类接入场景、参与者、前提、示例和错误解释；320/390/844/1440px 无横向溢出，键盘、复制、遮罩、加载、错误恢复和取消轮询有浏览器断言。
2. R2：真实 HTTP PKCE/OIDC 成功及错误 state/nonce/verifier/redirect、过期/重放、错误签名/issuer/audience、会话轮换和跨会话拒绝；UserInfo/退出与 JWT“仅解码/已验证”状态分开断言。
3. R3：真实过滤链和隔离 Redis 覆盖 Device pending、slow_down、批准、拒绝、过期、重放、错误客户端、未知 user_code、未登录和 CSRF；真实 HTTP 测试服务与两个独立 Playwright 浏览器上下文完成设备/用户两侧全过程。
4. R4/R5：机密授权码刷新、客户端凭据、introspection、撤销后失效和 scope 扩权拒绝；公共 PKCE 不发 refresh；兼容 password/sms 原参数、异常映射与默认验证码行为保持，未接入手机号身份源时展示真实错误。
5. R6：测试默认关闭、允许的非生产开启、prd/production 及混合 profile 强制关闭、客户端冲突/缺配置；关闭后所有演示路由/静态资源不可用，已发演示令牌和授权不可继续使用。
6. R6/R7：业务查询方看演示 token 为 inactive，演示查询方不可查询业务或其他演示客户端 token；没有业务角色/数据权限 claims；跨会话流程引用/任意 token 查询撤销、CSRF、上游 URL 注入均拒绝；会话限额、超时清理及日志/响应脱敏验证。
7. 回归保持已有 IAM 34 项及后续新增测试，执行 `mvn -pl macula-cloud-iam -am test -Plocal`、`package -Plocal`、全后端 `mvn test -Plocal`，并执行管理端既有 unit/build/e2e 和 IAM 旧/新浏览器脚本。实际数量以当次输出为准，不把目标数当作通过。
8. 真实 HTTP 测试服务模拟身份/客户端配置、使用独立 Redis，不连生产；前端响应 mock 仅用于错误 UI 分支，不能替代真实协议验证。记录环境缺失、跳过和真机限制，不擅自标记 green。
