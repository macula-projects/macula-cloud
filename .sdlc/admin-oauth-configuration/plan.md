# Plan: Admin OAuth 登录配置外置化 (from spec.md 2026-10-02)
Status: accepted

## Files that change
### SDLC contract
- `.sdlc/admin-oauth-configuration/plan.md`：记录获批实施顺序、风险、证明范围与实际偏差。

### Admin configuration and login
- `macula-cloud-admin/.env`：增加 OAuth client ID、client secret、scope、管理员示例用户名和密码的公共本地默认值。
- `macula-cloud-admin/src/config/index.js`：将五个登录配置加入 `DEFAULT_CONFIG`，保留生产 `APP_CONFIG` 最终覆盖语义。
- `macula-cloud-admin/public/config.js`：记录五个字段的生产运行时覆盖入口。
- `macula-cloud-admin/src/views/common/login/components/passwordForm.vue`：从共享配置读取管理员示例账号和 OAuth password grant 参数，移除对应硬编码，不改变普通用户与登录后流程。

### Admin Docker runtime configuration
- `macula-cloud-admin/Dockerfile`：复制运行时配置模板和 Nginx entrypoint，保证静态目录可由 UID 101 在启动时写入；不增加五个 OAuth/账号 build args。
- `macula-cloud-admin/nginx/templates/config.js.template`（新增）：只接收 Base64 编码后的安全字符，并在浏览器中以 UTF-8 解码为 `APP_CONFIG`。
- `macula-cloud-admin/nginx/entrypoint.d/25-admin-runtime-config.sh`（新增）：POSIX Shell 启动脚本，逐项读取容器环境、Base64 编码并通过白名单 `envsubst` 生成最终 `config.js`。
- `deploy/.env.example`：增加五个仅限本地 public/demo client 与示例账号的变量及安全说明。
- `deploy/docker-compose.yml`：只在 `macula-cloud-admin` 服务中注入五个运行时变量，默认值与 Admin 根 `.env` 一致；不改变共享后端环境锚点、端口、依赖或服务名。

### Documentation and tests
- `macula-cloud-admin/README.md`：说明根 `.env`、mode、进程环境、Docker runtime `APP_CONFIG` 的优先级，以及 SPA client secret 的公开边界。
- `macula-cloud-admin/tests/unit/config.test.js`（新增）：验证默认配置字段与生产运行时覆盖行为，不把测试放入 `src` 生产扫描路径。
- `macula-cloud-admin/cypress/e2e/example.cy.js`：拦截测试专用运行时 `config.js` 和 token 请求，断言表单默认值与 form-urlencoded OAuth 参数来自覆盖配置，并保留现有页面/IAM 失败用例。

不修改 `macula-cloud-admin/nginx.conf`、IAM/Gateway/System 后端、前端依赖和 `package-lock.json`。

## Order of work
1. 在 Admin 根 `.env`、`src/config/index.js` 和 `public/config.js` 建立五个配置字段及当前默认值，确保 Vite 原生 mode/进程覆盖语义不变。
2. 修改 `passwordForm.vue`：导入共享配置，管理员表单初始化、切回管理员类型及 token 请求全部读取配置；普通用户 `user/user`、错误提示、租户、用户、菜单与跳转代码保持原样。
3. 先新增无需 Docker 的 Vitest/Cypress 覆盖，证明配置读取、运行时覆盖和 token body；通过静态扫描阻止 OAuth client/scope/管理员示例值重新写入组件。
4. 新增 Base64 运行时模板和 POSIX entrypoint。脚本只导出五个编码变量，使用显式变量白名单调用 `envsubst`，避免容器环境意外替换模板中的其他内容。
5. 调整 Dockerfile：保持 Node 20.19.5 构建和 Nginx 1.27.5 runtime；复制模板/脚本，构建期以 root 调整 `/usr/share/nginx/html` 所有者，最终仍以 `101:101` 运行。
6. 更新 `deploy/.env.example` 与 Admin Compose service environment。不得采用或覆盖另一个 worktree 的 Nacos Compose 未提交改动；只修改当前分支基线中 Admin service 的最小区块。
7. 更新 Admin README，明确 IDE、普通构建、Docker runtime 的配置来源和优先级，以及 demo client 的安全限制。
8. 按 Proof 从静态检查、前端测试、构建、Compose 渲染到真实非 root 镜像启动逐层验证；记录任何实际偏差后再提交 Test 阶段。

## Risks
- `client_secret` 和示例密码无论明文还是 Base64 都能被浏览器读取；Base64 只用于安全序列化，不是加密。文档必须明确仅允许 public/demo client，禁止生产机密。
- 直接把环境值插入 JavaScript 会产生语法破坏或脚本注入风险。entrypoint 必须先对原始 UTF-8 字节做 Base64 编码，模板中只出现 Base64 字符；验证必须覆盖引号、反斜杠、换行、`</script>` 和中文。
- Nginx unprivileged 镜像默认静态目录可能不可写。Dockerfile 只在构建阶段切换 root 调整目录所有权，最终镜像继续以 UID/GID 101 运行；启动日志不得出现模板写入失败后静默使用旧 `config.js`。
- `public/config.js` 会被 Vite 复制进构建产物，entrypoint 必须原子或可靠地覆盖同一路径。生成失败应使容器启动失败，不能带空 client 悄悄运行。
- `deploy/docker-compose.yml` 与 `fix/nacos-compose-env` 存在并行修改。当前分支只能基于 `main` 修改 Admin service 区块，合并时必须保留 Nacos 分支修复并重新执行 Compose 渲染。
- 新增根 `.env` 字段会进入所有 mode 构建；共享环境必须通过 mode/进程或运行时配置覆盖本地 demo 值。默认回环绑定与安全注释不得移除。
- 回滚可按本分支提交整体回退；不涉及数据库、服务端协议、持久化数据或不可逆迁移。

## Proof
1. 运行 `git diff --check`、`sh -n macula-cloud-admin/nginx/entrypoint.d/25-admin-runtime-config.sh`，并用 `rg` 证明 `passwordForm.vue` 不包含 client ID、client secret、scope、管理员示例值字面量，后端模块与 `package-lock.json` 无变更。
2. 在 `macula-cloud-admin` 执行 `npm ci` 和 `CI=true npm run test:unit`；报告 Vitest 测试数、失败/跳过，并验证默认字段和生产 `APP_CONFIG` 覆盖。
3. 执行 `npm run build`、`npm run build:docker`，再用一组非默认 mode/进程配置构建并检查产物，证明 `.env < .env.<mode> < process env`。
4. 执行 `npm run test:e2e:ci`；Cypress 以测试专用 `config.js` 设置非默认 OAuth client/scope/账号，拦截 `/iam/oauth2/token` 并断言 form-urlencoded body、失败提示与按钮恢复，保留现有登录页断言。
5. 复制 `deploy/.env.example` 为隔离验证用 env 后，运行 `docker compose --env-file ... -f deploy/docker-compose.yml --profile apps config --quiet`，断言 Admin 环境包含五个变量且 Gateway/IAM service、端口、依赖、健康检查未改变。
6. 构建一次 Admin 镜像，以两组不同容器环境分别启动而不重建；确认 UID/GID 101、Nginx `-t`、SPA fallback、`/api` 与 `/iam` 代理，并验证两次生成的 `config.js` 分别反映环境值。
7. 用包含单引号、双引号、反斜杠、换行、`</script>` 和中文的测试值启动镜像；由浏览器或 Node 解析生成的 `config.js`，断言解码值逐字一致且未生成额外脚本语句。
8. 按 `REVIEW.md` 执行 Bugs、Security、Compliance 三个独立检查；明确浏览器 client secret 的公开性质、未执行的真实 IAM 登录范围，以及与 `fix/nacos-compose-env` 合并后需重跑的 Compose 检查。
