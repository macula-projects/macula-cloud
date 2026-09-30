# Plan: 开发与 Docker Compose 部署支持 (from spec.md 2026-10-01)

## Files that change
- SDLC 工件：`.sdlc/development-deployment/plan.md`；本计划替换已废弃的 Kubernetes 实施计划，后续实际偏差继续记录在本文件。
- 仓库入口与忽略规则：修改 `.gitignore`、根 `.dockerignore`、根 `README.md`，只保留 Compose、本地环境文件和镜像构建相关入口/忽略项。
- 删除 Kubernetes 与集中式 Dockerfile：删除空入口 `deploy/k8s.yml`、整个 `deploy/k8s/`、`deploy/docker/Dockerfile.java`；不新增任何集群部署文件。
- Compose 编排：重写 `deploy/docker-compose.yml`；更新 `deploy/.env.example`、`deploy/scripts/compose.sh`、`deploy/README.md`，增加默认中间件模式、`apps` profile、八个后端服务、Admin、构建命令和显式重置。
- 中间件初始化：修改 `deploy/init/mysql/init.sh`，只创建五个数据库/应用账号并导入 Nacos schema；保留 `deploy/init/mysql/nacos-mysql.sql`；删除 `deploy/init/mysql/seata-mysql.sql`、`deploy/init/mysql/snail-job-mysql.sql`。保留并调整 `deploy/init/nacos/init.sh`、`deploy/init/nacos/seata.properties`、`deploy/rocketmq/broker.conf`。
- 模块内 Flyway：修改 `macula-cloud-system/pom.xml`、`macula-cloud-tinyid/pom.xml`、`macula-cloud-seata/pom.xml`、`macula-cloud-snailjob/pom.xml`，使用父依赖管理的 Flyway 11.14.1 核心/MySQL 支持；分别新增 `src/main/resources/db/migration/V1__baseline.sql`。System/TinyID 的 V1 由现有 docs dump 移入资源目录并删除原 `docs/*.sql`；Seata/SnailJob 的 V1 由已核对上游版本 SQL 移入各自模块。
- Flyway 与运行配置：修改 System、TinyID、Seata、SnailJob 的 `application.yml`，配置 datasource、`baseline-on-migrate`、baseline version、validate 和 migration location；修改 Gateway、IAM、System、TinyID、SnailJob 的 `bootstrap.yml` 以及 Gateway/IAM/System/TinyID/SnailJob/Seata 的既有 `application.yml`，继续支持 IDE 本机默认值与 Compose 环境变量覆盖。
- System 编译兼容：只对 `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/*.java` 和 `service/impl/*.java` 中实际导入旧 MyBatis-Plus `extension.service` 的 28 个文件执行机械 import 迁移到 3.5.17 的 `spring.service` 包，不改方法、类型层次或业务逻辑。
- TinyID 兼容验证：先在 `macula-cloud-tinyid/pom.xml` 与配置范围内验证当前 Druid 1.2.28/Spring Boot 4 装配；若确认必须升级版本或引入未管理依赖，暂停实施并修订计划，不自行扩大依赖变更。
- 模块 Dockerfile：新增 `macula-cloud-gateway/Dockerfile`、`macula-cloud-iam/Dockerfile`、`macula-cloud-system/Dockerfile`、`macula-cloud-tinyid/Dockerfile`、`macula-cloud-seata/Dockerfile`、`macula-cloud-snailjob/Dockerfile`、`macula-cloud-rocketmq/Dockerfile`、`macula-cloud-docs/Dockerfile`。各文件从仓库根构建自身 Maven module，使用 Java 17 多阶段构建和非 root 运行用户；重复结构保持一致，不引入额外基础镜像链。
- Admin 镜像：完善 `macula-cloud-admin/Dockerfile`、`.dockerignore`、`.env.production`、`nginx.conf`，使用 `npm ci`、非 root Nginx、SPA 回退及 Gateway/IAM Compose DNS 代理。
- 最小装配：保留并完善 `macula-cloud-docs/src/main/java/dev/macula/cloud/docs/MaculaDocsApplication.java`、Docs/RocketMQ 的 `src/main/resources/application.yml` 及两个 `*ApplicationTest.java`，只保证独立端口和 Spring Boot 最小装配。
- 不修改公共 REST/Feign 契约、领域实体/Mapper、数据库业务结构、认证授权规则、租户行为、依赖版本属性、`bands.yaml` 或远端环境。

## Order of work
1. 清理旧方向：删除本次产生的 K8s 目录、旧空入口和集中式参数化 Dockerfile，移除 README、`.gitignore` 中的 K8s 引用，确保后续只有一个 Compose 部署路径。
2. 建立数据库所有权：将 System、TinyID、Seata、SnailJob 的已核对 SQL 移为各模块 V1 migration；MySQL bootstrap 改为建库/建用户并只导入 Nacos 官方 schema，确认默认中间件启动不会创建 Flyway history。
3. 集成模块内 Flyway：四个模块加入父级管理的 Flyway 依赖和显式配置；空库执行 V1，已有非空库使用 baseline version 1 接管，checksum 或失败 migration 必须阻止启动。System 是共享 `macula-system` 的唯一 migration owner，IAM 不引入 Flyway。
4. 修复已确认的最小构建阻断：机械迁移 System 的 28 个 MyBatis-Plus import 并先做编译；验证 TinyID 的真实装配状态，若必须修改未获准的依赖版本则停止，而不是用旧 target 产物绕过。
5. 为八个 Java 模块创建就近 Dockerfile：统一 Maven/Java 17/非 root 结构，模块名和端口写死在各自文件中；从仓库根逐个运行对应 Maven package，检查生成的是可执行 JAR。
6. 完成 Admin 镜像：保留模块本地构建上下文，验证生产环境变量、Nginx `/api/`、`/iam/` 代理和 SPA fallback。
7. 重写 Compose：基础设施和初始化任务不设 profile；八个后端与 Admin 使用 `apps` profile。使用 YAML anchors 复用公共应用环境、日志和重启策略，并按 MySQL/Nacos/RocketMQ、System migration、IAM/Gateway/Admin 的依赖关系组织启动顺序。
8. 更新脚本与文档：`compose.sh` 提供 `up`、`up-apps`、`build`、`status`、`logs`、`down`、`reset --confirm` 和显式服务参数；README 记录数据库所有权、Flyway 新增 migration 规则、IDE 顺序、Compose 全栈启动和故障诊断。
9. 执行静态与构建证明：检查 Compose 默认/`apps` profile、Shell、Dockerfile、敏感信息和 K8s 遗留；运行四个数据库模块及最小装配模块的 Maven 测试/打包和 Admin 构建。
10. Docker daemon 可用时执行运行证明：从空 volume 启动中间件，确认只有 Nacos schema 已初始化；启动 `apps` profile，验证四个应用库的 Flyway V1/history、重复启动、基本服务状态和显式 reset。若 daemon 仍不可用，明确标记运行项未验证。

## Risks
- System V1 dump 含 `DROP TABLE` 和种子数据，只能在空库执行；`baseline-on-migrate` 可避免已有库重放，但不能证明已有 schema 与 V1 一致，接管前需备份并核对。
- System/IAM 共用数据库，IAM 先于 System migration 启动会缺表；Compose 必须建立 System 就绪依赖，IDE 文档必须明确首次启动顺序。
- Seata 自身的 datasource 装配未必会暴露 Spring Boot Flyway 所需的主 `DataSource`；若自动配置无法运行，需要在 Seata 模块内补最小 migration datasource 装配，但不得改变 Seata store 行为。
- TinyID 当前 Druid starter 与 Spring Boot 4 可能运行不兼容；本计划只允许使用当前管理版本内的最小兼容方式，版本升级属于需要重新确认的偏差。
- Flyway 11 对 MySQL 使用独立 database support artifact；四个模块必须同时包含 `flyway-core` 与 `flyway-mysql`，不能只靠 core 在运行时猜测支持。
- 八个 Dockerfile 存在有意的少量重复；过度提炼成自定义基础镜像、生成器或符号链接会降低模块独立性，本次只通过一致模板和 Compose anchors 控制漂移。
- Compose `depends_on` 只能表达容器/健康状态，TCP 探针不等于业务健康；Gateway/IAM/System 调用链仍需运行验证。
- Docker daemon 或内存不足会限制全栈证明；静态 config 和 Maven package 不能替代实际容器启动。
- 删除 K8s 目录和迁移原 docs SQL 路径可能影响已有本地引用；根/部署 README 必须明确新入口和 migration 位置。

## Proof
- 范围与静态质量：`git diff --check`；`sh -n`，可用时运行 `shellcheck`；搜索并确认没有 `deploy/k8s`、`k8s.yml`、Helm/Kustomize 引用、真实 Secret、共享内网地址、floating image tag、target/dist 或范围外 API/业务修改。
- Flyway 依赖与资源：检查四个模块的依赖树包含父管理的 Flyway 11.14.1 core/MySQL support；打包后检查 JAR 内存在各自 `db/migration/V1__baseline.sql`，Nacos SQL只存在于部署初始化路径且无 `flyway_schema_history`。
- Flyway 行为：空库分别启动 System、TinyID、Seata、SnailJob，确认 V1 成功及关键表/history；重复启动无待执行项；对已有关键表无 history 的库验证 baseline 接管；修改 migration checksum 的临时验证必须失败且不提交修改。
- System/TinyID 兼容：System 完整重新编译，确认不再引用旧 `extension.service`；运行 System 目标测试。TinyID 运行目标测试与启动冒烟，明确区分 Druid 基线兼容失败和本次 Flyway 配置失败。
- Compose 合约：运行 `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml config` 以及 `--profile apps config`，核对默认集合不包含应用、apps 集合完整、端口只默认绑定 `127.0.0.1`、卷/健康检查/依赖/环境变量一致。
- 模块构建：对八个模块分别执行与 Dockerfile一致的 `mvn -pl <module> -am package -DskipTests -Plocal`，检查可执行 JAR；Docker 可用时逐个构建镜像并检查 Java 17、非 root 用户、入口和端口。
- Java/前端验证：运行四个数据库模块的相关 `-am test -Plocal`，运行 Docs/RocketMQ 最小装配测试；在 Admin 执行 `npm ci` 和 `npm run build`，检查 Nginx SPA/代理配置。全量测试失败必须区分既有依赖问题、外部环境问题与本次回归。
- 运行集成：从空 Compose volume 启动默认基础设施，确认 MySQL/Redis/Nacos/RocketMQ 健康、Nacos schema/config 已初始化且四个应用库仍为空；再启用 `apps` profile，确认四个应用库由各自模块迁移、八个后端/Admin 状态及 Gateway/IAM/System 基本链路；重复启动和 `reset --confirm` 分别证明数据保留与显式重建。
