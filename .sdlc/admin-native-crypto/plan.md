# Plan: 管理端移除 crypto-js 并修复依赖告警 (from spec.md 2026-10-18)

## Files that change
- `macula-cloud-admin/src/utils/tool.js`：一次性缓存迁移、保留同步缓存接口，移除 `crypto-js`；保留 `tool.crypto`，以浏览器 Web Crypto 实现异步 AES-GCM 与 SHA-256，以浏览器原生编码实现 Base64。
- `macula-cloud-admin/src/config/index.js`：移除公开的旧 AES 开关与密钥。
- `macula-cloud-admin/tests/unit/tool-storage.test.js`（新增）：覆盖迁移、缓存语义和登录状态。
- `macula-cloud-admin/tests/unit/tool-crypto.test.js`（新增）：覆盖 AES-GCM、Base64 与 SHA-256 新契约。
- `macula-cloud-admin/src/components/scEditor/index.vue`、`macula-cloud-admin/src/style/fix.scss`、`macula-cloud-admin/public/tinymce/`：以 MIT 许可的 Tiptap Vue 3 开源组件替代 TinyMCE；保持 `scEditor` 的 `v-model`、禁用、占位、高度及原默认工具栏的常用格式、链接、图片上传、媒体、表格、模板插入、分页符、源码和预览能力，移除不再使用的 TinyMCE 静态资源与样式。
- `macula-cloud-admin/tests/unit/sc-editor.test.js`（新增）：覆盖编辑器输入输出、禁用、图片上传和模板插入。
- `macula-cloud-admin/package.json`、`macula-cloud-admin/package-lock.json`：删除 `crypto-js`，升级有告警的直接及传递依赖，并声明受支持的 Node 版本。
- `macula-cloud-admin/Dockerfile`、`macula-cloud-admin/README.md`：采用兼容的 Node 24 构建镜像，说明最低版本、缓存迁移与安全边界。
- `macula-cloud-admin/vite.config.js`、`macula-cloud-admin/cypress.config.js` 及 `macula-cloud-admin/tests/unit/`、`macula-cloud-admin/cypress/e2e/` 下受升级影响的配置和测试：仅在新版本 API 或测试行为确实需要时调整，并记录实际文件。
- `macula-cloud-admin/.eslintrc.cjs`：`eslint-plugin-vue` 10 移除了旧的 `vue3-essential` 配置名，需要更新引用。
- `macula-cloud-admin/src/api/index.js`、`macula-cloud-admin/src/views/common/home/widgets/components/index.js`：Vite 7 将旧的 `globEager` 调用迁移为 `glob` 的 `eager` 选项。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`、`macula-cloud-admin/cypress/e2e/security-policy.cy.js`、`macula-cloud-admin/cypress/e2e/tinyid-management.cy.js`：测试预置的是新格式登录缓存，需一并预置缓存版本标记。
- 追加 lint 整治（用户于 2026-10-18 选择保留全部现有规则，逐文件修复；不新增库、不改 `.eslintrc.cjs` 的规则、不忽略源码、不批量格式化）：
  - `macula-cloud-admin/public/config.js`、`src/config/index.js`、`src/stores/viewTags.js`、`src/utils/request.js`。
  - `macula-cloud-admin/src/assets/icons/{Alipay,Code,Download,Gitee,Organization,Upload,Vue,Wechat,Wecom}.vue`。
  - `macula-cloud-admin/src/components/{scCodeEditor,scContextmenu,scContextmenuItem,scCron,scCropper,scDialog,scFileExport,scFileImport,scFileSelect,scFilterBar,scForm,scFormTable,scIconSelect,scListTable,scPageHeader,scPasswordStrength,scQrCode,scSelect,scSelectFilter,scStatistic,scStatusIndicator,scTable,scTableSelect,scTitle,scTrend,scUpload,scUploadFile,scUploadMultiple,scVideo,scWaterMark,scWorkflow}/index.vue`。
  - `macula-cloud-admin/src/components/scFileExport/column.vue`、`scFilterBar/my.vue`、`scListTable/columnListSetting.vue`、`scQrCode/qrcode.js`、`scTable/columnSetting.vue`、`scWorkflow/{select.vue,nodes/approver.vue,nodes/branch.vue,nodes/promoter.vue,nodes/send.vue}`。
  - `macula-cloud-admin/src/layout/{index.vue,components/search.vue,components/setting.vue,components/tags.vue,components/tasks.vue,components/tenant.vue,components/topbar.vue,components/userbar.vue,other/404.vue,other/empty.vue}`。
  - `macula-cloud-admin/src/views/common/home/{index.vue,widgets/index.vue,widgets/components/about.vue,widgets/components/echarts.vue,widgets/components/progress.vue,widgets/components/time.vue,widgets/components/ver.vue,widgets/components/welcome.vue,work/index.vue,work/components/myapp.vue}`。
  - `macula-cloud-admin/src/views/common/login/{index.vue,resetPassword.vue,components/passwordForm.vue,components/phoneForm.vue}`。
  - `macula-cloud-admin/src/views/system/{application/addMaintainer.vue,application/index.vue,application/save.vue,dept/index.vue,dept/save.vue,dict/dic.vue,dict/index.vue,dict/list.vue,log/index.vue,menu/index.vue,menu/save.vue,messageSettings/index.vue,role/index.vue,role/resource.vue,role/save.vue,tenant/index.vue,tenant/save.vue,user/index.vue,user/save.vue}`。

- `macula-cloud-admin/tests/unit/lint-regression.test.js`（新增）：固定 QRCode 原始矩阵、运行时配置覆盖及路由缓存名称映射。

## Order of work
1. 复核工作区和审计基线；确认 `tool.crypto`、`LS_ENCRYPTION`、`tool.data`、`TOKEN` 的完整调用链，记录受影响的直接依赖、传递依赖和 Node/peer 约束。
2. 在 `tool.js` 增加模块初始化时的一次性存储版本迁移：删除本应用使用的 `USER_INFO`、`MENU`、`PERMISSIONS`、`APP_LANG`、`tenantId`、`tenantStoreTenantOptions`、`tenantLabel` 等旧本地存储键，删除旧 `TOKEN` cookie，保留同源无关的存储键；用独立版本标记避免再次清理。移除 AES-ECB 和内置密钥配置，保留同步 `tool.data` 与过期行为。实现异步 AES-GCM（调用方提供 `CryptoKey`，密文携带版本与随机 IV）、异步 SHA-256 及同步 UTF-8 Base64。添加针对性单元测试。
3. 分批升级生产依赖，处理 `axios`、`echarts`、`element-plus`、`vue`、`vue-i18n` 等直接告警；删除 TinyMCE 与 Vue 包装器，安装仅 MIT 许可的 Tiptap 开源包并改造 `scEditor`。保留 `v-model` HTML、禁用、占位、高度和原默认工具栏的常用格式、链接、表格、图片上传、媒体、模板插入、分页符、源码和预览能力；不引入 Tiptap Pro、云服务或商业许可。移除旧静态资源并添加组件测试。继续解决剩余传递告警，每批核对锁文件、许可和调用 API。
4. 升级构建和测试依赖，处理 `vite`、`vitest`、`cypress`、`start-server-and-test`、插件及剩余传递告警；将 Docker 构建镜像升为 Node 24，更新 `engines`、README 和必要的 Vite/Cypress 配置。Docker 的前端构建跳过 Cypress 二进制下载。使用普通 `npm install` 更新锁文件，不运行 `npm audit fix --force`。
5. 检查所有实际改动和残余告警；执行锁文件可重现安装、相关单测、生产构建、适用构建模式、端到端测试、lint 和 Docker 构建。若因不可修复告警或外部环境导致验证不完整，逐项记录原因、风险和复验条件。
6. 在前述提交 `e1fefb1` 的基础上，按 lint 规则分类逐文件修复现有 97 个文件的 240 条错误：为 Element Plus 自动导入的调用显式导入符号；给单词名或 `index.vue` 组件设置稳定的多词组件名；修复未使用变量、重复声明、无效 prop 默认值、无用转义与常量条件；对内嵌 QRCode 源码作保持输出语义的最小修正。保留所有现有 lint 规则，不使用 `eslint-disable`、文件忽略或批量 `--fix` 充数。每组修改后运行针对性只读 lint 并检查 diff。
7. 对可能改变运行语义的修改补充必要测试，重点覆盖 QRCode 输出、运行时 `APP_CONFIG`、动态组件名称与自动导入；重跑完整只读 lint、全部 Vitest、生产及开发构建、关键 Cypress、审计和 Docker 构建。若完整 lint 未达到零错误，不进入 PR 审查。

## Risks
- 首次加载会使旧用户退出登录，并清除本应用已保存的菜单、权限、租户和语言偏好；回滚到仍启用旧 AES 的版本时，可能需要再次清除这些键并重新登录。
- 迁移必须先于语言模块、路由、请求拦截器和 Pinia 的缓存读取；若模块初始化顺序错误，旧令牌与空权限缓存可能同时存在。
- `axios`、`echarts` 与前端工具链的主版本升级可能改变运行时 API、构建产物、插件 peer 约束和 Cypress 测试行为；不能用只通过构建替代流程验证。
- 构建环境由 Node 20 升至 Node 24；本机、CI 与 Docker 的版本不一致可能使安装或测试结论失真。
- Web Crypto 只在安全上下文提供 `subtle`；AES-GCM 密钥由调用方管理，不能把前端常量当作保密密钥。旧同步 AES 调用及密文不兼容（用户已接受）。
- Tiptap 是无预置 UI 的编辑器，替换 TinyMCE 时需自行实现工具栏、媒体与图片上传、模板、分页符、源码和预览交互。旧 HTML 中非标准内联样式可能无法原样往返；应保留常用编辑语义并在测试中明确可支持范围。`plugins`、`toolbar`、`options` 属于 TinyMCE 专用配置，仓库内无外部调用，但不能保证外部调用方直接沿用。不可擅自启用付费扩展。
- 新公告可能在实施过程中增加审计数量；若有告警无法清除，不以静默忽略或覆盖为完成依据。
- lint 整治横跨 97 个文件，组件 `name` 可能影响 KeepAlive、动态组件与递归引用；显式导入可能影响 Vite 自动导入或样式；修订内嵌第三方 QRCode 源码可能改变编码行为。优先做语义等价修改并加针对性验证，不能以禁用规则掩盖错误。
- 当前 `prettier/prettier` 另有大量 warning，`npm run lint` 自带 `--fix` 会重排无关文件；本轮以 `npm run lint -- --no-fix --quiet` 判定零 error，并记录 warning 数量，不执行全仓库自动格式化。

## Proof
- `tests/unit/tool-storage.test.js` 验证首次迁移、再次启动不清理新数据、旧密文和旧令牌被清理、同源无关键保留、同步读写、过期时间及异常缓存处理。
- `tests/unit/tool-crypto.test.js` 验证 AES-GCM 往返、不同 IV、错误密钥和篡改拒绝、Base64 UTF-8 往返、SHA-256 固定测试向量。
- `tests/unit/sc-editor.test.js` 和实际表单集成流程验证 HTML 双向同步、禁用和占位、图片上传、媒体、表格、模板插入、分页符、源码和预览；检查原有已保存 HTML 的常用格式能重新加载。核对已安装 Tiptap 包均为 MIT，最终构建无 TinyMCE 资产。
- 运行全部现有 Vitest，并对实际受升级影响的 `axios` 请求、图表、富文本、i18n 和登录/权限行为增加或修正测试；运行关键 Cypress 流程。
- 使用 Node 24 运行 `npm ci`、`npm run test:unit`、`npm run build`、适用环境构建、`npm run test:e2e:ci`；`npm run lint` 前后检查 diff，避免改写无关文件。
- `npm audit --json` 和 `npm audit --omit=dev --json` 目标为零告警；若非零，提供逐项残余风险清单。核对依赖树与构建产物无 `crypto-js`、`node:crypto` 浏览器 polyfill，并验证 Docker 镜像构建。
- lint 扩展：`npm run lint -- --no-fix --quiet` 覆盖全仓库并达到零 error；核对原 240 条错误及 97 个文件均已处理，未关闭规则、忽略文件或由 `--fix` 造成无关格式化；对 QRCode、运行时配置、组件名和显式导入影响运行的部分补充或执行针对性测试，并重新执行完整前端验证矩阵。

## Implementation deviations
- 2026-10-18：移除无人使用的 `sass-loader` 并升级 `sass`，因为 Vite 7 要求 `sass` 1.70+，旧 `sass-loader` 仅供 Webpack 使用。影响：缩小依赖树，不改变应用接口；通过 Vite 构建验证样式。
- 2026-10-18：新增 `.eslintrc.cjs` 到文件清单，因为 `eslint-plugin-vue` 10 不再提供 `plugin:vue/vue3-essential`。影响：仅调整 lint 配置名；通过完整 lint 验证。
- 2026-10-18：`.eslintrc.cjs` 同时补充浏览器、Node 和 ES2022 环境，否则升级后会把现有浏览器全局变量误报为未定义。影响：配置范围扩大；使用只读 lint 命令验证，不批量改写旧代码。
- 2026-10-18：TinyMCE 从原 MIT 许可变为 GPL-2.0-or-later/商业许可，原计划中的直接升级会改变仓库分发许可边界。已暂停编辑器相关适配并请求工程决策；其他独立步骤继续。决定可能需要替换编辑器、提供商业许可或重新审查分发方式，并改变文件清单与验证范围。
- 2026-10-18：首轮 Cypress 暴露 Vite 7 不再转换 `import.meta.globEager`，导致应用启动失败。新增两个调用方到文件清单并改用 `import.meta.glob(..., {eager: true})`；证明范围增加应用启动和现有 Cypress 流程。
- 2026-10-18：第二轮 Cypress 的旧模拟登录只写缓存和 Cookie，按新迁移规则被正确清除。更新三个 Cypress fixture 在预置新格式缓存时同时写版本标记；旧缓存被清除的行为由独立单测证明。
- 2026-10-18：用户纠正原计划中删除所有 `tool.crypto` 方法的决定；修订规格为保留新异步加解密接口且不兼容旧密文，重新提交本计划待工程审批，旧计划不得继续作为接口实现依据。
- 2026-10-18：用户选择以非商业、许可兼容的编辑器替换 TinyMCE；选用 MIT 许可的 Tiptap 开源包，扩大到 `scEditor`、旧静态资源和组件测试，不使用 Pro 扩展。
- 2026-10-18：用户随后撤回 Tiptap 方向，要求继续升级 TinyMCE 8。已撤销本次 Tiptap 的组件、静态资源和依赖变更，恢复原编辑器文件；上方 Tiptap 步骤不再有效。TinyMCE 8 自托管的 GPL/商业许可选择和修订计划仍待工程确认，暂停进一步编辑器实施。
- 2026-10-18：用户又明确拒绝 TinyMCE 8 的 GPL 模式，并要求改回原版。已恢复 `tinymce@6.3.2` 与 `@tinymce/tinymce-vue@5.0.0`；`npm audit` 剩余 1 项高危 `tinymce` 告警，当前计划需重新审批，安全风险是否可接受仍待决定。
- 2026-10-18：用户澄清“改回去”指回到最初已批准的 Tiptap 开源替换方案，不是保留 TinyMCE 6。恢复 Tiptap 步骤和零告警目标；前两条临时方向变更仅作为决策轨迹保留，不再适用。
- 2026-10-18：逐文件修复单词组件名时发现原有路由名参与 KeepAlive 匹配。保留路由名，在已列入清单的 `src/layout/index.vue` 将其映射到新组件名，并增加 `tests/unit/lint-regression.test.js` 防回归；不改变后端菜单契约。
- 2026-10-18：`public/config.js` 的旧顶层 `const APP_CONFIG` 在独立脚本中供打包后的配置模块读取，ESLint 无法关联两个文件。初次改为仅使用 `globalThis.APP_CONFIG` 后，Cypress 运行时配置用例发现会破坏旧的外部脚本覆盖契约；现保留顶层 `const` 并同步赋值给 `globalThis`，读取侧兼容两种形式。由新增单测与 Cypress 验证，未改变外部运行时配置方式。
- 2026-10-18：Stage 5 审查发现 `scEditor` 源码模式仅在退出时写回，用户直接提交 `scForm` 会丢失源码编辑。按用户明确要求返回实施阶段，在已列入清单的 `scEditor/index.vue` 中让源码输入即时经 Tiptap 解析并更新 `v-model`，外部值变化时同步源码视图；在已列入清单的 `sc-editor.test.js` 中增加直接提交及外部更新回归测试。需重新执行 Stage 4 验证后才能恢复 PR 审查。
- 2026-10-19：Stage 5 审查发现 TinyMCE 6 默认将分页符持久化为 `<!-- pagebreak -->`，而 Tiptap 会丢弃 HTML 注释。按用户确认，在已列入清单的 `scEditor/index.vue` 读入时将旧标记转换为内部可视分页节点，输出到 `v-model` 与源码模式时还原为旧标记；源码预览仍展示可视分页。`sc-editor.test.js` 增加块间及段落内旧标记、源码编辑和新插入分页符的往返测试。风险是 Tiptap 序列化 HTML 结构变化可能影响标记转换，由回归测试约束；必须重新执行 Stage 4 验证后恢复 PR 审查。
- 2026-10-19：再次审查发现源码模式中可视化工具栏仍能改动隐藏编辑器，随后输入源码会以旧文本覆盖这些改动。用户要求一次性修复模式交互：在已列入清单的 `scEditor/index.vue` 统一禁用源码模式下的可视化按钮、选择器、颜色和图片输入，并在方法层拒绝可视化命令；隐藏编辑区切换为不可编辑，退出源码模式后恢复。为避免上传中的图片在切换模式后静默丢失，上传期间暂时禁止再次上传及切换源码模式；若外部将编辑器设为只读而上传恰好完成，则明确显示插入失败。`sc-editor.test.js` 增加全部可视入口、外部禁用状态、恢复编辑及上传竞态测试；需重跑 Stage 4 全量验证。
- 2026-10-19：Stage 5 复审发现图片上传待返回时，父组件可复用编辑器切换到另一条记录，旧请求完成后会把图片插入新记录。按用户此前“一次性搞好，修复”的要求，在已列入清单的 `scEditor/index.vue` 增加上传代次：外部 `modelValue` 替换或组件卸载使旧请求失效，旧请求不再插入内容、覆盖错误提示或解锁新请求。`sc-editor.test.js` 增加跨记录并发上传和卸载后返回的回归测试；需再次执行 Stage 4 全量验证后才可恢复 PR 审查。
