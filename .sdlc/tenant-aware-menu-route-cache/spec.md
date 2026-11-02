# Spec: 租户隔离的菜单路由缓存 (from intent.md 2026-10-29)
Status: accepted.

## Source intent
[租户隔离的菜单路由缓存 intent](./intent.md)：菜单路由缓存必须使用经过认证和授权的租户上下文隔离，不能复用无租户共享结果。

## Requirements
1. `GET /api/v1/menus/routes` 必须在读取缓存前解析并校验当前租户；无法确定可信租户时以 403 拒绝，不得使用默认租户或共享缓存兜底。
2. AK/SK 请求必须以 Gateway 验签后写入 JWT 的 `tenantId` claim 为权威租户；请求头不得覆盖与 claim 不一致的应用租户。
3. 用户请求选择与 token claim 不同的租户时，ROOT 可访问任意已存在租户；非 ROOT 只能访问 `sys_tenant_user` 明确关联的租户，无关联用户仅保留现有默认租户 1 的兼容访问。越权选择以 403 拒绝，且不得读取或写入该租户缓存。
4. 菜单路由缓存必须使用版本化租户键，例如 `routes:v2:{tenantId}`；租户 1 与租户 12 无论访问顺序如何都不能命中彼此的数据。
5. `listRoutes()` 的 REST 路径、请求参数和响应结构保持不变；租户继续来自认证上下文或已授权的租户选择，不新增可伪造的 `tenantId` 方法参数。
6. 新增、修改、删除菜单，修改菜单可见性，以及修改角色菜单关系成功提交后，必须只失效当前可信租户的路由缓存。失败或回滚不得提前淘汰缓存。
7. 旧的无租户 `system::routes` 键不得再被新代码读取；部署时应停止或排空旧 System 实例，旧键可精确删除或等待现有 24 小时 TTL 到期，不能通过清空整个 Redis 处理。
8. 前端角色过滤继续作为当前租户菜单内的展示规则；本次修复不得把租户隔离下放到 Cloud Admin 或 Samples Admin。
9. 缓存键、拒绝日志和异常响应不得包含 token、AK、SK、用户名或菜单内容，只允许记录租户 ID、固定失败分类和必要的请求追踪标识。

## Non-goals
- 不修改菜单 API 响应结构，不给 `listRoutes()` 增加租户参数，也不在前端菜单对象中增加 `tenantId`。
- 不修改 MyBatis-Plus 全局租户 SQL 注入算法，不关闭租户拦截器。
- 不在本变更中一次性整改所有 System API 对租户请求头的授权；菜单路由链路必须独立完成校验，其余端点另立安全变更。
- 不改变角色正向/反向过滤语义、菜单数据、租户成员关系或 AK/SK 凭据。
- 不清空共享 Redis、OAuth Client 缓存或其他业务缓存。

## Design
System 新增一个菜单路由租户上下文组件，在缓存切面计算 key 之前解析可信租户。解析顺序和规则如下：

1. 读取安全上下文 JWT 的 `tenantId` claim 与 `TenantContextHolder` 中由请求头建立的候选租户。
2. 候选租户为空时，只接受非空 JWT claim；两者都为空则拒绝。
3. 候选租户与 JWT claim 相同则直接接受。
4. 候选租户与 claim 不同时，ROOT 校验目标租户存在后接受；非 ROOT 查询当前用户的租户成员关系，只有目标租户在允许集合中才接受。普通用户没有显式关联时仅允许默认租户 1，以保持当前租户选项行为。
5. 服务到服务的 AK/SK principal 没有用户租户切换权限，只能使用 JWT claim；伪造请求头不能覆盖应用租户。

校验后的租户 ID 同时用于缓存 key 和 MyBatis 租户上下文。若需要临时调整 `TenantContextHolder`，必须在 `finally` 中恢复原值，确保缓存 key 与实际 SQL 使用同一租户且线程不泄漏。

路由缓存继续使用 Spring Cache，但 key 升级为 `routes:v2:{tenantId}`。菜单与角色菜单变更不再使用固定 `'routes'` 的注解失效；统一通过路由缓存服务在事务提交后删除当前租户的版本化 key。修改可见性纳入同一失效路径。非事务调用只有在数据库更新成功后才允许失效。

旧 `routes` key 与新 key 命名空间不重叠，新代码天然不读取旧值。发布说明要求排空旧 System 实例后精确删除 `macula:cache:system::routes`，或者允许它按 24 小时 TTL 自然过期。

## Data and interfaces
REST、Feign、数据库 schema 和菜单 VO 均不变化。

Redis 从单一旧键：

```text
macula:cache:system::routes
```

迁移为版本化租户键：

```text
macula:cache:system::routes:v2:1
macula:cache:system::routes:v2:12
```

内部新增可信租户解析与路由缓存失效接口；接口不接受来自 Controller 的任意租户参数，只读取当前认证和请求上下文。越权或缺失租户统一返回 403，不暴露目标租户是否存在给非 ROOT 用户。

## Flagged concerns
- 全局租户请求头校验仍不完整：Macula Boot 的 MyBatis 租户处理保留了“检查当前用户是否可以访问该租户”的 TODO；本次在菜单路由边界完成校验，但其他 System API 仍需独立安全审计，non-blocking。
- ROOT 跨租户切换：ROOT 现有产品行为是可选择全部租户，本设计保留该能力但要求目标租户真实存在；若产品要限制 ROOT，还需另行决策，non-blocking。
- 混合版本部署：旧 System 实例仍会读写无租户共享键，发布期间会继续产生错误菜单；部署必须排空旧实例后再验证新键，blocking。
- 旧共享缓存残留：旧键默认 TTL 为 24 小时，但新版本不会读取；是否部署后立即精确删除由运维决定，不影响代码正确性，non-blocking。

## Verification strategy
- 纯单元测试覆盖：AK/SK claim 租户通过、应用请求头覆盖被拒绝、用户选择本人租户通过、无成员用户仅默认租户通过、非 ROOT 越权拒绝、ROOT 有效租户通过、ROOT 不存在租户拒绝、缺失租户拒绝。
- Spring Cache 代理测试使用内存 CacheManager 和可控 Mapper，按租户 1、12、1 的顺序调用路由查询，证明 Mapper 分别执行两次且第三次只命中租户 1；反向顺序得到相同隔离结果。
- 事务测试覆盖菜单新增、修改、删除、可见性修改和角色菜单更新：成功提交后只淘汰当前租户 key，回滚不淘汰，其他租户 key 保留。
- System 目标测试执行 `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal`，并运行 `git diff --check`。
- Docker 验证依次以 Cloud Admin 用户租户 1、Samples AK/SK 租户 12、再租户 1 请求菜单，核对返回菜单归属和 Redis 中两个独立 key；验证后不得把真实 token 或 SK 写入报告。
