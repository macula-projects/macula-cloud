# Plan: Admin授权码登录接入 (from spec.md 2026-11-07)
Status: accepted

独立Plan评审共2轮：1项Important已修正，第二轮NO_FINDINGS；Rain已以YES接受计划，开始本轮实施。

沿用Vue3/Vite/Pinia/Element Plus、Web Crypto和现有Spring/MyBatis/Flyway/Redis。只新增jose用于JWT/JWKS签名与声明验证（采用兼容现有Node/Vite的稳定版本并锁定lockfile），不引入第二个OIDC会话SDK、不自实现签名算法。保护已有未提交改动；CustomOidcTokenCustomizer.java保持现有内容，无预定源码修改。

## Files that change
精确路径如下；不存在者新增，存在者只增量修改本轮职责。passwordForm.vue和phoneForm.vue将删除旧登录实现（其他注册/找回源码不扩展，旧公开路由转回统一登录入口）。未确需改动的文件保持原样，发现其他直接调用方先记录原因和路径。

### Admin授权、会话与路由
- `macula-cloud-admin/package.json`
- `macula-cloud-admin/package-lock.json`
- `macula-cloud-admin/src/utils/adminAuthSession.js`
- `macula-cloud-admin/src/utils/adminAuthorizationCode.js`
- `macula-cloud-admin/src/utils/adminAuthBootstrap.js`
- `macula-cloud-admin/src/views/common/login/index.vue`
- `macula-cloud-admin/src/views/common/login/callback.vue`
- `macula-cloud-admin/src/views/common/login/components/passwordForm.vue`
- `macula-cloud-admin/src/views/common/login/components/phoneForm.vue`
- `macula-cloud-admin/src/main.js`
- `macula-cloud-admin/src/router/index.js`
- `macula-cloud-admin/src/router/systemRouter.js`
- `macula-cloud-admin/src/api/model/common/auth.js`
- `macula-cloud-admin/src/utils/request.js`
- `macula-cloud-admin/src/utils/tool.js`
- `macula-cloud-admin/src/stores/tenant.js`
- `macula-cloud-admin/src/layout/components/userbar.vue`
- `macula-cloud-admin/src/config/index.js`
- `macula-cloud-admin/src/locales/lang/zh-cn.js`
- `macula-cloud-admin/src/locales/lang/en.js`

### 配置与受控本地初始化
- `macula-cloud-admin/.env`
- `macula-cloud-admin/.env.development`
- `macula-cloud-admin/.env.docker`
- `macula-cloud-admin/.env.dev`
- `macula-cloud-admin/.env.stg`
- `macula-cloud-admin/.env.pet`
- `macula-cloud-admin/.env.prd`
- `macula-cloud-admin/public/config.js`
- `macula-cloud-admin/index.html`
- `macula-cloud-admin/nginx.conf`
- `macula-cloud-admin/vite.config.js`
- `macula-cloud-admin/nginx/templates/config.js.template`
- `macula-cloud-admin/nginx/entrypoint.d/25-admin-runtime-config.sh`
- `deploy/docker-compose.yml`
- `deploy/.env.example`
- `deploy/scripts/provision-admin-client.sh`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/AdminClientProvisioningProperties.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/AdminClientProvisioningRunner.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningService.java`

### IAM最小CORS及验证材料
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSource.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSourceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/AdminClientProvisioningIT.java`
- `macula-cloud-admin/tests/unit/admin-auth-session.test.js`
- `macula-cloud-admin/tests/unit/admin-authorization-code.test.js`
- `macula-cloud-admin/tests/unit/admin-auth-bootstrap.test.js`
- `macula-cloud-admin/tests/unit/tool-storage.test.js`
- `macula-cloud-admin/tests/unit/config.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/cypress/support/auth-session.js`
- `macula-cloud-admin/cypress/e2e/admin-authorization-code.cy.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`
- `macula-cloud-admin/cypress/e2e/application-api-key.cy.js`
- `macula-cloud-admin/cypress/e2e/security-policy.cy.js`
- `macula-cloud-admin/cypress/e2e/tinyid-management.cy.js`
- `macula-cloud-admin/cypress/e2e/example.cy.js`
- `macula-cloud-admin/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-system/README.md`
- `deploy/README.md`

- `deploy/.env`：仅本地公开接入参数更新，不提交或输出其他凭据；提前作权限受限副本。实际恢复依据、测试账号材料及数据库快照写入/private/tmp独立0700目录，文件0600，不入仓库。

## Order of work
1. 记录git状态和计划文件基线，确认适用规则及当前V15/V16环境；不修改上一轮应用配置/绑定实现。读取当前Admin/Gateway配置，核对机密Client ID及认证租户1、用户目录employee（已只读核实；实施时若改变则停止，不猜测新的身份范围）。REVIEW.md未配置Stage3 reviewer，未发现codex-plan-review时按技能使用独立sdlc-reviewer Plan fallback。
2. adminAuthSession.js负责版本化sessionStorage会话和登录事务、10分钟TTL、Token expires_in（正整数且不超过平台86400秒上限）、旧TOKEN Cookie及旧认证缓存清理；不依赖Router/API，避免循环。tool.data仅对USER_INFO/MENU/PERMISSIONS/tenantId/tenantLabel/tenantStoreTenantOptions等固定认证键切到sessionStorage，其他偏好保持localStorage；迁移到新版本不搬运旧令牌。tenant store增加完整reset，清除tenantId/label/options。存储被禁用时登录失败关闭并提示，不恢复Cookie或localStorage令牌。会话同时具有sessionId与标签页内递增的认证代次；开始新授权、退出、当前会话失效均先推进代次并中止旧初始化。所有候选Token/初始化任务绑定其代次和AbortController，响应提交前重新比较；旧代次的成功/失败均不得写入或清理新会话。
3. adminAuthorizationCode.js实现配置校验、固定回调、授权事务、元数据和ID Token验证。Web Crypto生成32字节以上随机state/nonce/verifier，S256；事务记录issuer/clientId/redirectUri/nonce/verifier/创建时间/受控站内目标，sessionStorage按标签页保存、一次消费。新增事务前清理旧登录材料，重复点击锁定；回调读取唯一的code/state/error/可选iss，先验证事务、成功/失败均消费，错误不可用作开放重定向。所有返回目标解析后必须同origin且为站内hash路由，拒绝协议相对路径、反斜线、外部URL和登录/回调循环。
4. 固定物理/auth/callback由main.js在Vue/Router初始化前捕获响应到内存，立即history.replaceState清掉查询并转为/#/auth/callback，不将code复制到新的hash/存储。回调组件只读取该一次性内存结果；刷新/第二次处理无材料则给出重新登录操作，不能再兑换原code。Nginx精确回调location关闭access_log，no-store、no-referrer；index meta与Vite开发/preview回调响应同样保护Referer/缓存，路径正确返回SPA入口。回调和OAuth异常只显示受控错误码/说明，不拼接原始URL或server error_description。
5. 元数据仅从公开配置中的可信IAM issuer固定Discovery路径获取，HTTP仅允许明确localhost/127.0.0.1回环开发；校验返回issuer严格匹配、authorization/token/jwks端点为预期IAM origin，禁止重定向、userinfo URL凭据或片段。独立fetch请求credentials:omit、超时、禁止外跳；Token调用只带grant_type=authorization_code、client_id、code、原redirect_uri、verifier，绕过全局Axios旧Bearer/tenant头。jose使用经过同样URL校验的fetch取得的JWKS建立local JWK set，jwtVerify限制RS256并验证issuer/audience及必需exp/iat/sub/nonce，60秒时钟容差，iat必须落在本次事务创建时间与当前时间的受控窗口（拒绝未来/早于事务窗口的Token，JWT maxTokenAge不超过登录事务10分钟）；多aud须azp=clientId，nonce严格相等，sub非空，at_hash存在时用SHA256校验。JWKS短缓存且unknown kid只受控重取一次；忽略并不跟随JWT自带jku/x5u地址。不调用IAM UserInfo替代System权限接口，不持久化ID Token。
6. API common/auth.js删除旧systemToken密码入口，保留用户/菜单API并提供明确Bearer/跳过旧tenant头的初始化调用参数。adminAuthBootstrap.js先取/users/me，再取可访问租户并设置明确当前租户，再取菜单/权限；空租户/禁用/无权限/无效数据失败关闭。初始化使用内存中的候选Access Token显式传请求头，完整成功后才提交sessionStorage会话及认证缓存，失败全部清理。保留现有角色/反向角色和菜单过滤语义并正确使用逻辑运算，不因为登录成功放行ROOT或全部菜单。恢复现有会话时重新核实用户/租户/菜单；同一代次的恢复共用Promise防并发重复加载，清理旧动态路由再重建；退出/新授权通过代次和AbortController使旧Promise失效。每个await之后及最终提交前校验代次，旧响应不能重新登录或覆盖新用户缓存，失败也只能清理本次所属代次。
7. request.js从新会话读取未过期Bearer，仅向配置的受信平台API origin/basePath附带，不泄漏给IAM、外部绝对URL或Token交换；显式初始化Bearer不被旧值覆盖。每个携带Bearer的请求记录发送时sessionId/认证代次，401只有仍属于当前会话才清认证数据和租户状态；旧请求的迟到401不得清除新会话，无Bearer/外部请求的401也不能触发当前会话退出。过期计时器同样校验所属会话。当前会话失效后保留安全站内目标并提示重登，不自动重试授权/无限302。Router允许登录和回调静态路由，业务路由等完整会话初始化成功再注册；通过动态调用/bootstrap依赖边界避免request→router→bootstrap→API循环。main配置Pinia先于需要它的会话恢复，阻止启动阶段读旧认证缓存。
8. login/index.vue保留现有品牌/主题/语言，提供“前往IAM登录”和错误重试，不自动跳转；删除旧账号密码/手机号Token表单及演示微信入口。既有注册/找回公开路由转统一登录页，不扩展这些功能。userbar退出和清缓存调用同一会话清理/reset动态路由方法；本地退出不结束IAM SSO，用户下次主动登录可复用SSO，不能退出后自动回跳。语言文件补必要文案，避免将协议参数塞入普通用户错误流。
9. 公开配置使用OAUTH_CLIENT_ID、OAUTH_AUTHORITY、ADMIN_PUBLIC_URL、OAUTH_SCOPE=openid profile；固定回调由受信公开地址构造且必须与当前Admin origin一致。移除配置/各环境示例/生产config.js/注入脚本中的OAUTH_CLIENT_SECRET和DEMO_USERNAME/PASSWORD；全部环境不默认使用旧Gateway ID。Admin_PUBLIC_URL与authority由部署显式提供；不从任意回调Host或查询推断。config merge仍保持运行时优先，脚本仅编码公开值，不把已有服务端密钥复制到页面。
10. IAM ProtocolLoginCorsConfigurationSource仅新增/.well-known/openid-configuration及/oauth2/jwks的GET/必要OPTIONS CORS，来源来自已启用Client的精确Origin索引，Vary正确、allowCredentials=false。其他方法/未登记Origin拒绝；无Origin的公共元数据请求保留既有行为。Token实际请求的Client/Origin绑定及验证码CORS不变。保持issuer和CustomOidcTokenCustomizer/access claims，不通过更改签名sub来补前端缺口。
11. System增加显式开启的一次性AdminClientProvisioningRunner/Properties和事务Service。正常运行默认关闭；仅受控docker compose run --rm --no-deps一次性容器开启，无HTTP初始化端点。Flyway完成后通过既有OAuth2ClientConfigService完整校验，在一个事务中创建独立Admin公共Client并配置指定的既有Gateway机密Client；成功后关闭一次性应用上下文退出。属性必须明确两个不同Client ID、Admin origin、已核实的tenantId/userType；脚本预生成新UUID，不能复用Gateway ID。Admin固定SPA/ENABLED/OIDC/openid profile/code/none/S256、300秒code、3600秒reference Access Token、无refresh、无来源关联、托管方式继承平台默认、受信自有应用免二次授权确认。Gateway保留原机密摘要/ID/租户目录，显式SERVICE/ENABLED/OAUTH2_1/client_credentials/空Scope和Origin，仅完成内省所需新模型，不旋转或输出其密钥。
12. Provisioning严格限于两个指定ID：Gateway缺失、secret未配置、当前类型已明确为非SERVICE、认证范围改变均拒绝；Admin新ID若已存在，只有全部受管配置一致才幂等成功，有漂移则拒绝，不覆盖用户后续配置。两个Client的写入/关联替换共享事务与完整快照CAS；提交后才驱逐v3缓存，失败不留下半组Client。不新增Schema，不重写V16；未指定的其他Client及身份源/绑定均不改。一次性容器禁用MyBatis SQL/参数日志及发现注册，禁止输出Form/Entity/摘要/凭据；错误只给出不含材料的阶段和原因。
13. provision-admin-client.sh分只读检查/配置应用阶段，严格限定当前本地Compose项目及loopback公开地址，结构化读取compose渲染而不打印含secret全量环境，不source执行.env。先验证全部必要配置和镜像，再保存旧镜像ID、.env私密副本及相关Client表恢复依据到0700/0600目录。仅更新本地.env的公开Admin参数（原其余行保持），停用旧Admin/IAM/System后启动上述一次性System任务，执行迁移及两Client配置。成功后以--no-deps --no-build --force-recreate --wait整体启动这三个新服务。Gateway保持现有容器/凭据/路由，无需修改其镜像或配置。失败不自动恢复旧IAM+新草稿模型，不自动还原/清空业务库，保留维护状态和红acted阶段诊断；恢复步骤须核对匹配的镜像/Client/迁移组合。
14. 更新单元/浏览器测试和文档。旧管理页面Cypress用新sessionStorage认证素材，补用户/租户初始化Mock并使用公共helper；不得加生产测试后门或跳过新路由守卫。开发检查记录实跑输出；完成Build人工确认后执行正式Stage4及已接受的本地镜像切换。读取旧容器现有演示测试账号仅用于受限临时实际浏览器验证，不写入公开配置/仓库或截图日志；完成即删除临时材料。Git提交/推送/远程发布仍未授权。

## Risks
- 原Gateway ID曾与Admin共用，必须保持其机密身份，新增Admin独立ID；不能把Gateway改none。已核实本地范围tenant=1/userType=employee，实施前复核，变动即停止；其他历史Client继续保持DRAFT，不批量启用。
- sessionStorage仍受同源脚本/XSS影响，本次不宣称BFF/HttpOnly隔离；只保留Access Token及expiresAt，ID Token验证后不持久化，生命周期受真实expires_in约束。
- 退出是当前应用退出，IAM SSO可保留；浏览器只有用户点击才重新发起授权，避免自动登录循环。
- 物理回调在JavaScript运行前可能成为Referer或Nginx日志来源，必须通过精确响应头/access_log off保护，不能仅依赖后置URL清理。
- 本地受信Admin设置requireAuthorizationConsent=false，仅省去自身应用授权确认，不省去IAM认证、PKCE、nonce或System权限校验。若未来公开第三方应用，不复用此初始化约定。
- 初次切换需要本地维护窗口，V16 DDL不能当事务回滚；不能在失败后自动恢复不理解DRAFT的旧IAM。保留恢复依据，失败关闭并等待明确修复，不冒充已切换成功。
- 现有未提交修改较多，变更只限列出路径；用户引用的CustomOidcTokenCustomizer保留，不批量格式化或清理无关代码。

## Proof
- Vitest：RFC7636已知PKCE向量/密码学随机失败关闭、state/nonce/10分钟TTL/一次消费、缺失/重复/错误回调/issuer、redirect/外部站点/反斜线/登录循环拒绝；真实jose生成测试JWT覆盖有效签名、错key/alg/iss/aud/azp/exp/iat/sub/nonce/at_hash；JWKS未知kid受控刷新；独立Token通道无旧Bearer/tenant/Secret。
- 会话与初始化：Token不入Cookie/localStorage、expires_in/存储禁用/标签页隔离、旧缓存清理、刷新重新拉取、并发恢复单次、无租户/无权限/接口失败清理、401和本地退出/动态路由reset；初始化中退出、旧初始化晚于新授权、旧失败清理/旧401/旧过期定时器晚于新会话均不得改写或清除新会话；原有配置、工具和租户单测继续。API跨域/非受信URL不附Bearer。
- Cypress：模拟真实authorize→IAM页→无hash回调→hash页面完整流程，使用合成签名JWT与网络Mock，不加生产后门；state/nonce错误、拒绝/过期/重放/刷新/返回原页面、初始化失败、退出后不自动跳转、重试、390px。全部已有管理页Cypress回归，迁移测试素材不削弱认证检查。认证spec禁敏感截图/视频，日志仅合成材料；真实本地验证另行报告。
- IAM真实Security链：公共授权码S256、错误verifier、nonce/ID Token、无Secret/无refresh、Discovery/JWKS精确Origin GET/OPTIONS、非法方法与Origin拒绝、现有Token/验证码CORS保持；Gateway机密内省独立于新Admin且能读取公共Client签发的reference Token和原权限claims。
- System隔离MySQL/Redis：两Client事务创建/更新、Admin/Gateway ID碰撞/缺机密身份/身份范围或类型不符/重跑漂移拒绝、幂等无副作用、保留Gateway摘要、其他Client指纹不变、并发CAS及提交后缓存失效。一次性任务默认关闭、成功退出、失败非零且无秘密日志，不涉及真实业务库。
- 命令：最小Vitest/目标Maven -am test -Plocal→Admin全单测/build→Cypress→相关Checkstyle/不带fix的ESLint/diff检查；正式Stage4跨模块再扩大全仓及现有门控IT。单元Mock、真实Security链/数据库、真实浏览器及未做的第三方真机分别报告。
- 最终本地切换验证：同一公开origin从Admin进入IAM用本地测试账号认证，回到Admin完成用户/租户/菜单和新应用配置API，确认无password/Secret请求，无地址栏code残留、旧Cookie或持久Token；退出/刷新/过期可恢复。核对System/IAM/Admin容器镜像与健康、Flyway版本、Gateway内省和非目标容器未变。维护失败不能报告上线成功。

## Acceptance
Rain于2026-11-07以YES接受本计划。

## Implementation deviations
- 2026-11-07：增加 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java` 的最小修改。真实 Security 链开发测试发现 Discovery/JWKS 的 OPTIONS 不匹配原协议链而落入登录链（302）；必须将这两个精确 OPTIONS 路径纳入原协议链，才能让已计划的 CORS 生效。GET/OPTIONS 的 Origin/方法拒绝测试证明边界，不扩大其他路径或认证授权范围。

## Build evidence (in progress)
- 已确认本轮实施及本地切换范围：Rain此前“更新镜像”及本计划明确的协调切换；当前仍在Build开发检查，不执行apply。
- 只读脚本因deploy路径命中生产守卫。独立 `docker context inspect` 已核实 `orbstack` 的本地unix socket，`docker ps` 已核实 `macula-cloud-dev`；后续仅对此已授权本地项目的只读预检按守卫要求显式设置允许标记，不扩展至其他环境。

## Build handoff
- 授权码/PKCE、ID Token签名与声明验证、一次消费回调、标签页会话、权限初始化与竞态保护、登录/退出与动态路由已实施；公开配置删除浏览器密钥和演示账号；保留CustomOidcTokenCustomizer原样。
- System默认关闭的一次性初始化、两个身份的事务配置及漂移拒绝、私密备份/三服务协调切换脚本和文档已补齐。只读脚本PreToolUse守卫仍拒绝（包括显式允许标记的命令文本）；没有执行脚本、更新本地.env或切换容器，未尝试绕过守卫。
- 开发检查实跑：Vitest 178/178；目标Maven 47/47（IAM CORS 9、真实Security链33、System初始化5）；Cypress 41/41；Admin build通过；本轮JS ESLint无error、Java Checkstyle 0、git diff --check通过。证据位于/private/tmp/admin-code-{vitest,maven-final,cypress-final,build,eslint-final,checkstyle-final}.log。
- 新增AdminClientProvisioningIT已编写/编译，真实隔离MySQL/Redis执行、正式Stage4扩大验证和真实本地切换/登录仍待后续阶段；不把Mock浏览器或开发检查当作实际环境上线证据。
- 等待Build人工完成确认，不自行批准或进入正式Stage4；未commit/push。

## Stage4 corrections
- 2026-11-07：隔离IT实际运行发现测试错误使用未引入的Lettuce运行时；改用项目既有RedissonConnectionFactory，不新增依赖或改变生产Redis配置。用户“现在可以更新镜像了吧”确认实施完成并再次明确授权本地目标镜像更新。

- 本地切换修正：当前Compose run不支持--no-build；移除该参数（run默认使用现有镜像，不加--build），up保留--no-build。首次恢复依据保留在/private/tmp/macula-admin-cutover-j1189lkt。

- 实际Nginx回调验证修复：index.html运行时config.js由相对路径改为根路径，避免/auth/callback加载/auth/config.js而收到SPA HTML。Vite Mock原先匹配任意config.js掩盖此问题；使用实际Nginx/IAM重验。用户确认本地issuer统一127.0.0.1，System/IAM已同步。

- F12登录回归修复：独立真实会话已复现Chrome DevTools探测请求覆盖SavedRequest。增加DefaultSecurityConfiguration.java的共享RequestCache Bean，仅忽略精确/.well-known/appspecific/com.chrome.devtools.json的缓存保存；ProtocolOAuth2Configuration.java与默认链显式共享该Bean，其他认证、RequestCache默认规则和门户续转不变。IamProtocolTest.java补真实链授权→探测→密码认证回归；IAM README同步说明。属于已接受登录上下文完整性修复，不扩大匿名权限。
