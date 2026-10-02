# Plan: 修复 Nacos Compose 环境变量透传 (from spec.md 2026-10-02)
Status: accepted

## Files that change
- `deploy/docker-compose.yml`：修正公共 Java 应用环境中三项 Nacos 变量的 Compose 插值。
- `.sdlc/nacos-compose-env/plan.md`：记录实施顺序和实际偏差；不新增运行时代码或测试文件。

## Order of work
1. 保存修改前的 `apps` profile JSON 渲染结果和标准错误，确认当前三项变量渲染为错误字面量且存在 `symbol_dollar` 警告。
2. 仅修改 `x-app-environment` 中 `NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD` 三行，使用普通 Compose 默认值插值；不调整其他键或服务。
3. 使用 `deploy/.env.example` 执行 Compose 静态渲染，核对八个 Java 服务的三项默认值，并确认不再出现占位符警告或字面量残留。
4. 使用 `verify-namespace`、`verify-user`、`verify-password` 三个无敏感意义的覆盖值重新渲染，核对八个 Java 服务全部收到覆盖值。
5. 比较修改前后 Compose 服务集合、profile、镜像、端口、依赖、健康检查、卷、网络和除三项目标变量外的环境配置，证明非目标部署契约未变化。
6. 执行 `git diff --check`，确认工作区只有本功能的 SDLC 文档和三行 Compose 修复；正式测试阶段再复跑同一证明，不在 Build 阶段将静态检查表述为运行验证。

## Risks
- 调用者 shell 中已有同名变量可能覆盖 `.env.example`，导致默认值验证失真；验证命令会先清除三项变量，并用 `--env-file` 明确输入。
- JSON 查询若漏掉某个 Java 服务会形成假阳性；使用固定的八服务清单逐项断言，并同时核对公共环境 anchor 的所有消费者。
- 误改 YAML anchor 或邻近配置可能扩大影响；实现只替换三行，验证中比较除目标键外的完整渲染契约。
- 回滚只需恢复三行原始插值表达式和对应 SDLC 文档，不涉及数据、volume、应用镜像或环境迁移。

## Proof
- 失败基线：修改前 `docker compose --env-file .env.example --profile apps config --format json` 能复现 `symbol_dollar` 警告和三项错误字面量。
- 默认值覆盖：修改后 `docker compose config --quiet` 成功；JSON 中 macula-cloud-docs、gateway、iam、rocketmq、seata、snailjob、system、tinyid 均为 `MACULA5`、`nacos`、`nacos`。
- 自定义值覆盖：同一八服务均为 `verify-namespace`、`verify-user`、`verify-password`。
- 边界证明：渲染结果无 `symbol_dollar`、无 Compose 表达式字面量、三项目标值不为空；非目标部署契约比较无差异。
- 工作区证明：`git diff --check` 通过，`git status --short` 与文件清单符合已验收范围。
