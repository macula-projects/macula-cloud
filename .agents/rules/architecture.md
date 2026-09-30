# 架构与模块边界

仅在理解仓库结构、调整服务职责或进行跨模块修改时加载。

## 平台边界

Macula 平台分为三类职责：Macula Boot 提供可复用的微服务 SDK 和 Starter；Macula Cloud 提供统一网关、认证、系统管理、ID、任务等可部署的通用平台服务；`macula-cloud-admin` 提供与这些服务配套的统一管理端。本仓库不重复实现 Macula Boot 已有的通用框架能力。

## 顶层模块

- `macula-cloud-api`：服务间 API 契约聚合模块；当前包含 `macula-cloud-system-api`，聚合模块本身不承载业务实现。
- `macula-cloud-gateway`：平台统一入口，负责路由以及认证、鉴权、加解密等网关能力；保持响应式调用链，不引入 Servlet 栈假设。
- `macula-cloud-iam`：认证与授权中心，包含登录认证、OAuth 2.1 授权服务器及身份源扩展。
- `macula-cloud-system`：租户、应用、用户、组织、角色、菜单、权限、字典和审计日志等系统管理能力。
- `macula-cloud-tinyid`：基于数据库号段的分布式 ID 服务，可由客户端缓存号段。
- `macula-cloud-seata`：Seata 服务封装与配置。
- `macula-cloud-snailjob`：SnailJob 调度与重试服务。
- `macula-cloud-rocketmq`：RocketMQ 管理服务，当前能力仍不完整。
- `macula-cloud-docs`：API 与数据库文档服务，当前能力仍不完整。
- `macula-cloud-admin`：Vue 3 管理端，不属于 Maven reactor。
- `deploy`：Docker Compose 与 Kubernetes 部署材料；修改会影响运行环境，不把示例值当作生产配置。

## 依赖方向

- 业务服务可以依赖对应的 `*-api` 契约；API 模块不得反向依赖服务实现。
- 跨服务调用通过稳定的 API/Feign 契约完成，不直接引用其他服务的 Mapper、Entity、Service 实现或资源文件。
- `macula-cloud-system-api` 中只保留调用方真正需要的接口和传输模型，避免因复用方便而扩大公共契约。
- Gateway、IAM、System 共同形成认证授权链路。涉及 token、JWT、角色、菜单、URL 权限、租户上下文或权限缓存的改动，不得按单模块局部行为处理。
- 管理端通过 `src/api` 访问后端；后端 API 变化必须同步检查前端调用、路由、权限指令和错误处理。
- 可复用且与平台业务无关的能力优先回到 Macula Boot；只服务于 Cloud 平台的编排和领域逻辑留在本仓库。

## 数据与安全边界

- 租户、应用、用户、角色、菜单和 URL 权限存在关联约束；查询、缓存、批处理和定时任务都要显式维护租户隔离。
- 切换 `TenantContextHolder` 等线程上下文时必须在 `finally` 中恢复或清理，避免请求或任务之间泄漏上下文。
- 权限、客户端密钥、密码和 token 不写入日志，不通过宽松回退绕过校验。
- 数据库结构变化同步维护相应模块的 `docs/*.sql` 或项目采用的迁移材料，并评估 IAM 与 System 是否共享相关表结构。

## 跨模块修改检查

1. 搜索公共类型、配置键、接口路径和数据库字段的所有调用方。
2. 明确 API 契约是否向后兼容，必要时保留旧版本或提供迁移说明。
3. 同步核对 Gateway、IAM、System、System API、管理端及部署配置。
4. 从最小相关模块验证开始，再扩大到受影响服务组合和管理端构建。
