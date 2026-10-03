# Plan: TinyID 接入应用管理 (from spec.md 2026-10-03)
Status: accepted

## Files that change
- SDLC 合同：新增 `.sdlc/tinyid-application-management/plan.md`；Phase B 如发生偏差，仅在此文件记录原因、影响和补充证明。
- TinyID 构建与配置：修改 `macula-cloud-tinyid/pom.xml`、`macula-cloud-tinyid/src/main/resources/application.yml`、`macula-cloud-tinyid/README.md`；增加 Security、AuditLog 和测试所需的父 POM 已管理依赖，配置 JWT/JWK、现有发号接口匿名白名单及多数据源迁移参数。
- TinyID 数据库：新增 `macula-cloud-tinyid/src/main/resources/db/migration/V2__tinyid_management.sql`，增加 `(token,biz_type)` 唯一约束、管理审计表和幂等请求表；不得改写 `V1__baseline.sql` 或已有业务数据。
- 数据源与迁移基础设施：修改 `macula-cloud-tinyid/src/main/java/dev/macula/cloud/tinyid/config/DataSourceConfig.java`、`DynamicDataSource.java`；新增 `TinyIdDataSourceRegistry.java`、`TinyIdFlywayMigrationRunner.java`，以 Spring Bean 名称作为白名单数据源键，保留 `master` 管理数据源并对每个业务数据源执行相同增量迁移。
- 现有发号与敏感数据处理：修改 `controller/IdContronller.java`、`filter/RequestFilter.java`、`dao/TinyIdInfoDAO.java`、`dao/TinyIdTokenDAO.java`、`dao/impl/TinyIdInfoDAOImpl.java`、`dao/impl/TinyIdTokenDAOImpl.java`、`service/TinyIdTokenService.java`、`service/impl/TinyIdTokenServiceImpl.java`；保持四个发号接口兼容，移除 Token 日志暴露，并将授权缓存改为多数据源加载、不可变快照和即时刷新。
- TinyID 管理数据层：新增 `dao/TinyIdManagementDAO.java`、`dao/impl/TinyIdManagementDAOImpl.java`、`dao/entity/TinyIdAuditLog.java`、`dao/entity/TinyIdManagementRequest.java`，集中实现参数化分页查询、按白名单数据源读写、全库 Token 同步、补偿和审计/幂等持久化。
- TinyID 管理契约：新增 `form/CreateApplicationForm.java`、`UpdateApplicationRemarkForm.java`、`AddApplicationBusinessesForm.java`、`CreateBusinessForm.java`；新增 `query/ApplicationPageQuery.java`、`BusinessPageQuery.java`、`AuditLogPageQuery.java`；新增 `vo/PageVO.java`、`TinyIdApplicationVO.java`、`TinyIdBusinessVO.java`、`TinyIdBusinessConsistencyVO.java`、`TinyIdDataSourceVO.java`、`TinyIdAuditLogVO.java`。所有输入使用 Bean Validation，所有输出与 Entity 隔离。
- TinyID 管理业务与接口：新增 `service/TinyIdManagementService.java`、`service/impl/TinyIdManagementServiceImpl.java`、`controller/TinyIdAdminController.java`、`listener/TinyIdAuditLogEventListener.java`。Controller 统一位于 `/api/v1/admin` 并要求 `hasRole('ROOT')`；Service 实现安全 Token 生成、应用聚合、备注更新、只增授权、按数据源业务维护、一致性状态、幂等与失败补偿；AuditLog 禁止记录请求和响应正文。
- TinyID 后端测试：修改 `src/test/java/dev/macula/cloud/tinyid/ServerTest.java`，使其保留为轻量应用装配测试；新增 `filter/RequestFilterTest.java`、`service/impl/TinyIdTokenServiceImplTest.java`、`service/impl/TinyIdManagementServiceImplTest.java`、`controller/TinyIdAdminControllerTest.java`、`dao/impl/TinyIdManagementDAOIntegrationTest.java`、`config/TinyIdFlywayMigrationRunnerIntegrationTest.java`，覆盖安全、缓存、单库/多库、迁移、补偿和 Token 不泄露。
- Gateway：修改 `macula-cloud-gateway/src/main/resources/application.yml`，增加 `/tinyid/**` 路由并保留现有 System 路由和认证行为。
- System 权限数据：新增 `macula-cloud-system/src/main/resources/db/migration/V2__tinyid_management_menu.sql`，幂等地增加 TinyID 管理菜单、管理 API URL 权限及 ROOT 角色关联，不赋权给其他角色。
- Admin API 与配置：修改 `macula-cloud-admin/src/config/index.js`；新增 `macula-cloud-admin/src/api/model/tinyid/management.js`，统一封装应用、业务、数据源、一致性和审计 API。
- Admin 页面：新增 `macula-cloud-admin/src/views/tinyid/management/index.vue`、`ApplicationPanel.vue`、`BusinessPanel.vue`、`AuditLogPanel.vue`、`ApplicationCreateDialog.vue`、`ApplicationRemarkDialog.vue`、`ApplicationAuthorizationDialog.vue`、`BusinessCreateDialog.vue`。复用 Element Plus 与现有表格/对话框风格；多库时显示数据源切换器，切换后清理旧状态并重新加载。
- Admin 测试：新增 `macula-cloud-admin/tests/unit/tinyid-management.test.js`、`macula-cloud-admin/cypress/e2e/tinyid-management.cy.js`，使用 mock API 验证 ROOT/非 ROOT、单库/多库、切换隔离、创建流程、完整 Token 展示复制和禁止删除/停用/轮换。
- 不删除任何文件；不修改 `macula-cloud-api`、现有发号数据库基线、部署拓扑或前端依赖清单，除非实施中出现会使本计划失效的事实并先取得工程师确认。

## Order of work
1. 建立可测试的多数据源注册表：以 Bean 名称枚举、校验并选择数据源，固定 `master` 管理数据源；重构随机路由数据源只用于既有发号路径，管理 DAO 必须显式选库。
2. 实现逐数据源 Flyway：在所有注册业务数据源上校验并应用 V2，确保重复 `(token,biz_type)` 时明确失败，任何数据源迁移失败都阻止应用进入可服务状态；先用双 MySQL 容器证明重复执行和失败报告。
3. 建立安全边界：加入资源服务器配置和四个发号接口白名单，为管理 Controller 预留 ROOT 方法鉴权；修改 `RequestFilter` 统一脱敏 `token`，并确保异常与对象字符串不含原始 Token。
4. 扩展 DAO 与缓存：实现按数据源的业务分页/详情/创建、跨库一致性摘要、应用聚合、全库 Token 授权同步、审计与幂等访问；将 Token 缓存改为多库读取后的不可变原子快照，失败时保留旧快照。
5. 实现管理 Service：使用 `SecureRandom` 生成至少 256 bit Token；实现应用创建、备注修改、只增授权、即时缓存刷新、按选中数据源创建业务、跨库状态判定、预检、逐库提交、补偿和专用错误映射。
6. 实现管理 Controller 与审计监听器：完成 spec 中全部 `/api/v1/admin` 接口、分页和 Bean Validation；所有接口要求 ROOT，所有成功和失败调用写入 `master` 审计表且关闭请求/响应正文审计。
7. 接通平台入口：增加 Gateway TinyID 路由；增加 System V2 菜单、URL 权限和 ROOT 角色关系，并核对动态菜单组件路径与外部 `/tinyid` API 前缀。
8. 实现 Admin：增加 TinyID API 模块和管理页三个面板；业务面板先探测数据源，单库自动选择、多库显示切换器，切换时取消旧请求影响并清空表格/表单；实现一致性提示、完整 Token 复制以及无删除/停用/轮换入口。
9. 补齐后端单元、MockMvc 和 MySQL Testcontainers 集成测试；补齐前端 Vitest 与 Cypress mock 流程；测试数据只使用虚构 Token，断言日志、审计和错误响应均不出现哨兵 Token。
10. 更新 TinyID README，执行格式与静态 diff 检查，核对实际文件集合和顺序；任何偏离本计划的实现先记录到 `plan.md`，若改变合同则暂停并请求工程师决定。

## Risks
- 多数据源 Bean 的现有发现逻辑依赖 `DruidDataSource.name`，该名称可能为空或重复；改为 Bean 名称后必须避免把路由 DataSource 自身再次注册并形成循环依赖。
- V2 必须在每个业务数据源执行，而当前 Spring Boot Flyway 明确指向 `master`；自定义迁移协调器若与自动配置并存可能重复迁移或启动顺序错误，因此要么由协调器统一接管，要么明确排除重复初始化，不能混用。
- 应用与 Token 授权跨库同步没有分布式事务；预检和补偿只能降低而不能消除部分提交风险。补偿失败必须阻止缓存刷新、返回一致性错误并留下不含 Token 的审计证据。
- 业务按数据源逐库配置期间可能处于“配置中”；在所有库完成前，随机路由可能命中尚无该 `biz_type` 的数据库。页面必须明显提示，服务端必须拒绝会制造 `delta` 冲突或重复 `remainder` 的后续创建。
- `tiny_id_token` 明文保存并向 ROOT 完整展示属于已接受风险；日志、审计、URL、异常、测试报告和前端非 ROOT 路径必须严格避免二次泄露。
- 现有应用数据使用相同 Token 的多行表达一个应用，`appId=MIN(id)` 只在 `master` 中稳定；跨库更新必须先由 `master` 解析 Token，再按 Token 更新其他库，不能假设各库行 ID 相同。
- Gateway URL 权限与 TinyID 服务 ROOT 鉴权必须同时生效；只实现一层会留下直连绕过或动态权限缓存遗漏。
- Admin 当前测试基础较少，Cypress 流程需完全 mock 后端与登录状态；不得依赖真实共享环境或把生成物、视频、截图提交进仓库。
- `npm run lint` 会自动改写文件，不在 Build 实施阶段作为只读检查使用；如后续测试阶段执行，必须在前后核对 diff。
- 回滚代码和菜单迁移不能删除已创建的业务、Token、审计或幂等数据；数据库回滚采用停止新管理写入、恢复旧应用版本并保留新增表/索引的前向兼容方式。

## Proof
- `TinyIdAdminControllerTest`：证明匿名请求为 401、非 ROOT 为 403、ROOT 可访问全部管理端点；验证分页上限、非法数据源和 Bean Validation 拒绝路径。
- `RequestFilterTest`：使用唯一哨兵 Token 调用现有发号接口和失败路径，证明日志只出现脱敏值，原始 Token 不出现。
- `TinyIdTokenServiceImplTest`：证明多库授权并集、不可变原子快照、即时刷新、定时刷新失败保留旧快照以及并发读取安全。
- `TinyIdManagementServiceImplTest`：证明安全 Token 生成与碰撞重试、`begin_id/max_id=0`、参数创建后不可变、只增授权、备注全库更新、幂等重试、跨库一致性判定和补偿分支。
- `TinyIdManagementDAOIntegrationTest`：在双 MySQL 数据源验证按源隔离查询/创建、唯一索引、应用聚合、全库 Token 同步、第二库失败后的补偿、审计落库及审计不含 Token。
- `TinyIdFlywayMigrationRunnerIntegrationTest`：证明 V1 既有数据保留、V2 在每个数据源执行、重复执行安全、重复授权数据导致可诊断失败且不会静默删数据。
- 现有发号兼容测试：覆盖四个原路径、合法/非法 `(token,biz_type)`、批量上限和号段获取，确认路由、安全配置和缓存改造未改变外部协议。
- `tinyid-management.test.js`：证明单库自动选择、多库切换清空并重载、过期响应不覆盖新数据、表单边界、一致性状态、完整 Token 复制以及不存在删除/停用/轮换入口。
- `tinyid-management.cy.js`：mock ROOT 登录、动态菜单和 TinyID API，覆盖切换两个数据源分别创建业务、创建应用、增加授权、查看审计；覆盖非 ROOT 无菜单且直接请求被拒绝。
- Stage 4 运行最小后端命令 `mvn -pl macula-cloud-tinyid,macula-cloud-system -am test -Plocal`，再运行受影响范围 `mvn test -Plocal`；运行 Admin 相关 Vitest、`npm run build` 和 `npm run test:e2e:ci`。POM、Gateway、Flyway 与 profile 变化另做 `package`、`docker compose config --quiet`、多库迁移、服务启动、匿名发号兼容及 ROOT 管理链路冒烟验证，并明确报告任何环境未满足项。

## Deviations
- 2026-10-03：仓库不存在 `CLAUDE.md`，无法按 Build Phase B 步骤读取；实施继续遵循仓库 `AGENTS.md`、相关 `.agents/rules/*`、`REVIEW.md`、已接受 spec 与本计划，不影响文件范围和证明策略。
