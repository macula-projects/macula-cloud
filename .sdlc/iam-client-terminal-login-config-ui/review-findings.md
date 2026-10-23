# Review findings: IAM Client 按终端配置登录方式

## Plan

### Round 1

- High — 多个身份源选择只适用于 external code；网页登录没有 Client-registration 绑定字段。已限定 external code
  自动/人工绑定，网页登录继续要求同 provider 全局唯一启用身份源，否则禁用并提示整改。
- High — 企业微信/微信的浏览器与端内网页共享 provider 权限，不能表现为独立开关。已规定两处为同步镜像的
  “网页认证能力”，摘要明确浏览器/端内共享，并增加跨终端取消证明。
- High — System 不接受真正未知的 external code 枚举，前端无法承诺无损透传。已把无损范围限定为仍受支持但
  未进入常用 UI 的兼容类型，未知值改为阻止保存并提示迁移。
- Medium — 自动加入 authorization code 时必须同步处理重定向 URI。已要求常用区展示并校验回调 URI，并补充
  空值拒绝及已有值无损回显证明。

### Round 2

- Approved — 复核身份源边界、共享网页能力、兼容值处理和重定向 URI 联动修订，无新增发现。

## Code review

### Round 1 — Stage 5 — `dc4bc005dce7b2671c2151de628e5f5a2c4c978a` 加未提交工作区差异（2026-10-23）

- Scope：终端优先 OAuth Client 配置 UI 的完整实现差异，包括模型目录、页面、Vitest、Cypress 和 IAM README；
  不审查工作区中其他 IAM/System 功能改动。
- Passes：依据根 `REVIEW.md` 执行 Bugs、Security、Compliance；最多 5 个 Nit。
- Important — `macula-cloud-admin/src/views/system/oauth2Client/index.vue:149,529,667`：编辑已有 Client 时，
  `open()` 只复制旧 external provider binding；唯一候选自动绑定只在重新勾选方式时写入。旧 binding 已失效而
  当前仅有一个新候选时，界面显示“自动绑定”但仍提交旧 ID，服务端会拒绝保存。应在打开 Client 及 catalog
  加载完成后统一重整 binding，并增加旧 binding 失效后的编辑回归测试。
- Minor — `macula-cloud-admin/src/views/system/oauth2Client/index.vue:698`：列表摘要对企业微信和微信仍输出
  “网页：...”，没有按 accepted spec 表达“网页（浏览器/端内）”共享能力，可能使管理员误认为两个入口可独立
  授权。应拆分共享网页能力与普通浏览器摘要并增加断言。
- Minor — `macula-cloud-iam/README.md:148`：普通浏览器矩阵仍把飞书、钉钉、企业微信和微信描述为直接提交
  browser external code，与 accepted spec 和常用 UI 默认的 `/oauth2/authorize` browser provider 路径冲突。
  应把默认接入统一写为 `/oauth2/authorize`，将 browser code 明确标为高级兼容方式。
- Minor — `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js:73`、
  `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js:497`：当前 Vitest 主要验证源码字符串和静态
  目录，Cypress 只覆盖新增 Client 的唯一候选 happy path；没有实际证明 plan/spec 声称的编辑反向映射、取消、
  零/多身份源、共享开关、direct provider、回调校验、兼容值处理及保存后重开摘要。应抽取可测试映射逻辑或
  挂载组件，并补齐关键编辑和兼容分支。
- Security：无发现；前端映射没有扩大授权边界，服务端仍校验 provider、grant、binding 和 URI。
- Compliance：未发现适用的数据分类、同意、保留、审计或监管要求违规。
- Counts：发布 4 条；抑制 3 条 Plan review 已闭环问题；独立 verifier 拒绝 0 条；Nit 0 条。

### Round 2 — Stage 5 — `dc4bc005dce7b2671c2151de628e5f5a2c4c978a` 加 Round 1 后未提交修复（2026-10-23）

- Scope：仅复审 Round 1 四条 finding 的修复及直接影响；因修复仍位于同一未提交工作区，HEAD 未变化，范围限定
  为登录配置纯函数、页面集成、新增回归测试与 README 普通浏览器说明。
- Passes：依据根 `REVIEW.md` 执行 Bugs、Security、Compliance；最多 5 个 Nit。
- Round 1：上轮 4 条 finding 均已闭环并被抑制，不重复发布。
- Minor — `macula-cloud-admin/src/views/system/oauth2Client/index.vue:508,530`：Client 列表与 external-code catalog
  并发加载时，管理员若在 catalog 返回前编辑 APP/端内网页 Client，`terminalForClient()` 缺少 scene 元数据会回退
  到浏览器；catalog 返回后只重整 binding，不会重新推断终端。应等待 catalog，或在 catalog 成功后对尚未由用户
  切换的已打开表单重新推断终端，并增加延迟 catalog 回归测试。
- Minor — `macula-cloud-admin/src/views/system/oauth2Client/index.vue:607,651`：已选择的 browser provider 若后来变成
  零个或多个启用身份源，卡片为允许取消而保持可操作，但保存逻辑没有拒绝仍保留的无效 provider，无关编辑仍会
  发出请求并由服务端拒绝。应在保存前列出候选数不为 1 的已选 provider，显示明确提示并阻止请求，同时保留取消
  能力；补充零候选和多候选编辑保存测试。
- Security：无新发现；前端目录映射没有扩大服务端授权边界，也未引入凭据暴露。
- Compliance：未发现适用的数据分类、同意、保留、审计、可访问性或监管要求违规。
- Counts：发布 2 条；抑制 4 条 Round 1 finding；独立 verifier 拒绝 0 条；Important 0 条；Nit 0 条。

### Round 3 — Stage 5 — `dc4bc005dce7b2671c2151de628e5f5a2c4c978a` 加 Round 2 后未提交修复（2026-10-23）

- Scope：仅复审 Round 2 两条 Minor 的修复及直接测试影响；因修复仍位于同一未提交工作区，HEAD 未变化。
- Passes：依据根 `REVIEW.md` 执行 Bugs、Security、Compliance；最多 5 个 Nit。
- Round 1/2：此前 6 条 finding 均已闭环并被抑制，不重复发布。
- Minor — `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js:823,889`：新增测试只证明
  catalog 返回后自动重定位，以及零/多候选保留选择时保存被阻止；没有直接证明 catalog 返回前用户手动切换终端
  不会被覆盖，也没有证明取消失效 browser provider 后可以成功保存并从请求移除 provider/direct provider。
  删除手动切换保护或错误禁用失效已选卡片时，当前测试仍可能全绿。应补充延迟响应期间手动切换的断言，并对
  零/多候选分别取消、保存及校验 PUT 请求体。
- Bugs：除上述验证缺口外，未发现新的行为缺陷；Round 2 两条代码路径已闭环。
- Security：无新发现；未扩大服务端授权边界或暴露凭据。
- Compliance：未发现适用的数据分类、同意、保留、审计、可访问性或监管要求违规。
- Counts：发布 1 条；抑制 6 条 Round 1/2 finding；独立 verifier 拒绝 0 条；Important 0 条；Nit 0 条。
- Round cap：本 change 已完成 3 轮 Stage 5 代码复审；修复后不得启动第 4 轮自动复审，应由人工依据测试证据
  直接评估，或回到 plan 重新考虑测试策略。

## Recurring

- Round 3 再次扫描 `.sdlc/*/review-findings.md`，未发现上述 finding class 已在两个或更多 change 中重复，因此暂无需要
  编码到 `AGENTS.md`、lint 或 `REVIEW.md` 的 recurring class。
