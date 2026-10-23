# Plan: IAM Client 按终端配置登录方式 (from spec.md 2026-10-22)

## Files that change
- `.sdlc/iam-client-terminal-login-config-ui/intent.md`、`spec.md`、`plan.md`、`review-findings.md`：记录用户已确认的
  终端优先交互、实现顺序、风险和 Plan 评审。
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`：集中定义终端与中文登录方式目录，以及业务方式到现有
  网页 provider/external code type 的稳定映射；不新增 API。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：把当前平铺的网页登录和 code type 配置重构为终端
  选择器、当前终端登录方式卡片、按需身份源选择和折叠高级配置；保留现有表单字段及提交合同。
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`：覆盖目录、双向映射、自动 grant、身份源状态、
  direct 联动、高级值保留和中文摘要。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`：覆盖跨终端选择、自动身份源绑定、保存请求和
  编辑回显的管理员关键流程。
- `macula-cloud-iam/README.md`：把管理员配置说明改为“先选终端、再选登录方式”，说明 `code_type` 由系统映射但
  Token 协议仍携带该参数。

## Order of work
1. 在 API 模型中建立 UI 专用的终端和登录方式目录。每个业务方式明确映射 `browserProviderType`、
   `externalCodeType` 或 authorization-code 能力；标签使用中文，底层稳定值不改变。
2. 在 OAuth Client 页面增加当前终端状态与派生选择逻辑。从服务端现有字段反向识别业务方式；切换终端不修改
   表单，只有勾选或取消登录方式时才更新底层字段。
3. 将登录能力区改为终端选择器和方式卡片。external code 唯一身份源自动绑定、零个时禁用、多个时显示该
   方式自己的身份源选择；网页登录只在 provider 恰好有一个全局启用身份源时可选。企微/微信的浏览器与端内
   网页显示为同步生效的共享网页能力。网页登录 direct 选择只在存在候选时出现。
4. 将原始协议参数和仍受支持的兼容 code type 放入高级配置折叠区。IAM 统一登录或网页闭环方式选中时在常用
   区域展示并校验重定向 URI；保存前继续执行现有 PKCE、身份源绑定和 Origin 规范化检查，并确保折叠状态不
   影响数据提交。遇到服务端已不支持的未知值时阻止保存并提示迁移。
5. 将列表中的原始枚举串替换为终端化中文摘要；不能映射的历史值单独标记为高级兼容配置。
6. 更新 Vitest、Cypress 和 IAM README。实现阶段只运行相关 Vitest 与静态差异检查；完整构建和 Cypress 留给
   Stage 4，不提交、推送或更新 Docker。

## Risks
- 同一 provider 在浏览器和端内网页可能共用 `browserLoginProviderTypes`；UI 必须把终端当作配置导航而非新的
  服务端权限维度，企微/微信两处必须同步更新，不能宣称现有模型能分别禁止这两个容器。
- APP 的 IAM 统一登录和普通浏览器都使用 authorization code；终端选择不持久化，编辑时只能从已选业务能力
  派生建议入口，不能作为运行时事实。
- 现有 Client 可能包含 `FEISHU_BROWSER_CODE` 等仍受支持的高级 browser external code；默认界面可以隐藏，
  但保存必须原样保留，且列表要提示存在兼容配置。真正不受当前枚举支持的值必须阻止保存并提示迁移。
- 自动绑定只适用于 external code 且候选恰好为一个；网页登录必须继续满足全局唯一启用身份源，任何场景在零个
  或多个候选时静默选取都会造成错误凭据或跨应用身份风险。
- 取消某个方式时必须只删除该方式映射的底层值和绑定，不能清除同平台其他终端或网页登录配置。
- 页面当前包含大量协议字段，重排 DOM 可能破坏既有测试选择器和表单校验；测试应以稳定文本、角色和请求体
  断言用户可观察行为。

## Proof
- `admin-identity-security-settings.test.js` 证明五类终端只显示各自方式，选择与取消精确映射现有字段，微信小程序
  两种 code 不混淆，端内企微/公众号走 browser provider，企微/微信网页能力跨终端同步，external code grant
  随选择联动。
- 同一测试证明 external code 一个身份源自动绑定、多个身份源要求显式选择、无身份源不可选，网页登录要求
  全局唯一身份源，以及 direct provider 只来自已选网页方式。
- 兼容性用例载入包含受支持 browser external code 和高级 OAuth2 参数的旧 Client，关闭高级区直接保存，断言
  请求体完整保留原值；不受支持的未知值阻止保存并给出迁移提示。
- 授权码方式用例覆盖空重定向 URI 的前端拒绝和已有 URI 无损回显。
- Cypress 从编辑 Client 开始，依次配置浏览器、APP 和微信小程序方式，断言页面中文摘要与保存请求，再重新打开
  验证回显。
- Stage 4 执行相关 Vitest、`npm run build`、目标 Cypress 和 `git diff --check`，分别报告自动化结果；不声称完成
  真实第三方平台联调，因为本次不改变认证运行链。

## Implementation notes and deviations
- 已复用 System 现有 external-code catalog，未新增后端 API、数据库字段或依赖；终端类型是 Admin 配置导航，
  最终仍提交 `browserLoginProviderTypes`、`externalCodeTypes`、grant 和身份源绑定。
- 浏览器的本地账号方式与 APP 的 IAM 统一登录共同映射 `authorization_code`；当已选择网页登录提供商时，界面
  禁止关闭其依赖的 IAM 授权能力。企业微信、微信在浏览器和端内网页之间通过同一个 provider 值自然同步。
- 仍受支持的 browser external code 只在高级协议配置中显示并保留原绑定；服务端目录不存在的历史类型会阻止
  保存并提示迁移，不会静默清理。
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
