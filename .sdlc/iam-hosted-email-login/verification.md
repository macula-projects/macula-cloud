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
