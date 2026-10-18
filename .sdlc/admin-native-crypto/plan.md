# Plan: 管理端移除 crypto-js 并修复依赖告警 (from spec.md 2026-10-18)

## Files that change
- `macula-cloud-admin/src/utils/tool.js`：一次性缓存迁移、保留同步缓存接口、移除 `crypto-js` 和无人调用的加密工具。
- `macula-cloud-admin/src/config/index.js`：移除公开的旧 AES 开关与密钥。
- `macula-cloud-admin/tests/unit/tool-storage.test.js`（新增）：覆盖迁移、缓存语义和登录状态。
- `macula-cloud-admin/package.json`、`macula-cloud-admin/package-lock.json`：删除 `crypto-js`，升级有告警的直接及传递依赖，并声明受支持的 Node 版本。
- `macula-cloud-admin/Dockerfile`、`macula-cloud-admin/README.md`：采用兼容的 Node 24 构建镜像，说明最低版本、缓存迁移与安全边界。
- `macula-cloud-admin/vite.config.js`、`macula-cloud-admin/cypress.config.js` 及 `macula-cloud-admin/tests/unit/`、`macula-cloud-admin/cypress/e2e/` 下受升级影响的配置和测试：仅在新版本 API 或测试行为确实需要时调整，并记录实际文件。

## Order of work
1. 复核工作区和审计基线；确认 `tool.crypto`、`LS_ENCRYPTION`、`tool.data`、`TOKEN` 的完整调用链，记录受影响的直接依赖、传递依赖和 Node/peer 约束。
2. 在 `tool.js` 增加模块初始化时的一次性存储版本迁移：删除本应用使用的 `USER_INFO`、`MENU`、`PERMISSIONS`、`APP_LANG`、`tenantId`、`tenantStoreTenantOptions`、`tenantLabel` 等旧本地存储键，删除旧 `TOKEN` cookie，保留同源无关的存储键；用独立版本标记避免再次清理。移除 AES-ECB、MD5、Base64 工具和配置项，保留同步 `tool.data` 与过期行为。添加针对性单元测试。
3. 分批升级生产依赖，先处理 `axios`、`echarts`、`element-plus`、`tinymce`、`vue`、`vue-i18n` 等直接告警，再解决剩余传递告警；每批核对调用 API 与锁文件差异，仅在有实际兼容问题时调整页面或请求代码。
4. 升级构建和测试依赖，处理 `vite`、`vitest`、`cypress`、`start-server-and-test`、插件及剩余传递告警；将 Docker 构建镜像升为 Node 24，更新 `engines`、README 和必要的 Vite/Cypress 配置。使用普通 `npm install` 更新锁文件，不运行 `npm audit fix --force`。
5. 检查所有实际改动和残余告警；执行锁文件可重现安装、相关单测、生产构建、适用构建模式、端到端测试、lint 和 Docker 构建。若因不可修复告警或外部环境导致验证不完整，逐项记录原因、风险和复验条件。

## Risks
- 首次加载会使旧用户退出登录，并清除本应用已保存的菜单、权限、租户和语言偏好；回滚到仍启用旧 AES 的版本时，可能需要再次清除这些键并重新登录。
- 迁移必须先于语言模块、路由、请求拦截器和 Pinia 的缓存读取；若模块初始化顺序错误，旧令牌与空权限缓存可能同时存在。
- `axios`、`echarts` 与前端工具链的主版本升级可能改变运行时 API、构建产物、插件 peer 约束和 Cypress 测试行为；不能用只通过构建替代流程验证。
- 构建环境由 Node 20 升至 Node 24；本机、CI 与 Docker 的版本不一致可能使安装或测试结论失真。
- 新公告可能在实施过程中增加审计数量；若有告警无法清除，不以静默忽略或覆盖为完成依据。

## Proof
- `tests/unit/tool-storage.test.js` 验证首次迁移、再次启动不清理新数据、旧密文和旧令牌被清理、同源无关键保留、同步读写、过期时间及异常缓存处理。
- 运行全部现有 Vitest，并对实际受升级影响的 `axios` 请求、图表、富文本、i18n 和登录/权限行为增加或修正测试；运行关键 Cypress 流程。
- 使用 Node 24 运行 `npm ci`、`npm run test:unit`、`npm run build`、适用环境构建、`npm run test:e2e:ci`；`npm run lint` 前后检查 diff，避免改写无关文件。
- `npm audit --json` 和 `npm audit --omit=dev --json` 目标为零告警；若非零，提供逐项残余风险清单。核对依赖树与构建产物无 `crypto-js`、`node:crypto` 浏览器 polyfill，并验证 Docker 镜像构建。
