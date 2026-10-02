# Spec: Admin OAuth 登录配置外置化 (from intent.md 2026-10-02)
Status: accepted

## Source intent
[Accepted intent](./intent.md)：Macula Cloud Admin 的 OAuth client、scope 和示例账号必须脱离登录组件硬编码，同时支持 mode 配置与 Docker 容器启动时覆盖。

## Requirements
1. Admin 根 `.env` 必须提供与当前行为一致的本地 demo 默认值：OAuth client ID、client secret、scope、管理员用户名和密码；现有 `/api`、`/iam` 默认值和各 `.env.<mode>` 的覆盖语义保持不变。
2. `npm run dev`、`npm run build` 及现有 `build:docker|dev|stg|pet|prd` 命令必须继续按照 Vite 的 `.env`、`.env.<mode>`、进程环境优先级解析配置，不要求开发者修改 Vue 源码。
3. Admin 配置对象必须公开 `OAUTH_CLIENT_ID`、`OAUTH_CLIENT_SECRET`、`OAUTH_SCOPE`、`DEMO_USERNAME`、`DEMO_PASSWORD`，并允许生产运行时 `APP_CONFIG` 覆盖这些字段。
4. 密码登录表单必须从 Admin 配置对象读取 OAuth client、scope 和管理员示例账号，不得再包含这些值的字面量；普通用户选项的现有 `user/user` 行为保持不变。
5. 默认登录提交必须保持 password grant、`application/x-www-form-urlencoded`、当前 client/scope 值及现有错误提示行为，不改变 IAM token API 契约。
6. Docker 镜像必须在不重新构建的情况下，使用容器环境生成浏览器运行时 `config.js`，覆盖 OAuth client、scope 和示例账号；生成结果必须是有效 JavaScript，且不得把部署变量误用于 `/api`、`/iam` 的容器内代理目标。
7. `deploy/.env.example` 必须声明上述五个仅限本地 demo 的变量，`deploy/docker-compose.yml` 必须将其传入 Admin 容器并提供与根 `.env` 相同的默认值。
8. Macula Cloud Admin 在 Docker 中必须继续通过同源 `/api` 访问 `macula-cloud-gateway:9000`、通过同源 `/iam` 访问 `macula-cloud-iam:9010`，保留 SPA fallback、8080 监听、健康检查、非 root Nginx 和现有依赖顺序。
9. 登录成功后的当前用户、租户列表、菜单、权限和首页跳转流程必须保持不变；本变更不得修改 IAM、Gateway、System 服务端代码或接口。
10. README 必须说明配置层次、覆盖优先级、IDE 与 Docker 差异，并明确浏览器中的 client secret 不是安全秘密，只允许 public/demo client，禁止使用生产机密。
11. 自动化验证必须证明默认值可用、mode/进程配置可覆盖、Docker 运行时配置可覆盖且无需重建、登录请求实际使用覆盖后的 OAuth 参数，并证明组件中不存在 OAuth client/scope/管理员示例值硬编码。

## Non-goals
- 不修改 OAuth2 授权模式、IAM token endpoint、Gateway/IAM 路由或服务端 client 注册模型。
- 不重构登录后的用户、租户、菜单、权限处理，也不移除现有普通用户示例选项。
- 不把 SPA 中的 client secret 提升为真正机密；生产 confidential client 仍不应由浏览器持有。
- 不升级 Vue、Vite、Element Plus、Axios、Node 或其他前端依赖。
- 不处理 `fix/nacos-compose-env` 分支的 Nacos Compose 修复，也不覆盖该工作树的未提交内容。

## Design
适用政策包括根 `AGENTS.md`、`.agents/rules/frontend-development.md`、`.agents/rules/testing.md`、`.agents/rules/dependencies-release.md` 以及根 `REVIEW.md` 的 Bugs、Security、Compliance 三类审查规则；未发现额外的组织级品牌或数据处理 Skill。

Admin 根 `.env` 作为所有 mode 的公共默认层，新增五个 `VITE_APP_*` 登录配置。现有 `.env.development`、`.env.docker`、`.env.dev`、`.env.stg`、`.env.pet`、`.env.prd` 继续只表达 mode 差异；Vite 原生优先级和进程环境覆盖规则不改变。

`src/config/index.js` 将五个字段加入 `DEFAULT_CONFIG`。开发和普通构建从 `import.meta.env` 读取；生产环境继续在加载 `public/config.js` 后通过 `APP_CONFIG` 覆盖。`passwordForm.vue` 只消费配置对象：初始化管理员表单、切回管理员类型以及组装 token 请求时均使用配置值，其余控制流不动。

Docker runtime 参考 Archetype Admin：Nginx 配置保持静态 `/api`、`/iam` 代理；单独增加运行时配置模板与非 root 可写的输出目录，容器入口在启动时把 Compose 环境安全序列化为 `/usr/share/nginx/html/config.js`。Dockerfile不为五个值增加 build args，避免配置变化触发镜像重建。Compose 只向 Admin 容器注入浏览器运行配置，不改变后端共享环境锚点。

配置优先级为：Admin 根 `.env` < `.env.<mode>` < 构建进程环境；生产容器启动后，运行时 `APP_CONFIG` 对上述五个字段具有最终优先级。`/api` 与 `/iam` 保持现有同源值和 Nginx 服务发现，不纳入本次外部地址配置。

## Data and interfaces
不新增后端 API、数据库、消息或持久化结构。新增前端配置键：

- `VITE_APP_OAUTH_CLIENT_ID`
- `VITE_APP_OAUTH_CLIENT_SECRET`
- `VITE_APP_OAUTH_SCOPE`
- `VITE_APP_DEMO_USERNAME`
- `VITE_APP_DEMO_PASSWORD`

`DEFAULT_CONFIG`/`APP_CONFIG` 对应字段为 `OAUTH_CLIENT_ID`、`OAUTH_CLIENT_SECRET`、`OAUTH_SCOPE`、`DEMO_USERNAME`、`DEMO_PASSWORD`。`POST /iam/oauth2/token` 的字段名、编码和响应契约不变。

## Flagged concerns
- 浏览器 client secret 可见：SPA 构建产物、运行时配置和请求均可被用户读取，它只能是 public/demo client 的兼容值，不能提供 confidential client 的保密性；通过文档、示例注释和禁止生产机密约束缓解，non-blocking。
- 运行时 JavaScript 配置注入：未经转义的引号、换行或脚本片段可能破坏 `config.js` 或形成脚本注入；实现必须安全序列化环境值并以包含特殊字符的测试证明，blocking。
- Compose 文件并行修改：当前另一个 worktree 的 `fix/nacos-compose-env` 正在修改同一 `deploy/docker-compose.yml`；本分支不得读取或覆盖其未提交内容，合并前需要由维护者解决分支级冲突，non-blocking。
- 本地 demo 凭据暴露：保留当前默认账号和 client 是兼容要求，但会降低误部署时的安全性；Compose 默认回环绑定和文档警告必须保留，共享或生产环境必须覆盖，non-blocking。

## Verification strategy
1. 静态扫描 `passwordForm.vue`，确认不再出现当前 client ID、`client_secret: 'secret'`、scope 和管理员账号密码字面量；确认 IAM/Gateway/System 后端无改动。
2. 使用 `npm ci` 后运行相关 Vitest，验证 `DEFAULT_CONFIG` 从 mode 环境读取五个字段，并验证生产 `APP_CONFIG` 覆盖优先级。
3. 扩展 Cypress 登录测试：用测试专用运行时 `config.js` 覆盖 OAuth client、scope 和示例账号，拦截 token 请求并断言 form-urlencoded body 使用覆盖值；同时保留登录页可访问和 IAM 失败后按钮恢复验证。
4. 分别运行 `npm run build`、`npm run build:docker`，并至少用一个非默认 mode/进程变量构建，证明 mode 覆盖生效且现有命令兼容。
5. 运行 `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml --profile apps config --quiet`，检查 Admin 容器环境、端口、依赖和健康检查。
6. 构建并正常启动 Admin 镜像，使用两组不同容器环境而不重建镜像，分别读取生成的 `config.js` 并验证特殊字符安全序列化、UID 101、Nginx 配置有效、`/api` 与 `/iam` 代理及 SPA fallback。
7. 执行 `git diff --check`，并按 `REVIEW.md` 独立检查 Bugs、Security、Compliance；明确报告测试数量、失败/错误/跳过和未执行的真实 IAM 登录范围。
