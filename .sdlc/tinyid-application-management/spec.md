# Spec: TinyID 接入应用管理 (from intent.md 2026-10-03)
Status: accepted

## Source intent
[已接受的 TinyID 接入应用管理 intent](./intent.md)：为平台超级管理员提供 TinyID 应用、业务发号配置、授权关系与审计管理能力，同时保持现有发号协议兼容。

## Requirements
1. 只有携带有效平台访问令牌且具有 `ROOT` 角色的用户可以访问 TinyID 管理 API 和管理页面；匿名用户、普通管理员及其他角色必须被拒绝。
2. 现有 `/api/v1/id/nextId`、`nextIdSimple`、`nextSegmentId`、`nextSegmentIdSimple` 的路径、参数和响应语义保持兼容，并继续使用 `(token, biz_type)` 是否存在判断发号权限。
3. 管理端提供“接入应用”“发号业务”“审计日志”三个可访问视图，并完整处理加载、空数据、失败、表单校验和重复提交状态；操作审计的采集与事件模型统一使用 `macula-boot-starter-auditlog`。
4. 超级管理员可以分页查询接入应用；每个应用以唯一 Token 标识，以 `remark` 描述应用，并展示其已获授权的 `biz_type` 集合。
5. 超级管理员可以创建接入应用。Token 必须由服务端使用密码学安全随机源生成，不接受客户端指定；创建时至少关联一个已存在的 `biz_type`。
6. 超级管理员可以随时查看和复制完整 Token；Token 不得向非超级管理员返回，不得出现在应用日志、审计请求参数、审计响应内容或错误消息中。
7. 超级管理员可以修改应用的 `remark`、为应用新增业务授权或删除整个接入应用。删除应用必须清理该 Token 在所有已配置数据源中的全部授权，并在全部删除成功后立即刷新处理请求实例的 Token 缓存；不得提供单条业务授权撤销、应用停用或 Token 轮换能力。
8. 超级管理员分页查询聚合后的发号业务，结果必须展示各数据源的 `biz_type`、`step`、`delta`、只读 `remainder`、当前 `max_id`、版本和更新时间，以及跨数据源一致性状态；不提供按数据源切换维护入口。
9. 创建发号业务必须一次性写入全部当前可管理数据源。`begin_id` 和 `max_id` 由服务端固定初始化为 `0`，不得由请求覆盖。
10. 创建发号业务时，`biz_type` 必须非空，`step` 必须大于 `0`，`delta` 默认为 `10` 且允许创建时修改；`delta` 必须大于当前数据源的最大持久化顺序号。请求不得设置 `remainder`。
11. 所有数据源使用相同的 `step` 和 `delta`；每个数据源的 `remainder` 必须等于其从 `0` 开始、只追加且不复用的持久化顺序号。扩展新实例时不得修改原数据源的 `remainder`，数据源顺序号不得达到或超过 `delta`。
12. `biz_type`、`step`、`delta`、`remainder`、`begin_id` 和 `max_id` 创建后均不得通过管理 API 修改；页面只读展示这些字段。
13. 超级管理员可以删除尚未使用的发号业务。服务端必须确认该 `biz_type` 在所有已配置数据源中均存在且每个数据源的 `max_id=0`，任一数据源缺失、不可用或 `max_id` 非 `0` 都必须拒绝删除；删除时必须在数据库侧再次以 `max_id=0` 为条件删除各库业务记录，清理所有数据源中该 `biz_type` 的应用授权，并在全部成功后立即刷新处理请求实例的 Token 缓存。管理 API 和页面不得提供发号业务停用能力。
14. 应用创建、新增授权或删除成功后，处理请求的 TinyID 实例必须立即刷新本地 Token 授权缓存；不新增 Redis 发布订阅或其他跨实例失效传播机制。其他 TinyID 实例允许继续使用旧缓存，直到既有定时刷新成功或进程重启后重新加载；多实例部署必须接受这一最终一致性窗口。
15. TinyID 管理操作使用 `macula-boot-starter-auditlog` 的 `@AuditLog` 标准切面发布 `OperLogEvent`，并关闭请求与响应正文记录，确保完整 Token 不进入审计事件。TinyID 参考 `macula-cloud-system` 的 `AuditLogEventListener`，仅以 `@Async + @EventListener` 将标准事件映射到 `tiny_id_audit_log`，并提供 ROOT 查询 API；不自建另一套审计注解、切面、Servlet Filter 或事件模型，审计落库失败不得改变业务 API 已完成操作的结果。
16. 管理 API 必须使用有界分页和白名单排序；不允许无限列表、任意 SQL 排序或将数据库 Entity 直接作为 REST 契约。
17. 新增的数据库迁移必须兼容现有 `tiny_id_info`、`tiny_id_token` 数据，并在每个业务数据源中保证 `(token, biz_type)` 不重复；迁移不得修改或删除已有 Token 和已发放的 ID 进度。
18. Gateway 只代理 `/tinyid/api/v1/admin/**` 到 `macula-cloud-tinyid`，不得代理 `/api/v1/id/**` 发号接口；接入应用通过 Starter 配置的 TinyID 地址直连发号服务，以减少发号链路对 Gateway 的依赖。System 权限数据必须只向 `ROOT` 角色发布 TinyID 管理菜单和管理 API 权限；TinyID 服务本身仍须执行 `ROOT` 角色校验，避免绕过 Gateway 直连管理端口。
19. 现有请求日志必须对名为 `token` 的请求参数进行脱敏，验证失败日志只能记录业务类型、结果和关联标识，不得记录原始 Token。
20. README 和数据库迁移说明必须同步描述管理入口、字段语义、多数据库约束、安全边界、审计行为、应用删除语义、业务删除前置条件及不支持的停用、轮换操作。
21. TinyID 管理功能新增的 Form、Query、VO 分别统一放入 `dev.macula.cloud.tinyid.pojo.form`、`dev.macula.cloud.tinyid.pojo.query`、`dev.macula.cloud.tinyid.pojo.vo`，Controller、Service、测试和文档不得继续引用旧的顶层 `form`、`query`、`vo` 包。

## Non-goals
- 不改变 TinyID 的号段生成算法、客户端协议或现有四个发号接口。
- 不提供独立 Token 删除、停用、轮换、掩码展示或自定义 Token；删除接入应用会使其 Token 整体失效。
- 不提供既有应用业务授权的撤销能力。
- 不提供已使用发号业务的删除、任何发号业务的停用或发号参数修改能力。
- 不把 TinyID 的数据库模型或服务实现放入 `macula-cloud-api`，也不新增供其他微服务调用的 Feign 公共契约。
- 不在 TinyID 内实现独立于 `macula-boot-starter-auditlog` 的审计注解、切面、Servlet Filter 或事件模型。
- 不引入新的前端框架、状态管理方案或组件库。
- 不在本次变更中定义或调整生产 SLO、告警阈值、部署拓扑或数据保留策略。

## Design
约束来源：仓库根 `AGENTS.md`；`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`；根 `REVIEW.md` 的 Bugs/Security/Compliance 三类审查；根 `bands.yaml`（当前仅为待批准的示例控制带）；以及已接受的 `intent.md`。当前没有发现额外的组织级品牌规范或独立数据分类政策。

管理能力留在 `macula-cloud-tinyid`，数据仍由 TinyID 自己拥有。新增管理 Controller、Service、DAO，以及位于 `pojo.form`、`pojo.query`、`pojo.vo` 下的 Form/Query/VO；Controller 只负责协议、校验和编排，跨数据源写入、不可变规则、Token 生成、缓存刷新和事务补偿由 Service 负责，JdbcTemplate DAO 只执行参数化 SQL。

TinyID 引入仓库已管理版本的 `macula-boot-starter-security` 和 `macula-boot-starter-auditlog`。资源服务器复用平台 JWT/JWK 配置，现有发号路径加入明确的匿名白名单；管理 Controller 使用方法级 `hasRole('ROOT')` 校验。Gateway 只代理管理 API，并按现有权限模型校验管理 URL 权限，从而形成 Gateway 与服务端两层管理授权。发号接口不经过 Gateway，由接入应用使用 Starter 配置的服务地址直接调用 TinyID。

接入应用不新增独立应用表。`tiny_id_token` 继续以一行表示一个 `(token, biz_type)` 授权；相同 Token 的多行共同组成一个应用，`remark` 在同一 Token 的所有行中保持一致。应用列表使用 `MIN(id)` 作为应用存续期间稳定的 `appId`，前端和管理 API 以 `appId` 定位应用，避免将 Token 放入 URL。创建应用至少包含一个业务授权；修改备注会在事务内更新该 Token 的全部行；新增授权只插入不存在的组合。删除应用时先由 `master` 通过 `appId` 解析 Token，再保存各数据源中的授权快照并逐库删除该 Token 的全部行；部分失败时恢复已删除快照，只有全库成功后才刷新 Token 缓存。

Token 使用 `SecureRandom` 生成至少 256 bit 随机值，并编码为 URL-safe 字符串。写入前在所有目标数据源检查唯一性；极低概率碰撞时重新生成。Token 只在受 `ROOT` 保护的 VO 中完整返回，不写入 `toString` 日志、异常消息或审计载荷。

发号业务按所有当前数据源聚合展示，不提供数据源切换维护。创建请求只包含 `bizType`、`step`、`delta` 和幂等键；`delta` 默认为 `10`，服务端覆盖 `begin_id=0`、`max_id=0`、`version=0` 和服务端时间，并按数据源注册表中的持久化顺序号自动设置只读 `remainder`。创建前要求所有数据源健康、目标业务均不存在且最大顺序号小于 `delta`，随后逐库创建；失败时精确补偿本次新增记录。创建完成后不提供更新和停用入口。删除业务时先聚合检查全部已配置数据源，要求每个数据源都存在且 `max_id=0`；实际删除使用 `DELETE ... WHERE biz_type=? AND max_id=0` 并校验每库影响一行，以避免检查后首次发号造成竞态。服务保存业务和授权快照，逐库删除业务及该 `biz_type` 的全部授权，部分失败时恢复已删除快照，只有全部成功后才刷新 Token 缓存。

管理端查询同一 `biz_type` 在各数据源的摘要，以“完整”或“冲突”展示跨库一致性：所有当前数据源均存在、`step/delta` 一致且 `remainder` 与各自持久化顺序号一致时为完整；任一数据源缺失、参数不一致、余数重复或余数与持久化顺序不符时为冲突。创建业务为全库操作，不产生正常的“配置中”状态。

多数据库访问由显式的数据源注册表完成，不通过当前随机路由选择器执行管理操作。命名为 `master` 的数据源作为管理数据源，保存数据源顺序和幂等请求记录。发号业务与 Token 授权作为全局配置写入全部业务数据源：Service 先执行连接、表结构、业务存在性、顺序容量和重复授权预检，再逐库提交；任一写入失败时，仅补偿删除本次操作新插入且尚未对调用方报告成功的记录。若补偿失败，返回专用一致性错误、禁止刷新内存缓存并等待运维修复后重试。所有创建接口使用全局幂等请求键防止客户端重试制造重复记录。

现有 Token 缓存改为实例级、不可变快照并用原子引用替换。定时刷新和管理变更后的即时刷新复用同一加载逻辑；加载失败保留上一份完整快照，不发布半成品。删除应用成功后必须先在当前实例原子移除指定 Token；不增加 Redis 依赖、发布订阅主题或其他跨实例通知，其他实例通过既有定时刷新或进程重启重新加载后收敛。多数据源读取取授权并集，但发现同一 Token 的 `remark` 不一致或同一业务配置不一致时记录不含 Token 原文的一致性告警。

审计采集只使用 `macula-boot-starter-auditlog` 提供的 `@AuditLog` 切面和 `OperLogEvent`。所有 TinyID 管理方法关闭请求与响应正文记录，防止完整 Token 进入事件。TinyID 的监听器结构与 `macula-cloud-system/src/main/java/dev/macula/cloud/system/listener/AuditLogEventListener.java` 保持一致：使用 `@Async + @EventListener` 消费标准事件，将操作者、标题、方法、路径、结果、脱敏错误摘要、客户端地址和时间写入 `master` 的 `tiny_id_audit_log`。不再使用 Servlet Filter 重复捕获请求；异步审计失败记录错误但不反向改变业务 API 结果。审计查询 API 和页面只读取该表并仅向 ROOT 开放。

管理端新增 `src/api/model/tinyid/management.js` 及 `src/views/tinyid/management/` 页面，沿用现有 Vue 3、Element Plus、`scTable` 和请求封装。“发号业务”页聚合展示所有数据源状态，创建业务时提交全局 `bizType`、`step` 和 `delta`，`remainder` 只读展示。System 的增量 Flyway 迁移新增 TinyID 管理菜单、URL 权限及 ROOT 角色关联；新增 DELETE 管理权限必须通过新的前向迁移追加，不能改写已经执行的迁移。动态菜单加载 `tinyid/management/index`。前端配置增加 `MODEL.tinyid='tinyid'`，所有请求经 Gateway 发往 `/tinyid/api/v1/admin/**`。

## Data and interfaces
TinyID 数据库增量迁移：

- 所有配置为业务数据源的 TinyID 数据库都为 `tiny_id_token` 增加唯一索引 `uk_tiny_id_token_token_biz_type(token, biz_type)`；迁移前先检测重复组合，存在重复时失败而不是静默删数据。
- `master` 数据源新增 `tiny_id_management_request`，以数据源键与 `idempotency_key` 的唯一组合、操作类型、资源类型、资源标识、执行状态和时间保存创建请求结果；不重复保存完整 Token。
- `master` 数据源保存稳定的数据源顺序；已有顺序只追加不重排，移除实例留下的顺序号不复用。
- `master` 数据源使用 `tiny_id_audit_log` 保存 Starter 标准事件的必要脱敏字段，不保存请求正文、响应正文、Authorization Header 或原始 Token。
- 不改变 `tiny_id_info` 和 `tiny_id_token` 的现有列语义，不回写已有 `begin_id`、`max_id`、Token 或 remark。

System 数据库增量迁移：

- 新增 ROOT 专属 TinyID 管理菜单，组件为 `tinyid/management/index`。
- 新增与下述管理 API 对应的 URL 权限记录，并只关联 `ROOT` 角色。
- 已执行的迁移不得改写；应用与业务 DELETE 权限通过新的前向迁移追加并只关联 `ROOT` 角色。
- 迁移使用确定且可重复验证的菜单、权限和角色关联写法，不修改其他角色现有权限。

管理 API 均位于 `/api/v1/admin`，经 Gateway 后外部前缀为 `/tinyid`：

- `GET /api/v1/admin/apps`：分页查询应用，支持 remark 关键词；返回 `appId`、完整 `token`、`remark`、`bizTypes`、创建及更新时间。
- `GET /api/v1/admin/apps/{appId}`：查询应用详情和已授权业务，不在 URL 中传 Token。
- `POST /api/v1/admin/apps`：提交 `remark`、`bizTypes`、`idempotencyKey`，服务端生成 Token 并返回应用详情。
- `PUT /api/v1/admin/apps/{appId}/remark`：仅更新 remark。
- `POST /api/v1/admin/apps/{appId}/businesses`：仅新增业务授权，重复授权按幂等成功处理。
- `DELETE /api/v1/admin/apps/{appId}`：删除整个接入应用在全部数据源中的授权并使 Token 失效。
- `GET /api/v1/admin/businesses`：分页查询聚合后的发号业务。
- `GET /api/v1/admin/businesses/{bizType}`：查询该业务在所有数据源中的只读配置与当前进度。
- `GET /api/v1/admin/businesses/{bizType}/consistency`：查询该业务在所有可管理数据源中的存在性、`delta/remainder` 摘要和一致性状态。
- `POST /api/v1/admin/businesses`：提交 `bizType`、`step`、`delta` 和 `idempotencyKey`，在全部当前数据源创建业务，`remainder` 由服务端按稳定顺序自动配置。
- `DELETE /api/v1/admin/businesses/{bizType}`：仅当全部已配置数据源均存在该业务且 `max_id=0` 时，跨库删除业务和对应授权。
- `GET /api/v1/admin/data-sources`：仅返回可管理的数据源键和健康状态，不返回 JDBC URL、用户名或密码。
- `GET /api/v1/admin/audit-logs`：分页查询由 Starter 标准事件持久化的 TinyID 管理审计记录。

TinyID 内部 REST 模型包：

- 写入表单位于 `dev.macula.cloud.tinyid.pojo.form`。
- 查询条件位于 `dev.macula.cloud.tinyid.pojo.query`。
- 展示结果位于 `dev.macula.cloud.tinyid.pojo.vo`。
- 上述类型仅为 TinyID 服务内部 REST 契约，不移动到 `macula-cloud-api`；数据库 Entity 仍保留在数据访问层包中。

所有 Form 使用 Bean Validation；分页默认 20、最大 100。错误至少区分参数非法、未认证、非 ROOT、业务已存在、授权已存在、业务已使用不能删除、目标数据源不可用、跨库写入或删除失败及跨库补偿失败。错误响应不得包含 Token、数据库凭据或内部 SQL。

Gateway 增加 `Path=/tinyid/api/v1/admin/**`、`lb://macula-cloud-tinyid` 路由，不配置发号接口匿名白名单，也不代理 `/tinyid/api/v1/id/**`。TinyID `application.yml` 增加与 System 一致的 JWT/JWK profile 配置，并仅在 TinyID 服务自身对白名单中的现有发号接口开放匿名访问；接入应用通过 Starter 配置的 TinyID 服务地址直连发号接口。

## Flagged concerns
- 现有 `RequestFilter` 会记录包括 Token 在内的全部请求参数，直接违反已接受的敏感数据约束；管理功能合并前必须完成脱敏并用测试证明日志不含 Token，blocking
- 应用和业务删除跨多个独立数据库执行，没有分布式事务；实现必须保存恢复快照、使用业务 `max_id=0` 条件删除防止与首次发号竞态，并在部分失败时补偿，Reviewer 必须核对失败边界和恢复证明，blocking
- 当前 `DynamicDataSource` 为每次发号随机选择数据源；本规格要求管理写入对全部当前数据源执行预检、逐库提交和精确补偿，并使用持久化顺序生成不可复用的 remainder。跨库写入仍不能提供严格分布式原子性，Reviewer 必须核对补偿只影响本次新增记录，blocking
- 当前 Flyway 只明确迁移 `master` 数据源，多数据库场景下每个业务数据源如何执行同一 TinyID 增量迁移尚无现成机制；Build 计划必须落实可重复的逐数据源迁移与失败报告，不能只迁移 `master`，blocking
- 参考 System 的 `@Async + @EventListener` 后，审计是异步尽力落库：审计数据库故障不会使已完成的管理操作回滚，认证或方法调用前被拒绝的请求也可能不会产生 `OperLogEvent`。这是复用平台现有审计机制的既有语义，需要 Reviewer 明确认可其与 intent 中“所有管理操作都需要审计”的边界，blocking
- 不使用跨实例 Token 失效传播后，其他 TinyID 实例在既有定时刷新成功或进程重启前可能继续接受已删除应用的旧 Token；这是工程师明确接受的最终一致性窗口，README 和验证报告必须准确披露，non-blocking
- `tiny_id_token` 需要以明文保存 Token，才能兼容现有等值校验和“超级管理员始终可查看完整 Token”的要求；本次不引入可逆加密或密钥托管，数据库访问控制仍是敏感数据保护边界，non-blocking
- `bands.yaml` 仍是未批准的示例控制带，因此本功能只能提供功能、安全和审计验证，不能据此宣称生产 SLO 或控制带健康，non-blocking

## Verification strategy
后端单元测试覆盖 Token 安全随机生成与碰撞重试、ROOT/非 ROOT/匿名授权、Form 边界、`begin_id/max_id` 强制初始化、参数不可变、只增授权、应用删除及当前实例缓存失效、业务删除的全库存在与 `max_id=0` 条件、级联授权清理、幂等重试、分页上限、Token 缓存原子刷新及刷新失败保留旧快照；另以包扫描或编译引用证明 Form、Query、VO 已全部迁入 `pojo` 子包且旧包无残留类型。

双实例缓存测试验证处理管理请求的实例立即生效，未收到跨实例通知的另一实例可暂时保留旧授权，并在定时刷新或重启加载后收敛；依赖检查证明未为此功能新增 Redis 发布订阅机制。

DAO 与数据库集成测试使用 MySQL 验证应用聚合查询、唯一索引、全库查询和创建业务、稳定顺序生成 remainder、delta 容量校验、remark 全行更新、授权新增、应用全库删除、业务 `max_id=0` 条件删除、业务授权清理和迁移对既有数据的兼容；多数据源测试至少覆盖中间实例移除后顺序不复用、最大顺序号达到 delta 时拒绝、完整/冲突状态、Token 全库同步成功、第二库写入或删除失败且精确补偿成功、补偿失败等路径。

安全测试验证现有发号接口仍可通过 TinyID 直连地址匿名携带 TinyID Token 调用，Gateway 不暴露 `/tinyid/api/v1/id/**`；管理接口匿名返回 401、非 ROOT 返回 403、ROOT 成功，且 TinyID 直连不能绕过 ROOT 校验。日志和审计事件测试使用哨兵 Token，断言应用日志、`OperLogEvent` 和 `tiny_id_audit_log` 均不包含该值；验证 TinyID 不再注册自建审计 Filter，审计查询 API 仅 ROOT 可访问。

前端 Vitest 覆盖列表状态、跨库聚合与一致性提示、全库创建校验、只读 remainder、完整 Token 展示与复制、不可编辑字段、应用删除确认、业务删除确认与已使用业务拒绝、审计列表、无停用/轮换入口、重复提交保护和错误提示；Cypress 覆盖 ROOT 菜单可见、全库创建业务、创建应用、追加授权、删除未使用资源和查看审计的主流程，并验证非 ROOT 不显示菜单且直接访问失败。

实现完成后执行 `mvn -pl macula-cloud-tinyid,macula-cloud-system -am test -Plocal`，再按跨服务影响扩大到 `mvn test -Plocal`；前端执行相关 Vitest、`npm run build` 和关键流程 `npm run test:e2e:ci`。涉及 Gateway、Flyway 和 profile 时补充目标模块 `package`、`docker compose config --quiet`、TinyID/System/Gateway 启动、数据库迁移、TinyID 直连匿名发号兼容、Gateway 不代理发号接口及 ROOT 管理链路冒烟验证。环境依赖缺失必须单独报告，不得描述为代码测试通过。
