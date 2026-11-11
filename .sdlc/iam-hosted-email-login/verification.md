# Build 自检证据

Status: Build自检已执行，用户已以YES确认实现完成；未自批正式Test或PR。这里记录实际执行，不构成Test/PR/上线签字。

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
