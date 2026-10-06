# Plan: TinyID 接入应用管理 (from spec.md 2026-10-03)
Status: accepted

## Files that change
- SDLC 合同：修改 `.sdlc/tinyid-application-management/plan.md`；Phase B 仅在本文件记录实际偏差及其风险、证明影响。
- TinyID 构建、配置与文档：修改 `macula-cloud-tinyid/pom.xml`、`src/main/resources/application.yml`、`README.md`；复用父 POM 管理的 Security、AuditLog、Flyway 和测试依赖，不增加 Redis 发布订阅依赖，文档明确跨实例缓存最终一致性窗口。
- TinyID 数据源与迁移：修改 `config/DataSourceConfig.java`、`config/DynamicDataSource.java`；新增或完善 `config/TinyIdDataSourceProperties.java`、`TinyIdDataSourceRegistry.java`、`TinyIdDataSourceOrderCoordinator.java`、`TinyIdFlywayMigrationRunner.java`、`TinyIdBusinessDataSourceReconciler.java`。修改或新增 `src/main/resources/db/migration/V2__tinyid_management.sql`、`db/master/V3__tinyid_management_master.sql`、`V4__tinyid_datasource_order.sql`、`V5__tinyid_management_request_fingerprint.sql`；不改写 `V1__baseline.sql`。
- TinyID 管理数据层：新增或完善 `dao/TinyIdManagementDAO.java`、`dao/impl/TinyIdManagementDAOImpl.java`、`dao/entity/TinyIdAuditLog.java`、`dao/entity/TinyIdManagementRequest.java`，实现全库应用授权、聚合业务、稳定数据源顺序、条件删除、精确补偿、幂等请求和审计持久化。
- TinyID REST 模型：把 `form/AddApplicationBusinessesForm.java`、`CreateApplicationForm.java`、`CreateBusinessForm.java`、`UpdateApplicationRemarkForm.java` 移至 `pojo/form/`；把 `query/ApplicationPageQuery.java`、`AuditLogPageQuery.java`、`BusinessPageQuery.java` 移至 `pojo/query/`；把 `vo/ErrorCode.java`、`PageVO.java`、`TinyIdApplicationVO.java`、`TinyIdAuditLogVO.java`、`TinyIdBusinessAggregateVO.java`、`TinyIdBusinessConsistencyVO.java`、`TinyIdBusinessVO.java`、`TinyIdDataSourceVO.java` 移至 `pojo/vo/`。同步修改全部生产代码和测试 import，最终删除空的顶层 `form`、`query`、`vo` 包。
- TinyID 管理业务与接口：新增或完善 `service/TinyIdManagementService.java`、`service/impl/TinyIdManagementServiceImpl.java`、`controller/TinyIdAdminController.java`；修改 `controller/IdContronller.java` 以引用迁移后的 `ErrorCode`。管理接口保持 `/api/v1/admin`、ROOT 方法鉴权、有界分页和 Bean Validation。
- Token 缓存与请求日志：修改 `service/TinyIdTokenService.java`、`service/impl/TinyIdTokenServiceImpl.java`、`filter/RequestFilter.java`。缓存使用实例内不可变快照和原子替换，管理成功后只更新当前实例，其他实例依靠既有定时刷新或重启收敛；请求日志对 Token 脱敏。
- 审计：修改 `listener/TinyIdAuditLogEventListener.java`，结构对齐 System 的 `@Async + @EventListener`，把 Starter 的 `OperLogEvent` 映射到 `tiny_id_audit_log`；删除 `filter/TinyIdManagementAuditFilter.java`，不保留第二套 Servlet Filter 审计路径。
- TinyID 后端测试：修改 `src/test/java/dev/macula/cloud/tinyid/ServerTest.java`；新增或完善 `config/TinyIdDataSourceRegistryTest.java`、`TinyIdDataSourceOrderCoordinatorTest.java`、`TinyIdFlywayMigrationRunnerIntegrationTest.java`、`TinyIdBusinessDataSourceReconcilerTest.java`、`TinyIdBusinessDataSourceReconcilerIntegrationTest.java`、`controller/IdContronllerTest.java`、`TinyIdAdminControllerTest.java`、`dao/impl/TinyIdManagementDAOIntegrationTest.java`、`filter/RequestFilterTest.java`、`listener/TinyIdAuditLogEventListenerTest.java`、`service/impl/TinyIdManagementServiceImplTest.java`、`TinyIdTokenServiceImplTest.java`；删除 `filter/TinyIdManagementAuditFilterTest.java`。
- Gateway 与 System 权限：修改 `macula-cloud-gateway/src/main/resources/application.yml`，仅代理 `/tinyid/api/v1/admin/**`；保留 `macula-cloud-system/src/main/resources/db/migration/V2__tinyid_management_menu.sql`，新增或完善前向迁移 `V3__move_tinyid_menu_under_system.sql`、`V4__tinyid_management_delete_permission.sql`，菜单名为“ID管理”、挂在“系统管理”下，权限只关联 ROOT。
- Admin API、配置与页面：修改 `macula-cloud-admin/src/config/index.js`、`src/api/model/tinyid/management.js`；新增或完善 `src/views/tinyid/management/index.vue`、`ApplicationPanel.vue`、`BusinessPanel.vue`、`AuditLogPanel.vue`、`ApplicationCreateDialog.vue`、`ApplicationRemarkDialog.vue`、`ApplicationAuthorizationDialog.vue`、`BusinessCreateDialog.vue`。业务视图按所有数据源聚合展示，不提供数据源切换维护入口。
- Admin 测试：新增或完善 `macula-cloud-admin/tests/unit/tinyid-management.test.js`、`cypress/e2e/tinyid-management.cy.js`，使用可控 mock 覆盖权限、空态、失败、全库业务创建、完整 Token 展示复制、授权、删除和审计。
- 不修改 `macula-cloud-api`、`V1__baseline.sql`、部署拓扑或前端依赖清单；不发布、部署、推送或重建 Docker。

## Order of work
1. 先整理模型包：移动全部 Form、Query、VO（包括既有 `ErrorCode`）到 `pojo.form`、`pojo.query`、`pojo.vo`，更新 Controller、Service、DAO、测试引用，并用搜索确认旧包声明和 import 清零。
2. 校正数据源注册与稳定顺序：以 Bean 名称维护白名单，`master` 保存只追加且不复用的顺序号；业务创建和新数据源补齐都从持久化顺序读取 `remainder`，禁止使用当前列表下标，任一顺序号达到或超过业务 `delta` 时拒绝。
3. 校正逐数据源迁移与启动协调：业务库执行公共 V2，master 依次执行 V3/V4/V5；保证重复迁移安全、重复授权明确失败、既有 Token 和发号进度不被修改，新实例只追加顺序并按既有业务参数补齐。
4. 校正 DAO 跨库写入和补偿：应用、授权、业务创建及删除均先预检，再按数据源执行；只补偿本次实际插入或删除的精确记录。新增授权失败时不得由 Service 再按整个请求集合删除，避免误删调用前已经存在的授权。
5. 校正业务规则：Token 使用 `SecureRandom` 生成；创建业务一次写入所有当前数据源，固定 `begin_id/max_id=0`、统一 `step/delta`、按稳定顺序设置只读 `remainder`；应用允许整体删除，业务只在所有数据源均存在且 `max_id=0` 时条件删除，同时清理相应授权。
6. 校正当前实例缓存：保留启动加载和一分钟定时刷新，加载失败继续使用上一份完整快照；管理成功后以原子快照更新当前实例，删除应用或业务授权时先定向移除失效项。不新增 Redis、消息主题或跨实例监听，其他实例允许到定时刷新或重启后收敛。
7. 统一审计：保留 Controller 上 Starter `@AuditLog` 且关闭请求、响应正文，删除自建审计 Filter；监听器按 System 示例使用 `@Async + @EventListener` 映射标准事件到 `tiny_id_audit_log`，异步失败不改变已完成的业务响应。
8. 校正安全与平台入口：管理接口只允许 ROOT，发号接口保持直连和原协议；Gateway 只代理管理路径；System 以前向迁移提供“系统管理 > ID管理”菜单、管理 URL 权限和 DELETE 权限；请求日志、异常和审计均不得出现完整 Token。
9. 校正 Admin：三个面板统一使用 TinyID 管理 API；发号业务按多数据源聚合展示，不显示切换器，创建操作一次提交并作用于全部数据源；保留完整 Token 的 ROOT 展示复制、应用删除、未使用业务删除、只增授权及无停用/轮换入口。
10. 补齐和修正测试：覆盖稳定顺序存在空洞时 remainder 不复用、delta 容量、新实例追加、精确补偿不误删旧授权、全库 `max_id=0` 删除、当前实例即时缓存变化、另一实例最终收敛、Starter 审计监听、无自建 Filter、Token 不泄露、ROOT/拒绝路径及前端聚合交互。
11. 更新 README 并做实施阶段静态核对：检查文件清单、包名、路由、迁移版本、Javadoc/Author、`git diff --check` 和敏感 Token 搜索；正式 Maven、Vitest、Cypress、构建和运行时验证留给 Stage 4。

## Risks
- 多数据源没有分布式事务；即使预检和精确补偿完善，补偿本身仍可能失败。失败必须返回明确的一致性错误、保留原异常及补偿异常证据，并禁止发布与数据库不一致的缓存快照。
- 数据源稳定顺序是 `remainder` 的长期合同。若误用当前列表位置、重排或复用已移除实例的序号，会造成不同数据库生成重复 ID；迁移和扩容路径必须只追加，并以 `sequence < delta` 为硬约束。
- 新数据源加入时需要补齐全部既有业务；若历史业务的 `step/delta` 已跨库不一致或新序号超出 delta，启动协调必须失败并给出可诊断信息，不能静默改写原实例配置。
- 不做跨实例失效传播意味着删除应用或授权后，其他服务实例在下一次成功定时刷新或重启前仍可能接受旧 Token。该窗口是已接受风险，不能在 README、测试报告或交付说明中描述为全实例即时失效。
- Token 明文存储且向 ROOT 完整展示是已接受边界；日志、审计、异常、URL、测试输出和非 ROOT 响应仍必须避免泄露。
- `appId=MIN(id)` 只在 master 中稳定，跨库操作必须先由 master 解析 Token，再按 Token 处理其他数据源，不能假设各库自增 ID 相同。
- Flyway 公共迁移与 master 专属迁移若混用 location 或启动顺序错误，可能重复执行或遗漏；测试需覆盖空库、既有 V1 数据库和重复启动。
- Starter 审计采用异步尽力落库，审计数据库故障不会回滚已完成的管理操作，鉴权前拒绝也可能没有 Controller 审计事件；这是已接受的平台审计语义。
- 已执行的 System Flyway 文件不能改写校验和；菜单移动和 DELETE 权限只能通过 V3/V4 前向迁移完成。
- `npm run lint` 带 `--fix`，Stage 4 如执行必须前后核对 diff，避免夹带无关格式化。
- 工作树已有大量本功能的 staged/unstaged 文件；实施必须保留用户改动和索引状态，不清理、不回滚、不覆盖无关内容。

## Proof
- 包结构检查：`rg` 证明 `dev.macula.cloud.tinyid.form|query|vo` 的 package/import 为零，所有 Form、Query、VO 位于 `pojo` 子包，Maven 编译证明引用完整。
- `TinyIdDataSourceRegistryTest`、`TinyIdDataSourceOrderCoordinatorTest`、`TinyIdBusinessDataSourceReconcilerTest` 及其集成测试：证明顺序号只追加不复用、列表空洞不会改变 remainder、新实例使用下一顺序、`sequence >= delta` 拒绝、既有实例配置不被改写。
- `TinyIdFlywayMigrationRunnerIntegrationTest`：证明 V1 数据保留、公共与 master 迁移按正确 location 执行、重复启动安全、重复 `(token,biz_type)` 可诊断失败。
- `TinyIdManagementDAOIntegrationTest`：双 MySQL 验证聚合查询、全库创建与删除、`begin_id/max_id=0`、稳定 remainder、统一 step/delta、条件删除、应用授权清理，以及第二库失败时只补偿本次变更且不删除预先存在的授权。
- `TinyIdManagementServiceImplTest`：证明安全 Token、幂等指纹、只增授权、备注同步、应用删除、业务全库 `max_id=0` 前置条件、缓存调用顺序、补偿失败映射及不允许参数修改。
- `TinyIdTokenServiceImplTest`：证明实例内不可变原子快照、并发读取、加载失败保留旧快照、当前实例定向更新/删除立即生效、独立实例在刷新或重新初始化前可以保留旧快照，且代码不存在 Redis 发布订阅路径。
- `TinyIdAuditLogEventListenerTest` 与静态 Bean 检查：证明标准 `OperLogEvent` 通过 `@Async + @EventListener` 写入脱敏审计记录、请求和响应正文关闭、自建 `TinyIdManagementAuditFilter` 不再存在。
- `TinyIdAdminControllerTest`、`IdContronllerTest`、`RequestFilterTest`：证明匿名 401、非 ROOT 403、ROOT 管理成功、四个既有发号接口兼容、有界分页和 Bean Validation 生效，日志、错误与审计不含哨兵 Token。
- `tinyid-management.test.js`：证明加载、空态、错误态、重复提交、跨库聚合、一致性提示、只读 remainder、完整 Token 复制、应用删除、未使用业务删除，以及不存在数据源切换、停用和轮换入口。
- `tinyid-management.cy.js`：mock ROOT 菜单与 API，覆盖“系统管理 > ID管理”进入、全库创建业务、创建应用、追加授权、删除未使用业务、删除应用和查看审计；覆盖非 ROOT 不显示菜单且直接请求被拒绝。
- Stage 4 最小验证为 `mvn -pl macula-cloud-tinyid,macula-cloud-system -am test -Plocal`；随后扩大到 `mvn test -Plocal`，Admin 执行相关 Vitest、`npm run build`、`npm run test:e2e:ci`。POM、Gateway、Flyway 和 profile 另做目标模块 `package`、Compose 配置渲染、多库迁移/重启、TinyID 直连发号、Gateway 发号路径不可达及 ROOT 管理链路冒烟；环境缺失单独报告，不冒充测试通过。
