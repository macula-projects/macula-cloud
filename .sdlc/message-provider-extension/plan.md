# Plan: 邮件服务扩展、自定义短信与消息包结构整理 (from spec.md 2026-11-13)
Status: accepted
Author: Rain

## Files that change
以下路径均相对 macula-cloud 仓库；花括号表示逐项列出的文件，不代表目录通配授权。

### System
Java 基路径：macula-cloud-system/src/main/java/dev/macula/cloud/system/
- 修改 enums/MessageProviderType.java；新增 enums/MailSecurityMode.java。
- 修改 form/MessageProviderForm.java、vo/MessageProviderVO.java、service/impl/MessageSettingsServiceImpl.java。
- 新增 message/mail/MailSender.java；移动 message/MailTestSender.java 到 message/mail/impl/MailTestSender.java 并实现接口。
- 新增 message/sms/SmsSender.java；移动 message/{AliyunSmsTestSender,TencentSmsTestSender}.java 到 message/sms/impl/ 下同名文件并实现接口。
- 新增 message/sms/impl/CustomHttpSmsSender.java、message/sms/support/CustomSmsHttpClient.java、message/sms/support/CustomSmsOptions.java。
- 测试基路径 macula-cloud-system/src/test/java/dev/macula/cloud/system/：修改 service/impl/MessageSettingsServiceImplTest.java、message/{MailTestSenderTest,SupplierSmsTestSenderTest}.java；新增 message/{MessageProviderTypeTest,CustomHttpSmsSenderTest,CustomSmsOptionsTest}.java。
- 修改 macula-cloud-system/src/test/java/dev/macula/cloud/system/vo/SettingsModelJsonTest.java，更新发送器嵌套配置类型导入。
- 修改 macula-cloud-system/README.md。

### IAM
Java 基路径：macula-cloud-iam/src/main/java/dev/macula/cloud/iam/
- 保留 message/mail/MailSender.java、message/sms/SmsSender.java 接口位置。
- 移动 message/mail/SmtpMailSender.java 到 message/mail/impl/SmtpMailSender.java 并增加 STARTTLS。
- 移动 message/sms/{AliyunLoginSmsSender,TencentLoginSmsSender}.java 到 message/sms/impl/ 下同名文件。
- 移动 message/mail/LoginMailDispatcher.java、message/sms/LoginSmsDispatcher.java 到 message/dispatch/ 下同名文件。
- 新增 message/mail/MailSecurityMode.java、message/sms/impl/CustomHttpSmsSender.java、message/sms/support/CustomSmsHttpClient.java、message/sms/support/CustomSmsOptions.java。
- 修改 config/DefaultSecurityConfiguration.java、authentication/email/EmailCodeService.java、authentication/sms/SmsCodeService.java 中的调用方导入。
- 测试基路径 macula-cloud-iam/src/test/java/dev/macula/cloud/iam/：修改 message/mail/{SmtpMailSenderTest,LoginMailDispatcherTest}.java、message/sms/LoginSmsDispatcherTest.java；新增 message/sms/{CustomHttpSmsSenderTest,CustomSmsOptionsTest}.java；修改 message/support/FakeSmtpServer.java，并按需新增 message/support/FakeStartTlsSmtpServer.java，支持合成证书的隔离 TLS 场景。
- 更新包移动涉及的测试导入：config/IamProtocolTest.java、controller/{HostedSmsLoginFlowTest,HostedEmailLoginFlowTest}.java、authentication/verification/VerificationCodeChannelServicesTest.java、authentication/sms/SmsCodeServiceTest.java（相对上述 IAM 测试基路径）。
- 修改 macula-cloud-iam/README.md。

### Admin 与交付文档
- 修改 macula-cloud-admin/src/views/system/messageSettings/index.vue。
- 修改 macula-cloud-admin/src/locales/lang/{zh-cn,en}.js。
- 修改 macula-cloud-admin/tests/unit/message-settings.test.js。
- 按回归结果同步更新 macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js、macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js 中旧的“不支持 Exchange”断言。
- 新增 macula-cloud-admin/cypress/e2e/message-provider-extension.cy.js。
- 修改 macula-cloud-admin/README.md。
- 本目录 intent.md、spec.md、plan.md、review-findings.md 记录状态、评审及实际偏差。

## Order of work
1. 完成计划评审并由 Rain 批准后再开始源码修改；使用现有 JavaMail、Java 17 HTTP 客户端和 Jackson，不新增 Maven/npm 依赖。
2. 先补供应商类型、安全模式、authHeaders 表单和状态 VO，以及 System 按类型校验/存储逻辑。邮件默认值由服务端决定；Exchange 可编辑主机、端口及安全模式，新建默认 587/STARTTLS。CUSTOM_HTTP 不要求 account，签名和模板约束保持；无关字段允许缺省，实质非空值拒绝，避免旧前端模型空字段误拒绝。
3. Header 使用 Map 配置，名称大小写不敏感去重，拒绝非法 HTTP token、CR/LF、传输控制 Header；序列化后执行 2048 字符限制。未提供/空白表示保留，只能替换不能隐式清空；切换供应商不得复用旧 credential。VO 不回显 Header。HTTP 地址校验禁止凭据、fragment 及非法 scheme。
4. 把 System 邮件/短信发送器移至 impl，接口保留职责包；按供应商注册短信实现，发现重复标识即失败。服务编排仅依赖 MailSender/SmsSender。同步更新测试导入与装配。
5. 完成两服务 STARTTLS：enable=true、required=true、SSL=false；保留旧 NONE/SSL 设置，保持证书和主机名校验。自建私有 CA 由部署的 JVM 信任库提供，不添加跳过校验开关。
6. 按 spec 的固定请求/响应契约实现两服务的自定义短信适配。System 传模板变量；IAM 把验证码封装为 code，场景 SMS_LOGIN、语言 zh-CN 与当前模板查询保持一致。不跨服务依赖实现。
7. Java HTTP 客户端禁用跳转和重试，连接及总调用 5 秒超时。使用有界响应订阅器，接收时累计字节、超过 64 KiB 即取消；不能先读完整响应再检查。总超时覆盖响应体消费，禁止在 send 返回后无期限阻塞读取流。异常输出统一安全消息。
8. 移动 IAM dispatcher 至 dispatch 并更新全部调用方；保留 SmsSender 的现有验证码接口，供应商从服务端配置选择。构造契约样例让两服务独立断言相同字段及成功/失败语义。
9. 更新 Admin 的新增选项、条件字段、安全模式、Header 键值编辑和已配置提示；切换供应商清理无关字段及临时秘密，既有空白凭据保留行为不变。模板仍按供应商/语言/场景保存。URL 及 Header 仅由配置 API 提交，不进入测试发送请求。
10. 更新中英文文案、README 与测试；完成最小测试、构建及关键页面流程验证。记录实际修改列表与必要偏差，再提交实施完成情况供确认；不执行 Git 提交或部署。

## Risks
- Rain 已允许迁移现有数据；当前实现仍计划沿用表结构，不新增列，必要时提供保留配置的迁移材料。旧供应商标识和 credential 格式不变；Header JSON 仅用于 CUSTOM_HTTP。回滚代码前需先将新增供应商切回旧版支持类型，否则旧版按不支持配置失败；配置备份不含明文秘密。
- 包移动会改变 Java 类型路径，需搜索生产与测试调用方并更新导入；不改验证码 Token 的持久化类型、认证链或公开 REST 路径。
- 自建 Exchange 必须支持账号密码 SMTP AUTH 和相应 TLS/发送权限；真实服务器未联调，不宣称所有 Exchange 部署均兼容。
- 自定义 URL 由可信管理员配置，允许内网；HTTP 明文可能暴露 Header，界面和文档提示优先 HTTPS。无权限用户不能设置 URL/凭据。
- HTTP 超时/连接中断可能发生在服务已受理之后，不自动重试，避免重复短信；只标记 submitted，不能声称 delivered。
- 不扩大其他 IAM 包重构范围；若实施需要新增计划外文件，先记录偏差，若改变契约则重新请求确认。

## Proof
- System 配置测试覆盖新增预设、Exchange 默认及覆盖、STARTTLS 保存、authHeaders 不回显/空白保留/替换、切换凭据隔离、大小写重复/非法/超长 Header、URL 校验、空 account 和无关字段拒绝；旧供应商回归。
- System/IAM 的 SMTP 测试覆盖真实隔离 SMTP 提交、STARTTLS 成功、不支持升级、证书及主机名错误、NONE/SSL 回归，不依赖外部邮件服务。
- 两服务 HTTP 契约测试使用本地合成服务，断言 POST JSON/Header、templateParams 过滤、2xx+success=true 成功，以及 false/缺字段/字符串 true/空响应/非 JSON/非2xx、跳转、超时、慢响应体、超大响应、禁用渠道和缺模板拒绝。审计输出不含秘密、验证码和完整目标。
- 验证接口注册、重复供应商拒绝、IAM Bean 装配和移动后的全部调用方编译；原阿里云/腾讯云 SDK 测试仍通过。
- Admin Vitest 覆盖新增选项、动态字段、Header 保留/替换与切换清空、表单验证、中英文、异步失败/成功及重复提交；Cypress mock API 验证配置保存、模板编号和测试发送，断言请求不泄密。
- Maven 先分别运行 System 与 IAM 相关 test -Plocal，再按跨服务影响运行 mvn test -Plocal；Admin 运行 npx vitest run tests/unit/message-settings.test.js、npm run build，以及指定新增 Cypress 流程。检查变更 Java 的 Checkstyle，按当前仓库插件支持选择命令，不运行自动格式化。
- 未运行或受环境影响的检查明确记录；隔离协议测试与真实 Exchange/短信端到端验收分开报告。确认无 POM、数据库迁移、其他 IAM 模块或生成物改动。

## Implementation notes
- Rain 已允许迁移现有数据；当前字段可承载新增配置，因此无需强制迁移，也未访问业务库。
- 邮箱预设测试直接并入 MessageSettingsServiceImplTest，验证实际保存行为；不新增重复枚举实现的 MessageProviderTypeTest。
- System STARTTLS 测试辅助类内嵌于现有 MailTestSenderTest；IAM 使用计划内 FakeStartTlsSmtpServer。
- 未发现 CLAUDE.md，按仓库 AGENTS.md 及按需规则实施。
- 全量 Admin 回归发现既有身份/消息测试仍断言不支持 Exchange；此断言与本次已批准规格相冲突，已更新两处测试并扩大相关回归，不改变功能范围。

## Build verification (2026-11-13)
实施已完成，等待 Rain 确认 Phase B 完成；以下为开发期间实际验证，不代替 Stage 4 的人工流程。

- `mvn -o test -Plocal`：11 个 reactor 模块构建成功；总计 710 项测试，709 通过、0 失败/错误、1 跳过。跳过的是既有 `SysUserUsernameUniqueMigrationTest#duplicateUsernameStopsV7UntilDataIsFixed`，未设置 `SYSTEM_MIGRATION_IT`，未访问业务数据库。
- 最后规范修订后，复核 System/IAM 配置及 SMTP/HTTP 最小相关测试全部通过；测试涵盖真实隔离 TLS 升级和有界 HTTP 投递，无真实收件人。
- `npm run test:unit -- --run`：23 个文件、223 项全部通过。最初直接使用默认 Node 环境不适用于本项目浏览器测试，已改用 package.json 的 jsdom 命令。
- `npm run build`：通过；存在现有大 chunk 体积提示。
- 新增 `message-provider-extension.cy.js`：4/4 通过；原 `admin-identity-security-settings.cy.js`：17/17 通过。使用本地预览及合成 session/mock API，未接真实供应商。
- Cypress npm 包原已安装，缺少 16.2.0 运行器；补装匹配的缓存运行器，未修改 package.json/lockfile。
- 变更 Java Checkstyle：用临时检查 POM 明确列入 System/IAM 的 main/test 根目录及实际变更的 42 个 Java 路径，按仓库 `.config/checkstyle.xml` 和版权头检查，0 违规。初始根级 `-N` 不覆盖模块源码，因此未把该结果当作验证完成。
- `git diff --check`：通过。未修改依赖、数据库 schema、其他 IAM 实现包；未提交、推送或部署。除包移动所需调用方导入及相应测试外，未扩大其他认证逻辑范围。
- 真实自建 Exchange 的认证/CA/发送权限和自定义短信服务仍需部署方联调，以上结果不代表真实供应商兼容性已验收。

## Authorized local image refresh (2026-11-13)
- Rain 回复“OK，更新镜像”，明确确认 Phase B 实施完成并授权本次本地镜像更新；此授权覆盖此前非目标中的本次定向部署，不代表 Git 提交、推送或镜像发布。
- 使用当前 deploy/.env、deploy/docker-compose.yml 和 apps profile，仅 build/recreate macula-cloud-system、macula-cloud-iam、macula-cloud-admin。up 使用 --no-deps --no-build --force-recreate --wait，等待健康检查通过。
- `macula-cloud-system`：新镜像 `sha256:72287e61f30099905fa99ddebabbcd2a656e5cad0440900833f1560a8cc91c5e`；新容器 healthy，运行镜像与构建标签一致。
- `macula-cloud-iam`：新镜像 `sha256:705eabe2e7854830dfed841760c0a7702bfd9e3f1716f7d6076c476a2c06aa02`；新容器 healthy，运行镜像与构建标签一致。
- `macula-cloud-admin`：新镜像 `sha256:789319cc76ec8e30423bcb33fdd8eb54e9e55a7c9f36170aafb008367543316d`；新容器 healthy，运行镜像与构建标签一致。
- 镜像内 System/IAM 的 9 个关键 class 与本地已测试输出 SHA-256 一致，旧实现路径不存在；Admin 实际 HTTP 返回的 JS 已包含新邮箱类型、CUSTOM_HTTP 和 authHeaders。
- 运行冒烟：Admin 根页面 200；IAM Discovery 200；System 菜单 API 未认证 401；Admin 代理消息设置 API 未认证 401。两后端启动日志均确认 Started，无启动失败标识。
- 其余 14 个容器及镜像标识未改变，deploy/.env 指纹未改变；没有修改业务数据库、配置凭据或数据卷。SnailJob 在更新前已有 unhealthy 状态，本次未纳入修改。
- 本次证据为镜像/容器/启动及 HTTP 冒烟验证；没有发送真实邮件或短信，不代替供应商联调。

## Stage 4 verification
完整正式验证已执行，结果及真实输出见 [verification.md](verification.md)。此文件为新增的阶段证据文档，不改变源码范围或运行配置。

## Review fix — round 1
独立审查确认停用已配置服务时未完成 Header 会阻塞请求。Admin 现缓存非敏感已保存配置，停用请求仅使用该快照并保留服务端凭据，丢弃未完成编辑；新增实际浏览器回归覆盖不完整 Header/无效新 URL 后停用。无后端或协议契约变化，修复后重新执行全部测试。

## Review completion
代码审查共两轮，首轮 1 项 Minor 已修复并重验，第二轮无新增发现。结果见 review-findings.md；本地 PR 说明草案为 pr-description.md。等待明确 Git/PR 操作授权，未提交或推送。

## Review fix runtime refresh
- 原 Dockerfile 定向 Admin 构建连续两次遇到 Docker Hub manifest HEAD EOF（node/dockerfile 元数据），旧运行容器保持健康。
- 使用已存在的 Admin 镜像运行层，在临时隔离副本排除原 Dockerignore 中的本地 env 覆盖后，使用已验证的本地依赖与 `VITE_APP_API_BASEURL=/api npm run build -- --mode docker` 生成静态资源；临时 Dockerfile 离线构建，仅替换静态 assets，未修改仓库 Dockerfile 或依赖。
- 构建前后 image Config 的 User、Entrypoint、Cmd、Env、ExposedPorts、WorkingDir、Volumes、Healthcheck 均一致。
- 只用 --no-deps --no-build --force-recreate --wait 更新 Admin。最终镜像 `sha256:5506350d87f3e4bff94db330972f9aa738c8849f8e1731ae71e1e84336dc3806`，容器 healthy；HTTP 根页面 200、受保护消息 API 未认证 401；实际 HTTP index 和消息设置 JS 与修复产物逐字节一致。
- System、IAM 及其余共 16 个非 Admin 容器 ID/image 未改变，deploy/.env 指纹未改变。没有发送真实邮件/短信。
- 两轮代码审查已完成，Verification is green；本地 PR 说明已备好，等待明确授权提交、推送、创建 PR。尚未创建远端 PR，人工 PR 审批 gate 未通过。

## Git and PR authorization
Rain 在明确询问“是否授权提交、推送并创建 PR”后回复“OK”，授权将本次已审查变更创建功能分支、提交、推送并创建 PR。此授权不包含合并、发布或删除分支。此前“不提交、推送”约束对此次操作被明确更新，源码仍与验证及审查快照一致。
