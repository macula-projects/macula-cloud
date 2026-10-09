# Spec: TinyID Server 遗留清理 (from intent.md 2026-10-09)
Status: accepted.

## Source intent
[已接受需求](intent.md)：移除旧 Token 全链路，统一异常处理，简化网关路由，直接清理 V1。

## Requirements
1. 运行时不再创建、查询、缓存或刷新 TinyID 专属 Token；不再提供接入应用和业务授权管理。
2. 新库执行 V1 后不存在 tiny_id_token；保留业务表、审计表及业务序列数据定义。
3. 删除 TinyIdHttpExceptionAdvice，直接依赖现有 Macula Web 统一处理器，不创建替代 Advice。
4. Gateway 路由为 /tinyid/**，保留 StripPrefix=1、共享认证授权与 Server JWT 校验。
5. 业务、数据源、审计管理及 ROOT 权限继续可用；文档和测试不再要求配置旧 Token。

## Non-goals
不改发号算法、数据源顺序、分片规则、IAM 或共享异常处理器；不迁移或删除真实数据库数据，不自动 repair Flyway，不新增 V2，不部署。

## Design
- 后端删除 Token Service/Impl、定时刷新、Entity、Mapper/XML；清除管理 Service/Controller/Converter 中应用与授权方法及专用 Form/Query/BO/VO。
- 业务删除去掉授权快照、授权删除及缓存更新，只保留业务自身的校验、跨库补偿与审计；历史审计记录及其可读性保留。
- 前端删除接入应用页签、相关对话框和 applications API；保留发号业务、审计页签及统一 success/data 处理。
- 删除专属 Advice 及测试导入；业务异常继续通过 TinyIdSysException/BizException 处理。空 bizType 使用统一参数校验异常，不依赖 ResponseStatusException 维持专属 HTTP 状态。
- 路由只简化 Path 谓词，不扩展匿名白名单，不增加 HMAC/个人 Token 区分。

## Data and interfaces
删除 /api/v1/admin/apps 及其子接口，不提供兼容空实现；其余发号和管理成功响应不变。
V1 删除 tiny_id_token 的 DDL、示例数据、锁表语句及索引；不触碰实际库。已有库的旧表不会因修改脚本而自动消失。
HTTP 异常遵循共享处理器：不再承诺专属 Advice 原来的 400/405 等状态；统一业务错误仍返回 Result。

## Flagged concerns
- V1 校验差异：已有环境需人工安排基线处理，用户已明确选择直接清理 V1；不自动修复或重置，non-blocking。
- 兼容变化：旧应用/授权接口删除，参数及 MVC 错误状态遵循共享处理器，调用方和测试同步更新，non-blocking。
- 通配路由：会覆盖 TinyID 现有及未来其他路径，按用户要求采用；保留认证并验证非发号路径不会新增匿名放行，non-blocking。
- 跨库删除：移除授权关联代码不能破坏业务校验与补偿，必须有回归测试，non-blocking（实施验收条件）。

## Verification strategy
- 搜索生产代码及初始化 SQL，确认无旧 Token 链路、应用 API 和专属 Advice 残留；历史文档/审计记录不作机械删除。
- 单测及 MySQL 集成测试覆盖业务创建、分页、跨库一致性、删除限制、失败补偿、审计和新 V1 无 Token 表。
- Web/Security/Gateway 测试覆盖统一成功/异常、旧应用路由不再映射、ROOT 权限、有效/匿名/伪造/过期身份、通配路径及 StripPrefix。
- Vitest、前端构建和 Cypress 验证仅保留业务/审计入口及成功、空态、失败交互；模拟测试与真实后端证据分开报告。
- 测试阶段先运行 TinyID/Gateway 目标测试，再执行 Cloud 全仓 verify -Plocal；本设计阶段不执行测试。

## Governing policies
已读取 Cloud AGENTS.md 及 architecture、backend-development、frontend-development、dependencies-release、testing 规则。未发现额外适用的组织安全、品牌或合规技能；不虚构额外政策。
