# 正式验证与本地容器更新记录

2026-11-06：Rain以YES确认Build实现完成，进入Stage 4；随后明确要求“更新docker”。本记录仅涉及iam-application-configuration，不改变其他SDLC事项。

## 结论

Verification is green。
全项目默认Maven套件436项中435项直接通过、1项环境门控；该门控升级用例随后在隔离MySQL补跑1/1通过。所有既有*IT套件均已执行：本次外部码迁移/并发2项、身份源迁移1项、安全策略迁移1项、API Key真实Mapper/Redis6项、System持久化与真实缓存6项，全部通过。Admin单元134项、完整Cypress34项全部通过。

## 实际命令与输出

```text
mvn test -Plocal
[INFO] BUILD SUCCESS
# 汇总各测试类：Tests run 436, Failures 0, Errors 0, Skipped 1
```

后续所有数据库与Redis检查均连接本次独立测试容器，数据库名带测试前缀，未在业务数据库运行fixture。通过Surefire显式-Dtest执行所有IT类；实际命令和输出如下：

```text
mvn -pl macula-cloud-system -am test -Plocal -Dtest=ExternalCredentialLoginMigrationIT,OAuth2ClientApplicationConcurrencyIT -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```text
mvn -pl macula-cloud-system -am test -Plocal -Dtest=SysUserUsernameUniqueMigrationTest -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```text
mvn -pl macula-cloud-system -am test -Plocal -Dtest=IdentityProviderMigrationIT -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```text
mvn -pl macula-cloud-system -am test -Plocal -Dtest=SecurityPolicyMigrationIT -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```text
mvn -pl macula-cloud-system -am test -Plocal -Dtest=ApplicationApiKeyIntegrationIT -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```text
mvn -pl macula-cloud-system -am test -Plocal -Dtest=SettingsPersistenceIT -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

SettingsPersistenceIT首次运行4项失败，实际原因是代理提供的临时AES fixture key误配为17字节，日志为“AES秘钥长度应该为16位、24位、32位，实际为17位”。改为16字节后6/6通过；没有据此修改生产加密代码。其新建Client fixture同步显式SPA类型，继续验证真实Redis提交后驱逐。

```text
npm run test:unit -- --run
Test Files  15 passed (15)
Tests  134 passed (134)

npm run build
✓ built

npm run test:e2e:ci
All specs passed! 34 34 - - -

mvn -f /private/tmp/iam-checkstyle-pom.xml checkstyle:check
You have 0 Checkstyle violations.
```

变更33个Java文件使用项目.config/checkstyle.xml及版权头定向检查。变更前端业务文件、路由与布局ESLint退出0；git diff --check退出0；Compose config --quiet退出0。

## Proof与规格对应

| 范围 | 证据 |
| --- | --- |
| 类型/平台与历史维护、三态字段、无密钥初始化 | Client Service与Controller契约测试、应用模板/请求构造测试 |
| 不可变类型与旧PUT并发、409与副作用 | 隔离MySQL真实Mapper/Service两个独立事务，竞争确认与旧PUT两种交错 |
| Discovery来源、Issuer、限长、跳转、超时、过期 | 隔离HTTP服务器验证，不带认证、不跟随跳转；缺失端点不生成伪地址 |
| 托管PASSWORD/SMS同策与信任边界 | Policy/Filter单测、真实SecurityFilterChain/Thymeleaf/MockMvc、既有Token/SSO/第三方协议回归 |
| 短信Client隔离 | 真实验证码存储隔离测试与托管过滤链Client传递测试 |
| 四分区保存、创建/切换清理、历史确认与密钥 | Vitest组件与完整管理壳层Cypress；离开/停用清理，失败与重试 |
| 原数据保留与迁移三态 | V8到V14隔离升级含失败预检，NULL/空字符串往返；实际本地V13到V14指纹核对 |
| 桌面/窄屏 | 1440px/390px截图已查看；根和内部滚动容器无横向溢出断言通过 |

未修改CLAUDE.md、AGENTS.md、skills、hooks、REVIEW.md或reviewer定义，因此本轮无适用的保护配置eval；未虚构eval结果。浏览器请求为Mock，真实第三方设备/供应商登录未执行，不将其记作已验证能力；不在本轮新增供应商协议。

## 已授权的本地Docker更新

只构建并重建macula-cloud-system、macula-cloud-iam、macula-cloud-admin，使用既有deploy/.env及Compose。

```text
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps build macula-cloud-system macula-cloud-iam macula-cloud-admin
docker compose ... up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 macula-cloud-system
docker compose ... up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 macula-cloud-iam macula-cloud-admin
```

先System执行迁移，健康后更新IAM/Admin。三者最终均healthy；其他Compose服务ID逐项保持不变。Flyway最新成功V14，三个新字段在历史记录中均NULL；Client ID/密钥/grant/渠道指纹及固定身份源绑定在前后保持一致。

| 服务 | 镜像ID |
| --- | --- |
| System | sha256:cdaf8824ffeb03e12071377746260843a46a50997f908e9ceb5babdd03a013ec |
| IAM | sha256:86934ba0d80418a79d64303edb0d74b0e721294d9abd9cd31ef58592899b5213 |
| Admin | sha256:ffdeff992aaac983aafbc20dcf2f1c6ba39825866b26032638849285e1967f8d |

Admin首页HTTP200，实际新创建/登录控制/协议JS资源HTTP200；IAM Discovery实际发布http://iam.macula.local:9010的Issuer与端点；System metadata无token返回401。临时MySQL/Redis已删除；未提交、推送或创建PR，未运行Stage 5源码审阅。SettingsPersistenceIT仅调整测试fixture，对已构建业务代码无影响。

## 页面风格反馈修正验证

按Rain反馈去除接入应用页1320px居中和详情卡片限制，使用显式纵向el-container及标准页头/主体/分页，列表填满主体、详情满宽。截图检查中修正了Fragment内ElHeader未被容器自动识别导致横排的问题。

实际执行：Admin Vitest 15文件134项通过；生产构建通过；目标Cypress应用8/8通过，含1920px满宽、列表主体高度、页头/主体纵向排列与390px无溢出断言；ESLint与diff检查通过。未修改后台业务与数据库。

仅重建macula-cloud-admin，最终容器d18fc7d07ae6为healthy，首页HTTP200；逐项核对其他Compose容器ID均未改变。

## 2026-11-09 创建应用标签与单选项对齐

- 用户截图回归：接入用途、小程序平台标签高于单选项且两组起点不一致。ClientCreate 局部统一桌面 100px 标签与 32px 行高，600px 以下改上下排列；卡片说明文字样式收窄，避免影响其他 span。不改创建模型或认证行为。
- `npm run test:unit -- --run`：退出 0，21 文件 199 项通过（`/private/tmp/create-alignment-unit.log`）；`npm run build -- --mode docker`：退出 0（`/private/tmp/create-alignment-build.log`）。
- 首次带 spec 参数的 npm wrapper 实际运行全部 8 个 spec，56 项断言均通过，但 runner 报 server closed unexpectedly，退出 1，不将该命令记为成功；日志 `/private/tmp/create-alignment-cypress.log`。
- 正确定向 runner 首次 18/19 通过，手机上下排列边界相差约百万分之一像素导致严格浮点断言失败；改为桌面同等的 1px 几何容差。最终 `npx start-server-and-test preview http://localhost:4173/ 'cypress run --e2e --spec cypress/e2e/oauth-client-configuration.cy.js'` 退出 0，19/19 通过（`/private/tmp/create-alignment-cypress-final.log`）。断言同时证明桌面标签与 radio 中心差不超过 1px、两组 radio 左边缘差不超过 1px，手机选项位于标签下方、页脚可见。
- `npx eslint src/views/system/oauth2Client/components/ClientCreate.vue cypress/e2e/oauth-client-configuration.cy.js --quiet` 退出 0，仅检查错误、未自动修复；`git diff --check` 通过。本次纯局部 CSS，不重复后端测试。
- 已实际查看 create-purpose-alignment-desktop.png、create-platform-alignment-desktop.png、create-radio-alignment-390px.png，确认桌面对齐与手机排列。截图位于 Admin cypress/screenshots/oauth-client-configuration.cy.js。
- 沿用本轮用户镜像更新授权，仅构建和重建 Admin，不操作后端 / 业务数据。日志 `/private/tmp/create-alignment-image.log`、`/private/tmp/create-alignment-up.log`。

## 2026-11-09 文档复核后的终端 / 第三方能力约束修正

### 接受依据与实现范围

Rain明确要求“OK，你先仔细研究我之前给你的文档，核对一下。继续优化代码”。本次落实已接受规格的应用架构与登录渠道分离、兼容来源关联及服务端拒绝要求；实际补充清单和偏差已写入 plan.md。逐平台原文、接口、参数、证据缺口见 research.md 新增章节，全部旧官方链接保留。

- 共享应用类型白名单：WEB_SERVER / SPA 仅WEBVIEW Code；NATIVE仅APP Code；MINIAPP对应平台；SERVICE无用户能力。浏览器关联另行判断，公众号不成为NATIVE原生能力。
- System目录声明每个Code的applicationTypes、网页来源的browserApplicationTypes / browserEnvironments。完整快照、空协议草稿及投影保存均执行同一拒绝；旧内部DTO构造保留重载，未知/旧类型不反向推断。
- IAM RegisteredClient从已保存实体携带架构 / 小程序平台；Token换码在claim/厂商调用前校验，不能通过请求自报终端绕过。网页显示、自动直登、显式发起均考虑来源已发布环境；回调继续原state，不按UA换合同。
- 手机普通浏览器不展示微信网站、企微桌面入口；飞书已读官方OAuth / AppLink文档，允许手机浏览器及飞书内OAuth，端内直接续转且错误后不循环。钉钉手机网页登录合同尚未取得完整原文，暂不发布；现有端内Code和原生Code分开。
- 官方飞书页面公布的Markdown证实已有AppLink预留适配所需的固定回调 / S256 PKCE / state / Token参数，故开放FEISHU_APPLINK_V3；不再延用旧抓取失败结论。缺少平台verifier先拒绝，有效证明必须交固定来源适配器；精确HTTP测试验证固定redirect_uri和verifier实际发送。飞书旧JSAPI链不混用此证据，仍不启用。
- Admin按应用架构筛选来源 / Code，协议和指南复用同一校验。历史不兼容关系显示错误待整改，不静默清空。没有新增Schema、SDK依赖、登录Token接口或JSAPI→网页SSO桥接；AppLink是OAuth客户端唤起，不宣称通用原生SDK。

### 本轮执行命令与真实输出

| 执行命令 | 实际结果 | 日志 |
| --- | --- | --- |
| Cloud 根 `mvn test -Plocal` | 最终568总计，567通过、1既有环境门跳过，0失败/错误，退出0 | `/private/tmp/terminal-backend-final.log` |
| Admin `npm run test:unit -- --run` | 22文件、201项通过，退出0 | `/private/tmp/terminal-admin-unit-final.log` |
| Admin `npm run build -- --mode docker` | 成功，保留原Sass/chunk提示，退出0 | `/private/tmp/terminal-admin-build-final.log` |
| Admin `npm run test:e2e:ci` | 8个spec、59项通过，退出0 | `/private/tmp/terminal-cypress.log` |
| 最后接入示例增加同策略检查后，`npx start-server-and-test preview http://localhost:4173/ 'cypress run --e2e --spec cypress/e2e/oauth-client-configuration.cy.js'` | 受影响应用配置22/22再次通过，退出0 | `/private/tmp/terminal-cypress-final.log` |
| `mvn -f /private/tmp/terminal-checkstyle-pom.xml checkstyle:check` | 精确22个本次Java主代码/测试文件；XML解析0错误，退出0 | `/private/tmp/terminal-checkstyle-final.log`、`/private/tmp/terminal-checkstyle.xml` |
| 精确9个前端变更文件的 `npx eslint ... --format json`，无fix | 0错误、620条格式警告，退出0，未自动排版 | `/private/tmp/terminal-eslint-final.json` |
| `git diff --check` | 通过 | 最终工作区直接执行 |

实际输出摘录：
```text
[INFO] BUILD SUCCESS
 Test Files  22 passed (22)
      Tests  201 passed (201)
    ✔  All specs passed!                        01:12       59       59        -        -        -
    ✔  All specs passed!                        00:31       22       22        -        -        -
```

### 关键证明与失败修正

- OAuth2ClientApplicationTypeTest独立字面量覆盖全部Code和应用矩阵、公众号/网站/SDK差异、微信/企微UA优先级、桌面和手机环境；不由被测枚举生成预期。
- System新负向测试证明跨终端Code在写绑定前拒绝，并定位到完整快照最早的来源校验先于协议投影；现已在真实入口执行白名单，而不是只修旧私有校验函数。
- RegisteredClientRepository测试证明可信架构确实进入Token策略；ExternalCodeProvider测试证明即使持久白名单包含Code，架构 / 平台不匹配仍在任何上游调用前拒绝。
- AppLink生产构造路径测试覆盖缺少PKCE先拒绝及有效proof转交；FeishuClient精确断言v3 Token请求中的grant_type / client_id / client_secret / code / 固定redirect_uri / code_verifier和Bearer用户资料调用。HTTP Mock不代表真实飞书端到端验收。
- BrowserLoginClientPolicy测试证明NATIVE / MINIAPP / SERVICE不能借用公众号端内来源；真实过滤器测试证明手机显式企微browser发起不创建state；IamProtocolTest证明手机页面不显示桌面微信 / 企微入口、飞书容器直接OAuth续转、错误不循环。
- 新Vitest验证前端白名单与服务端能力收窄；Cypress新增SPA / NATIVE / MINIAPP三个正反场景，分别可见端内、原生、对应小程序来源。三张截图identity-capabilities-SPA/NATIVE/MINIAPP.png已实际查看，关联列表和页脚可见。其余既有桌面/390px保存与布局回归继续通过。
- 初次编译发现目录Record新增字段影响旧内部测试构造，补兼容重载；未机械修改无关fixture。旧测试中SPA携带小程序Code、无容器UA模拟端内发起均调整为真实场景后重跑。第一次Maven误在Boot根目录选择Cloud模块，立即失败且未执行测试；正式结果均来自明确Cloud工作目录。
- 全后端唯一跳过项仍为现有开关控制的SysUserUsernameUniqueMigrationTest，非本次新增测试。没有数据库结构、匹配事务或绑定数据改动，本次未重复手工隔离迁移 / 绑定IT；全后端中的原有Testcontainers范围按既有套件实际执行。

### 验证边界与交付状态

无真实平台Code、企业凭据、手机设备或实际用户授权操作；原生SDK安装/唤起、微信回调和企业权限仍需独立联调。UA只辅助交互，不能证明身份；应用自身state校验和平台PKCE不由IAM网页PKCE替代。飞书JSAPI旧链、钉钉手机普通浏览器合同缺口明确记录，不将保留来源或可见枚举当作已支持。

本次不更改CLAUDE.md、技能、hooks、REVIEW.md或reviewer配置，无适用配置eval。修改前快照在`/private/tmp/terminal-policy-baseline`，原混合工作区保留。未提交/推送、未更新运行镜像、未操作业务数据库；用户可审阅代码和证据后安排镜像更新。

## 2026-11-10 用户授权更新本轮镜像

Rain明确要求“更新镜像”，授权将已验证的终端兼容 / 第三方登录优化部署到现有本地macula-cloud-dev Compose。更新System、IAM、Admin三个服务，标签均为6.1.0-SNAPSHOT。

- `docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps build macula-cloud-system macula-cloud-iam macula-cloud-admin`：退出0，日志`/private/tmp/terminal-image-build.log`。Dockerfile的跳过测试仅是打包；质量依据为上节实际执行的568后端总计、201单元及59浏览器用例。
- 先对System，再对IAM/Admin分别执行`up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180`，均退出0；日志`/private/tmp/terminal-system-up.log`、`/private/tmp/terminal-iam-admin-up.log`。
- System只读核对V21成功；启动日志`Successfully validated 21 migrations`、`No migration necessary`，没有新增迁移。
- 三个新容器均healthy，运行image ID与构建镜像一致：
  - macula-cloud-admin：`sha256:2796e86500c5c6bbcd66b4383a627563c8e5512812a071c46b955b39645d87d8`。
  - macula-cloud-iam：`sha256:94cc31c0a1a08e0580f445efda3545f692b58af63aa210a09228cb193865663e`。
  - macula-cloud-system：`sha256:ddfe2bbb6a0ed27b7c3258a10c0f9fddf1dff2970aef54cc4270c7c1b57625c9`。
- 其余11个运行容器与更新前ID完全一致，中间件及其他应用未重建。
- HTTP冒烟：Admin首页200、IAM Discovery 200、Admin IAM Discovery反代200、System无凭据菜单接口401（正常认证边界），issuer为http://127.0.0.1:9010。结果保存`/private/tmp/terminal-deploy-result.json`。
- 未提交、推送或修改现有业务配置；运行更新不代表真实厂商/设备授权已验收，相关边界仍按上一节。

## 2026-11-10 飞书端内Code纠正验证

用户指出“飞书端内也是code啊”。此前端内标准OAuth路径并不覆盖JSAPI Code，本次按官方免登示例及v1→v2→v3迁移链接补齐独立FEISHU_WEBVIEW_V3；旧显式v1不自动升级，新增来源默认v3。证据原文/流程图和接口字段已追加research.md及接入文档，临时资料在/private/tmp/terminal-doc-audit。

- FeishuClient WEBVIEW改为固定v3 client_id/client_secret/code换码及Bearer user_info，移除历史app_access_token / v1 Token调用，不失败后回退接口。精确Mock测试确认没有redirect_uri / code_verifier，返回稳定open_id；同一共享应用矩阵仍拒绝原生/小程序误用。
- 应用登录控制可独立关联FEISHU_WEBVIEW_CODE，Cypress验证保存对应固定来源并保持browserLoginProviderTypes为空；正常业务H5直接取Code交Token端点，不建立IAM浏览器SSO。
- 接入指南明确requestAccess(appID,scopeList)、3分钟单次Code、可选state核对及仅“不支持”错误兼容requestAuthCode。身份源页面不再将JSAPI来源显示为浏览器回调来源；默认合同用于显示必要参数，不恢复Flow选择器。
- `mvn test -Plocal`：退出0，568总计（567通过、1既有环境门跳过），0失败/错误；日志/private/tmp/feishu-webview-backend.log。
- 前端全单元201通过（22文件），Docker模式构建成功；最终日志/private/tmp/feishu-webview-unit-final.log、feishu-webview-build-final.log。
- 全8个Cypress spec 60/60通过；来源配置提示最后调整后两个受影响完整spec再次29/29通过，均退出0。日志/private/tmp/feishu-webview-cypress.log、feishu-webview-cypress-final.log。
- Checkstyle精确4个Java主代码/测试文件XML零错误；ESLint无fix零错误；git diff --check通过。日志/private/tmp/feishu-webview-checkstyle.log、feishu-webview-eslint.json。
- 没有Schema、业务数据或凭据变更；没有真实飞书设备、企业权限、Code或用户授权验收，Mock/契约测试不能替代真实联调。修改前本轮快照/private/tmp/feishu-webview-baseline。
- 沿用用户刚才授权的System/IAM/Admin镜像更新范围交付该纠正，构建日志/private/tmp/feishu-webview-image-build.log，重建日志/private/tmp/feishu-webview-system-up.log、feishu-webview-iam-admin-up.log；构建/按序重建均退出0；System、IAM、Admin健康且运行image与新构建一致，其余11个容器ID未变。Admin/IAM Discovery/反代均200，System无凭据边界401。镜像ID见/private/tmp/feishu-webview-deploy-result.json；没有新增数据库迁移。


## 2026-11-10 用户确认双入口模型后的验证

使用sdlc-test核对已接受计划的Proof和规格Verification strategy；本次仅修正入口组合、展示与保存校验，无Schema/真实Client/身份绑定数据迁移，无AGENTS/CLAUDE/skill/hook/REVIEW/评审器配置变更，无本轮适用的配置eval。

| 实际命令 | 实际结果 | 日志 |
| --- | --- | --- |
| Cloud根 `mvn test -Plocal` | 退出0；570总计，569通过、1既有环境门跳过，0失败/错误；`BUILD SUCCESS` | `/private/tmp/dual-login-backend-complete.log` |
| Admin `npm run test:unit -- --run` | 退出0；`Test Files 22 passed (22)` / `Tests 202 passed (202)` | `/private/tmp/dual-login-unit.log` |
| Admin `npm run build -- --mode docker` | 退出0；`built in 6.20s`；既有chunk体积提示 | `/private/tmp/dual-login-build.log` |
| Admin `npm run test:e2e:ci` | 退出0；`All specs passed!`，8个spec、62/62 | `/private/tmp/dual-login-cypress.log` |
| 精确7个本轮Java文件Checkstyle | XML解析0错误 | `/private/tmp/dual-login-checkstyle.log`、`/private/tmp/dual-login-checkstyle.xml` |
| 7个本轮前端文件ESLint（不修复） | 0错误，559条现有风格警告 | `/private/tmp/dual-login-eslint.json` |

Proof对应：API/System测试覆盖网页来源按入口允许、小程序仅托管和双入口、错误平台及SERVICE拒绝；IAM真实Security过滤链测试证明微信UA小程序保留默认登录入口，密码登录后签发授权码并使用S256 PKCE交换access_token/id_token，明确直登仍自动授权；Admin单元与Cypress覆盖仅托管、双入口、关闭托管保留Code/绑定及显式[]策略，既有创建/布局/保存用例继续通过。隔离DB迁移/并发证据沿用前文；本次没有执行真实微信小程序、原生SDK或第三方授权，业务小程序桥接仍待接入方实现与真机验证。

首次新增IAM用例失败原因为fixture把公众号ID6误绑定成网站ID5，改为真实fixture ID后重跑完整Maven成功；再增加实际密码登录/发码/换Token链路，最终完整Maven仍成功。无测试失败被隐藏或当作环境跳过。

本轮未构建或更新运行中镜像，未提交/推送。功能回归结果通过；生产与真机可用性不包含在此结论中。


## 2026-11-10 双入口模型镜像更新（用户“更新我看看”授权）

仅构建并替换macula-cloud-system、macula-cloud-iam、macula-cloud-admin，使用deploy/.env及deploy/docker-compose.yml，build退出0，up使用--no-deps --no-build --force-recreate --wait退出0。未发布远程镜像、未提交/推送、未变更业务配置。

三服务均running/healthy，新容器使用的Image ID与本轮构建镜像一致：System sha256:889fdb9ccc0d、IAM sha256:4b6f55767098、Admin sha256:96be41cdc1b4。近5分钟启动日志均有启动标记、0条ERROR；其余11个容器ID未变。Admin首页、IAM登录页与Discovery均HTTP 200；System /api/v1/menus/routes无Token返回预期HTTP 401。运行证据为/private/tmp/dual-login-image-build.log、dual-login-image-up.log、dual-login-runtime-result.txt。单元/端到端测试沿用上一节已完成结果，本次镜像刷新未重复运行；真实第三方与小程序真机联调仍未执行。


## 2026-11-10 身份源真实应用注册与多能力改版验证

Rain在完整修订计划及钉钉范围展示后回复OK，本轮按已批准计划实施。微信网站/移动/公众号/小程序分别建源；企微、飞书及适用钉钉能力在同一真实注册内共享凭据。新增V22及按能力固定网页登录关联；源码、调用方、页面和说明已同步，未更新运行中业务库/镜像，未提交/推送。实现已完成，待工程师核对实现结果；不自批人工门禁或宣称真实厂商联调。

### 实际执行与输出

| 命令/范围 | 实际输出与结果 | 日志 |
| --- | --- | --- |
| Cloud根 `mvn test -Plocal` | `BUILD SUCCESS`，退出0；577总计，576通过、1既有环境门跳过，0失败/错误 | `/private/tmp/cap-backend-delivery.log` |
| 一次性MySQL，`mvn -pl macula-cloud-system -am verify -Plocal -Dtest=NoSelectedUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IdentitySourceCapabilitiesMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false` | `BUILD SUCCESS`，1测试，0失败/错误，退出0 | `/private/tmp/cap-it-migration.log` |
| 同一隔离库，以上命令改为`-Dit.test=IdentitySourceCapabilitiesConcurrencyIT` | `BUILD SUCCESS`，1测试，0失败/错误，退出0 | `/private/tmp/cap-it-concurrency.log` |
| 同一隔离库和专用Redis，`mvn -pl macula-cloud-iam -am verify -Plocal -Dtest=NoSelectedUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IdentitySourceBindingIT,ProviderApplicationTokenCacheIT -Dfailsafe.failIfNoSpecifiedTests=false` | `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，退出0 | `/private/tmp/cap-it-binding-cache.log` |
| Admin `npm run test:unit -- --run` | `Test Files 22 passed (22)`，`Tests 206 passed (206)`，退出0 | `/private/tmp/cap-admin-unit-complete.log` |
| Admin `npm run build -- --mode docker` | 构建成功，退出0；保留既有chunk体积提示 | `/private/tmp/cap-admin-build-delivery.log` |
| Admin `npm run test:e2e:ci` | `All specs passed!`，8个spec、64/64通过，退出0 | `/private/tmp/cap-cypress-delivery.log` |
| 精确57个本轮Java主代码/测试文件Checkstyle | XML解析0错误；检查器退出0 | `/private/tmp/cap-checkstyle-delivery.log`、`/private/tmp/cap-checkstyle.xml`、`/private/tmp/cap-java-files.txt` |
| 精确10个前端文件ESLint，不自动修复 | 0错误、757条项目风格警告，退出0 | `/private/tmp/cap-eslint-delivery.json` |
| `git diff --check` | 退出0 | 本轮终端实测 |

### Proof对应

- 凭据与能力：共享枚举和System真实保存服务测试覆盖微信注册互斥、企微一次凭据多选、空集合关闭、旧PUT省略保留、跨注册能力及未核实新钉钉SDK拒绝；Client配置服务实际保存微信网站/服务号分别绑定的两个registration，并拒绝把服务号用作网站能力。
- 运行时：IAM真实Security过滤链/MockMvc证明同Client的网站与服务号分别生成正确AppID的授权地址；停用服务号后旧state在厂商换码前拒绝。配置服务证明非旧锚点的企微小程序能力使用同一CorpID/AgentID/Secret和正确flow，撤销后不可继续使用。
- 迁移：专用空库建立V21后升级V22，逐项比较registrationId、AppID、密文、版本、Client及绑定快照；企微旧网页只等价登记原PC/端内能力，旧SDK不增小程序，微信四注册不合并，飞书显式旧V1保留；重复启动迁移执行数为0。
- 并发：真实MyBatis Mapper、IdentityProviderServiceImpl和两个独立事务同时读取同一版本；一个成功、一个HTTP409，最终能力和Secret没有半更新。不复刻CAS SQL代替生产服务。
- 绑定与缓存：隔离MySQL的15个现有绑定测试覆盖实际事务、规范化冲突、SDK账号证明/短信用途隔离、原Client约束；独立Redis的4个缓存测试包含非旧锚点能力成功取Token及loader执行期间撤销后不发布缓存值，原owner隔离/刷新合并继续通过。
- 前端：新增多选共享凭据保存、微信四类型切换清空创建凭据、旧null关联省略/显式多源保留；桌面1440和手机390多选保存及无横向溢出断言。已查看完整桌面和手机截图，能力区域/凭据布局/固定页脚清楚可见。Electron默认原生窗口会裁切大viewport截图，额外使用临时外部Cypress配置把窗口设为1600×1000重跑同spec，16/16通过（`/private/tmp/cap-cypress-visual.log`）；项目cypress.config.js未修改。截图位于Admin cypress/screenshots/admin-identity-security-settings.cy.js/identity-capabilities-1440.png及identity-capabilities-390.png，仅测试产物，不提交。

### 失败修正与证据边界

首次后端回归暴露旧Mock未携带能力标记、旧重载方法和来源级目录标签断言，已更新精确fixture及断言，不放宽身份等值匹配。前端回归发现历史禁用AppLink记录不应强制补新回调，已修正并重建后64/64通过。隔离绑定IT还发现SDK pending共用构造器错误按网页模式推导能力，已改为传递经过核验的identity.loginCapability，最终21个集成测试全部通过。早期集成测试脚本漏写Redis安全标记属于测试资源准备缺失，已补齐；各次实例均在finally清理，没有连接或清理业务库/Redis。

本轮没有修改AGENTS/CLAUDE、skills、AI hooks、REVIEW或评审器配置，无该类适用eval；临时测试浏览器配置未进入仓库。没有真实厂商AppID/Secret、Code或手机设备联调；没有将Mock、Redis或数据库通过等同于真实微信/企微/飞书/钉钉授权验证。源码已实现且上述检查通过；新镜像部署需System先应用V22、IAM与Admin配套更新，当前运行环境仍为上一版。


## 2026-11-10 工程师确认后的正式Stage 4结论

Rain以YES确认实现。使用sdlc-test逐项核对原Proof、本轮能力模型Proof与Verification strategy；根README.md已读取，其内容为模块/启动说明，验证命令以AGENTS.md和testing.md为准。

实际执行的命令、完整输出和失败修正见上一节。本轮再次解析同一实施过程的最终日志与报告，确认57个Java实现/测试文件均未晚于最终后端测试修改，10个前端文件均未晚于最终单测修改；当前代码与这些证据一致，没有用旧版本的绿色结果替代当前实现验证，因此未无故重复运行已经通过的完整测试。

真实输出摘录：

```text
Backend: BUILD SUCCESS
Tests total: 577; Failures: 0; Errors: 0; Skipped: 1
Admin: Test Files 22 passed (22); Tests 206 passed (206)
Cypress: All specs passed! 64/64
Isolated migration: RESULT migration 0
Isolated concurrency: RESULT concurrency 0
Isolated binding/cache: RESULT binding-cache 0
ISOLATED_RESOURCES_REMOVED
Checkstyle XML errors: 0
ESLint errors: 0
```

隔离集成实际21项通过、0跳过；单元测试的1项既有环境门跳过不属于本次迁移/并发/绑定验证缺失。项目AI策略、评审器、技能或Hook配置未被本次修改，无该类适用eval。git diff --check再次退出0。

Verification is green。此结论限于记录中的单元、MockMvc、隔离MySQL/Redis与Mock API浏览器验证；真实厂商/设备验证仍未完成。可以进入后续代码审阅阶段；未自批PR/发布门禁，未更新镜像或业务数据库。


## 2026-11-10 用户授权更新多能力模型镜像

Rain明确要求“更新镜像”并附根README。按本地限定范围构建System、IAM、Admin镜像（6.1.0-SNAPSHOT），build退出0；先对System执行`up -d --no-deps --no-build --force-recreate --wait`，待健康及V22检查通过后，以相同参数更新IAM/Admin。两次up均退出0，其他11个运行中容器ID未变，未发布远程镜像或提交/推送。

更新前确认实际数据库macula-system为V21；相关来源/注册绑定/Client配置及Flyway记录备份至/private/tmp/cap-deployment/pre-v22-config-backup.sql（文件0600、目录0700，不输出内容，不提交）。通过System正常启动的Flyway完成V22，确认3个新列存在。迁移前后比较原来源标识/凭据/版本/映射、Client凭据和旧关联、注册绑定的校验摘要，结果一致。最初检查使用了MySQL容器未设置的MYSQL_DATABASE变量，查询返回未选择数据库，未执行写入；确认实际库名后完成正确备份和核验。

运行镜像与本轮构建一致：System sha256:261d9ccc3bbb、IAM sha256:6f19d6a9532c、Admin sha256:e9c8a65c53ef；三者running/healthy，启动标记存在、近10分钟启动日志0条ERROR。Admin首页、IAM登录页和Discovery均HTTP200，System菜单路由无Token返回预期401。最终再次确认V22及原配置/绑定摘要一致。

证据：/private/tmp/cap-deployment/build.log、up-system.log、up-iam-admin.log、schema-result.txt、runtime-result.json。完整测试结果沿用上文当前实现的正式Stage4证据，本次镜像刷新未重复执行无改动测试；真实第三方/设备授权仍未联调。此前“尚未更新业务库/镜像”是本次用户授权前的历史状态，当前已完成更新。


## 2026-11-10 唯一应用类型默认选中并隐藏

Rain截图要求唯一类型直接默认选择且不显示类型行。沿已接受UI范围修正：新建/切换平台时只对唯一应用类型自动初始化，微信多类型保留选择；编辑已有来源仅补充缺失的唯一类型标识，不调用会清空凭据、组织和绑定策略的类型切换逻辑。

实际验证：Admin `npm run test:unit -- --run`退出0，22文件206测试通过；`npm run build -- --mode docker`退出0；相关admin-identity-security-settings.cy.js在1440/390px运行，16/16通过，唯一类型控件不存在、默认能力已勾选、请求携带正确credentialType/loginCapabilities；截图已视检。3个变更前端文件无fix ESLint为0错误，git diff --check通过。日志/private/tmp/cap-single-unit.log、cap-single-build.log、cap-single-cypress.log、cap-single-eslint.json。原有组件测试增加切换平台、编辑保留凭据/能力/组织/绑定及微信类型选择仍显示的断言，无后端/Schema变更，本轮未重复后端测试。

沿用同会话镜像更新授权，仅构建并重建macula-cloud-admin，up使用--no-deps --no-build --force-recreate --wait。Admin healthy且运行镜像等于本轮构建、首页HTTP200，其他13个容器ID未变。证据/private/tmp/cap-single-image-build.log、cap-single-image-up.log、cap-single-runtime.txt。未提交或推送。


## 2026-11-10 钉钉移动SDK统一应用合同修正

Rain提供官方移动登录文档并指出钉钉移动端同属统一应用。本轮实际读取官方Markdown正文：`https://open.dingtalk.com/document/dingstart/mini-app-procedures-for-authorized-logon-to-ios-applications.md`，并核对userAccessToken、用户信息、网页授权及端内免登文档。移动SDK的appId就是该实际应用的Client ID／AppKey，回调authCode由服务端以同一AppKey／AppSecret调用POST `/v1.0/oauth2/userAccessToken`，使用clientId、clientSecret、code、grantType四字段；用户身份通过个人Token查询contact/users/me。前次未读到HTML正文不能作为禁用已实现能力的理由，已移除DINGTALK_SDK人为不可选限制。

同一钉钉应用现在可同时配置网页授权、移动SDK授权和端内免登，共用一组凭据；不同取码方式仍使用各自核验合同，企业成员准入、注册来源边界不变。增加来源创建、SDK能力选择、实际HTTP请求字段及界面提交回归；同步客户端接入说明和凭据审计。界面提示按平台显示，钉钉不再出现企微小程序提示。

验证结果：根Maven全量577项，0失败、0错误、1项既有跳过；前端22文件207项通过；构建通过；全量Cypress 8规格65/65通过。最后平台提示调整后再次执行207单测、构建及目标Cypress 17/17，均通过。6个Java文件Checkstyle 0错误，相关前端ESLint 0错误，git diff --check通过。证据位于/private/tmp/ding-mobile-full.log、ding-mobile-unit-final.log、ding-mobile-build-final.log、ding-mobile-cypress.log、ding-mobile-cypress-final.log、ding-mobile-checkstyle.xml及ding-mobile-eslint-final.json。未进行真实钉钉设备授权联调。

沿用本会话本地镜像更新授权，构建System、IAM、Admin，使用`up -d --no-deps --no-build --force-recreate --wait`更新这三个服务；均running/healthy，运行镜像等于本轮构建，启动标记存在，近10分钟启动日志0条ERROR。Admin首页、IAM登录页及Discovery HTTP200；System无Token边界预期401；其他11个容器ID不变。数据库仍为V22，无本轮新增迁移。运行证据/private/tmp/ding-mobile-runtime-result.json，镜像构建日志ding-mobile-images.log及ding-mobile-admin-image-final.log。未提交、推送或发布远程镜像。


## 2026-11-10 飞书组织与AppLink回调表单缺陷

用户截图询问两个字段并报告保存错误。只读System近30分钟日志确认17:18:39保存返回400：回调值的前导空格触发URI解析异常；截图另有HTTP，与现有HTTPS校验不符。未输出凭据或更新业务数据。结合既存官方AppLink正文（/private/tmp/terminal-doc-audit/feishu-applink.md）核对业务回调与IAM网页登录回调的区别；本轮web打开官方页面仍无可读正文，未以空壳作为新证据。

本轮仅修正Admin：提交前trim组织/回调，提前校验现有HTTPS及userinfo/fragment边界，补充中英说明；没有放宽后端合同或实施仍为draft的多条绑定增量。新增组件测试覆盖空格、HTTP、非法URI、userinfo和fragment。22文件208单测通过、Docker模式构建通过、目标Cypress17/17通过、4文件ESLint0错误、git diff --check通过。日志/private/tmp/feishu-form-unit.log、feishu-form-build.log、feishu-form-cypress.log及feishu-form-eslint.json。本轮未更新运行镜像、未进行真实飞书授权联调。


## 2026-11-10 回调与多条绑定增量：Stage 3开发检查

Rain在合并计划和两轮Plan审阅后回复YES，已记录计划accepted并实施。此节为开发期间检查证据，未将本增量宣布Stage4 green；历史Stage4绿色仅适用于先前已交付范围。

- 相关后端：`mvn -pl macula-cloud-system,macula-cloud-iam -am test -Plocal -Dtest=IdentityBindingMappingTest,IdentityBindingMappingsTest,IdentityProviderServiceImplTest,OAuth2ClientConfigurationCatalogServiceImplTest,OAuth2ClientConfigServiceImplTest,SettingsModelJsonTest,IdentityProviderConfigServiceTest,IdentityBindingMatcherTest,IdentitySourceBindingPolicyServiceTest,RegistrationIdentityBindingServiceTest,OAuth2ExternalCodeAuthenticationConverterTest,OAuth2ExternalCodeAuthenticationProviderTest,ExternalCodeVerifierRegistryTest,FeishuExternalCodeVerifierTest,FeishuClientTest -Dsurefire.failIfNoSpecifiedTests=false`最终BUILD SUCCESS，128项0失败/0错误/0跳过。后补目录用例及非旧锚点AppLink参数用例，再对对应两类执行同命令目标子集，16项通过。日志/private/tmp/callback-bindings-dev-tests5.log与callback-bindings-dev-final-backend.log。
- Admin：`npm run test:unit -- --run`，22文件211项通过；`npm run build -- --mode docker`通过。最后UI静态修正后已重复相应检查，日志callback-bindings-dev-front-final.log与callback-bindings-dev-build-final.log。
- 相关Cypress：admin-identity-security-settings、identity-provider-policy、oauth-client-configuration三规格51/51通过；最终日志callback-bindings-dev-cypress-final.log。1440/390px多规则截图已视检，字段和按钮无横向溢出，固定操作区可见；测试仅Mock管理API。临时Electron窗口尺寸配置位于/private/tmp，不进入仓库。
- Java本轮变更Checkstyle 0错误，前端12文件无fix ESLint 0错误，git diff --check通过；临时日志callback-bindings-checkstyle-final2.log、callback-bindings-checkstyle.xml、callback-bindings-eslint-final.json。

新增V23隔离迁移与真实Mapper双字段冲突、锁内候选变化用例已编写且测试编译通过，未实际运行。全量根Maven、隔离MySQL/Redis及真实飞书/钉钉设备授权尚未在本增量验证；不能用上述Mock/单测推定已完成这些验证。未连接/修改业务数据库，未更新镜像、远程发布、提交或推送；等待实现确认后进入正式Stage4。


## 2026-11-10 回调与多条绑定增量正式Stage4

Rain在实现及开发检查展示后回复YES，确认进入正式验证；本轮使用sdlc-test，核对已接受的spec.md、applink-callback-spec.md、multi-binding-spec.md及plan.md增量Proof。实施代码存在、两项规格及计划均accepted。下列结果为本轮实际执行，不沿用上一轮绿色声明。

### 实际命令与输出

1. 根目录`mvn test -Plocal`退出0，原始日志/private/tmp/callback-bindings-formal-maven.log：总589项，0失败、0错误、1项因SYSTEM_MIGRATION_IT未设置跳过。随后为该唯一跳过项另建空的本地一次性MySQL库，以SYSTEM_MIGRATION_IT=true及隔离SPRING_DATASOURCE_*运行`mvn -pl macula-cloud-system -am test -Plocal -Dtest=SysUserUsernameUniqueMigrationTest -Dsurefire.failIfNoSpecifiedTests=false`退出0，1项通过、0跳过，验证重复用户名失败关闭、修复后完整迁移至当前版本。日志callback-bindings-formal-it-username-migration.log；没有用跳过代替成功。

2. 在另一一次性MySQL 8.4容器和仅绑定127.0.0.1的测试Redis中执行以下隔离命令，均退出0、0失败/0错误/0跳过：

```text
mvn -pl macula-cloud-system -am verify -Plocal -Dtest=NoSelectedUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IdentityBindingMappingsMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false
mvn -pl macula-cloud-system -am verify -Plocal -Dtest=NoSelectedUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IdentitySourceCapabilitiesConcurrencyIT -Dfailsafe.failIfNoSpecifiedTests=false
mvn -pl macula-cloud-iam -am verify -Plocal -Dtest=NoSelectedUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IdentitySourceBindingIT,ProviderApplicationTokenCacheIT -Dfailsafe.failIfNoSpecifiedTests=false
```

分别为迁移1、CAS并发1、真实Mapper/绑定17及应用Token缓存4，共23项。凭据随机生成只经子进程环境传递，不输出；数据库名前缀严格限定测试命名空间，Redis设置独立runId标记。两个测试MySQL容器均已移除，测试Redis已终止；未连接业务数据库。执行器/private/tmp/callback-bindings-run-isolated.py及callback-bindings-run-username-it.py，日志callback-bindings-formal-it-{migration,concurrency,binding-cache}.log。既有CAS测试前置V22改V23，原因及精确文件已在plan.md记录，未改变并发断言或生产代码。

3. Admin执行`npm run test:unit -- --run`退出0，22文件211项通过；`npm run build -- --mode docker`退出0。`npx start-server-and-test preview http://localhost:4173/ 'cypress run --config-file /private/tmp/cap-cypress.config.cjs'`退出0，8规格68/68通过。日志callback-bindings-formal-{unit,build,cypress}.log；临时浏览器窗口配置不进入仓库。

4. 本轮Java变更及前置适配测试使用仓库Checkstyle规则检查，0错误；Admin12个变更文件无fix ESLint为0错误；git diff --check退出0。日志callback-bindings-formal-checkstyle.log、callback-bindings-checkstyle.xml及callback-bindings-formal-eslint.json。

真实输出摘录：

```text
Backend: BUILD SUCCESS
Reactor Tests run: 589, Failures: 0, Errors: 0, Skipped: 1
Username follow-up: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Migration: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Concurrency: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Binding/cache: Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
RESULT migration 0
RESULT concurrency 0
RESULT binding-cache 0
ISOLATED_RESOURCES_REMOVED
Test Files 22 passed (22)
Tests 211 passed (211)
All specs passed! 68/68
ESLint files 12 errors 0
Checkstyle errors 0
```

### Proof与规格逐项对照

- 共享线格式、未知/重复键与类型拒绝、字段边界及旧字段兼容：IdentityBindingMappingsTest、SettingsModelJsonTest、IdentityProviderServiceImplTest验证真实Jackson请求→严格集合→存储JSON→VO；IdentityProviderConfigServiceTest用相同稳定JSON验证IAM加载、不可变完整集合及所有能力视图。旧请求省略保留、新旧不一致及旧客户端截断多条拒绝、关闭清空、自动绑定空规则拒绝均有对应断言。
- 自动匹配：IdentityBindingMatcherTest覆盖单项命中、同一用户双命中、不同用户冲突、单项多账号及锁内完整重查；既有Policy/Registration测试覆盖缺失、已有绑定优先、禁用账号、跨Client/租户/用户类型、手机号证明限制。实际MySQL IdentitySourceBindingIT17项包含新双字段冲突→修正后唯一绑定及首次候选后第二规则变化→事务拒绝；原范围锁、并发、来源撤销和OIDC绑定链继续通过，未回写或合并用户资料。
- AppLink：converter/provider/verifier/client用例覆盖单值、缺失、超长、重复、HTTP/userinfo/fragment/空白、未登记/前缀地址、其他Client语义及适配器扩参拒绝；带旧来源值但缺新回调仍拒绝。来源锚点WEB的AppLink能力用forCode正确选择，专用回调和PKCE精确发往固定v3 Token端点；网页/JSAPI合同和反重放回归通过。System来源保存、来源目录、Client启用及Admin测试证明无需来源回调，纯external_code应用可登记已有Client白名单、保留原合法授权码回调。
- V23：从空库执行旧迁移，在V22插入旧来源及真实注册绑定/Client来源关联，升级V23只加NULL列；原凭据、版本、映射和关联摘要一致，再启动0迁移。另行补跑的用户名迁移用例也从空库经历旧迁移冲突并修复后执行当前全部迁移。CAS在V23上证明仅一个胜者，失败者不覆盖配置。缓存IT保持来源/版本/能力用途及owner隔离。
- 前端：211单测和68浏览器用例覆盖多规则增删/联动/回显/重复拒绝、旧单项转换、开关及保存失败保留、凭据不丢、AppLink无来源回调、纯Code回调登记/保存及指南运行时参数。1440/390px多规则截图已在实施阶段视检，本轮全量E2E重新产生同名截图。Mock管理API不代表真实厂商授权。

### 配置与边界

本增量没有修改AGENTS/CLAUDE、skills、hooks、REVIEW.md或reviewer定义，无此类适用eval；未虚构eval结果。根README仍为项目概览，具体测试命令以AGENTS和testing.md为准。没有真实飞书/钉钉授权Code、手机设备或平台后台联调；这是已接受的非阻塞环境验收边界，不能把Mock、单元和隔离数据库结果表述为真机成功。

Verification is green。本结论覆盖已接受的本增量Proof及上述实际执行范围；可以进入代码审阅阶段。未自批PR、发布或部署门，未更新业务数据库/运行镜像，未提交或推送。此前Stage3“正式验证尚未执行”是本轮授权前的历史状态，现已完成上述正式验证。


## 2026-11-10 用户授权更新回调与多绑定镜像

Rain明确要求“更新镜像”。核对本地Compose当前三服务及V23脚本后，仅构建System、IAM、Admin三个6.1.0-SNAPSHOT镜像，build退出0。先使用`up -d --no-deps --no-build --force-recreate --wait macula-cloud-system`，健康后确认V23及数据校验，再以相同参数更新IAM和Admin；两次up均退出0。未执行Compose全栈重启、删除卷或远程镜像发布。

升级前当前库macula-system为V22；身份源、注册身份绑定、Client、应用协议配置、Client来源关联及Flyway记录备份至/private/tmp/callback-bindings-deployment/pre-v23-config-backup.sql（目录0700、文件0600，不输出内容、不提交）。使用System正常启动的Flyway完成V23。确认binding_mappings_json列存在、旧行仍为NULL；旧身份源/凭据/能力/版本/映射、Client凭据及来源关联、协议配置、注册用户绑定的摘要前后一致。IAM/Admin更新后再次检查，结果相同。

实际运行镜像：macula-cloud-system sha256:efdf3a81b448、macula-cloud-iam sha256:219685924750、macula-cloud-admin sha256:8588969a27da。三个服务running/healthy、运行镜像与本轮构建相同，启动标记存在且近10分钟启动日志0条ERROR；其他11个容器ID不变。Admin首页、IAM登录页及Discovery均HTTP200；System菜单路由无Token返回预期401。

证据目录/private/tmp/callback-bindings-deployment：build.log、up-system.log、up-iam-admin.log、schema-result.txt、runtime-result.json及私有备份。此次仅部署此前正式Stage4已验证代码，未无改动重复全量测试；真实飞书设备授权仍未联调。未提交或推送；此前“尚未更新镜像/业务库”是本次用户授权前历史状态，当前三服务及V23已更新。


## 2026-11-10 绑定规则单行布局

Rain截图要求“搞成一行吧”。只调整Admin每条规则的展示：源下拉→箭头→目标下拉→移除同排，权限说明放在行下方；不改变规则匹配、校验、请求格式或数据库。原多规则浏览器场景增加1440/390px控件同排与无横向溢出的几何断言，截图已视检。

实际验证：Admin22文件211单测通过，Docker模式build通过；admin-identity-security-settings及identity-provider-policy两规格25/25通过；两个变更文件无fix ESLint0错误，git diff --check通过。日志/private/tmp/inline-bindings-{unit,build,cypress}.log及inline-bindings-eslint.json。后端未变，不重复后端测试。

沿同会话镜像更新授权，仅构建并以--no-deps --no-build --force-recreate --wait重建Admin；healthy、运行镜像等于本轮构建、首页HTTP200，其他13个容器ID未变。镜像sha256:14b3b4383cb7；证据inline-bindings-image-build.log、inline-bindings-image-up.log、inline-bindings-runtime.json。没有业务库写入、镜像远程发布、提交或推送。


## 2026-11-10 应用类型排他边界：Stage 3 开发检查

范围：已接受application-capability-boundary-spec.md及plan.md“2026-11-10 应用类型排他边界增量计划”。Build实现等待人工退出确认；下表是开发检查，不替代Stage4正式验证或真实厂商/设备验收。

| 检查 | 实测结果 | 本机证据 |
| --- | --- | --- |
| 后端25个相关测试类 | 去重187项，0失败/错误/跳过；多批次 | `/private/tmp/app-boundary-development-check2.log`、`app-boundary-service-check.log`、`app-boundary-system-final.log`、`app-boundary-binding-final.log`、`app-boundary-snapshot-final.log`、`app-boundary-version-final.log` |
| Admin单测 | 22文件213项通过 | `/private/tmp/app-boundary-admin-final.log` |
| Admin build | 通过，保留既有大chunk提示 | `/private/tmp/app-boundary-admin-build-final.log` |
| 应用配置Cypress | 33/33通过，覆盖1440/390px、单来源、转换预览及Service无用户目录提交 | `/private/tmp/app-boundary-browser-final.log` |
| Java Checkstyle | 46个本轮文件，XML 0 error | `/private/tmp/app-boundary-static-last.log`、`app-boundary-checkstyle.xml` |
| 无fix ESLint | exit0，0 error、1732 warning，未运行自动排版 | `/private/tmp/app-boundary-eslint-final.log` |
| git diff --check | 本轮文件通过 | 以本轮baseline manifest精确文件集合执行；未清理混合工作区 |
| 新转换IT | 已编译，未执行 | `ApplicationLoginBoundaryConversionIT`，仅允许本机全新`macula_identity_binding_it_*`库及显式环境标记 |

主要命令：

```bash
mvn -pl macula-cloud-iam,macula-cloud-system -am test -Plocal '-Dtest=InApp*Test,OAuth2ApplicationLoginPolicyTest,OAuth2ClientApplicationTypeTest,ApplicationProtocolRegistryTest,OAuth2ClientConfigServiceImplTest,SettingsModelJsonTest,OAuth2ExternalCodeAuthenticationProviderTest,HostedLoginFlowTest,HostedLoginPolicyFilterTest,BrowserLoginClientPolicyServiceTest,ThirdPartyAuthenticationFlowTest,HttpSessionThirdPartyLoginStateRepositoryTest,MfaCompletionServiceTest,ThirdPartyBindingControllerTest,RegistrationIdentityBindingServiceTest,MaculaRegisteredClientRepositoryTest,ProtocolAwareRegisteredClientRepositoryTest,OAuth2ClientProtocolPolicyTest' -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl macula-cloud-iam -am test -Plocal '-Dtest=RegistrationIdentityBindingServiceTest,ThirdPartyBindingServiceTest,ThirdPartyAuthenticationFlowTest,IdentitySourceBindingPolicyServiceTest,ThirdPartyIdentityResolutionServiceTest,InAppLoginFlowTest,IamProtocolTest' -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl macula-cloud-iam -am test -Plocal -Dtest=SysOAuth2ClientServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl macula-cloud-system -am test -Plocal -Dtest=OAuth2ClientConfigServiceImplTest,SettingsModelJsonTest -Dsurefire.failIfNoSpecifiedTests=false
npm run test:unit -- --run
npm run build
npx start-server-and-test preview http://localhost:4173/ 'cypress run --config-file /private/tmp/cap-cypress.config.cjs --spec cypress/e2e/oauth-client-configuration.cy.js'
```

本轮基线及逐文件首次写入备份：`/private/tmp/app-boundary-build-baseline/manifest.json`。Checkstyle临时POM只读取项目规则并枚举本轮Java文件；未修改REVIEW/AGENTS/技能、格式化规则或门禁。前端截图中已视检端内能力单选弹窗；桌面/窄屏布局与溢出断言由Cypress执行。

### 失败与修复

- 旧测试预期仍允许Web/SPA端内Code、Native/Miniapp第三方网页，以及按UA自动直登：按已接受新矩阵调整素材，补独立来源、数量、类型、平台拒绝测试。实际新保存测试仍拒绝未知/失效来源，不通过修改规则迁就旧预期。
- 直接以SAS授权过滤器作addFilterBefore锚点在configure之前无法启动；改为SecurityContextHolderFilter之后，实际链测试断言仍早于OAuth2AuthorizationEndpointFilter。普通SSO请求结束及异常路径均保留原Session上下文对象。
- 新真实授权码→Token检查触发SS7的`authenticationTime cannot be null`：第三方成功认证工厂记录标准AUTHORIZATION_CODE认证时间，绑定后保留同一来源证明与认证记录；没有声明其他MFA因子或放松MFA完成检查。正常Token、旧Secret不可认证、Client及来源撤销后的旧Code拒绝均通过。
- 绑定需在Client锁内再次比较原端内发起版本，自动绑定与手动pending均透传；关联表使用FOR UPDATE current read，避免RR旧快照。对应旧版本测试在任何后续来源/用户写入前拒绝。
- Service不应要求用户目录：利用既有可空列，只有SERVICE免用户目录，其他用户应用仍强制目录/租户；机器Token真实链与保存测试通过，不增加默认员工目录。

### 尚未执行的正式Proof

- 根`mvn test -Plocal`、Admin全Cypress及适用隔离MySQL/Redis回归，待Build人工退出确认后进入Stage4。
- 新`ApplicationLoginBoundaryConversionIT`使用真实Mapper/Service/事务验证并发转换、CAS、回滚、ID/密钥和用户绑定保留，尚未执行，不能记为数据库已验证。
- 真实第三方Code、设备、授权提示和业务小程序回传桥接未联调；Cypress为Mock管理API，SAS安全链使用Mock厂商/账号边界，不宣称第三方端到端成功。
- 未更新镜像或业务库，未修改Schema/历史迁移，未提交、推送或发布。


## 2026-11-10 应用类型排他边界：正式Stage4

Rain回复“YES，更新镜像”，明确确认实现并授权当前本地System/IAM/Admin镜像刷新。本轮使用sdlc-test，对已接受的应用类型增量规格、计划Proof与实际实现执行正式验证；未自批PR或合并。

| 实际执行命令/范围 | 实测输出摘要 | 证据 |
| --- | --- | --- |
| 根`mvn test -Plocal`，测试断言修正后完整重跑 | `BUILD SUCCESS`；611项、0失败/错误、1环境限定跳过 | `/private/tmp/app-boundary-formal-maven-final.log` |
| 隔离补跑SysUserUsernameUniqueMigrationTest | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` | `app-boundary-formal-it-username.log` |
| ApplicationLoginBoundaryConversionIT | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` | `app-boundary-formal-it-conversion.log` |
| IdentityBindingMappingsMigrationIT | 1通过，0跳过 | `app-boundary-formal-it-migration.log` |
| IdentitySourceCapabilitiesConcurrencyIT | 1通过，0跳过 | `app-boundary-formal-it-concurrency.log` |
| IdentitySourceBindingIT + ProviderApplicationTokenCacheIT | 21通过，0失败/错误/跳过 | `app-boundary-formal-it-binding-cache.log` |
| Admin `npm run test:unit -- --run` | `Test Files 22 passed (22)`；`Tests 213 passed (213)` | `app-boundary-formal-unit.log` |
| Admin `npm run build -- --mode docker` | 构建成功，退出0，保留既有chunk提示 | `app-boundary-formal-build.log` |
| 全Cypress，临时Electron窗口配置 | `All specs passed!`，8规格75/75 | `app-boundary-formal-cypress.log` |
| Checkstyle / 无fix ESLint / diff --check | 46个Java文件0错误；ESLint0错误（保留排版warning）；空白检查通过 | `app-boundary-formal-checkstyle.log`、`app-boundary-formal-eslint.log` |

上表短日志名均位于`/private/tmp/`。原始执行器`app-boundary-isolated-executor.py`以随机凭据建立一次性MySQL 8.4容器，仅绑定127.0.0.1，并启动独立Redis/独立runId标记。转换及用户名补跑分别使用全新测试库；其他绑定/迁移用例使用限定测试命名空间。所有阶段输出`RESULT ... 0`，最终`ISOLATED_RESOURCES_REMOVED`；没有连接业务数据库运行测试。

首跑新转换IT因复制的旧素材验证`cache.clear()`失败，实际生产调用是立即`cache.invalidate()`；仅将两处断言修正为invalidate，仍断言仅获胜提交触发一次、冲突和回滚不额外触发。失败原始日志保留在`app-boundary-formal-it-conversion-first-failure.log`。之后转换IT和全量后端均重跑通过，没有把首轮失败隐藏为成功。

### Proof逐项对应

- 六类架构与来源注册数量、微信双Code同源、端内恰一个能力及Web/SPA拒绝JSAPI：共享矩阵、System保存、IAM直接grant和Admin单测/浏览器用例均覆盖；完整后端回归拒绝路径继续通过。
- 转换：新隔离IT实际执行完整Flyway至V23、真实Mapper/Service/事务，两个相同CAS快照仅一个转换成功，另一409；ID、原密钥哈希和用户绑定保留，授权版本递增；强制回滚不改变已提交快照或额外驱逐缓存。JSON确认、托管身份/跨路由及非法转换仍由单测拒绝；1440/390px预览确认和请求正文由Cypress验证。
- 托管入口与端内SSO：真实SAS链覆盖无会话/普通会话、固定平台续转、错误容器/错误页、内置密码/发码拒绝、上下文顺序及共享Session不被改写；平台证明正常授权码换Token成功，非法回调、同意提交改版、Client/来源撤销后的Code及旧密钥认证拒绝。
- 账号绑定与MFA：统一来源、目录准入、多字段绑定、绑定Session证明/认证时间保留及SDK不建SSO回归；原Client版本透传到绑定锁内，旧pending在来源/用户写入之前拒绝。相关Mock与真实绑定Mapper并发用例均通过，未改变MFA策略本身。
- 固定来源快照与缓存：当前协议/来源映射、重复映射失败关闭及Native历史网页引用不关闭内置入口单测通过；17项数据库绑定及4项独立Redis应用Token缓存用例通过。Service无用户目录只发机器Token，其余用户应用仍保留租户/用户目录约束。
- 管理端：全部213单测及75浏览器用例重新运行，含单SDK替换确认、小程序双入口、端内单选和转换预览、Service隐藏用户目录与不适用配置、单行多规则绑定回归；浏览器管理API为Mock，不代表第三方真实授权。

未修改CLAUDE/AGENTS、skills、hooks、REVIEW或reviewer定义，无此类适用eval；未虚构配置eval结果。真实厂商Code、手机设备和业务小程序桥接仍属已接受的环境验收边界，不能将Mock/SAS/隔离数据库通过视为真机成功。

Verification is green。已满足本轮本地验证Proof，按Rain同条指令继续本地镜像更新；本结论不自批PR/合并。此前Stage3“尚未正式验证”是历史状态，本节为最新正式结果。


## 2026-11-10 用户授权更新应用类型边界镜像

Rain“YES，更新镜像”确认实现并明确授权本地更新。正式Stage4完成后执行：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps build macula-cloud-system macula-cloud-iam macula-cloud-admin
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 macula-cloud-system
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 macula-cloud-iam macula-cloud-admin
```

三个命令均退出0。System先恢复健康，然后更新IAM/Admin；未重建其余11个Compose容器，没有删除卷、远程推送镜像或提交/推送代码。

| 服务 | 实际运行镜像 | 状态 |
| --- | --- | --- |
| macula-cloud-system | `sha256:c11db4509c321cbdaf31fd3003f965596cc736ee91958f8d2c9c950b7d1f082d` | running/healthy |
| macula-cloud-iam | `sha256:2a1ee22b430d7b606566a35284e81dee2d3a78243a40fad65fcf52211a91eb72` | running/healthy |
| macula-cloud-admin | `sha256:19f921b7778de53e1b0e2319df562ed850e6f2f3576ddff28b02a022ebe42714` | running/healthy |

标签均6.1.0-SNAPSHOT，实际运行Image ID与本轮构建一致；三服务均有启动标记，近10分钟启动日志ERROR计数均0。Admin首页、IAM登录页和Discovery均HTTP200；System菜单路由无Token为预期401。未以HTTP200冒充真实第三方登录成功。

数据库仍V23，无新Schema迁移。部署前、System重启后、三服务全部重启后的身份源、Client（含类型/版本/凭据摘要）、用户绑定、协议配置及关联表指纹完全一致；没有自动转换历史Client或旋转凭据。

证据目录`/private/tmp/app-boundary-deployment/`：build.log、up-system.log、up-iam-admin.log、runtime-result.json、containers-before.json、snapshot-before.json、snapshot-after-system.json、snapshot-after.json。此前本轮“未更新镜像”是授权前历史状态，当前已部署上述新镜像。真实厂商及设备联调仍未执行。
