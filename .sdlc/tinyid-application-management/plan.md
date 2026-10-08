# Plan: TinyID 接入应用管理多数据源简化 (from spec.md 2026-10-07)
Status: accepted

## Files that change
- 修改 `macula-cloud-tinyid/src/main/java/dev/macula/cloud/tinyid/config/DataSourceConfig.java`：沿用原有 `List<DataSource>` 收集方式，同时提供命名限定的物理库 `List<JdbcTemplate>`；管理列表只包含 `DruidDataSource`，不包含 `DynamicDataSource`。
- 修改 `macula-cloud-tinyid/src/main/java/dev/macula/cloud/tinyid/config/DynamicDataSource.java`：恢复简单的 key 列表随机路由，仅服务现有发号链路。
- 删除 `TinyIdDataSourceProperties.java`、`TinyIdDataSourceRegistry.java`、`TinyIdDataSourceOrderCoordinator.java`、`TinyIdFlywayMigrationRunner.java`、`TinyIdBusinessDataSourceReconciler.java`，不再注册额外数据源、不持久化顺序、不在启动时迁移或补齐多库。
- 修改 `TinyIdManagementServiceImpl.java`：直接注入 Mapper 和路由数据源；所有聚合、预检、创建、更新、删除和补偿按物理列表循环，`remainder` 与数据源展示序号均使用列表下标。
- 删除原有 DAO 接口及实现，发号与缓存 Service 直接使用 Mapper，不改变管理 REST API。
- 修改 `macula-cloud-tinyid/src/main/resources/application.yml`：移除 `macula.tinyid.datasource-order`；恢复 master 的常规 Flyway 配置。多库公共迁移由部署流程对每个物理库执行，服务不负责逐库编排。
- 保留 `db/master/V4__tinyid_datasource_order.sql` 不改写，兼容已经执行过该迁移的环境；运行时代码不再访问 `tiny_id_datasource_order`。其余管理迁移保持现有版本与校验和。
- 修改 `macula-cloud-tinyid/README.md`：说明发号随机路由、管理 `JdbcTemplate` 列表循环、列表下标即 `remainder`、配置顺序不可重排且扩容只能末尾追加，以及新库迁移和历史初始化由部署负责。
- 删除 `TinyIdDataSourceRegistryTest.java`、`TinyIdDataSourceOrderCoordinatorTest.java`、`TinyIdFlywayMigrationRunnerIntegrationTest.java`、`TinyIdBusinessDataSourceReconcilerTest.java`、`TinyIdBusinessDataSourceReconcilerIntegrationTest.java`。
- 新增 `DataSourceConfigTest.java`，修改 `TinyIdManagementServiceIntegrationTest.java` 及受构造函数变化影响的 Service、Token 缓存测试，证明管理显式路由与随机发号隔离、按下标处理多库及跨库补偿行为。
- 不修改现有发号 API、Gateway/System/Admin 页面、权限、审计、Token 缓存最终一致性或 POJO 包结构；不部署、不重建 Docker、不推送。

## Order of work
1. 先恢复数据源基础定义：`DataSourceConfig` 从 Spring 的 `List<DataSource>` 提取有序物理 `DruidDataSource`，同一份顺序分别配置随机路由和命名管理 `List<JdbcTemplate>`；`DynamicDataSource` 只保留随机选择。
2. 删除注册表、配置属性、持久化顺序、启动 Flyway 编排和业务补齐组件，移除所有生产代码引用及 `datasource-order` 配置。
3. 重构 `TinyIdManagementServiceImpl`：以物理数据源列表下标代替持久化 sequence，第一个数据源承担聚合查询和审计；所有跨库操作仍逐库预检、执行并精确补偿。
4. 保持创建业务规则：所有库使用相同 `step/delta`，`begin_id/max_id=0`，每库 `remainder=index`，要求 `delta > 最大下标`；聚合一致性检查改为比较当前列表下标。
5. 调整 Token 授权全库加载、应用和业务删除及缓存刷新调用，确保不通过随机路由模板执行管理操作，也不重复包含同一物理库。
6. 调整配置、README 和迁移说明：master 使用常规 Flyway；其他物理库由部署流程应用公共迁移和初始化。保留历史 V4 文件但不再使用其表。
7. 删除复杂组件专属测试，新增简单数据源配置测试并改写 Service 多库集成测试；执行实施阶段编译、目标测试、引用清零和 `git diff --check`。

## Risks
- `remainder` 依赖 Spring 注入的物理数据源列表顺序；重排、删除或中间插入数据源会使实例身份错位并可能生成重复 ID。README 必须把“既有顺序不变、扩容只在末尾追加”写成部署硬约束。
- `List<DataSource>` 在路由数据源创建前后可能包含不同 Bean；筛选必须只接受物理 `DruidDataSource`，并用测试证明管理列表不包含 `DynamicDataSource`，否则管理操作会随机重复或漏写。
- 聚合应用与审计查询均以物理列表第一个数据源为基准；审计事件逐库尽力写入，单库失败可能造成各库审计副本短暂不一致。
- 删除启动迁移和补齐意味着新增数据库在加入随机发号池前必须由部署流程完成公共迁移、历史业务和授权初始化；服务无法自动阻止一个结构完整但历史数据未补齐的新库投入使用。
- 多库写入仍无分布式事务；保留逐库预检、局部事务、快照和精确补偿，补偿失败仍需人工修复。
- `V4__tinyid_datasource_order.sql` 可能已在本地或共享环境执行，不能删除或改写校验和；保留未使用表比破坏 Flyway 历史更安全。
- 当前工作树已有大量本功能改动；实施只删除本次明确废弃的新增组件和测试，不清理或回滚其他改动。

## Proof
- `DataSourceConfigTest` 证明物理数据源按注入顺序生成管理 `JdbcTemplate` 列表、随机路由使用相同物理集合、路由数据源不会进入管理列表、空物理列表启动失败。
- `TinyIdManagementServiceIntegrationTest` 使用两个物理数据库证明应用和业务查询聚合、所有写操作逐库执行、创建业务得到 `remainder=0/1`、`delta <= 最大下标` 被拒绝、第二库失败只补偿本次实际变更、应用与未使用业务删除仍检查全部数据库。
- `IdContronllerTest` 和现有 Token Service 测试证明四个发号接口及 `(token,bizType)` 校验不变，发号 Mapper 仍经 `@Primary DynamicDataSource` 随机路由。
- 静态搜索证明生产代码不再引用 `TinyIdDataSourceRegistry`、`TinyIdDataSourceOrderCoordinator`、`TinyIdFlywayMigrationRunner`、`TinyIdBusinessDataSourceReconciler`、`TinyIdDataSourceProperties` 或 `macula.tinyid.datasource-order`。
- `mvn -pl macula-cloud-tinyid -am test -Plocal` 证明模块编译和单元测试通过；外部 MySQL 集成测试明确报告运行条件和跳过情况。`git diff --check` 证明补丁格式正确。

## Deviations
- 2026-10-07：工程师进一步明确 `master` 只是物理数据源列表中的普通成员，不应在配置阶段单独提取并强制放到首位。实现改为完全保持 Spring `List<DataSource>` 注入顺序；计划中“master 固定首位”的描述由本偏差取代。聚合查询和审计仍使用列表第一个模板，但不绑定数据源名称。验证增加列表顺序保持测试。
- 2026-10-07：工程师指出 DAO 返回 VO 会让数据访问层依赖接口展示模型。实现新增 `pojo.bo` 查询对象与 `TinyIdManagementConverter`，DAO 仅返回 BO/Entity，Service 使用 MapStruct 将 BO 转换为既有 VO，Controller 与 REST 契约不变；`macula-cloud-tinyid/pom.xml` 因此新增 MapStruct Starter。验证增加 DAO 包不得依赖 `pojo.vo` 的静态扫描，并由 Service 单元测试覆盖真实 Converter。
- 2026-10-07：工程师要求 DAO 数据访问风格与 `macula-cloud-system` 统一，使用 `macula-boot-starter-mybatis-plus` 替换生产代码中的 `JdbcTemplate` 和 Java SQL 拼接。实现新增 Entity 注解、`BaseMapper`、Mapper XML，并让 `TinyIdInfoDAO`、`TinyIdTokenDAO` 和管理 DAO 统一委托 Mapper；`DynamicDataSource` 在无上下文时继续随机发号，管理 DAO 则按物理列表下标临时固定路由后循环访问。跨库本地事务使用路由数据源事务模板，既有补偿、BO/VO 边界和 REST 契约不变。验证增加生产 DAO 无 `JdbcTemplate`/`java.sql` 引用扫描、显式路由上下文恢复测试及 MyBatis-Plus 编译测试。
- 2026-10-07：工程师要求分页直接采用 MyBatis-Plus 标准定义，不保留重复包装。实现删除 `PageBO`、`PageVO`，DAO 返回 `IPage<BO>`，Service 使用 Converter 的 `IPage.convert` 转换为 `IPage<VO>`，Controller 同步返回 `IPage<VO>`；分页 JSON 继续使用 `records`、`total` 等标准字段，并增加静态引用清零检查。
- 2026-10-07：工程师确认通用管理请求幂等机制属于过度设计。实现删除 `TinyIdManagementRequest` Entity/Mapper/DAO/Service 状态链、三个 Form 的 `idempotencyKey` 和前端 UUID；考虑当前 Docker 可能已执行 V3/V5，为保持 Flyway 校验和不变而保留原迁移，并新增 V6 前向删除 `tiny_id_management_request`。保留唯一约束、提交期间按钮禁用以及跨数据源精确补偿。风险变为创建应用在“服务端成功但客户端响应丢失”后人工重试可能生成另一个 Token；验证增加幂等代码引用清零、请求体契约、迁移制品和补偿路径测试。
- 2026-10-07：工程师确认 MyBatis-Plus Mapper 已承担单库数据访问，原 DAO 层可以并入 Service。实现删除 `TinyIdInfoDAO`、`TinyIdTokenDAO`、`TinyIdManagementDAO` 及全部 `DAOImpl`；号段和 Token 缓存 Service 直接使用 Mapper，管理跨库预检、聚合、事务和补偿合并进 `TinyIdManagementServiceImpl`，审计监听器直接在首个显式路由数据源写 Mapper。验证改为 Service 单元与多库集成测试，并增加生产代码 DAO 引用清零。
- 2026-10-07：工程师进一步指定审计日志只保存在 `master` 数据源。`DataSourceConfig` 在保持物理列表顺序不变的同时记录 `master` 的实际下标，`DynamicDataSource.executeMaster` 用于审计写入与查询；业务聚合、跨库维护和随机发号逻辑不变。验证增加 `master` 不在列表首位时仍准确路由的测试。
- 2026-10-07：工程师认为审计专用 `master` 路由增加了不必要复杂度，改为与管理写入一致地循环全部物理数据源。实现删除 `masterSequence/executeMaster`，审计监听器为每库创建独立实体并逐库尽力写入，单库失败记录脱敏错误后继续；查询仍取列表首库避免重复。历史 master 迁移不改校验和，新增公共 V7 以 `CREATE TABLE IF NOT EXISTS` 确保全部物理库具备审计表。
- 2026-10-07：工程师决定审计日志默认只落在 sequence=0 的物理数据源。实现将监听器写入从逐库循环收敛为显式 `execute(0, ...)`，审计查询继续使用同一序号；不重新引入 `master` 专用识别或路由。验证调整为两个路由键下只发生一次 Mapper 插入，确保审计副本不再重复。
- 2026-10-07：工程师确认 `RequestFilter` 仅提供与平台能力重复的请求耗时 DEBUG 日志，不承担鉴权、审计或业务职责，因此删除该过滤器及其专属测试；因不存在其他 Servlet 组件扫描目标，一并移除应用入口的 `@ServletComponentScan`。审计仍只依赖 Starter 的 `@AuditLog` 标准事件链。
- 2026-10-07：执行删除后的模块回归时发现 `IdContronllerTest` 仍以无参构造和反射注入测试对象，但 Controller 已改为构造器注入。测试改为直接传入既有 mock 依赖，仅继续反射设置 `@Value` 配置字段；不改变发号接口实现或测试断言。
- 2026-10-08：工程师指出多库集成测试不应依赖调用者手工提供数据库 URL。实现改为 Testcontainers 自动启动两套隔离 MySQL 8.4.6 实例，并由父 POM 管理的 `testcontainers-junit-jupiter` 与 `testcontainers-mysql` 测试依赖提供运行时；删除 `TINYID_TEST_MYSQL_*` 环境变量前提。验证改为直接运行目标 Maven 测试并确认五个真实多库场景不再跳过。
- 2026-10-08：部署前审查发现业务列表仅以 sequence=0 为分页来源，会遗漏仅存在于其他物理库的异常业务。实现改为汇总全部物理数据源的 `biz_type`，以名称稳定排序、去重后分页，再聚合当前页；新增集成测试证明首库缺失而后续库存在时返回 `PENDING`。同时将应用查询关键字注释收紧为仅支持备注，避免诱导将完整 Token 放入 URL。
