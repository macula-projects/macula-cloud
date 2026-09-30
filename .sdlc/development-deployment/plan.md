# Plan: 开发与 Kubernetes 部署支持 (from spec.md 2026-09-30)

## Files that change
- SDLC 工件：`.sdlc/development-deployment/plan.md`；如实施发生偏差，在本文件对应的文件、顺序、风险或证明条目中追加偏差说明。
- 仓库入口与忽略规则：修改 `.gitignore`、根 `README.md`，补充部署入口并忽略本地 `.env`、临时渲染目录和运行产物。
- Compose 与开发文档：实现 `deploy/docker-compose.yml`，新增 `deploy/.env.example`、`deploy/README.md`、`deploy/scripts/compose.sh`。
- Compose 初始化与中间件配置：新增 `deploy/init/mysql/init.sh`、`deploy/init/mysql/nacos-mysql.sql`、`deploy/init/mysql/seata-mysql.sql`、`deploy/init/mysql/snail-job-mysql.sql`、`deploy/init/nacos/init.sh`、`deploy/init/nacos/seata.properties`、`deploy/rocketmq/broker.conf`；System 与 TinyID 继续直接使用 `macula-cloud-system/docs/macula-system-dump.sql` 和 `macula-cloud-tinyid/docs/macula-tinyid-dump.sql`，不复制领域 SQL。
- 本机及容器运行配置：修改 Gateway、IAM、System、TinyID、Seata、SnailJob 的现有 `src/main/resources/application.yml` 或 `bootstrap.yml`，把基础设施地址、凭据、namespace、服务地址及端口改为“环境变量覆盖 + 当前本地默认值”；新增 `macula-cloud-rocketmq/src/main/resources/application.yml`、`macula-cloud-docs/src/main/resources/application.yml`，分别使用不冲突的默认端口 `9083`、`9084`。
- 最小启动修复与证明：修改 `macula-cloud-docs/src/main/java/dev/macula/cloud/docs/MaculaDocsApplication.java` 补齐 Spring Boot 应用装配；新增 `macula-cloud-docs/src/test/java/dev/macula/cloud/docs/MaculaDocsApplicationTest.java` 和 `macula-cloud-rocketmq/src/test/java/dev/macula/cloud/rocketmq/MaculaRocketMQApplicationTest.java`，只验证最小应用装配，不扩展业务功能。
- 后端镜像：新增根 `.dockerignore`、`deploy/docker/Dockerfile.java`，以 `MODULE`、JAR 路径和端口参数构建 Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务和 Docs 的 Java 17 非 root 镜像。
- 管理端镜像：实现现有空文件 `macula-cloud-admin/Dockerfile`，新增 `macula-cloud-admin/nginx.conf`、`macula-cloud-admin/.dockerignore`、`macula-cloud-admin/.env.production`，提供可配置的 Gateway/IAM 构建参数、SPA 回退和非 root 静态服务。
- Kubernetes 入口：删除空的 `deploy/k8s.yml`，改由 `deploy/k8s/` 承载原生 YAML 模板和脚本；新增 `deploy/k8s/env.example`、`deploy/k8s/secrets.env.example`、`deploy/k8s/lib.sh`、`deploy/k8s/apply.sh`、`deploy/k8s/status.sh`、`deploy/k8s/delete.sh`、`deploy/k8s/purge-data.sh`。
- Kubernetes YAML：新增 `deploy/k8s/00-namespace.yaml.tpl`、`01-config.yaml.tpl`、`10-mysql.yaml.tpl`、`11-redis.yaml.tpl`、`12-nacos.yaml.tpl`、`13-rocketmq.yaml.tpl`、`20-mysql-init-job.yaml.tpl`、`21-nacos-init-job.yaml.tpl`、`30-cloud-apps.yaml.tpl`、`31-admin.yaml.tpl`、`40-ingress.example.yaml.tpl`。脚本把模板渲染到临时目录后交给 `kubectl`，不会把 Secret 或渲染产物写回仓库。
- 不修改公共 REST/Feign API、领域实体/Mapper、现有 System/TinyID 表结构、Maven 依赖版本、`bands.yaml` 或远端环境。

## Order of work
1. 锁定部署契约：从当前有效 Maven 依赖和官方发布材料确认 MySQL、Redis、Nacos、RocketMQ、Seata 2.0.0、SnailJob 1.9.0 的兼容固定版本；记录每份上游 SQL 的版本、来源和许可证，确认镜像支持当前开发机架构。若官方材料与已接受规格冲突，暂停并更新计划而不是自行换版本。
2. 实现可重复初始化：编写 MySQL 初始化脚本，创建五个数据库并只在目标 schema 缺失时导入对应 SQL；编写 Nacos namespace/Seata 配置初始化脚本，默认只创建缺失项，强制覆盖使用独立参数；为 RocketMQ 编写适合本机客户端连接的 broker 配置。
3. 实现 Compose：使用固定镜像、命名 volume、`127.0.0.1` 默认端口、健康检查和 `service_completed_successfully` 依赖串联 MySQL、数据库初始化、Nacos、Nacos 初始化、Redis、RocketMQ NameServer/Broker；脚本提供 `up`、`up <services>`、`status`、`logs`、`down`、`reset`，其中 `reset` 明确二次确认并单独删除数据。
4. 统一本机运行配置：保留 `local` profile 当前可用默认值，同时为数据库、Redis、Nacos、IAM、Seata、SnailJob 和服务端口增加标准环境变量覆盖；补齐 Docs 的 Spring Boot 装配以及 Docs/RocketMQ 管理服务的独立端口和最小装配测试。先验证这两项最小修复，再扩大到全部模块。
5. 实现镜像构建：使用参数化 Java 17 多阶段 Dockerfile 从仓库根构建各 Maven 模块，运行阶段使用非 root 用户；实现 Admin 的 Node 构建和非 root Nginx 运行镜像，处理 SPA 回退与 Gateway/IAM 地址。逐个核对入口 JAR、端口和平台架构，不把环境 Secret 烘焙进镜像。
6. 实现 Kubernetes 基础设施：先完成 namespace、ConfigMap/Secret 引用、PVC、MySQL、Redis、Nacos、RocketMQ 及两类初始化 Job；初始化 SQL 由 `apply.sh` 从仓库文件创建临时 ConfigMap，避免复制领域 SQL或把大段 SQL重复嵌入 YAML。
7. 实现 Kubernetes 应用层：为八个后端运行模块和 Admin 添加 Deployment/Service、资源限制、滚动策略及 startup/readiness/liveness 探针；内部地址使用 Service DNS，Gateway、Admin、TinyID 仅通过独立示例 Ingress 可选暴露。TCP 探针只标记进程监听，不描述为业务健康。
8. 实现安全的部署脚本：集中在 `lib.sh` 做依赖、参数、Secret、namespace 和模板检查；`apply.sh` 先渲染到 `mktemp` 目录并执行客户端 dry-run，再按依赖顺序部署和等待；`delete.sh` 保留 PVC，`purge-data.sh` 要求显式确认后才删除持久化数据。
9. 同步文档与契约：在 `deploy/README.md` 写明版本、端口、数据库、服务名、启动顺序、IDE 启动配置、管理端启动、重复初始化、重置、镜像构建、Kubernetes 操作、故障诊断和已知限制；根 README 只增加入口链接。逐项核对 Compose、Kubernetes、应用配置和文档使用相同名称与端口。
10. 执行分层证明：先做 shell/YAML/Compose 静态检查和最小模块测试，再做后端/前端构建、Compose 实际启动与重复初始化、镜像构建，最后做 Kubernetes 客户端 dry-run及条件允许时的一次性本地集群验证。记录所有未运行项及环境原因，不发布镜像、不连接远端集群。

## Risks
- 官方 schema 与版本不匹配会导致 Nacos、Seata 或 SnailJob 启动后才暴露错误；通过锁定版本、保留来源、检查关键表并实际启动对应模块降低风险。
- System dump 含种子数据且不是天然幂等；初始化脚本必须在 schema 已存在时跳过，不能在普通重复启动中重放 `DROP/INSERT`。回滚只移除新部署材料，不自动删除用户 volume。
- Spring Boot/Spring Cloud 的环境变量映射、Nacos namespace 和 Seata 配置项可能与当前依赖实际绑定名不同；逐模块检查有效配置并用真实启动证据验证，不能仅凭 YAML 语法判断。
- RocketMQ broker 对 IDE 客户端公布容器内地址、Nacos gRPC 派生端口或 Apple Silicon 镜像架构不正确时会出现“容器健康但宿主机不可用”；必须分别验证宿主机访问及镜像架构。
- 参数化 Maven 镜像构建可能受根 POM 的 `maven.install.skip`、Snapshot 父依赖或不同模块可执行 JAR 布局影响；逐模块构建并检查 JAR 入口，失败时记录偏差后再决定是否改为模块专用构建文件。
- Docs 和 RocketMQ 管理服务目前业务能力不完整；本次仅保证进程装配和端口不冲突，测试不得把空应用启动成功解释为业务功能可用。
- 原生 YAML 模板依赖 shell 渲染，缺少 `envsubst`、`kubectl` 或必需变量时必须快速失败；临时目录使用 `mktemp` 并通过 trap 清理，避免 Secret 残留。
- Kubernetes 示例不是生产级高可用方案；默认单副本、示例 requests/limits、可选 Ingress 和默认 StorageClass 只用于开发/集成基线，文档必须保留该限制。
- TCP 探针存在假健康风险；只在缺少安全 HTTP 健康端点时使用，并通过日志、进程启动和关键调用链补充验证。
- Docker 或本地 Kubernetes 不可用会限制运行证明；这种情况不阻塞生成部署材料，但必须在验证报告中明确区分“静态通过”和“未实际运行”。
- 删除 `deploy/k8s.yml` 虽然该文件当前为空，仍属于文件路径迁移；根 README 和部署 README 必须明确新入口，避免调用方继续引用旧路径。

## Proof
- 文件与静态质量：`git diff --check`；shell 使用 `sh -n`，可用时使用 `shellcheck`；所有模板渲染后进行 YAML 解析；检查无真实 Secret、内网地址、floating image tag、生成目录或运行产物被提交。
- Compose 合约：运行 `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml config`；检查所有 host 端口默认绑定 `127.0.0.1`、镜像固定版本、volume、healthcheck、依赖和一次性任务退出条件。
- 初始化集成：在干净 volume 启动完整基础设施，确认长期服务健康、初始化任务成功、五个数据库及关键表存在、Nacos namespace/Seata 配置可读取、RocketMQ broker 可由宿主机客户端访问；再次执行初始化确认不丢数据、不覆盖已有配置；显式 reset 后确认可从零恢复。
- Java 最小测试：运行 Docs 与 RocketMQ 管理模块的目标测试，证明两个应用可装配且端口配置隔离；随后运行受配置影响模块的 `mvn -pl ... -am test -Plocal`，条件允许时扩大到 `mvn test -Plocal`。外部服务失败与代码失败分别记录。
- 本机冒烟：按文档顺序以 `local` profile 启动 Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务和 Docs，确认端口无冲突并可连接 Compose 基础设施；启动 Admin `npm run dev`，确认其访问本机 Gateway/IAM。未完成业务功能的模块只验证进程和探针契约。
- 前端证明：在 `macula-cloud-admin/` 执行 `npm ci`、`npm run build` 和相关静态检查；验证 Nginx SPA 回退及构建后的 Gateway/IAM 地址。`npm run lint` 带 `--fix`，只有在检查 diff 可控时执行并报告其改动。
- 镜像证明：分别构建八个后端模块和 Admin 镜像，检查 Java 17、非 root 用户、入口、端口、平台架构和镜像历史中无 Secret；启动后执行声明的探针或端口检查。
- Kubernetes 静态证明：对渲染后的全部清单执行 `kubectl apply --dry-run=client` 和可用的 schema validator；脚本覆盖缺参、缺 Secret、等待超时、普通卸载保留 PVC、显式清理数据等路径。
- Kubernetes 运行证明：条件允许时在一次性本地集群从空 namespace 部署，确认基础设施、初始化 Job、八个后端 Deployment、Admin 及关键入口就绪，并验证卸载重装；若未执行，明确列为未验证，不连接共享或远端集群。
- 范围证明：最终 diff 不包含公共 API、领域逻辑、租户/权限行为、依赖版本升级、Helm/Kustomize、远端发布或生产 SLO 变更；按 `REVIEW.md` 对实现执行 Bugs、Security、Compliance 三轮检查。
