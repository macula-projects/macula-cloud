# Spec: 开发与 Docker Compose 部署支持 (from intent.md 2026-09-30)
Status: accepted

## Source intent
[已接受的 intent.md](./intent.md)：用简单、清晰的 Docker Compose 同时支持中间件初始化、IDE 调试和容器化运行；每个模块就近提供 Dockerfile，不再提供 Kubernetes。2026-10-01 用户进一步明确：Seata、SnailJob、Docs、RocketMQ 管理服务尚不完整，保留现有入口但不纳入本次阻断性验收。

## Requirements
1. 删除本变更产生的全部 Kubernetes 模板、脚本、示例配置和文档，不提供 Helm、Kustomize 或其他集群部署入口。
2. Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务、Docs 服务和 Admin 保留各自模块目录中的 `Dockerfile`；本次阻断性构建与运行验收只覆盖 Gateway、IAM、System、TinyID 和 Admin。
3. 验收范围内的 Java 模块必须能够从仓库根目录以 `docker build -f <module>/Dockerfile .` 独立构建；构建阶段使用 Java 17/Maven，运行阶段使用 Java 17 JRE 和非 root 用户。Seata、SnailJob、Docs、RocketMQ 管理服务的 Dockerfile 仅做静态存在性与明显 Secret 检查，不要求本次构建或启动成功。
4. 模块 Dockerfile 保持相同、可直接阅读的构建约定，只允许模块路径、应用端口等必要差异；不引入需要预先发布的自定义基础镜像。Compose 中的重复配置使用 YAML anchor 适度提炼。
5. Admin Dockerfile 必须执行可重复的 `npm ci` 和生产构建，并用非 root Web Server 提供静态资源、SPA 路由回退及 Gateway/IAM 反向代理。
6. `deploy/docker-compose.yml` 必须统一保留 MySQL、Redis、Nacos、RocketMQ 中间件、初始化任务、八个后端模块和 Admin 的编排入口；SnailJob、Seata 是应用服务，不能归入中间件。本次运行验收不包含 Seata、SnailJob、Docs 和 RocketMQ 管理应用。
7. Compose 默认只启动 MySQL、Redis、Nacos、RocketMQ 及初始化任务，供源码在 IDE 中调试；`apps` profile 保留全部应用容器和 Admin 的入口，但本次验收通过显式服务列表只启动范围内应用。
8. Compose 必须提供固定镜像版本、持久化卷、健康检查、启动依赖和默认绑定 `127.0.0.1` 的开发端口；应用容器之间通过 Compose 服务名通信。
9. MySQL bootstrap 负责幂等创建应用账号及 `macula-system`、`macula-tinyid`、`seata`、`macula-snailjob`、`nacos` 五个数据库，并使用 Nacos 官方 SQL 初始化 Nacos 中间件数据库；Nacos 数据库不由 Flyway 管理。
10. System、TinyID 两个验收范围内的关系数据库模块必须在模块内集成 Flyway，并分别管理 `macula-system`、`macula-tinyid`。IAM 与 System 共用数据库，但数据库迁移所有权只属于 System，IAM 不得重复执行 migration。Seata、SnailJob 已存在的 Flyway 材料保留，但本次不对其正确性作验收结论。
11. System、TinyID 的现有初始化 SQL 必须作为各自 `src/main/resources/db/migration/` 下的 `V1__baseline.sql`；后续数据库变更只能新增有序 migration，不得修改已执行的 migration。已有数据库若存在关键表但没有 Flyway history，使用明确的 baseline-on-migrate 策略接管，不能重放旧 dump。
12. Nacos 初始化必须在 Nacos 服务健康后幂等创建 namespace 和 Seata 最小配置。普通启动不得删除、覆盖或重建已有数据，重置必须显式确认。
13. 应用配置必须支持本机 IDE 的 `127.0.0.1` 默认值和 Compose 环境变量覆盖；Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务、Docs 和 Admin 的端口不得冲突。
14. 为实现容器干净构建和启动，只允许修复已确认的最小装配或依赖兼容问题；不得修改业务规则、公共 API、数据库领域结构、租户或权限行为。
15. `deploy/scripts/compose.sh` 必须提供基础设施启动、完整应用启动、部分服务启动、状态、日志、停止、构建和显式数据重置入口；Flyway 的 migrate/info/validate 由对应模块或 Maven 命令执行，不创建通用 Flyway 容器。
16. 仓库只提交本地开发示例值；实际 `.env`、凭据、token、私钥及构建产物必须被忽略，不得烘焙进镜像。
17. 中文部署文档必须说明版本、端口、数据库所有权、模块内 Flyway migration 规范、已有数据库 baseline、服务分组、IDE 启动、容器启动、模块独立构建、重复启动、重置和已知限制，并明确 Seata、SnailJob、Docs、RocketMQ 管理服务不属于本次绿色验收范围。

## Non-goals
不提供 Kubernetes、Helm、Kustomize、生产级高可用、证书、备份恢复或跨可用区方案；不发布镜像、不部署远端环境；不实现或验收 Seata、SnailJob、RocketMQ 管理服务、Docs 服务缺失的业务功能及运行完整性；不升级 Java、Macula Boot、Spring Boot、Spring Cloud、Seata、SnailJob 或其他既有依赖版本；不为 Nacos 等中间件数据库引入 Flyway；不修改公共 REST/Feign 契约、租户、认证或授权规则。

## Design
保留单个 `deploy/docker-compose.yml`。MySQL、Redis、Nacos、RocketMQ 与初始化任务不设置 profile，因此默认 `docker compose up` 即得到 IDE 调试环境；八个后端模块和 Admin 继续设置 `apps` profile。完整入口仍保留，但本次绿色验证使用显式服务列表，只启动 Gateway、IAM、System、TinyID 和 Admin。

每个 Java 模块目录保留独立 Dockerfile，构建上下文统一为仓库根目录。Dockerfile 使用相同的多阶段结构并明确写出自身 Maven module 和端口。由于 Dockerfile 没有可靠的 include 机制，本次优先保持模块独立、直观和可复制，不再使用集中式参数化 Dockerfile或自定义基础镜像；Compose 通过 extension fields/YAML anchors 复用公共环境变量、重启策略和日志/资源约定。阻断性镜像验证只覆盖 Gateway、IAM、System、TinyID 和 Admin。

基础设施继续使用固定版本和命名卷。MySQL 健康后运行 bootstrap，创建账号和五个数据库，并只对 Nacos 数据库执行与固定 Nacos 版本匹配的官方 schema。Nacos 服务随后启动，namespace 与 Seata 配置仍由 Nacos 初始化任务处理。

System、TinyID 在自身 POM 中使用由父依赖管理的 Flyway 核心与 MySQL 支持，并在自身资源目录维护 migration。应用启动时由 Spring Boot Flyway 自动配置先迁移所属数据库，再完成模块装配。IAM 虽访问 `macula-system`，但不包含 migration；验收启动顺序确保 System 完成迁移后再启动 IAM。Seata、SnailJob 的现有 Flyway 配置和 migration 保留为后续完善基础，本次不据此声明其可用。

为兼容当前已经由旧初始化脚本创建的 volume，System、TinyID 对非空且没有 `flyway_schema_history` 的数据库启用明确的 V1 baseline 接管策略；空数据库执行 V1 migration。状态不明确或 migration checksum 不一致时必须失败，不自动修复或清库。Seata、SnailJob 的相同配置保留但不纳入本次结论。

验收范围内的应用服务使用 Compose DNS 地址连接 MySQL、Redis、Nacos、RocketMQ 中间件及其他应用。Admin 通过自身 Nginx 将 `/api/` 和 `/iam/` 转发到 Gateway/IAM。Seata、SnailJob、Docs、RocketMQ 管理应用可以保留在 `apps` profile 中，但不作为本次绿色门槛；运行验收使用显式服务列表排除它们。

模块运行配置采用环境变量覆盖，不新增远程配置协议。Seata、SnailJob、Docs 与 RocketMQ 管理服务的现有最小装配、端口和进程探测继续保留，但不据此作本次可用性结论。验收范围内若干净构建暴露当前依赖与源码命名空间不兼容，只允许最小机械兼容修复并验证无业务行为变化。

## Data and interfaces
不新增公共 API 或新的领域表。部署数据仍保留 `macula-system`、`macula-tinyid`、`seata`、`macula-snailjob`、`nacos` 五个数据库。System/TinyID 使用仓库现有 SQL 作为模块 V1 migration；Nacos 继续使用中间件官方初始化 SQL，不生成 Flyway 元数据。Seata、SnailJob 数据库与 migration 材料保留但不纳入本次数据验证结论。

新增部署接口包括各模块 Dockerfile、Compose `apps` profile、四个模块内的 Flyway migration 目录、标准环境变量、固定服务名和 `compose.sh` 命令。端口沿用 Gateway 9000、IAM 9010、System 9081、TinyID 9082、RocketMQ 管理服务 9083、Docs 9084、SnailJob 9086/17888、Seata 9091/8091、Admin 5900/8080。

## Flagged concerns
- System 干净构建：当前父依赖中的 MyBatis-Plus 3.5.17 已将 `IService`/`ServiceImpl` 从 `extension.service` 移至 `spring.service`，而 System 源码仍使用旧包名；完整镜像构建需要授权机械迁移相关 import，不能把旧 `target` 产物当作通过，blocking
- TinyID 启动：当前完整测试曾因 Druid 自动配置引用 Spring Boot 4 已不存在的 `DataSourceProperties` 而失败；本规格不允许自行升级依赖，实施应先确认是否为实际启动阻断，若需要改依赖版本则必须暂停并另行确认，blocking
- Dockerfile 重复：八个 Java Dockerfile 会有少量结构性重复，但使用自定义基础镜像、生成脚本或符号链接会增加使用门槛；本规格选择可读、模块独立的重复，并只在 Compose 中提炼公共项，non-blocking
- Flyway 历史接管：已有应用数据库的实际 schema 可能与仓库 V1 SQL 不完全一致；baseline-on-migrate 只能避免重放，不能证明 schema 完全一致，因此首次接管前仍应备份并核对，non-blocking
- 共享数据库所有权：IAM 与 System 共用 `macula-system`，只有 System 执行 migration；独立启动 IAM 前必须保证 System 已完成迁移，否则 IAM 可能因缺表启动失败，non-blocking
- 上游 SQL 演进：Seata、SnailJob 升级时必须在所属模块新增 migration，而不是替换 V1；本变更锁定当前组件版本，不处理未来升级脚本，non-blocking
- 完整运行验证：需要可用 Docker daemon 和足够内存；若当前环境仍无法连接 Docker，只能完成 Compose 解析、模块构建和静态检查，不能声称完整容器环境已运行，non-blocking
- 延后模块现状：Seata、SnailJob、Docs、RocketMQ 管理服务保留的入口和兼容代码尚未形成完整可用性承诺，后续应另立变更补齐和验证，non-blocking
- 验收范围与原 intent 的冲突：原 intent 将 Seata、SnailJob、Docs、RocketMQ 管理服务列为受影响并期望完整容器化运行，用户现要求将四者排除出本次测试；必须由 reviewer 明确接受该缩小范围，接受后它们的构建、启动、Flyway 和业务可用性不再阻断本次绿色结论，blocking
- RocketMQ 名称歧义：排除项仅指 `macula-cloud-rocketmq` 管理应用，NameServer、Broker 和 `rocketmq-init` 仍属于基础设施验收范围，non-blocking

## Verification strategy
1. 对 Compose 执行 `docker compose config`，分别解析默认服务集合和 `apps` profile，核对固定镜像、端口、卷、健康检查、数据库迁移所有权、依赖、服务 DNS 和环境变量。
2. 构建 Gateway、IAM、System、TinyID 和 Admin 镜像；检查 Java 17、非 root 用户、可执行 JAR、暴露端口和镜像中无 Secret。Seata、SnailJob、Docs、RocketMQ 管理服务只做 Dockerfile 静态检查。Admin 额外验证 `npm ci`、生产构建和 SPA/代理配置。
3. 在干净 volume 启动默认基础设施，确认五个数据库已创建、Nacos 官方 schema 已导入、Nacos namespace/Seata 配置和 RocketMQ 可用；确认默认中间件路径不创建任何 Flyway history。
4. 分别启动 System、TinyID，确认两个模块执行 V1、创建各自 `flyway_schema_history` 和关键表；重复启动确认无待执行 migration 且数据保留。
5. 用已有关键表但没有 Flyway history 的 System、TinyID 数据库验证 baseline 接管路径；验证 migration checksum 被修改时启动或 validate 失败，失败 migration 不会被静默忽略。
6. 使用 `apps` profile 和显式服务列表启动 Gateway、IAM、System、TinyID、Admin，确认 System 迁移先于 IAM，检查进程、端口、服务注册及 Gateway/IAM/System 基本调用链；不启动 Seata、SnailJob、Docs、RocketMQ 管理应用。
7. 运行 System、TinyID 相关测试和打包、IAM Security 目标测试、Gateway 编译测试及管理端 `npm ci`/build/E2E；不运行 Seata、SnailJob、Docs、RocketMQ 管理应用的测试。
8. 运行 `sh -n`、可用时的 `shellcheck`、`git diff --check`，扫描真实 Secret、内网地址、floating tag、Kubernetes 遗留、构建产物及范围外 API/业务修改。
