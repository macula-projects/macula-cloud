# Spec: 管理端原生加密能力与依赖安全治理 (from intent.md 2026-10-18)
Status: accepted

## Source intent
基于已接受的 [intent.md](./intent.md)：以安全为主要目标，移除 `crypto-js` 并处理管理端依赖告警；旧 AES 缓存允许清除。

## Requirements
1. `macula-cloud-admin` 的生产与开发依赖中不再包含 `crypto-js`，浏览器代码不引入 `node:crypto` 或其 polyfill。
2. 保留 `tool.data` 对本地存储的同步 `get`/`set` 行为以及现有缓存过期语义；登录、菜单、权限、租户和语言读取继续工作。
3. 在升级后首次启动时清除本应用旧格式的本地缓存及与其关联的登录状态，使用户重新登录；不得清空同源其他应用的不相关数据。
4. 移除无人调用的 MD5、Base64、AES 工具接口及前端内置的 AES-ECB 密钥和开关；文档说明前端内置密钥不能提供可靠的保密边界。以后确有浏览器加密需求时使用 Web Crypto API 并单独定义异步调用契约。
5. 处理当前 `package-lock.json` 中所有有可用修复的安全告警，覆盖直接及传递依赖、生产及开发依赖。若因上游或兼容性无法清零，逐项记录告警、暴露条件、替代措施和后续处理人，不将残留告警表述为已修复。
6. `package.json`、`package-lock.json`、Node 构建镜像及文档的版本要求一致；升级后的管理端能够安装、构建、运行相关单元测试及端到端测试。

## Non-goals
- 不改动 IAM、Gateway、System 的 API、权限模型或持久化结构。
- 不把原有 AES-ECB 密文转换为新密文，也不保留旧密文读取器。
- 不为没有实际调用的 MD5、Base64、AES 工具维持兼容接口。
- 不进行与安全告警无关的全量依赖追新，也不部署或发布管理端。

## Design
- 已核对的约束来源：仓库 `AGENTS.md`、`.agents/rules/frontend-development.md`、`.agents/rules/testing.md`、`.agents/rules/dependencies-release.md` 和 `.agents/rules/architecture.md`。没有发现另行适用的组织级安全或数据处理 skill。
- `tool.data` 继续使用当前 JSON 包装格式和同步接口，但不再进行客户端加解密。旧缓存迁移采用一次性版本标记，只移除本应用已知的本地存储键；迁移时同时使旧登录状态失效，防止令牌仍在而用户、菜单或权限缓存已不存在。迁移发生在任何路由、请求拦截器或 Pinia 状态读取之前。新缓存照常遵守过期时间。
- 删除 `tool.crypto` 中无调用方的 MD5、Base64、AES 方法以及 `LS_ENCRYPTION`、`LS_ENCRYPTION_key` 配置。此改动移除了现有唯一的浏览器端密码学调用，因此不新增空置的 Web Crypto 封装；若后续需要真正的加密功能，使用浏览器 `globalThis.crypto.subtle`，不使用 Node 专有模块。
- 以当前锁文件为审计基线（`npm audit`：59 项，含 6 严重、30 高危；`--omit=dev`：18 项，含 1 严重、11 高危）。优先选择能消除告警的最小受支持版本，逐组处理运行时依赖和构建测试依赖，检查传递依赖、peer 约束及现有 API 用法。不能仅靠 `overrides` 隐藏根因，也不运行未经审阅的强制升级。
- 对必须跨主版本的 `axios`、`echarts`、`cypress`、`vitest` 及其配套工具作针对性适配。当前 Docker 构建镜像为 Node 20.19.5，而审计建议的 `vitest@5.0.5` 需要 Node 22.12+、`cypress@16.2.0` 需要 Node 22+；因此同步升级构建 Node 基线及 README 中的 Node 版本说明。

## Data and interfaces
- 后端 HTTP API 和数据库结构不变。
- 管理端本地存储中的旧密文不再读取；旧会话在迁移时退出，用户重新登录后生成新缓存。`tool.data` 对调用方保留同步接口和 JSON 值语义。
- 移除公开的 `LS_ENCRYPTION`、`LS_ENCRYPTION_key` 配置键与 `tool.crypto` 工具接口；更新管理端 README 的缓存迁移说明和 Node 版本要求。
- 更新 `package.json` 与 `package-lock.json`；若安全修复需要更新 Dockerfile、Vite/Cypress 配置或测试代码，按受影响调用方同步修改。

## Flagged concerns
- 移除可选的 AES 加密开关而不以 Web Crypto 重建：现有密钥随前端公开，Web Crypto 加解密又会改变同步读取契约；这一取舍需要审阅者确认，blocking。
- Node 构建基线升级：清除开发依赖的严重告警需要兼容 Node 22.12+ 的工具链，并改变当前 Node 20 Docker 构建环境；需要审阅者确认环境约束，blocking。
- 审计结果随公告和 registry 数据变化：实施时必须重新审计，若有无修复版本或冲突告警，应提交逐项残余风险供审阅，non-blocking。
- 完成期限尚未指定：不影响范围与技术方案，按正常开发验证节奏推进，non-blocking。

## Verification strategy
- 对旧密文缓存、首次迁移、重复启动、未加密缓存、过期缓存和同源无关存储键编写 Vitest 测试；验证旧令牌不会在用户/权限缓存清空后继续被使用。
- 验证登录、菜单、权限、租户与语言相关的现有单元测试及关键 Cypress 流程；对升级引起的 API 行为变化增加针对性测试。
- 在更新后的 Node 构建环境运行 `npm ci`、`npm run test:unit`、`npm run build` 及适用构建模式、`npm run test:e2e:ci`。`npm run lint` 含自动修复，运行前后核对 diff。
- 运行 `npm audit --json` 和 `npm audit --omit=dev --json`，记录告警数量及任何残留项；检查产物不含 `crypto-js` 或 Node crypto polyfill，并验证 Docker 构建可用。
