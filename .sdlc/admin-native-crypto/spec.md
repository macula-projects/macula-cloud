# Spec: 管理端原生加密能力与依赖安全治理 (from intent.md 2026-10-18)
Status: accepted

## Source intent
基于已接受的 [intent.md](./intent.md)：以安全为主要目标，移除 `crypto-js` 并处理管理端依赖告警；旧 AES 缓存允许清除。

## Requirements
1. `macula-cloud-admin` 的生产与开发依赖中不再包含 `crypto-js`，浏览器代码不引入 `node:crypto` 或其 polyfill。
2. 保留 `tool.data` 对本地存储的同步 `get`/`set` 行为以及现有缓存过期语义；登录、菜单、权限、租户和语言读取继续工作。
3. 在升级后首次启动时清除本应用旧格式的本地缓存及与其关联的登录状态，使用户重新登录；不得清空同源其他应用的不相关数据。
4. 保留 `tool.crypto` 中可用的加解密能力，以浏览器 Web Crypto API 实现新的异步 AES-GCM 加解密；Base64 使用浏览器原生编码接口。MD5 不再作为安全哈希提供，改为异步 SHA-256。移除前端内置的 AES-ECB 密钥和开关，并说明前端内置密钥不能提供可靠的保密边界。
5. 处理当前 `package-lock.json` 中所有有可用修复的安全告警，覆盖直接及传递依赖、生产及开发依赖。若因上游或兼容性无法清零，逐项记录告警、暴露条件、替代措施和后续处理人，不将残留告警表述为已修复。
6. `package.json`、`package-lock.json`、Node 构建镜像及文档的版本要求一致；升级后的管理端能够安装、构建、运行相关单元测试及端到端测试。

## Non-goals
- 不改动 IAM、Gateway、System 的 API、权限模型或持久化结构。
- 不把原有 AES-ECB 密文转换为新密文，也不保留旧密文读取器。
- 不兼容原 AES-ECB 密文、同步 AES 调用和 MD5 输出；不提供 Node 专有 `crypto` 模块或浏览器 polyfill。
- 不进行与安全告警无关的全量依赖追新，也不部署或发布管理端。

## Design
- 已核对的约束来源：仓库 `AGENTS.md`、`.agents/rules/frontend-development.md`、`.agents/rules/testing.md`、`.agents/rules/dependencies-release.md` 和 `.agents/rules/architecture.md`。没有发现另行适用的组织级安全或数据处理 skill。
- `tool.data` 继续使用当前 JSON 包装格式和同步接口，但不再进行客户端加解密。旧缓存迁移采用一次性版本标记，只移除本应用已知的本地存储键；迁移时同时使旧登录状态失效，防止令牌仍在而用户、菜单或权限缓存已不存在。迁移发生在任何路由、请求拦截器或 Pinia 状态读取之前。新缓存照常遵守过期时间。
- `tool.crypto.AES.encrypt` / `decrypt` 保留方法名但改为返回 Promise，使用 AES-GCM 和每次加密独立的 96 位随机 IV；密文包含格式版本和 IV，解密时校验格式与认证标签。密钥由调用方以 `CryptoKey` 提供，工具提供生成和导入 AES 密钥的方法，不内置或持久化密钥。`tool.crypto.BASE64` 保留同步 UTF-8 字符串编码/解码；原 `MD5` 改为返回 Promise 的 `SHA256`，输出十六进制摘要。旧 `LS_ENCRYPTION` 和 `LS_ENCRYPTION_key` 配置移除，`tool.data` 不依赖异步密码学。
- 以当前锁文件为审计基线（`npm audit`：59 项，含 6 严重、30 高危；`--omit=dev`：18 项，含 1 严重、11 高危）。优先选择能消除告警的最小受支持版本，逐组处理运行时依赖和构建测试依赖，检查传递依赖、peer 约束及现有 API 用法。不能仅靠 `overrides` 隐藏根因，也不运行未经审阅的强制升级。
- 对必须跨主版本的 `axios`、`echarts`、`cypress`、`vitest` 及其配套工具作针对性适配。当前 Docker 构建镜像为 Node 20.19.5，而审计建议的 `vitest@5.0.5` 需要 Node 22.12+、`cypress@16.2.0` 需要 Node 22+；因此同步升级构建 Node 基线及 README 中的 Node 版本说明。

## Data and interfaces
- 后端 HTTP API 和数据库结构不变。
- 管理端本地存储中的旧密文不再读取；旧会话在迁移时退出，用户重新登录后生成新缓存。`tool.data` 对调用方保留同步接口和 JSON 值语义。
- 移除公开的 `LS_ENCRYPTION`、`LS_ENCRYPTION_key` 配置键；`tool.crypto` 保留，但 AES 与哈希改为异步新契约，更新管理端 README 的用法、缓存迁移说明和 Node 版本要求。
- 更新 `package.json` 与 `package-lock.json`；若安全修复需要更新 Dockerfile、Vite/Cypress 配置或测试代码，按受影响调用方同步修改。

## Flagged concerns
- AES 方法改为异步且不再接收原字符串密钥或旧密文；`tool.data` 继续同步但不加密，旧缓存会清除：这是用户“不用兼容”后的提案，需要审阅者确认新的接口边界，blocking。
- Node 构建基线升级：清除开发依赖的严重告警需要兼容 Node 22.12+ 的工具链，并改变当前 Node 20 Docker 构建环境；需要审阅者确认环境约束，blocking。
- TinyMCE 8 自托管采用 GPL-2.0-or-later 或商业许可；TinyMCE 6 原版本仍存在高危 XSS 告警。用户最终明确选择此前已批准的 MIT 许可 Tiptap 开源版替换 TinyMCE，不接受 GPL/商业模式，也不保留旧 TinyMCE，resolved。
- Web Crypto 在浏览器中需要安全上下文（HTTPS 或可信本地开发环境）；生产 HTTP 部署无法使用新 AES 接口，需要部署方确认 HTTPS，blocking。
- 审计结果随公告和 registry 数据变化：实施时必须重新审计，若有无修复版本或冲突告警，应提交逐项残余风险供审阅，non-blocking。
- 完成期限尚未指定：不影响范围与技术方案，按正常开发验证节奏推进，non-blocking。

## Verification strategy
- 对 AES-GCM 加解密往返、随机 IV、错误密钥和篡改密文拒绝、Base64 UTF-8 往返、SHA-256 摘要，以及旧密文缓存、首次迁移、重复启动、未加密缓存、过期缓存和同源无关存储键编写 Vitest 测试；验证旧令牌不会在用户/权限缓存清空后继续被使用。
- 验证登录、菜单、权限、租户与语言相关的现有单元测试及关键 Cypress 流程；对升级引起的 API 行为变化增加针对性测试。
- 在更新后的 Node 构建环境运行 `npm ci`、`npm run test:unit`、`npm run build` 及适用构建模式、`npm run test:e2e:ci`。`npm run lint` 含自动修复，运行前后核对 diff。
- 运行 `npm audit --json` 和 `npm audit --omit=dev --json`，记录告警数量及任何残留项；检查产物不含 `crypto-js` 或 Node crypto polyfill，并验证 Docker 构建可用。
