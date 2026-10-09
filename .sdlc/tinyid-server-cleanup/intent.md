# Intent: TinyID Server 遗留 Token 与专属异常处理清理
Author: Rain. Status: accepted.

## Problem
发号已采用统一网关认证，但 Server 仍保留不再参与发号鉴权的 Token 服务、表和管理入口，造成维护与使用上的混淆；专属异常 Advice 和逐接口网关路由也偏离用户要求的统一处理方式。

## Proposed outcome
TinyID 不再提供专属 Token 或接入应用授权管理；保留发号业务、数据源及审计功能。异常由现有统一机制处理，网关统一代理 TinyID 路径。

## Affected users and systems
Macula Cloud TinyID Server、管理前端、Gateway、V1 初始化脚本，以及相关测试和文档。

## Constraints
- 按用户明确要求直接清理 V1 中 tiny_id_token 的建表、示例数据及索引，不新增 V2，不操作实际数据库。
- 删除专属 Token 的服务、缓存刷新、持久化模型、接口和对应管理页面，不再保留旧 Token 兼容入口。
- 删除 TinyIdHttpExceptionAdvice，不新建替代 Advice，也不修改共享异常处理器；异常响应遵循统一机制。
- 网关使用 /tinyid/**，保留 StripPrefix=1 和现有共享认证授权逻辑。
- 保留发号算法、业务配置、数据源、审计和 ROOT 管理权限，不重置发号进度。
- 直接修改已应用的 V1 会引起 Flyway 校验差异；需在文档明确，本次不自动 repair 或重置既有环境。
- 独立任务分支和工作树实施，保留原工作区网关及 IAM 改动；不自动提交实现、合并或部署。

## Open questions
- 无；用户 YES 确认范围及署名，数据库按用户要求直接清理 V1。
