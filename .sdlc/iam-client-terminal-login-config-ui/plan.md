# Plan: IAM Client 按终端配置登录方式 (from spec.md 2026-10-22)

## Files that change
- `.sdlc/iam-client-terminal-login-config-ui/intent.md`、`spec.md`、`plan.md`、`review-findings.md`：记录用户已确认的
  终端优先交互、实现顺序、风险和 Plan 评审。
- `macula-cloud-system/src/main/resources/db/migration/V14__oauth_client_terminal_type.sql`：为 OAuth Client 新增 nullable
  `terminal_type`，不猜测、不回填已有 Client 的终端归属。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientTerminalType.java`：定义五种稳定终端值及
  provider/code type allowlist。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`、`vo/OAuth2ClientVO.java`、
  `pojo/entity/SysOAuth2ClientConfig.java`：在写入、展示和持久化模型中增加 `terminalType`。
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`：保存时拒绝
  缺失终端、跨终端 provider/code type 和与登录能力不一致的派生 grant。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`、
  `integration/ExternalCredentialLoginMigrationIT.java`：覆盖单终端校验和 V14 不回填升级。
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`：集中定义终端与中文登录方式目录，以及业务方式到现有
  网页 provider/external code type 的稳定映射；REST 端点不变，但请求/响应合同新增必填 `terminalType`。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：把当前平铺的网页登录和 code type 配置重构为终端
  选择器、当前终端登录方式卡片、按需身份源选择和折叠高级配置；提交和回显同步读写 `terminalType`。
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`：覆盖目录、双向映射、自动 grant、身份源状态、
  direct 联动、高级值保留和中文摘要。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`：覆盖单终端切换清理、自动身份源绑定、
  `terminalType` 保存请求和编辑回显；同步更新所有 OAuth Client API mock/fixture。
- `macula-cloud-system/README.md`、`macula-cloud-iam/README.md`：说明一个 Client 只归属一个终端，多终端需拆分
  Client；`terminalType` 不进入 Token 请求。

## Order of work
1. 增加 V12 `terminal_type` nullable 列和 System 终端枚举，扩展 Form/VO/Entity。迁移只加列，不从现有字段
   猜测终端；新建和更新保存必须提交合法终端。
2. 在 System Service 按终端校验 `browserLoginProviderTypes`、`externalCodeTypes` 及其绑定：浏览器只允许网页
   provider，APP/小程序/端内网页只允许各自目录；`authorization_code` / `external_code` 必须与当前终端所选
   登录方式一致。非法组合在写库前明确拒绝。
3. 在 Admin API 模型增加单终端归一化函数。切换终端先清除所有终端派生的 provider、code type、绑定、direct
   provider 和两种派生 grant，再由用户勾选当前终端方式；保留 refresh、scope、Origin、TTL 等非终端字段。
4. 将登录能力区维持为单选终端选择器和方式卡片。external code 唯一身份源自动绑定、零个时禁用、多个时显示该
   方式自己的身份源选择；网页登录只在 provider 恰好有一个全局启用身份源时可选。企微/微信虽复用底层
   provider 字段，但只在持久化 `terminalType` 对应的浏览器或端内网页中显示和生效，不镜像另一终端状态。
   网页登录 direct 选择只在当前终端存在候选时出现。
5. 将原始协议参数保留在高级配置折叠区。IAM 统一登录或网页闭环方式选中时在常用
   区域展示并校验重定向 URI；保存前继续执行现有 PKCE、身份源绑定和 Origin 规范化检查，并确保折叠状态不
   影响数据提交。遇到服务端已不支持的未知值时阻止保存并提示迁移。
6. 列表摘要以持久化终端为主；旧记录没有终端时显示待选择，不把跨终端字段自动解释成合法配置。
7. 更新 System/IAM README、System 单元/迁移测试、Admin Vitest/Cypress。Build 阶段运行目标测试和静态检查，
   完整根 Maven、Admin 构建/E2E、隔离迁移及 Docker 验证留给 Stage 4。

## Risks
- 新增终端字段会改变 System 保存契约。数据库列保持 nullable 以允许旧服务先运行，但新 System 对写入强制非空；
  发布必须先升级数据库/System/Admin，旧记录在管理员选择终端前只能读取，不能无损保存跨终端组合。
- `/system/api/v1/oauth2-clients` 路径和 HTTP 方法不变，但请求/响应 JSON 新增 `terminalType`；除当前 Admin 外未发现
  其他仓库内写调用方，仍须同步全部 Admin mock/fixture，并在发布说明中提示外部管理 API 调用方升级。
- 企业微信/微信在浏览器和端内网页复用 provider 字段；必须依赖持久化终端区分归属。若只清前端状态而不保存
  终端，重开后会再次产生镜像勾选。
- 切换终端会主动删除原终端登录能力，属于用户明确选择后的可见破坏性变更；界面应立即反映清除结果，保存前
  不自动恢复。
- 自动绑定只适用于 external code 且候选恰好为一个；网页登录必须继续满足全局唯一启用身份源，任何场景在零个
  或多个候选时静默选取都会造成错误凭据或跨应用身份风险。
- 切换终端必须清除全部旧终端派生字段；同终端内取消单个方式时仍只删除该方式及绑定。
- 页面当前包含大量协议字段，重排 DOM 可能破坏既有测试选择器和表单校验；测试应以稳定文本、角色和请求体
  断言用户可观察行为。

## Proof
- `OAuth2ClientConfigServiceImplTest` 证明五类终端合法组合允许，缺失终端、跨终端 provider/code type、错误绑定
  和不一致派生 grant 在写库前拒绝；`ExternalCredentialLoginMigrationIT` 证明 V14 只加列且不改原登录字段。
- `admin-identity-security-settings.test.js` 证明五类终端只显示各自方式，切换后旧终端 provider/code type/绑定/
  direct/grant 全部清除，微信小程序两种 code 不混淆，端内企微/公众号由 `terminalType` 区分归属。
- 同一测试证明 external code 一个身份源自动绑定、多个身份源要求显式选择、无身份源不可选，网页登录要求
  全局唯一身份源，以及 direct provider 只来自已选网页方式。
- 旧记录用例载入缺少终端或跨终端试验值的 Client，列表显示待选择；选择终端后只保留该终端合法值并提交唯一
  `terminalType`，不保留历史 browser code。
- 授权码方式用例覆盖空重定向 URI 的前端拒绝和已有 URI 无损回显。
- Cypress 从编辑 Client 开始，从浏览器切到 APP 再切到微信小程序，每次断言前一终端无勾选；保存请求只包含
  最终终端字段及其登录能力，重新打开定位到同一终端。
- Stage 4 执行 System 目标测试、根 Maven、隔离迁移、完整 Vitest/build/Cypress、Docker System/Admin 更新和
  `git diff --check`；不声称完成真实第三方平台联调，因为 Token 请求协议未改变。

## Implementation notes and deviations
- 2026-10-29，Rain 以“之前那个也OK”明确接受 single-terminal revision 的 spec 与本计划，Stage 3 进入实现阶段。
- 本节及其后的旧 Verification/Code review 记录属于“终端仅作导航”的历史实现证据，已被 2026-10-29
  single-terminal revision 取代，不再描述当前实现目标。新修订将新增 System 字段和数据库列，并在实现后追加
  新的实施及验证记录。
- 历史实现复用了 System external-code catalog，未新增后端 API、数据库字段或依赖；该决策因无法持久化唯一终端
  归属而废止。
- 浏览器的本地账号方式与 APP 的 IAM 统一登录共同映射 `authorization_code`；新修订通过 `terminalType` 区分
  归属，不再让企业微信、微信在浏览器和端内网页之间镜像同步。
- browser external code 已在后续人工反馈中删除；新修订不恢复该兼容能力。
- 唯一 external code 身份源已自动绑定；多个候选显示选择器；网页登录继续要求同 provider 恰好一个启用身份源。
- 项目根目录不存在 `CLAUDE.md`，因此实现继续遵循根 `AGENTS.md` 及按需加载的前端、测试规则，没有替代或生成
  项目记忆文件。
- Stage 3 已执行目标 Vitest（5 项通过）、变更文件 ESLint 和 `git diff --check`。完整 Admin 构建和 Cypress 按
  计划保留给 Stage 4，当前结果不代表测试 gate 已通过。

## Verification evidence
- `npm run test:unit`：9 个测试文件、47 项测试全部通过。
- `npm run build`：Vite 生产构建成功，共转换 2467 个模块；现有 Sass `@import` 废弃提示和大 chunk 提示不影响
  构建结果。
- `npm run test:e2e:ci`：4 个 Cypress spec、26 项测试全部通过，其中接入应用配置用例 16 项通过。
- 变更文件 `npx eslint` 与仓库 `git diff --check` 均通过；`dist/` 未进入工作区改动。
- 本次仅调整管理端配置表达与 README，不替代真实第三方平台联调，也未执行 Docker 更新、提交、推送或部署。

## Code review remediation
- 针对 Stage 5 Round 1 Important，已抽取并复用 external provider binding 重整逻辑：编辑表单打开时和 catalog
  加载完成时都会按当前候选更新绑定；唯一候选替换失效 ID，多候选只保留仍有效的显式选择，零候选清除失效
  绑定，未知历史类型继续保留并由保存校验阻止提交。
- 登录方式增删、grant 联动、direct provider 清理、终端反向识别、未知类型识别和列表摘要已收敛为可直接测试的
  纯函数；企业微信与微信摘要明确显示“网页（浏览器/端内）”。
- README 普通浏览器默认接入已统一为 `/oauth2/authorize`；browser code 类型仅作为业务前端自行取 code 的高级
  兼容接入说明。
- 新增纯逻辑单测及 Cypress 编辑回归、共享开关、零/多候选、失效绑定、回调拒绝、未知类型拒绝、兼容值保留、
  保存后重开和摘要断言。Stage 3 目标 Vitest 共 8 项通过，变更文件 ESLint 与 `git diff --check` 通过；完整
  Vitest、构建和 Cypress 需回到 Stage 4 重新验证，之前的 Stage 4 结果已因代码变化失效。

## Remediation verification evidence
- `npm run test:unit`：9 个测试文件、50 项测试全部通过。
- `npm run build`：Vite 生产构建成功，共转换 2467 个模块；仅保留既有 Sass `@import` 废弃提示和大 chunk 提示。
- Cypress 首轮发现新增测试的 Element Plus 选择器误命中复选框，业务代码未变；限定到精确表单标签及已选项后，
  目标 spec 18 项通过，并再次执行 `npm run test:e2e:ci`，最终 4 个 spec、28 项全部通过。
- 变更文件 ESLint 与仓库 `git diff --check` 均通过；`dist/`、Cypress screenshots 未进入工作区改动。
- 未修改 `CLAUDE.md`、skill、hook、`REVIEW.md` 或 reviewer 定义，因此没有适用的配置 eval；仍未执行真实第三方
  平台联调、Docker 更新、提交、推送或部署。

## Code review Round 2 remediation
- 为终端自动推断增加用户手动切换保护：catalog 延迟返回时会对仍处于自动选择状态的已打开表单重新识别
  APP/端内网页；用户已经手动选择终端时不覆盖。新增延迟 catalog 的 Cypress 编辑回归。
- 新增已选 browser provider 唯一身份源检查；零候选或多候选仍允许管理员取消该方式，但保留选择时会在保存前
  显示对应平台名称并阻止请求。新增纯函数单测以及零候选、多候选的 Cypress 拒绝用例。
- Stage 3 目标 Vitest 共 9 项通过，变更文件 ESLint 与 `git diff --check` 通过；Round 2 修复后的完整 Vitest、构建
  和 Cypress 尚需回到 Stage 4 重新验证。

## Round 2 remediation verification evidence
- `npm run test:unit`：9 个测试文件、51 项测试全部通过。
- `npm run build`：Vite 生产构建成功，共转换 2467 个模块；仅有既有 Sass `@import` 废弃提示和大 chunk 提示。
- 目标 OAuth Client Cypress spec：20 项全部通过，包含延迟 catalog 重定位和网页登录身份源零/多候选保存拦截。
- 完整 Cypress 首次运行曾无输出挂起并由人工终止；第二次完整运行中无关 TinyID loading-mask 时序断言偶发失败，
  单独复跑 TinyID 5 项全部通过。随后再次执行 `npm run test:e2e:ci`，最终 4 个 spec、30 项全部通过。
- 变更文件 ESLint 与仓库 `git diff --check` 均通过；`dist/`、Cypress screenshots 未进入工作区改动。
- 未修改受保护 SDLC 配置，无适用 eval；未执行真实第三方平台联调、Docker 更新、提交、推送或部署。

## Code review Round 3 remediation
- 延迟 catalog 回归在验证自动重定位后重新载入页面，在响应返回前手动切换到微信小程序，并断言响应完成后仍保留
  用户选择、不重新显示 APP 登录方式。
- 网页登录身份源零候选和多候选用例在证明保存被阻止后，分别取消失效 Provider 并成功 PUT；请求体必须同时
  移除 `browserLoginProviderTypes` 与 `directLoginProviderType`。
- 本轮只修改 Cypress 测试，Prettier、目标文件 ESLint 与 `git diff --check` 通过；完整 Cypress 和项目验证需回到
  Stage 4 执行。Stage 5 已达三轮上限，验证完成后不再启动第 4 轮自动复审，由人工依据证据评估。

## Round 3 remediation verification evidence
- `npm run test:unit`：9 个测试文件、51 项测试全部通过。
- `npm run build`：Vite 生产构建成功，共转换 2467 个模块，用时 4.84 秒；仅有既有 Sass `@import` 废弃提示和
  大 chunk 提示。
- 目标 OAuth Client Cypress spec：20 项全部通过；最终完整 `npm run test:e2e:ci` 为 4 个 spec、30 项全部通过，
  总用时 26 秒。中间暴露的同页二次访问 alias 计数、抽屉遮挡和 alias 复用均属于测试夹具问题，修正后目标与
  全量回归均稳定通过；早先出现的无关 TinyID loading-mask 时序波动也在单独及最终全量复跑中通过。
- 变更文件 ESLint 与仓库 `git diff --check` 均通过；`dist/`、Cypress screenshots 未进入工作区改动。
- 未修改 `CLAUDE.md`、`REVIEW.md`、`.claude/`、`.codex/` 或 `.agents/`，无适用配置 eval；未执行真实第三方
  平台联调、Docker 更新、提交、推送或部署。
- Stage 5 已达到三轮自动评审上限，未启动 Round 4；本次变更交由人工依据上述实现、评审记录与验证证据确认。

## 2026-11-05 恢复已接受的单终端实现
- 用户明确纠正“终端类型切换是要清空的”，恢复本计划已接受的单终端持久化和切换清理，不再沿用终端只作导航的历史实现。
- V12 已被 application_auth_tenant 使用，V13 为 application_apikey，因此终端列迁移顺延为 V14；只加 nullable 列，不推断历史数据。
- 新建 client_id 自动生成 UUID 的要求在当前 grant-selection 页面/helper 的已接受文件范围内实现；保存后的 ID 保持不可修改。
- “编辑状态不能再改终端类型”存在希望锁定或希望允许的语义歧义，已询问用户；该具体编辑限制待回复，不自行选择。

## 2026-11-05 单终端恢复实施与验证
- 已增加 V14 nullable terminal_type、System Entity/Form/VO 字段与跨终端 provider/code type 校验；旧记录不回填。保存/回显使用明确终端，不从 catalog 或登录能力猜测。
- 新建切换终端清空原终端 provider、code type、绑定、直登、回调 URI 以及 authorization_code/external_code 派生授权；scope、Origin、refresh、TTL 保留。切回旧终端不会恢复旧勾选。
- Client ID 新建自动生成 UUID，可按接入约定修改；保存后沿用服务端既有不可变约束。历史 terminalType 缺失/null 均显示待选择，必须明确选择后保存。
- 验证：完整 Admin Vitest 14文件133项通过；两组 Cypress 27/27通过；System目标22项通过，完整System单测141项（140通过、1项环境门控跳过）；隔离MySQL8.4.6的 ExternalCredentialLoginMigrationIT 1/1通过，覆盖V14列不回填及历史Client字段保持；定向Checkstyle 0违规，生产构建、ESLint及diff检查通过。
- 临时MySQL容器已清理，没有修改业务数据库或部署新版System/Admin。本次尚待用户澄清“编辑状态不能再改终端类型”是希望锁定还是希望允许编辑切换，该分支不自行决定。

2026-11-06：后续应用模型重构由已接受的 iam-application-configuration/plan.md 承接；保留UUID、grant复选框与密钥生命周期，旧终端模型被应用架构类型替代，历史证据保持原样。
