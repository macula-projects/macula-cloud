# Plan: TinyID 接入应用管理多数据源简化 (from spec.md 2026-10-07)
Status: accepted

## Files that change
- 修改 `macula-cloud-tinyid/src/main/java/dev/macula/cloud/tinyid/config/DataSourceConfig.java`：沿用原有 `List<DataSource>` 收集方式，同时提供命名限定的物理库 `List<JdbcTemplate>`；管理列表只包含 `DruidDataSource`，不包含 `DynamicDataSource`。
- 修改 `macula-cloud-tinyid/src/main/java/dev/macula/cloud/tinyid/config/DynamicDataSource.java`：恢复简单的 key 列表随机路由，仅服务现有发号链路。
- 删除 `TinyIdDataSourceProperties.java`、`TinyIdDataSourceRegistry.java`、`TinyIdDataSourceOrderCoordinator.java`、`TinyIdFlywayMigrationRunner.java`、`TinyIdBusinessDataSourceReconciler.java`，不再注册额外数据源、不持久化顺序、不在启动时迁移或补齐多库。
- 修改 `TinyIdManagementDAOImpl.java`：构造函数注入命名的 `List<JdbcTemplate>`，第一个模板作为 master；所有聚合、预检、创建、更新、删除、补偿和授权缓存加载按列表循环，`remainder` 与数据源展示序号均使用列表下标。
- 视编译结果同步精简 `TinyIdManagementDAO.java`、`TinyIdDataSourceVO.java` 及其调用方中只为注册表存在的参数或语义，不改变管理 REST API。
- 修改 `macula-cloud-tinyid/src/main/resources/application.yml`：移除 `macula.tinyid.datasource-order`；恢复 master 的常规 Flyway 配置。多库公共迁移由部署流程对每个物理库执行，服务不负责逐库编排。
- 保留 `db/master/V4__tinyid_datasource_order.sql` 不改写，兼容已经执行过该迁移的环境；运行时代码不再访问 `tiny_id_datasource_order`。其余管理迁移保持现有版本与校验和。
- 修改 `macula-cloud-tinyid/README.md`：说明发号随机路由、管理 `JdbcTemplate` 列表循环、列表下标即 `remainder`、配置顺序不可重排且扩容只能末尾追加，以及新库迁移和历史初始化由部署负责。
- 删除 `TinyIdDataSourceRegistryTest.java`、`TinyIdDataSourceOrderCoordinatorTest.java`、`TinyIdFlywayMigrationRunnerIntegrationTest.java`、`TinyIdBusinessDataSourceReconcilerTest.java`、`TinyIdBusinessDataSourceReconcilerIntegrationTest.java`。
- 新增 `DataSourceConfigTest.java`，修改 `TinyIdManagementDAOIntegrationTest.java` 及受构造函数变化影响的 Service、Token 缓存测试，证明管理列表与随机路由隔离、按下标处理多库及跨库补偿行为。
- 不修改现有发号 API、Gateway/System/Admin 页面、权限、审计、Token 缓存最终一致性或 POJO 包结构；不部署、不重建 Docker、不推送。

## Order of work
1. 先恢复数据源基础定义：`DataSourceConfig` 从 Spring 的 `List<DataSource>` 提取有序物理 `DruidDataSource`，同一份顺序分别配置随机路由和命名管理 `List<JdbcTemplate>`；`DynamicDataSource` 只保留随机选择。
2. 删除注册表、配置属性、持久化顺序、启动 Flyway 编排和业务补齐组件，移除所有生产代码引用及 `datasource-order` 配置。
3. 重构 `TinyIdManagementDAOImpl`：以管理模板列表下标代替数据源 key/持久化 sequence，第一个模板承担 master 查询、幂等和审计；所有跨库操作仍逐库预检、执行并精确补偿。
4. 保持创建业务规则：所有库使用相同 `step/delta`，`begin_id/max_id=0`，每库 `remainder=index`，要求 `delta > 最大下标`；聚合一致性检查改为比较当前列表下标。
5. 调整 Token 授权全库加载、应用和业务删除及缓存刷新调用，确保不通过随机路由模板执行管理操作，也不重复包含同一物理库。
6. 调整配置、README 和迁移说明：master 使用常规 Flyway；其他物理库由部署流程应用公共迁移和初始化。保留历史 V4 文件但不再使用其表。
7. 删除复杂组件专属测试，新增简单数据源配置测试并改写 DAO 多库集成测试；执行实施阶段编译、目标测试、引用清零和 `git diff --check`。

## Risks
- `remainder` 依赖 Spring 注入的物理数据源列表顺序；重排、删除或中间插入数据源会使实例身份错位并可能生成重复 ID。README 必须把“既有顺序不变、扩容只在末尾追加”写成部署硬约束。
- `List<DataSource>` 在路由数据源创建前后可能包含不同 Bean；筛选必须只接受物理 `DruidDataSource`，并用测试证明管理列表不包含 `DynamicDataSource`，否则管理操作会随机重复或漏写。
- 第一个物理数据源被约定为 master；如果配置顺序错误，应用查询、幂等和审计会落到错误数据库。启动时需校验列表非空，并明确顺序约定。
- 删除启动迁移和补齐意味着新增数据库在加入随机发号池前必须由部署流程完成公共迁移、历史业务和授权初始化；服务无法自动阻止一个结构完整但历史数据未补齐的新库投入使用。
- 多库写入仍无分布式事务；保留逐库预检、局部事务、快照和精确补偿，补偿失败仍需人工修复。
- `V4__tinyid_datasource_order.sql` 可能已在本地或共享环境执行，不能删除或改写校验和；保留未使用表比破坏 Flyway 历史更安全。
- 当前工作树已有大量本功能改动；实施只删除本次明确废弃的新增组件和测试，不清理或回滚其他改动。

## Proof
- `DataSourceConfigTest` 证明物理数据源按注入顺序生成管理 `JdbcTemplate` 列表、随机路由使用相同物理集合、路由数据源不会进入管理列表、空物理列表启动失败。
- `TinyIdManagementDAOIntegrationTest` 使用两个物理数据库证明应用和业务查询聚合、所有写操作逐库执行、创建业务得到 `remainder=0/1`、`delta <= 最大下标` 被拒绝、第二库失败只补偿本次实际变更、应用与未使用业务删除仍检查全部数据库。
- `IdContronllerTest` 和现有 Token Service 测试证明四个发号接口及 `(token,bizType)` 校验不变，发号 DAO 仍经 `@Primary DynamicDataSource` 随机路由。
- 静态搜索证明生产代码不再引用 `TinyIdDataSourceRegistry`、`TinyIdDataSourceOrderCoordinator`、`TinyIdFlywayMigrationRunner`、`TinyIdBusinessDataSourceReconciler`、`TinyIdDataSourceProperties` 或 `macula.tinyid.datasource-order`。
- `mvn -pl macula-cloud-tinyid -am test -Plocal` 证明模块编译和单元测试通过；外部 MySQL 集成测试明确报告运行条件和跳过情况。`git diff --check` 证明补丁格式正确。
