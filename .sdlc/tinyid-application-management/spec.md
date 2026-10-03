# Spec: TinyID 接入应用管理 (from intent.md 2026-10-03)
Status: accepted

## Source intent
[已接受的 TinyID 接入应用管理 intent](./intent.md)：为平台超级管理员提供 TinyID 应用、业务发号配置、授权关系与审计管理能力，同时保持现有发号协议兼容。

## Requirements
1. 只有携带有效平台访问令牌且具有 `ROOT` 角色的用户可以访问 TinyID 管理 API 和管理页面；匿名用户、普通管理员及其他角色必须被拒绝。
2. 现有 `/api/v1/id/nextId`、`nextIdSimple`、`nextSegmentId`、`nextSegmentIdSimple` 的路径、参数和响应语义保持兼容，并继续使用 `(token, biz_type)` 是否存在判断发号权限。
3. 管理端提供“接入应用”“发号业务”“审计日志”三个可访问视图，并完整处理加载、空数据、失败、表单校验和重复提交状态。
4. 超级管理员可以分页查询接入应用；每个应用以唯一 Token 标识，以 `remark` 描述应用，并展示其已获授权的 `biz_type` 集合。
5. 超级管理员可以创建接入应用。Token 必须由服务端使用密码学安全随机源生成，不接受客户端指定；创建时至少关联一个已存在的 `biz_type`。
6. 超级管理员可以随时查看和复制完整 Token；Token 不得向非超级管理员返回，不得出现在应用日志、审计请求参数、审计响应内容或错误消息中。
7. 超级管理员只能修改应用的 `remark`，以及为应用新增业务授权；管理 API 和页面不得提供 Token 删除、停用、轮换或既有业务授权撤销能力。
8. 超级管理员可以分页查询发号业务及其各数据源配置，查询结果必须区分 `biz_type`、`step`、`delta`、各数据源的 `remainder`、当前 `max_id`、版本和更新时间。
9. 超级管理员可以创建发号业务。`begin_id` 和 `max_id` 由服务端固定初始化为 `0`，不得由请求覆盖。
10. 创建发号业务时，`biz_type` 必须非空且全局唯一，`step` 必须大于 `0`，`delta` 必须大于 `0`，目标数据源数量必须等于 `delta`，各数据源的 `remainder` 必须无重复且完整覆盖 `0` 至 `delta-1`。
11. 单数据库创建默认使用 `delta=1`、`remainder=0`；多数据库创建必须显式选择目标数据源，并为每个目标数据源配置 `remainder`。
12. `biz_type`、`step`、`delta`、`remainder`、`begin_id` 和 `max_id` 创建后均不得通过管理 API 修改；页面只读展示这些字段。
13. 管理 API 和页面不得提供发号业务删除或停用能力。
14. 应用创建或新增授权成功后，内存中的 Token 授权缓存必须立即刷新；不允许依赖现有一分钟定时刷新后才生效。刷新过程必须以完整的新快照原子替换旧快照。
15. 所有管理 API 调用（包含查询、查看完整 Token 和变更操作）的成功与失败结果都必须写入审计记录，至少包含操作者、动作、请求方法、请求路径、结果、客户端地址和时间，但不得保存原始 Token。
16. 管理 API 必须使用有界分页和白名单排序；不允许无限列表、任意 SQL 排序或将数据库 Entity 直接作为 REST 契约。
17. 新增的数据库迁移必须兼容现有 `tiny_id_info`、`tiny_id_token` 数据，并保证 `(token, biz_type)` 不重复；迁移不得修改或删除已有 Token 和已发放的 ID 进度。
18. Gateway 必须增加 `/tinyid/**` 到 `macula-cloud-tinyid` 的路由，System 权限数据必须只向 `ROOT` 角色发布 TinyID 管理菜单和管理 API 权限；TinyID 服务本身仍须执行 `ROOT` 角色校验，避免绕过 Gateway 直连管理端口。
19. 现有请求日志必须对名为 `token` 的请求参数进行脱敏，验证失败日志只能记录业务类型、结果和关联标识，不得记录原始 Token。
20. README 和数据库迁移说明必须同步描述管理入口、字段语义、多数据库约束、安全边界、审计行为及不支持的删除、停用、轮换操作。

## Non-goals
- 不改变 TinyID 的号段生成算法、客户端协议或现有四个发号接口。
- 不提供 Token 删除、停用、轮换、掩码展示或自定义 Token。
- 不提供既有应用业务授权的撤销能力。
- 不提供发号业务删除、停用或发号参数修改能力。
- 不把 TinyID 的数据库模型或服务实现放入 `macula-cloud-api`，也不新增供其他微服务调用的 Feign 公共契约。
- 不引入新的前端框架、状态管理方案或组件库。
- 不在本次变更中定义或调整生产 SLO、告警阈值、部署拓扑或数据保留策略。

## Design
约束来源：仓库根 `AGENTS.md`；`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`；根 `REVIEW.md` 的 Bugs/Security/Compliance 三类审查；根 `bands.yaml`（当前仅为待批准的示例控制带）；以及已接受的 `intent.md`。当前没有发现额外的组织级品牌规范或独立数据分类政策。

管理能力留在 `macula-cloud-tinyid`，数据仍由 TinyID 自己拥有。新增管理 Controller、Service、DAO、Form/Query/VO 和审计监听器；Controller 只负责协议、校验和编排，跨数据源写入、不可变规则、Token 生成、缓存刷新和事务补偿由 Service 负责，JdbcTemplate DAO 只执行参数化 SQL。

TinyID 引入仓库已管理版本的 `macula-boot-starter-security` 和 `macula-boot-starter-auditlog`。资源服务器复用平台 JWT/JWK 配置，现有发号路径加入明确的匿名白名单；管理 Controller 使用方法级 `hasRole('ROOT')` 校验。Gateway 再按现有权限模型校验 URL 权限，从而形成 Gateway 与服务端两层授权。

接入应用不新增独立应用表。`tiny_id_token` 继续以一行表示一个 `(token, biz_type)` 授权；相同 Token 的多行共同组成一个应用，`remark` 在同一 Token 的所有行中保持一致。应用列表使用 `MIN(id)` 作为稳定的 `appId`，前端和管理 API 以 `appId` 定位应用，避免将 Token 放入 URL。因为数据不可删除，`appId` 不会因授权变化而漂移。创建应用至少包含一个业务授权；修改备注会在事务内更新该 Token 的全部行；新增授权只插入不存在的组合。

Token 使用 `SecureRandom` 生成至少 256 bit 随机值，并编码为 URL-safe 字符串。写入前在所有目标数据源检查唯一性；极低概率碰撞时重新生成。Token 只在受 `ROOT` 保护的 VO 中完整返回，不写入 `toString` 日志、异常消息或审计载荷。

发号业务按 `biz_type` 聚合展示。创建请求包含 `bizType`、`step`、统一的 `delta` 和一组 `{dataSourceKey, remainder}` 配置；服务端验证数据源数量等于 `delta` 且余数完整覆盖 `0..delta-1`，并覆盖 `begin_id=0`、`max_id=0`、`version=0` 和服务端时间。单库页面默认填充 `delta=1`、`remainder=0`。创建完成后不提供更新、删除和停用入口。

多数据库写入由显式的数据源注册表完成，不通过当前随机路由选择器执行管理写入。命名为 `master` 的数据源作为管理数据源，保存审计和幂等请求记录；业务与授权数据按请求写入全部目标业务数据源。Service 先对所有目标库执行连接、表结构、重复业务、参数和 Token 授权预检，再逐库提交。任一写入失败时，仅补偿删除本次操作新插入且尚未对调用方报告成功的记录；若补偿失败，返回专用一致性错误、保留完整审计记录并禁止刷新内存缓存，等待运维修复后重试。所有创建接口使用幂等请求键防止客户端重试制造重复记录。

现有 Token 缓存改为实例级、不可变快照并用原子引用替换。定时刷新和管理变更后的即时刷新复用同一加载逻辑；加载失败保留上一份完整快照，不发布半成品。多数据源读取取授权并集，但发现同一 Token 的 `remark` 不一致或同一业务配置不一致时记录不含 Token 原文的一致性告警。

审计使用现有 `@AuditLog` 切面，但所有 TinyID 管理方法关闭请求与响应正文记录，防止完整 Token 进入事件。TinyID 增加本地审计监听器，并将审计写入 `master` 数据源的 `tiny_id_audit_log` 表，保存操作者、标题、方法、路径、成功/失败、错误摘要、客户端地址和时间；原始 Token、Authorization Header 和业务响应正文均不落库。审计页面读取该表并分页展示。

管理端新增 `src/api/model/tinyid/management.js` 及 `src/views/tinyid/management/` 页面，沿用现有 Vue 3、Element Plus、`scTable` 和请求封装。System 的增量 Flyway 迁移新增 TinyID 管理菜单、URL 权限及 ROOT 角色关联；动态菜单加载 `tinyid/management/index`。前端配置增加 `MODEL.tinyid='tinyid'`，所有请求经 Gateway 发往 `/tinyid/api/v1/admin/**`。

## Data and interfaces
TinyID 数据库增量迁移：

- 所有配置为业务数据源的 TinyID 数据库都为 `tiny_id_token` 增加唯一索引 `uk_tiny_id_token_token_biz_type(token, biz_type)`；迁移前先检测重复组合，存在重复时失败而不是静默删数据。
- `master` 数据源新增 `tiny_id_audit_log`：`id`、`operator`、`action`、`request_method`、`request_uri`、`result`、`error_summary`、`client_ip`、`create_time`；不设置 Token 字段。
- `master` 数据源新增 `tiny_id_management_request`，以唯一 `idempotency_key`、操作类型、资源类型、资源标识、执行状态和时间保存创建请求结果；不重复保存完整 Token。
- 不改变 `tiny_id_info` 和 `tiny_id_token` 的现有列语义，不回写已有 `begin_id`、`max_id`、Token 或 remark。

System 数据库增量迁移：

- 新增 ROOT 专属 TinyID 管理菜单，组件为 `tinyid/management/index`。
- 新增与下述管理 API 对应的 URL 权限记录，并只关联 `ROOT` 角色。
- 迁移使用确定且可重复验证的菜单、权限和角色关联写法，不修改其他角色现有权限。

管理 API 均位于 `/api/v1/admin`，经 Gateway 后外部前缀为 `/tinyid`：

- `GET /api/v1/admin/apps`：分页查询应用，支持 remark 关键词；返回 `appId`、完整 `token`、`remark`、`bizTypes`、创建及更新时间。
- `GET /api/v1/admin/apps/{appId}`：查询应用详情和已授权业务，不在 URL 中传 Token。
- `POST /api/v1/admin/apps`：提交 `remark`、`bizTypes`、`idempotencyKey`，服务端生成 Token 并返回应用详情。
- `PUT /api/v1/admin/apps/{appId}/remark`：仅更新 remark。
- `POST /api/v1/admin/apps/{appId}/businesses`：仅新增业务授权，重复授权按幂等成功处理。
- `GET /api/v1/admin/businesses`：分页查询按 `biz_type` 聚合的发号业务。
- `GET /api/v1/admin/businesses/{bizType}`：查询各数据源的只读配置与当前进度。
- `POST /api/v1/admin/businesses`：提交 `bizType`、`step`、`delta`、`dataSources[{dataSourceKey,remainder}]` 和 `idempotencyKey`，创建业务及各数据源配置。
- `GET /api/v1/admin/data-sources`：仅返回可管理的数据源键和健康状态，不返回 JDBC URL、用户名或密码。
- `GET /api/v1/admin/audit-logs`：分页查询 TinyID 管理审计记录。

所有 Form 使用 Bean Validation；分页默认 20、最大 100。错误至少区分参数非法、未认证、非 ROOT、业务已存在、授权已存在、目标数据源不可用、跨库写入失败及跨库补偿失败。错误响应不得包含 Token、数据库凭据或内部 SQL。

Gateway 增加 `Path=/tinyid/**`、`lb://macula-cloud-tinyid`、`StripPrefix=1` 路由。TinyID `application.yml` 增加与 System 一致的 JWT/JWK profile 配置，并仅对白名单中的现有发号接口开放匿名访问。

## Flagged concerns
- 现有 `RequestFilter` 会记录包括 Token 在内的全部请求参数，直接违反已接受的敏感数据约束；管理功能合并前必须完成脱敏并用测试证明日志不含 Token，blocking
- “不删除、不停用”与“维护应用获准访问的业务”存在撤权语义冲突；本规格选择授权关系只增不减，Reviewer 必须确认该不可撤权模型符合运营和安全要求，blocking
- 当前 `DynamicDataSource` 为每次访问随机选择数据源，且仓库没有跨库事务；本规格选择 `master` 管理数据源、全库预检、逐库提交、失败补偿和显式一致性错误，Reviewer 必须接受其不能提供严格分布式原子性的限制，blocking
- 当前 Flyway 只明确迁移 `master` 数据源，多数据库场景下每个业务数据源如何执行同一 TinyID 增量迁移尚无现成机制；Build 计划必须落实可重复的逐数据源迁移与失败报告，不能只迁移 `master`，blocking
- `tiny_id_token` 需要以明文保存 Token，才能兼容现有等值校验和“超级管理员始终可查看完整 Token”的要求；本次不引入可逆加密或密钥托管，数据库访问控制仍是敏感数据保护边界，non-blocking
- `bands.yaml` 仍是未批准的示例控制带，因此本功能只能提供功能、安全和审计验证，不能据此宣称生产 SLO 或控制带健康，non-blocking

## Verification strategy
后端单元测试覆盖 Token 安全随机生成与碰撞重试、ROOT/非 ROOT/匿名授权、Form 边界、`begin_id/max_id` 强制初始化、参数不可变、只增授权、幂等重试、分页上限、Token 缓存原子刷新及刷新失败保留旧快照。

DAO 与数据库集成测试使用 MySQL 验证应用聚合查询、唯一索引、业务创建、remark 全行更新、授权新增、审计落库和迁移对既有数据的兼容；多数据源测试至少覆盖全部成功、预检失败、第二库写入失败且补偿成功、补偿失败四种路径。

安全测试验证现有发号接口仍可匿名携带 TinyID Token 调用，管理接口匿名返回 401、非 ROOT 返回 403、ROOT 成功；Gateway 与 TinyID 直连都不能绕过 ROOT 校验。日志和审计测试使用哨兵 Token，断言应用日志、审计参数、审计响应和异常内容均不包含该值。

前端 Vitest 覆盖列表状态、创建校验、完整 Token 展示与复制、不可编辑字段、无删除/停用/轮换入口、重复提交保护和错误提示；Cypress 覆盖 ROOT 菜单可见及创建业务、创建应用、追加授权的主流程，并验证非 ROOT 不显示菜单且直接访问失败。

实现完成后执行 `mvn -pl macula-cloud-tinyid,macula-cloud-system -am test -Plocal`，再按跨服务影响扩大到 `mvn test -Plocal`；前端执行相关 Vitest、`npm run build` 和关键流程 `npm run test:e2e:ci`。涉及 Gateway、Flyway 和 profile 时补充目标模块 `package`、`docker compose config --quiet`、TinyID/System/Gateway 启动、数据库迁移、匿名发号兼容及 ROOT 管理链路冒烟验证。环境依赖缺失必须单独报告，不得描述为代码测试通过。
