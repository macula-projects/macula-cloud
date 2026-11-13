# 验证记录

Status: Verification is green（邮件/SMS结构统一补充的批准范围）。用户yes确认Build后已执行本轮测试；未自批PR。历史记录单独保留。

## 本轮Stage 4正式验证（2026-11-12）

本补充不修改数据库迁移、管理端源码或第三方平台合同；验证覆盖计划的新增结构与安全边界。所有命令退出码为0，除下述已修复的测试夹具错误。没有真实短信/邮件外发或业务数据库写入；隔离数据库进程与浏览器预览进程已停止。

| 命令/范围 | 实际输出与结论 | 原始日志 |
| --- | --- | --- |
| `mvn -o -pl macula-cloud-iam -am test -Plocal` | `Tests run: 404, Failures: 0, Errors: 0, Skipped: 0`（IAM首轮）及API21；后续补强后IAM为405。 | channel-tests.log |
| 最终源码 `mvn -o test -Plocal` | `BUILD SUCCESS`；IAM `Tests run: 405, Failures: 0, Errors: 0, Skipped: 0`。全仓661项，660通过、1个环境门控。 | channel-reactor-final.log |
| 专用空库 `mvn -o -pl macula-cloud-system -am test -Plocal -Dtest=SysUserUsernameUniqueMigrationTest -Dsurefire.failIfNoSpecifiedTests=false` | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，补跑上述唯一跳过项；本轮661个单元用例都有通过证据。 | channel-username-migration.log |
| `mvn -o verify -Plocal -Dtest=NoTestsForFailsafe -Dsurefire.failIfNoSpecifiedTests=false` | `BUILD SUCCESS`；实际24项通过：MfaProtocolIT13、ProviderApplicationTokenCacheIT4、Gateway5、TinyID2。门控50项并未计作通过。 | channel-reactor-verify.log |
| 专用MySQL/Redis历史映射迁移及绑定IT | 迁移 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；绑定 `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`。覆盖50项门控中的相关18项；合计42个集成用例实际通过。其余32项独立System数据库场景未改动，未列入本补充Proof，不宣称已执行。 | channel-isolated-migration-it.log、channel-isolated-binding-it.log |
| `npm run test:unit -- --run` | `Test Files 23 passed (23)`；`Tests 213 passed (213)`。 | channel-admin-unit.log |
| `npm run build` | `✓ built in 5.70s`；保留既有大chunk提示，构建退出0。 | channel-admin-build.log |
| 全套Cypress，显式开启真实IAM模板预览 | `All specs passed!`；83/83通过，无跳过，含5项IAM真实Thymeleaf页面交互与320/390/1440px布局。 | channel-admin-cypress.log |
| Checkstyle / diff / 残留扫描 | 155个变更Java文件通过；`git diff --check`通过；旧SmsService/SmsServiceImpl/UnavailableSmsService在生产/测试/资源调用中无残留。两Token与Redis内核没有本轮改动；补充名单外124个import文件逐字节保留。 | channel-checkstyle-final.log |

数据库用例由临时channel_binding_runner.py/channel_username_runner.py创建随机本地端口的全新MySQL/Redis实例、空库及显式运行标记；测试只使用专用库，结束后停止自有进程。绑定用例先执行历史V23映射迁移，保持其schema guard，不把测试指向业务库。客户端无真实凭据。

Cypress由临时channel_browser_runner.py执行，展开命令为：`npx --no-install start-server-and-test 'vite preview --host 127.0.0.1 --port <本轮空闲端口> --strictPort' http://127.0.0.1:<预览端口> 'cypress run --expose IAM_PREVIEW_URL=http://127.0.0.1:<模板端口>/login.html --config baseUrl=http://127.0.0.1:<预览端口>,screenshotsFolder=<临时输出目录>,video=false'`。模板静态服务只指向IAM的target/iam-preview，预览服务结束后均停止；未使用用户浏览器会话。

### Proof逐项对应

- 公共通道与失败关闭：VerificationCodeChannelServicesTest5项及SmsCodeServiceTest4项，证明缺内核/投递器、双Bean名称选择、发送失败不激活、受信显示名、邮件拒绝绑定、SMS用途/Client/browser域保留。邮件HTML转义仍由原LoginMailDispatcherTest证明。
- 认证模板：VerificationCodeAuthenticationContractTest6项参数化覆盖两通道的输入、POST、重复/空白/长度/六位格式、受信Client、错误码不查目录、停用/锁定/账号过期/凭据过期、角色/details/真实OTT时间及无验证码成功载体。
- 真实登录安全链：HostedSmsLoginFlowTest4项、HostedEmailLoginFlowTest5项；真实Redis、SavedRequest、Session、S256、nonce、auth_time、UserInfo、重放、跨Client、禁用、CSRF及MFA拒绝。只替换外发渠道和业务目录。
- MFA与持久化：MfaProtocolIT13项包含两通道拒绝不建Session；MaculaOAuth2AuthorizationServiceTest14项覆盖当前两种Token因子及原SMS/旧email授权读写、撤销。精确类型允许规则不扩大。EmailCodeAuthenticationTokenSerializationTest2项与LegacyEmailPrincipalReaderTest3项通过。
- 绑定：真实绑定服务单元与页面真实模板分别验证可用性传递/选项隐藏恢复；IdentitySourceBindingIT17项验证事务、锁、提交后消费、跨Client/用途隔离与旧SMS键/摘要合同。页面用例明确Mock绑定服务，不将其当成数据库绑定证明。
- 本轮未修改CLAUDE.md、AGENTS.md、skill、hook、REVIEW.md或reviewer定义；无适用的保护配置eval变更，也未声称执行不存在的eval。

### 失败与修正

补强绑定页面用例首次出现真实输出：`Expected: a string containing "value=\"sms\""`。原因是该页面夹具Mock整个ThirdPartyBindingService，验证码端口Mock的返回值不会传递到页面。已将页面可用性Mock放到正确边界，同时增加真实ThirdPartyBindingService的能力传递合同。`channel-binding-ui-fixed.log`为`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`；之后重跑全仓，最终输出见上表。生产实现没有因此放宽。

本轮原始日志和测试临时产物均位于`/private/tmp/macula-iam-review-20261111/`，不提交。Verification is green，下一阶段可进入sdlc-deploy审查；此结论不等于PR批准、推送、合并、部署或真实平台联调通过。端内Web/SPA新模型仍独立等待开关语义确认。

## 本轮补充构建证据（2026-11-12）

| 检查 | 实际结果 |
| --- | --- |
| `mvn -o -pl macula-cloud-iam -am clean test-compile -DskipTests -Plocal` | BUILD SUCCESS；清洁编译IAM及API的主代码与全部测试源码，没有执行测试。完整日志在临时channel-clean-compile.log。 |
| Checkstyle | 当前155个变更Java文件通过，包含新增文件和此前import整理；未通过宽松排除绕过。 |
| `git diff --check` | 通过。 |
| 旧符号/装配引用 | IAM内无SmsService/SmsServiceImpl/UnavailableSmsService、旧便利重载或Service定位器残留；两个Token字段/类型未变。 |
| 已有改动保留 | 补充名单外124个import整理文件与先前after快照逐字节一致；用户原有AbstractThirdPartyAuthenticationFilter改动保留。 |
| 本轮运行测试 | 未执行。待人工确认Build完成后，运行双通道合同、IAM/API、隔离绑定/MFA/存储IT和全仓测试。不得沿用此前通过数冒充本轮证据。 |

编译过程中新增断言的泛型约束和一个matcher引用错误已修正；最终clean编译通过。未修改POM或依赖声明，未操作运行中服务、业务库或真实邮件/短信渠道。

## 此前实现的历史回归记录


## 后端

| 检查 | 结果 |
| --- | --- |
| 全仓`mvn test -Plocal` | 643项，0失败/错误，1项按环境门跳过；实际642项通过。IAM387，System199含1跳过，System API21，其余模块合计36。先clean重建IAM/API排除退役类残留；首次编译缺anyBoolean已修正。 |
| EMAIL完整安全链 | 实际Spring安全链+隔离Redis：发码、Session、SavedRequest、授权码+S256、nonce、OIDC auth_time、UserInfo及一次消费；关闭/CSRF/跨Client/重复字段与MFA拒绝覆盖。仅外发邮件与业务目录用fixture。 |
| SMTP与验证码 | JavaMail连接本地假SMTP捕获MIME；Redis真实原子消费、TTL、次数、并发一次成功及通道/用途/Client隔离。未向真实邮箱发送。 |
| V25迁移IT | 1项通过，MySQL8.4全新空库从24到25；NULL/空/合法/已有EMAIL/非法小写/不支持类型及资源身份，Secret、版本、协议、用户和绑定保持，取消后再次migrate不重开。 |
| 身份绑定IT | 17项通过；专用新建MySQL/Redis及显式标记，另历史V23迁移夹具1项通过；锁、回滚、提交后消费与SMS用途隔离保留。 |
| MFA与缓存IT | MfaProtocolIT12项、ProviderApplicationTokenCacheIT4项通过；真实安全链/签名及专用Redis，第三方平台交互仍为Mock。 |
| System EMAIL写入 | OAuth2ClientConfigServiceImplTest定向通过，保存EMAIL后取消、空与NULL合同保留。 |
| Client CAS | OAuth2ClientApplicationConcurrencyIT通过，真实MySQL竞争EMAIL/PASSWORD，只有胜者写入，败者409，缓存仅失效一次。 |

单元命令：`mvn test -Plocal`；集成命令：`mvn -pl macula-cloud-iam -am verify -Plocal -Dtest=NoTestsForFailsafe -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MfaProtocolIT,ProviderApplicationTokenCacheIT -Dfailsafe.failIfNoSpecifiedTests=false`。有门控的MySQL测试由临时执行器初始化全新独立实例与空库并设置专用开关，结束后停止自有进程；未读取业务数据库凭据。

## 前端与静态检查

- Admin相关Vitest：44项通过；`npm run build`通过，仍有既有大chunk提示。
- IAM专用Cypress：5项通过。使用真实Thymeleaf输出和仓库静态文件，320/390/1440px核验五图标单行、无页面横向溢出、44px操作区、本地图像、可访问名、焦点/键盘页签、邮箱CSRF发码、倒计时、投递失败重试与重复提交。
- Admin目标Cypress：36项通过，包含EMAIL取消保存后reload保持关闭。正确命令为`npx start-server-and-test preview http://localhost:4173 'cypress run --spec cypress/e2e/oauth-client-configuration.cy.js --config screenshotsFolder=/private/tmp/macula-iam-review-20261111/email-admin-screenshots,video=false'`。
- 早先包装参数误用触发全套83项，断言全部通过但包装器退出时报server closed unexpectedly；不将该次包装命令记绿。之后正确目标命令返回成功。IAM初次Cypress未覆盖baseUrl，未执行断言；更正4175后5项通过。
- Java变更按仓库Checkstyle通过；git diff --check通过。源码无package-info.java，静态资源使用仓库已有许可SVG，无新增CDN/依赖。
- 已打开并检查320px登录截图及重新渲染的架构PNG。图标横向同一行，EMAIL表单及架构标签可读。

## 证据位置与限制

原始命令日志与临时素材位于`/private/tmp/macula-iam-review-20261111/`，不提交：email-final-reactor2.log、email-flow-tests5.log、email-migration-it.log、isolated-binding-it.log、email-mfa-cache-it2.log、email-system-target.log、email-concurrency-it.log、email-admin-unit.log、email-admin-build.log、email-iam-browser.log、email-admin-browser-target.log、checkstyle-final.log。

Build自检未执行真实邮件送达、真实厂商/设备、运行环境Session存储及业务库升级/回滚；随后授权的本机Compose更新见build-notes.md。V25含新增枚举值，部署旧版本前需恢复兼容显式列表；上线执行仍需单独授权。未提交、推送或向远端部署；本机Compose镜像更新已获授权并完成。


## Stage 4 正式复验（2026-11-11，推送前最终代码）

用户已用YES确认Build完成，并明确授权推送合并。已重新执行验证，不将之前的Build运行冒充本轮结果。

Verification is green。

| 本轮命令/证据 | 实际结果 |
| --- | --- |
| `mvn clean test -Plocal`，`pr-backend-tests3.log` | `BUILD SUCCESS`，643项，0失败/错误，1项既有环境门控跳过；642项实际通过。 |
| 补类型职责注释后的`mvn test -Plocal`，`pr-backend-final.log` | `BUILD SUCCESS`；同一完整reactor通过。 |
| `npm run test:unit -- --run`，`pr-admin-final-unit.log` | `Test Files 23 passed (23)` / `Tests 213 passed (213)`。 |
| `npm run build`，`pr-admin-build.log` | `built in 5.78s`；既有chunk大小提示保留。 |
| `npx start-server-and-test preview http://localhost:4173 'cypress run --expose IAM_PREVIEW_URL=http://127.0.0.1:4176/login.html --config screenshotsFolder=/private/tmp/macula-iam-review-20261111/pr-final-screenshots,video=false'` | `All specs passed!`，83项，0失败/待执行/跳过，含5项真实Thymeleaf页面场景；`pr-admin-browser-final.log`。 |
| IdentityBindingMappingsMigrationIT + IdentitySourceBindingIT | 1+17项通过，新建隔离MySQL/Redis；`pr-binding-run.log`及isolated相关日志。 |
| HostedEmailLoginMigrationIT | 1项通过，新建隔离MySQL从V24到V25；`pr-migration-run.log`及`email-migration-it.log`。 |
| OAuth2ClientApplicationConcurrencyIT | 1项通过，新建隔离MySQL内EMAIL/PASSWORD竞争，409及唯一缓存失效；`pr-concurrency-run.log`。 |
| MfaProtocolIT + ProviderApplicationTokenCacheIT | `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`；`pr-mfa-cache.log`。 |
| 实际全部暂存Java差异Checkstyle（包含重命名目的文件） | `Java files: 311 Checkstyle exit: 0`；`checkstyle-final.log`。 |
| 其他 | `git diff --cached --check`通过。暂存无target/dist/node_modules/.env；未变更CLAUDE.md、AGENTS.md、技能、hooks或REVIEW.md，无适用配置eval变更。 |

唯一后端跳过项为既有SysUserUsernameUniqueMigrationTest的独立数据库环境门；本次不修改用户名唯一性迁移，相关账号歧义拒绝由本次账号回归及真实绑定IT覆盖。不能把该跳过项计为通过。真实厂商、设备及邮件送达仍是单独验收范围。

本轮初始失败及修复：JSON夹具被排版后，拒绝测试的紧凑字符串替换没有修改样本，改为结构化JSON变异；不削弱生产兼容读取器。真实模板输出改为模块target，Cypress16使用expose公开预览配置，未启用时显式跳过，最终完整运行已启用并通过。Checkstyle按暂存+重命名完整集合扫描，补既有排版类型的Javadoc及since；未修改校验规则。所有最终命令退出码均0。

完整原始日志仅保存在本地临时目录，不提交日志或私有库备份。本节证明本地测试，不代替第三方联调或人工PR风险接受。
