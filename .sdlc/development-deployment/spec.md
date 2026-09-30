# Spec: 开发与 Kubernetes 部署支持 (from intent.md 2026-09-30)
Status: accepted

## Source intent
[已接受的 intent.md](./intent.md)：为本机 IDE 调试提供可快速启动、可重复初始化的基础设施，并提供原生 Kubernetes YAML 部署入口。

## Requirements
1. `deploy/docker-compose.yml` 必须只编排开发基础设施，不容器化启动 Macula Cloud 后端模块或管理端；默认基础设施范围为 MySQL、Redis、Nacos 和 RocketMQ。
2. 开发人员必须能够用一条文档化命令启动完整基础设施；同时必须能够显式选择单个或部分中间件进行启动、停止和状态检查。
3. Compose 暴露给本机 IDE 的端口必须默认绑定 `127.0.0.1`，允许通过示例环境文件覆盖，且不得与仓库已有应用端口冲突。
4. MySQL、Redis、Nacos 和 RocketMQ 必须有持久化卷、健康检查和明确的启动依赖；初始化任务必须等待依赖健康后再执行，并以可观察的成功或失败状态退出。
5. MySQL 初始化必须创建并初始化 `macula-system`、`macula-tinyid`、`seata`、`macula-snailjob` 和 Nacos 所需数据库；System 与 TinyID 使用仓库现有 SQL，其他数据库使用与实际组件版本匹配且记录来源的初始化材料。
6. 数据与配置初始化必须可重复执行：已有数据不得因普通启动或重复初始化被删除；破坏性重置必须使用单独、显式且带警告的命令。
7. Nacos 初始化必须创建或确认本地默认 namespace，并提供 Seata 等模块启动所需的最小配置；重复执行不得产生冲突或覆盖用户已修改的配置，除非调用明确的强制更新命令。
8. 本机开发配置必须使 Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务和 Docs 服务能够使用 `local` profile 在 IDE 中以互不冲突的端口启动；管理端使用 Vite 在本机启动并访问本机 Gateway/IAM。
9. SnailJob 和 Seata 必须作为 Macula Cloud 应用模块由 IDE 启动，不得作为 Compose 中间件服务启动；RocketMQ broker 属于中间件，`macula-cloud-rocketmq` 属于 Cloud 管理服务，两者必须明确区分。
10. 对当前缺少完整启动配置的 Cloud 模块，只允许补充实现“可启动、可配置端口、可探测”所需的最小装配，不得在本变更中实现其尚未完成的业务能力。
11. 所有需要部署到 Kubernetes 的后端模块及管理端必须有可重复构建的容器镜像定义；Java 镜像保持 Java 17 兼容、使用非 root 用户运行，前端镜像提供静态资源服务和 SPA 路由回退。
12. `deploy/k8s/` 必须提供可直接审阅的原生 Kubernetes YAML，不依赖 Helm 或 Kustomize，并按 namespace、配置、Secret 引用、持久化、中间件、初始化任务、Cloud 应用、管理端和可选入口分层组织。
13. Kubernetes 中间件必须使用持久化声明和稳定服务名；Cloud 应用及管理端必须使用 Deployment/Service，并配置资源 requests/limits、startup/readiness/liveness 探针及滚动更新策略。
14. Kubernetes 配置必须通过 ConfigMap、Secret 引用和环境变量注入；仓库只提供无敏感信息的示例，真实密码、token、私钥、镜像仓库凭据和集群地址不得进入版本库。
15. Kubernetes 部署脚本必须支持预检、部署、等待就绪、查看状态和删除工作负载；删除持久化数据必须是独立的显式操作，不能随普通卸载执行。
16. Kubernetes 镜像仓库、镜像 tag、namespace、Ingress host 和 StorageClass 必须可由部署者配置；脚本应在缺少必需值时快速失败，不得静默使用共享或生产环境默认值。
17. 必须提供中文部署文档，覆盖前置条件、端口、默认示例账号的适用范围、首次启动、重复初始化、IDE 启动顺序、管理端启动、故障诊断、数据重置、Kubernetes 操作和已知限制。
18. Compose 与 Kubernetes 的服务名、数据库名、端口、配置键、镜像名和启动顺序必须保持一致，并以文档化部署契约为单一核对清单。

## Non-goals
本变更不实现 RocketMQ 管理服务或 Docs 服务尚未完成的业务功能，不修改公共 REST/Feign 契约，不改变租户、认证或授权规则，不升级 Java、Macula Boot、Spring Boot、Spring Cloud、Seata 或 SnailJob 版本，不提供 Helm/Kustomize，不发布镜像、不连接或修改远端集群，也不声称提供生产级高可用、备份恢复、跨可用区容灾或完整可观测性方案。

## Design
适用政策包括根目录 `AGENTS.md` 的 Java 17、配置分层、秘密管理和 AI-SDLC 门禁；`.agents/rules/architecture.md` 的模块职责与安全边界；`.agents/rules/dependencies-release.md` 的部署、启动顺序、健康检查和发布安全要求；`.agents/rules/testing.md` 的外部依赖验证与结果报告要求；`REVIEW.md` 的 Bugs、Security、Compliance 三轮审查要求。`bands.yaml` 目前是示例基线，不作为本次部署 SLO。

开发路径采用“基础设施容器 + 本机应用”的双层结构：

1. `deploy/docker-compose.yml` 仅启动 MySQL、Redis、Nacos、RocketMQ NameServer/Broker，以及完成数据库和 Nacos 初始化的一次性任务。应用进程不加入 Compose 网络，而是通过发布到 `127.0.0.1` 的端口连接基础设施。
2. `deploy/.env.example` 保存可公开的本地默认值和端口覆盖项；实际 `deploy/.env` 不提交。所有镜像使用固定版本而非 floating tag，最终版本在实施计划中依据当前依赖兼容矩阵锁定。
3. 初始化材料放在 `deploy/init/`，按数据库和配置中心分类。初始化过程记录 schema/config 版本或校验标记，普通重复执行只补齐缺失项；显式 reset 流程才允许删除 volume 或重建数据库。
4. IDE 启动顺序为基础设施及初始化完成后，依次启动数据/平台服务、IAM/System、Gateway，再启动 Vite 管理端；实际可并行项和健康检查地址在实施计划中由模块装配验证确定。
5. 后端端口沿用已存在的 Gateway `9000`、IAM `9010`、System `9081`、TinyID `9082`、Seata HTTP `9091`、SnailJob HTTP `9086` 和 SnailJob gRPC `17888`。RocketMQ 管理服务与 Docs 服务必须分配未占用且可覆盖的本地端口；管理端沿用 Vite 配置的 `5900`。所有服务地址通过环境变量覆盖，避免把容器内地址写死到本机配置。
6. 后端容器镜像采用共享的 Java 17 多阶段构建约定，但每个可运行模块保留明确的构建目标、入口类、暴露端口和健康契约；管理端采用 Node 构建阶段与非 root 静态服务器运行阶段。镜像不携带环境 Secret。

Kubernetes 路径采用原生清单加小型 shell 驱动脚本：

1. `deploy/k8s/` 中的 YAML 按可预测顺序拆分，使用一个可配置 namespace。MySQL、Nacos、RocketMQ broker 使用有状态工作负载或等价的持久化模式；Redis 根据本次开发/验证定位使用单实例持久化部署。初始化使用 Job，并通过重试与明确退出码体现结果。
2. Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务、Docs 服务和 Admin 分别使用 Deployment/Service。内部调用使用 Kubernetes Service DNS；仅 Gateway、Admin、TinyID 的外部访问按现有职责提供可选 Ingress/Service 暴露，默认不生成真实域名或公网负载均衡。
3. 非敏感配置进入 ConfigMap；数据库、Nacos、OAuth/Seata 等凭据只通过 Secret key 引用。仓库提供 Secret 创建说明或不含真实值的模板，部署脚本必须检查必需 Secret 是否存在。
4. 镜像仓库与 tag 由部署脚本参数或环境文件传入。脚本只把渲染结果写入临时目录，执行前运行客户端 dry-run；不把生成的敏感清单写回仓库。
5. 探针优先使用模块已有健康端点；没有可靠 HTTP 健康端点时先使用与模块协议匹配的 TCP/startup 探针，并在文档中标明其证明范围，不能把端口可连通描述为业务健康。

## Data and interfaces
本变更不新增公共 API，也不改变现有领域表结构。部署数据契约包括五个 MySQL 数据库：`macula-system`、`macula-tinyid`、`seata`、`macula-snailjob` 和 Nacos 数据库；现有 System/TinyID dump 继续作为其 schema 与种子数据来源，Seata 2.0.0、SnailJob 1.9.0 和所选 Nacos 固定版本必须使用对应官方 schema，并记录来源与版本。

新增或标准化的运行配置接口包括数据库连接、Redis 地址、Nacos地址/namespace/凭据、RocketMQ NameServer、Seata registry/config/store、SnailJob 数据源、服务端口、前端 Gateway/IAM 地址、镜像仓库/tag、Kubernetes namespace/Ingress/StorageClass。配置必须支持“本机 `127.0.0.1`”和“Kubernetes Service DNS”两套值，但配置键语义保持一致。

Compose 服务名、Kubernetes Service 名和数据库名形成部署契约；README 中必须列出映射关系。任何为 Docs/RocketMQ 管理模块增加的启动注解或端口配置只属于进程装配，不新增业务接口。

## Flagged concerns
- Cloud 模块完整范围：`macula-cloud-docs` 当前启动类缺少完整 Spring Boot 装配，`macula-cloud-rocketmq` 仍标记为 TODO；为满足“工程内模块均可 IDE 启动”，规格包含最小启动修复和独立端口，但不补业务功能，需要 Reviewer 明确接受部署任务包含这部分最小源码/配置变更，blocking
- Kubernetes 定位：intent 未要求生产级高可用，因此本规格将原生 YAML 定位为开发、集成和部署基线；进入共享或生产环境前仍需组织确定副本数、SLO、备份、证书、网络策略、Pod 安全、镜像签名和灾备方案，non-blocking
- 镜像构建范围：仓库当前后端 Dockerfile 和管理端 Dockerfile 均无可用内容；Kubernetes 可部署性要求本变更新增各运行模块的镜像构建定义，这超出只填写两个现有空清单文件的最窄理解，但属于已接受结果的必要条件，non-blocking
- 上游初始化材料：仓库缺少 Nacos、Seata 和 SnailJob 的完整初始化 SQL；实施必须使用与锁定版本匹配的官方材料并保留来源/许可证信息，未经版本核对不得复制任意网上脚本，blocking
- Secret 与旧配置：现有源码中包含空数据库密码、本地示例凭据、Seata 静态 secret 以及 `dev` profile 内网地址；本规格要求新部署路径覆盖而不传播这些值，但全面清理历史配置不在本次范围，non-blocking
- 健康端点：仓库未发现统一 Actuator 健康契约；TCP 探针只能证明监听状态，实施计划必须逐模块确认可用的无副作用健康端点或明确验证局限，non-blocking
- 容器与集群运行条件：完整运行验证需要可用的 Docker Compose 环境以及一个一次性本地 Kubernetes 测试集群；如果执行环境缺失，只能完成渲染和客户端 dry-run，不能宣称运行通过，non-blocking

## Verification strategy
1. 对 Compose 运行 `docker compose config`，检查固定镜像版本、端口绑定、volume、健康检查、依赖和 profile；在干净 volume 上执行完整启动，验证所有长期服务健康、初始化任务成功退出、五个数据库及关键表存在、Nacos namespace/config 可读取、RocketMQ broker 可用。
2. 在不删除 volume 的情况下重复启动和初始化，验证数据保留、无重复错误；单独验证显式 reset 后可从零恢复。分别启动单个和部分中间件，确认命令契约有效。
3. 使用 `local` profile 对全部可运行后端模块做打包和 IDE 等价启动冒烟，确认端口无冲突、能连接基础设施、Gateway/IAM/System 基本调用链可达；运行 `npm ci`、前端构建及本机 API 地址检查。外部依赖导致的失败必须与代码失败分开报告。
4. 构建所有后端和 Admin 镜像，检查 Java 17 运行时、非 root 用户、入口、暴露端口、镜像内无 Secret，并对启动后的进程执行对应健康检查。
5. 对全部 Kubernetes YAML 执行 YAML 解析、`kubectl apply --dry-run=client` 及可用时的 schema 校验；对部署脚本执行 shell 静态检查，验证缺参失败、Secret 缺失失败、等待就绪、状态查询、普通卸载保留 PVC、显式数据清理等路径。
6. 条件允许时在一次性本地 Kubernetes 集群执行从空 namespace 部署、初始化、应用就绪、Admin/Gateway/TinyID 访问和卸载重装；若未实际运行集群，验证报告必须明确标记为未验证。
7. 最后运行 `git diff --check`，核对 README、Compose、Kubernetes、Dockerfile、初始化材料和应用配置描述的是同一套版本、端口、服务名和启动顺序，并按 `REVIEW.md` 完成 Bugs、Security、Compliance 三轮审查。
