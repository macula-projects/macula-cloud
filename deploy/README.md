# Macula Cloud 本地部署

本目录提供 Docker Compose 开发环境，支持两种模式：

- IDE 调试：只启动 MySQL、Redis、Nacos、RocketMQ 和初始化任务，应用在 IDE 中运行。
- 完整容器：启用 `apps` profile，启动后端应用和 Admin。

该编排仅用于本地开发与集成验证，不包含生产级高可用、证书、备份和灾备方案。示例账号与 Secret 不得用于共享或生产环境。

## 快速开始

前置条件：Docker Compose v2。IDE 调试还需要 Java 17、Maven 和 Node.js/npm。

```bash
cp deploy/.env.example deploy/.env
```

按需修改 `deploy/.env`，然后启动中间件：

```bash
./deploy/scripts/compose.sh config
./deploy/scripts/compose.sh up
./deploy/scripts/compose.sh status
```

启动完整环境：

```bash
./deploy/scripts/compose.sh config-apps
./deploy/scripts/compose.sh up-apps
./deploy/scripts/compose.sh status
```

脚本找不到 `deploy/.env` 时会使用 `.env.example`。也可通过 `MACULA_COMPOSE_ENV` 指定其他环境文件。

## 端口

| 服务 | 默认本机端口 | 容器端口 |
| --- | ---: | ---: |
| MySQL | 3306 | 3306 |
| Redis | 6379 | 6379 |
| Nacos client / console / gRPC | 8848 / 8080 / 9848 | 8848 / 8080 / 9848 |
| RocketMQ NameServer | 9876 | 9876 |
| RocketMQ Broker | 10909 / 10911 / 10912 | 10909 / 10911 / 10912 |
| Gateway | 9000 | 9000 |
| IAM | 9010 | 9010 |
| System | 9081 | 9081 |
| TinyID | 9082 | 9082 |
| RocketMQ 管理服务 | 9083 | 9083 |
| Docs | 9084 | 9084 |
| SnailJob HTTP / gRPC | 9086 / 17888 | 9086 / 17888 |
| Seata console / service | 9091 / 8091 | 9091 / 8091 |
| Admin | 5900 | 8080 |

端口规则：

- `SERVER_PORT` 是 Spring Boot 进程监听端口；容器运行时就是容器端口。
- SnailJob gRPC 和 Seata service 分别使用 `SNAILJOB_GRPC_PORT`、`SEATA_SERVER_SERVICE_PORT`。
- `*_HOST_PORT` 只控制 Compose 的宿主机发布端口，不传入应用。
- 本机端口默认绑定 `127.0.0.1`，可在 `deploy/.env` 中调整。

## 配置规则

后端模块使用单一 `application.yml`：

- 公共段：应用名、`SERVER_PORT`、Nacos 公共连接引用和环境无关配置。
- `local`：IDE 所需的本机数据库、Redis、Nacos、IAM、RocketMQ 等配置；依赖端口使用 `deploy/.env` 中对应的宿主机端口变量。
- `docker`：通过 `docker -> local` profile group 继承本地默认配置，并覆盖为 Compose DNS 和容器端口。
- `dev`、`stg`、`pet`、`prd`：仓库只保存连接 Nacos 所需参数，其余环境配置由配置中心提供。

Compose 固定激活 `docker` profile，并显式传入每个应用的 `SERVER_PORT`。数据库地址和库名由模块配置维护；System、IAM、TinyID、Seata、SnailJob 的 MySQL 账号密码使用 `MYSQL_APP_USER`、`MYSQL_APP_PASSWORD`，Compose 从 `deploy/.env` 注入，并与 MySQL 初始化账号保持一致。

Nacos 配置使用以下 Data ID：

```text
${spring.application.name}.yml
${spring.application.name}-${spring.profiles.active}.yml
```

## 数据库与 Flyway

`mysql-init` 创建应用账号和以下数据库，只直接初始化 Nacos schema：

| 数据库 | Migration owner |
| --- | --- |
| `macula-system` | `macula-cloud-system` |
| `macula-tinyid` | `macula-cloud-tinyid` |
| `seata` | `macula-cloud-seata` |
| `macula-snailjob` | `macula-cloud-snailjob` |
| `nacos` | `deploy/init/mysql/nacos-mysql.sql`，不使用 Flyway |

IAM 与 System 共用 `macula-system`，但只有 System 管理 migration。新增数据库变更时创建新的 `V2__description.sql`、`V3__description.sql`，不得修改已执行的 migration。

## IDE 启动顺序

中间件和初始化任务就绪后：

1. `MaculaSystemApplication`
2. `MaculaTinyIdApplication`、`MaculaSeataApplication`、`MaculaSnailJobApplication`
3. `MaculaIamApplication`
4. `MaculaGatewayApplication`
5. 按需启动 `MaculaRocketMQApplication`、`MaculaDocsApplication`
6. 在 `macula-cloud-admin/` 执行 `npm ci && npm run dev`

System 首次启动完成 Flyway migration 后再启动 IAM。

## 常用命令

```bash
# 启动指定中间件
./deploy/scripts/compose.sh up mysql mysql-init redis
./deploy/scripts/compose.sh up mysql mysql-init nacos nacos-init

# 构建全部或指定应用镜像
./deploy/scripts/compose.sh build
./deploy/scripts/compose.sh build macula-cloud-system

# 查看状态和日志
./deploy/scripts/compose.sh status
./deploy/scripts/compose.sh logs macula-cloud-system

# 停止并保留数据
./deploy/scripts/compose.sh down
```

每个 Java Dockerfile 均从仓库根目录构建，使用 Java 17 和非 root 用户；JVM 参数通过 `JAVA_OPTS` 配置。

永久删除本地 volume 必须显式确认：

```bash
./deploy/scripts/compose.sh reset --confirm
```

该操作无法恢复。

## 常见问题

- `mysql-init` 失败：检查 `MYSQL_ROOT_PASSWORD`、`MYSQL_APP_USER`、`MYSQL_APP_PASSWORD`。
- Flyway 失败：检查数据库权限、`flyway_schema_history` 和 migration checksum，禁止直接修改已执行 migration。
- IAM 缺表：确认 System 已完成 `macula-system` migration。
- Nacos 启动失败：确认 `nacos.config_info` 存在，并检查 8848、8080、9848 端口。
- RocketMQ 本机客户端失败：检查 9876、10911 端口及 Broker 公布地址。
- 应用容器未就绪：先查看对应应用日志；TCP healthcheck 只表示端口是否监听。
