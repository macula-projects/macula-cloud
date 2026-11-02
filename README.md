# Macula Cloud

## 作用

Macula Cloud 是基于 Macula Boot 6.1、Java 17、Spring Boot 4.0 和 Spring Cloud 2025.1 构建的微服务应用平台，提供统一网关、身份认证、租户与权限管理、分布式 ID、事务、任务调度和文档等基础能力。管理端位于 `macula-cloud-admin/`，采用 Vue 3 和 Vite。

| 模块 | 作用 |
| --- | --- |
| `macula-cloud-api` | 跨服务 API 契约与模型 |
| `macula-cloud-gateway` | 统一入口、认证与路由 |
| `macula-cloud-iam` | OAuth2/OIDC 认证与登录 |
| `macula-cloud-system` | 租户、应用、用户、角色、菜单和系统配置 |
| `macula-cloud-tinyid` | 分布式 ID 服务 |
| `macula-cloud-rocketmq` | RocketMQ 集成服务 |
| `macula-cloud-seata` | 分布式事务服务 |
| `macula-cloud-snailjob` | 任务调度与重试服务 |
| `macula-cloud-docs` | 文档服务 |
| `macula-cloud-admin` | Vue 3 管理控制台 |

## 启动

前置条件：Docker Compose v2。使用 IDE 启动后端时还需要 Java 17 和 Maven；启动管理端需要符合 `macula-cloud-admin/package.json` 要求的 Node.js。

### 完整 Docker Compose 环境

```bash
cp deploy/.env.example deploy/.env
./deploy/scripts/compose.sh config-apps
./deploy/scripts/compose.sh up-apps
./deploy/scripts/compose.sh status
```

启动完成后，管理端默认访问地址为 <http://127.0.0.1:5900>。

### IDE 本地开发

先启动 MySQL、Redis、Nacos、RocketMQ 等中间件：

```bash
cp deploy/.env.example deploy/.env
./deploy/scripts/compose.sh config
./deploy/scripts/compose.sh up
./deploy/scripts/compose.sh status
```

然后在 IDE 中按 System、IAM、Gateway 及其他业务服务的依赖顺序启动所需应用；管理端启动方式见 [macula-cloud-admin/README.md](macula-cloud-admin/README.md)。

完整端口、配置和启动顺序见 [deploy/README.md](deploy/README.md)。
