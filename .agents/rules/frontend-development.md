# 管理端开发规则

修改 `macula-cloud-admin/` 时加载。

## 技术与目录

- 沿用现有 Vue 3、Vite、Vue Router、Pinia、Element Plus 和 JavaScript 技术栈；不要为单个功能引入平行框架或新的包管理器。
- `src/api` 统一维护后端请求与模型，`src/views` 放业务页面，`src/components` 放可复用组件，`src/router` 维护路由，`src/stores` 维护跨页面状态，`src/directives` 维护权限等指令。
- 先复用现有布局、组件、样式变量、工具和请求封装，再新增抽象；只被单个页面使用的代码优先留在该功能附近。
- 路由、菜单、按钮权限与后端 System/Gateway 权限模型有关，不能只修改前端可见性而不检查服务端授权。

## API 与状态

- 页面不直接散落 Axios 调用；API 路径、参数和响应适配集中在 `src/api`。
- 保持 Query/Form/DTO/VO 与后端契约一致。后端字段或分页结构变化时，同步检查表格、表单、校验、导入导出和错误提示。
- 认证信息只通过现有安全存储与请求拦截器传递，不记录 token、密码或敏感个人信息。
- 异步请求必须处理 loading、空数据、失败和重复提交；组件卸载或路由切换后避免写入失效状态。

## 交互与可维护性

- 保持现有页面布局、命名、国际化和 Element Plus 交互风格；新增文本需要检查 `src/locales`。
- 表单在前端提供即时校验，但不能把前端校验当作服务端安全边界。
- 列表查询应保留分页和筛选边界；避免一次加载无界数据或在浏览器中处理本应由服务端完成的权限过滤。
- 共享组件应提供清晰 props/events 接口，不直接耦合具体业务 API 或全局页面状态。

## 依赖与验证

- `package-lock.json` 是依赖锁定依据。普通安装使用 `npm ci`；只有明确升级依赖时使用 `npm install` 并审查 lockfile diff。
- `npm run lint` 含 `--fix`，会改写文件；执行前后检查 `git diff`，不要顺带格式化无关文件。
- 纯逻辑和组件行为使用 Vitest；关键用户流程使用 Cypress。变更至少运行相关测试与对应构建模式。
- 不提交 `node_modules/`、`dist/`、Cypress 视频/截图、覆盖率或本地 `.env` 凭据。
