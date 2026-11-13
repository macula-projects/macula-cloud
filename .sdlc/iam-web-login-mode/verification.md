# Web/SPA 登录模式验证

Status: pending — 创建后固定修订已实现，等待Build确认后正式验证

历史可切换版本及空草稿修复已完成验证；当前用户接受创建后固定的新合同，本轮尚未正式执行测试，不能引用旧结果标记新修订绿色。

用户回复“YES”确认Build完成后进入Stage4。本报告只使用本轮实际执行结果，不沿用Email/SMS或import整理的历史绿色结论。

## 实际执行与输出

| 范围 | 实际命令/执行器 | 结果 |
|---|---|---|
| 全仓后端 | `mvn -o verify -Plocal` | BUILD SUCCESS；Surefire 669项，失败0、错误0、默认跳过1；Failsafe 75项，失败0、错误0、默认跳过51 |
| 专项隔离验证 | `python3 /private/tmp/macula-iam-review-20261111/web_mode_isolated_runner.py`、`web_mode_related_runner.py` | 42项显式执行全部通过；ModeConcurrency以最终双Redis连接版为准，不重复累计旧Mock版 |
| 管理端单元 | `npm run test:unit -- --run` | 23个文件、217项全部通过 |
| 管理端构建 | `npm run build` | 成功；原有Sass弃用及chunk体积提示不影响构建 |
| 浏览器 | `python3 /private/tmp/macula-iam-review-20261111/web_mode_browser_runner.py` | 85项全部通过，0失败/0待定/0跳过，包含390/1440布局及原生键盘选择/取消 |
| 静态 | Checkstyle所有变更Java、ESLint本轮JS/Vue与用例（无fix）、`git diff --check` | 通过 |

后端最终日志`web-mode-all-verify-complete.log`的真实汇总（按插件汇总，不重复累计逐类输出）：

```text
Surefire: Tests run: 669, Failures: 0, Errors: 0, Skipped: 1
Failsafe: Tests run: 75, Failures: 0, Errors: 0, Skipped: 51
[INFO] BUILD SUCCESS
[INFO] Total time:  01:04 min
```

上两行是从真实插件汇总计算的总数；原始每模块输出与完整日志保存在`/private/tmp/macula-iam-review-20261111/`，不是虚构的单模块输出。代表性原始输出：

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
Test Files  23 passed (23)
Tests  217 passed (217)
✓ built in 5.14s
```

首行来自显式IAM绑定/MFA/缓存IT合计。默认Surefire跳过的用户名迁移另在全新隔离库显式执行1项通过。默认Failsafe的51项由专用环境开关控制，本次计划依赖的模式、绑定、认领、并发及迁移用例已另行启用，不以默认跳过作为通过证据；其余专用历史迁移/管理场景未启用，不包含在本次验收声明内。

## 计划Proof与证据对应

| 要求 | 证据 | 结论 |
|---|---|---|
| R1/R2 架构与模式 | WebLoginMode、ApplicationType、ApplicationLoginPolicy及ApplicationProtocolRegistry单元测试 | 两种Web架构、缺省、非法值、非Web禁配、单能力与grant排他通过 |
| R2/R4 保存与严格输入 | SettingsModelJson、ConfigService、Catalog、ProtocolConfigurationService、两Provisioning单元 | 重复/null/错误JSON类型拒绝；更新省略保留；有效模式往返；摘要与CAS包含模式；受管Admin固定BROWSER |
| R2 数据结构 | WebLoginModeMigrationIT、HostedEmailLoginMigrationIT、IdentityBindingMappingsMigrationIT | V25→V26只加列，旧IN_APP未转换；重复迁移不重置选择；V25历史测试固定target25；V23旧夹具先验证再升级26 |
| R4 并发与缓存 | WebLoginModeConcurrencyIT、OAuth2ClientApplicationConcurrencyIT、ApplicationProtocolConfigurationConcurrencyIT、AdminClientProvisioningIT | 并发仅一方提交；409拒绝旧快照；双独立Redis连接观察提交驱逐；回滚不驱逐；ID/Secret/用户绑定保留 |
| R2/R3 登录与协议 | InApp策略/Filter/Flow、HostedLoginPolicy、BrowserLoginPolicy、IamProtocolTest、两RegisteredClient仓库 | 原浏览器登录/SSO/授权码保持；端内不能用普通Session绕过证明；SavedRequest、当前Client与来源/版本/MFA边界通过 |
| R3 Code机密认证 | Converter/Provider单元及IamProtocolTest真实安全链 | Web basic/post/private_key_jwt成功、错误/缺失/禁用方法拒绝；SPA none成功且不能用残留Secret；BROWSER拒绝WEBVIEW且不调用平台；直接Code无浏览器Session/refresh |
| R3 成果发布与兼容 | MfaProtocolIT 13项、IdentitySourceBindingIT 17项、ProviderApplicationTokenCacheIT 4项；原Token/旧邮箱序列化回归 | 当前证明/版本贯穿绑定与发布；序列化类型/字段/UID/白名单保持，全部通过 |
| R4 管理端 | Vitest 217项、build、最终Cypress | 三项通过；取消/确认/失败重试/重载及原生键盘均验证 |

专项执行全部使用新建loopback MySQL 8.4与Redis。来源绑定先在V23验证历史迁移，再仅对该临时库升级V26，之后运行当前Mapper。所有自建进程已关闭；不使用业务数据库或运行中业务服务。

## 失败与修正记录

- 首轮后端IAM 407项中6失败、8错误，System未运行；前端217项中3失败。修正了新增运行时grant校验对旧只读sms/email快照的误阻断、可信Client测试夹具缺少架构、前端布尔返回与按钮/错误列表断言。
- 真实Web机密认证链发现Converter原白名单拒绝client_secret_post/private_key_jwt认证字段。新增与已认证方法一致的接收规则、重复/错方法拒绝，并在生成grant附加参数前丢弃Secret/JWT认证字段；新增用例证明凭据不进入授权附加数据。源和测试两条补充路径先加入plan。
- System后续2个失败修正：恢复普通客户端显式改变认证方式时原有无效Secret清理行为；跨终端拒绝夹具使用符合自身架构的认证方法。模式切换助手仍不改变认证方式，有效Web凭据保留。
- 基础缓存先拒绝旧类型时，SAS授权端点曾出现通用异常强转问题；统一适配专用授权异常，并新增回归。正常Token端点保持原错误。
- 新拒绝用例将unauthorized_client误断言为401，按真实协议响应修正为400并断言错误码及无平台调用。测试lambda的Checkstyle问题已修正。
- 浏览器新增键盘用例先遇到鼠标遮挡检查，随后合成keydown不触发原生radio默认行为；改用本地Cypress文档声明的`cy.press(Cypress.Keyboard.Keys.RIGHT)`执行真实按键，不以强制点击替代键盘可达性。

所有后端修正后重新执行全仓verify；前端修正后执行全部Vitest、构建和Cypress。保留真实失败日志，不把中间结果称为最终绿色。

## 配置、基线与边界

本轮没有修改AGENTS.md、CLAUDE.md、skill、hook、REVIEW.md或reviewer定义；无适用配置eval变更。源代码旧独立类型与convertToInApp引用已清除；历史SQL/SDLC、拒绝夹具及新模式值IN_APP为合理保留项。

计划外130个已有修改文件摘要保持；4个SMS旧类/测试的删除在Build开始前已经存在，保持原状。重叠文件按原未提交内容增量修改，未清理或覆盖原import/Email/SMS工作。

截至Stage4未提交、推送、合并、迁移业务库或更新镜像；用户随后明确要求更新本地镜像，结果见下节。飞书/钉钉真实SDK/设备/租户联调仍未进行，原登录故障按用户要求暂停。本轮真实安全链/Redis/MySQL与浏览器证据不代表厂商真机成功。

最终浏览器原始输出：

```text
✔  All specs passed!                        01:43       85       85        -        -        -
Cypress exit: 0
```

已检查390与1440截图，并用DOM宽度断言验证无横向内容溢出。相关自建浏览器预览/MySQL/Redis进程均已退出。下一阶段为sdlc-deploy代码审查；本报告不自批PR，不代表合并或部署授权。

## 用户授权后的本地镜像更新

2026-11-12用户明确要求“好，更新镜像”。更新范围为本次涉及的System、IAM、Admin，本机Compose项目macula-cloud-dev；未推送镜像仓库或修改Git提交。

实际执行：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps build macula-cloud-system macula-cloud-iam macula-cloud-admin
# 依次执行，每项完成健康检查后才继续下一项：System → IAM → Admin
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps up -d --no-deps --no-build --force-recreate --wait <service>
```

更新前确认三个服务的环境变量无变化，数据库为V25，无独立IN_APP记录，受管Admin为SPA且只有授权码入口。数据库完整备份保存在私有临时目录，权限0600；未将备份或凭据写入仓库。System启动仅应用V26的ADD COLUMN：

```text
Successfully applied 1 migration to schema `macula-system`, now at version v26
```

三个目标容器均running/healthy，运行镜像ID与刚构建的镜像一致：

| 服务 | 新镜像ID |
|---|---|
| macula-cloud-admin | `sha256:afae2cb39a0b6ced8a83ca9317223592aa1f16249161e9330ea9631ebdf4883b` |
| macula-cloud-iam | `sha256:3c596fedbcfcb138d65f0ea900aaaa50531bba4b44a2c920763fd91b6f711f04` |
| macula-cloud-system | `sha256:01b4770f44478ae7471ab70652d315d3332a0b217b8d9ed8bf21016054c017ed` |

- System、IAM均出现应用Started日志，启动ERROR为0。Admin首页200，IAM直连/login为200，经Admin的/iam/login与OIDC Discovery均200；issuer与原配置一致。System及Admin代理的无Token业务入口均401，符合预期。
- 对比镜像JAR内System配置Service、IAM Converter/ClientRepository、共享模式enum及V26迁移，与已验证本地产物字节一致。
- 最终复查Client、协议、第三方来源、来源关联、注册身份绑定及用户表的原有字段，全部摘要不变；新增web_login_mode可空列存在。没有轮换密钥、重写Client配置或用户绑定。
- 其他11个容器ID均未变化，包括MySQL/Redis/Nacos/Gateway；原有SnailJob unhealthy不是本次引入，也未在本次范围内变更。
- 本次只做构建、定向容器替换、迁移与冒烟；未重新进行真实第三方平台/设备认证，原飞书/钉钉故障仍待用户后续安排排查。

临时构建/启动日志、镜像与容器receipt、数据库原字段摘要均保存在/private/tmp/macula-iam-review-20261111；不提交生成物、日志或备份。

## 部署后空协议草稿修正（验证中）

截图与新增回归复现了settings=null时直接写authorizationGrantTypes的错误。修复前新增4项用例全部失败；修复后完整Vitest为221 passed，build成功。当前正在执行包含两个新增空协议保存/重载场景的完整Cypress。此修正仅影响前端空值处理与测试夹具，不修改协议配置合同，不制造默认协议，不改后端或数据库。


空协议草稿热修的最终Cypress为87/87通过，221项单元和build也通过，已构建Admin候选镜像，但未替换运行容器。用户随后要求创建选择后不再允许切换，进入本plan“创建后固定模式修订”确认；该新合同尚未实施，热修证据不能作为新不可变合同证据。


## 创建后固定修订的待执行验证

当前仅完成clean test-compile（-DskipTests）、Checkstyle、无fix ESLint和diff检查；详见build-notes。待工程师确认Build完成后，按plan“Proof（修订）”执行新创建/不可变单元、固定模式内双实例并发、全仓verify及全套Admin单元/build/Cypress。此前切换成功/取消的用例已被新合同用例替代，其历史绿色结果不证明当前实现。

本轮无新迁移；新证据绿色后按已接受范围定向更新System/Admin，IAM与其他容器保持。


## 创建后固定修订正式验证（2026-11-12）

用户YES确认Build完成后执行，Verification is green。

- `mvn -o -pl macula-cloud-system -am test -Plocal`：206项，Failures 0，Errors 0，Skipped 1。
- `mvn -o verify -Plocal`：BUILD SUCCESS；各模块汇总749项，Failures 0，Errors 0，Skipped 52（环境开关控制）。默认跳过不作为实际执行证据。
- 独立空MySQL与Redis执行WebLoginModeConcurrencyIT、ApplicationProtocolConfigurationConcurrencyIT、AdminClientProvisioningIT：各1项，全部通过、无跳过。固定IN_APP模式配置并发只允许一次提交；双独立Redis客户端观察提交后失效，回滚和模式修改拒绝不产生额外缓存驱逐，ID/Secret/绑定保持。临时进程已停止，不操作业务数据库。
- Admin完整Vitest：23 files、223 passed；npm run build成功（既有包大小提示）。完整Cypress：9 specs、96/96通过，0 pending/skipped，包括创建期Web/SPA两模式、390/1440布局、空协议草稿、创建后/刷新后只读、非Web/资源服务器不提交模式；后续协议选择仍区分Web机密认证和SPA none。
- Checkstyle成功，无fix ESLint输出为空，git diff --check通过。范围外198个既有改动摘要不变；模式切换helper/事件/确认逻辑已移除。
- 本轮首次正式测试均通过，无新增测试失败。截图人工抽查手机协议配置无横向溢出；平台接口采用测试夹具，不代表真实飞书/钉钉端内联调。
- 日志位于/private/tmp/macula-iam-review-20261111/immutable-*.log。新合同证据取代历史切换成功证据。
- System/Admin运行环境键比较无变化，更新前完成0600数据库备份与现有6张业务表摘要；无新迁移。按已接受授权继续仅更新System/Admin，IAM及其他容器保持。


## 创建后固定修订镜像更新与运行验证（2026-11-12）

按用户已有授权，在上述新验证全部绿色后构建并依次替换System、Admin。未提交、推送或合并代码。

- macula-cloud-admin：`sha256:bc4660aba08ff55739591bfa0bcd4548bf551e2f348faa878598b24dbc0f7dbb`，healthy；容器实际镜像ID与新tag一致。
- macula-cloud-system：`sha256:3de485dc3bd802ffd1084546a7f4072509078e7b42c7d1a8f26b12b3d5a45c97`，healthy；容器实际镜像ID与新tag一致。

- System出现Started MaculaSystemApplication，启动ERROR为0；Admin运行静态资源含创建后只读展示。部署JAR内DraftForm、配置Service、共享enum及V26资源字节与验证构建一致。
- Admin首页、IAM直连/login、Admin代理/iam/login、OIDC Discovery均200；issuer与原运行配置一致。System及Admin代理无Token业务访问均401。
- IAM、Gateway、中间件等其他12个容器ID均未改变。原有SnailJob unhealthy未触碰。
- 6张受保护业务表更新前后摘要完全相同；运行前环境键对比无变化。数据库已是V26，本修订无新迁移，不转换Client、Secret、来源或用户绑定。
- 临时日志、0600备份及receipt仅保留在/private/tmp/macula-iam-review-20261111，未纳入仓库。本轮未做真实飞书/钉钉设备联调。
