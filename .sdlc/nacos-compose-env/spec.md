# Spec: 修复 Nacos Compose 环境变量透传 (from intent.md 2026-10-02)
Status: accepted

## Source intent
[intent.md](./intent.md)：默认环境样例和自定义 `.env` 都应把预期的 Nacos namespace、用户名和密码传给全部相关 Java 应用。

## Requirements
1. 使用 `deploy/.env.example` 渲染 `apps` profile 时，所有使用公共 Java 应用环境的服务都必须得到 `NACOS_NAMESPACE=MACULA5`、`NACOS_USERNAME=nacos`、`NACOS_PASSWORD=nacos`。
2. 使用自定义 `NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD` 渲染时，同一组服务必须分别得到三个自定义值，不能保留默认值或 Compose 表达式字面量。
3. Compose 渲染不得再报告 `symbol_dollar` 未定义，也不得在最终环境中残留 `{NACOS_NAMESPACE:-...}`、`{NACOS_USERNAME:-...}` 或 `{NACOS_PASSWORD:-...}`。
4. 现有变量名、默认值、Java 服务集合、profile、镜像、端口、依赖、健康检查、卷、网络和启动顺序必须保持不变。
5. Nacos 服务端现有鉴权开关与身份配置保持不变；不得提交真实 namespace、用户名、密码或其他凭据。

## Non-goals
不调整 Nacos 服务端鉴权策略，不修改应用 `application.yml`、Nacos 初始化脚本或 `.env.example`，不改变共享环境和生产环境配置，不启动或部署完整 Macula Cloud，不处理与三项 Nacos 环境变量无关的 Compose 问题。

## Design
修正 `deploy/docker-compose.yml` 的公共 Java 应用环境映射，使三项 Nacos 配置使用普通 Compose 变量插值。各 Java 服务继续通过既有 YAML anchor 合并同一份环境映射；没有新的服务、条件分支、配置层或迁移路径。

默认渲染流为 `.env.example` 或 Compose 默认值进入公共环境映射，再进入启用 `apps` profile 的 Java 容器。覆盖流保持相同，仅由调用者提供的环境值替代默认值。

## Data and interfaces
不涉及 API、数据库 schema、持久化或消息契约变化。部署接口仍为 `NACOS_NAMESPACE`、`NACOS_USERNAME`、`NACOS_PASSWORD` 三个现有环境变量，默认值仍分别为 `MACULA5`、`nacos`、`nacos`。

## Flagged concerns
- 本地 Nacos 服务端当前关闭鉴权：用户名和密码会完整透传，但本地服务端不会据此强制认证；这是既有且由 intent 明确要求保持的行为，non-blocking。
- `nacos/nacos` 是公开的本地示例值：仅允许作为回环绑定的开发默认值，不能被解释为共享或生产凭据；现有策略已要求生产环境外部注入，non-blocking。
- 本变更只验证 Compose 渲染，不启动完整应用栈：三项值的运行时注册行为此前可由应用配置支持，但本规格不把静态修复表述为完整运行验证，non-blocking。

## Verification strategy
1. 对 `deploy/docker-compose.yml` 的 `apps` profile 执行 `docker compose config --quiet`，确认语法与插值成功且标准错误中没有 `symbol_dollar` 警告。
2. 使用 `deploy/.env.example` 输出 JSON 配置，逐个断言 macula-cloud-docs、gateway、iam、rocketmq、seata、snailjob、system、tinyid 的三项环境值等于默认值。
3. 设置一组无敏感意义的自定义测试值重新输出 JSON配置，逐个断言八个服务收到覆盖值。
4. 扫描渲染结果，确认不存在 `symbol_dollar`、三项 Compose 表达式字面量或空值。
5. 执行 `git diff --check` 并检查差异只包含已验收 SDLC 文档和三项环境变量插值修复。
