# Macula Cloud ID ID中心

统一的ID生成服务

## 简介

macula-cloud-tinyid是基于滴滴[Tinyid](https://github.com/didi/tinyid)
开发的一款分布式id生成系统，基于数据库号段算法实现，关于这个算法可以参考美团leaf或者tinyid原理介绍。Tinyid扩展了leaf-segment算法，支持了多db(
master)
，同时提供了starter使id生成本地化，获得了更好的性能与可用性。

## 性能与可用性

### 性能

http方式访问，性能取决于http server的能力，网络传输速度
java-client方式，id为本地生成，号段长度(step)越长，qps越大，如果将号段设置足够大，则qps可达1000w+

### 可用性

依赖db，当db不可用时，因为server有缓存，所以还可以使用一段时间，如果配置了多个db，则只要有1个db存活，则服务可用
使用tiny-client，只要server有一台存活，则理论上可用，server全挂，因为client有缓存，也可以继续使用一段时间

### Tinyid的特性

1. 全局唯一的long型id
2. 趋势递增的id，即不保证下一个id一定比上一个大
3. 非连续性
4. 提供http和java client方式接入
5. 支持批量获取id
6. 支持生成1,3,5,7,9...序列的id
7. 支持多个db的配置，无单点

- 适用场景:只关心id是数字，趋势递增的系统，可以容忍id不连续，有浪费的场景
- 不适用场景:类似订单id的业务(因为生成的id大部分是连续的，容易被扫库、或者测算出订单量)

## 依赖

JDK 17、Maven、MySQL。

## 管理端

TinyID 管理端 API 仅通过网关 `/tinyid/api/v1/admin/**` 访问，管理页为 `/tinyid/management`，动态菜单显示为“ID管理”并挂载在“系统管理”下，仅授权给 `ROOT`
超级管理员。管理功能包括：

- 接入应用：生成 256 bit 随机 Token，在备注中描述 Token 对应的应用，并为应用追加 `biz_type`
  授权。接入应用可以整体删除，删除后该 Token 在所有数据源中的授权和处理请求实例的本地缓存立即失效；
  不使用 Redis 发布订阅等跨实例通知，其他 TinyID 实例在下一次定时刷新成功或进程重启后收敛。Token 不轮换、不停用，
  且对超级管理员始终完整展示。
- 发号业务：`biz_type` 表示独立业务，与应用无关；应先创建业务，再创建应用并授权业务。
  `begin_id` 和 `max_id` 固定从 `0` 初始化，`step` 是每次获取 ID 的步长。只有该业务在全部数据源
  都存在且每个数据源的 `max_id` 都为 `0` 时才允许删除；删除业务会同时清理对应应用授权。
- 多数据库：创建业务时系统一次写入当前全部物理数据源，不需要切换数据源。`delta` 是该业务预留的
  数据库实例容量，默认 `10`、创建时可以修改；`remainder` 按物理数据源列表下标从 `0` 开始自动
  分配，页面只读展示。
- 审计：管理 Controller 统一使用 `macula-boot-starter-auditlog` 发布标准事件，并由异步监听器写入
  `tiny_id_audit_log`；不保存请求/响应正文、原始异常、SQL 或原始 Token。审计是异步尽力写入，
  鉴权或方法调用前被拒绝的请求可能不会产生 Controller 审计事件，审计失败也不会回滚已完成的管理操作。

发号时仍按 `(token, biz_type)` 组合校验接入权限。管理端不提供单条授权撤销、应用或业务停用和
Token 轮换功能；`step`、`delta`、`remainder` 创建后不提供修改入口。应用和未使用业务的删除均跨
全部数据源执行并记录审计，只有全部当前数据源配置完整且参数一致的业务才可以授权给应用。

### 数据源与迁移

多数据库继续沿用原有定义：每个物理库注册为 Spring `DruidDataSource`，`master` 只是其中之一；发号使用
`@Primary DynamicDataSource` 随机选择物理库。Service 直接使用 `macula-boot-starter-mybatis-plus` Mapper；
管理服务在同一个路由数据源上按列表下标显式固定目标库后循环读写，普通发号没有显式上下文时仍随机选库。

管理列表完全保持 Spring 注入顺序，不按名称单独提取或重排 `master`。列表下标也是创建业务时的
`remainder`；管理审计仅写入并读取列表中的第一个数据源（sequence=0），避免在各业务库复制审计记录。因此数据源顺序是部署硬约束：已有数据源
不得重排、删除或在中间插入，扩容只能追加到列表末尾，并且新增下标必须小于业务 `delta`。服务不再
持久化数据源顺序，也不会在启动时自动补齐新库。

Flyway 通过 `@FlywayDataSource` 使用同一个 `DynamicDataSource`，不单独配置 URL、用户名或密码。
Flyway 配置位于 `local` profile，`docker` 通过 profile 分组继承；共享环境由配置中心提供配置。
迁移策略按列表序号固定路由后逐库执行，确保迁移 SQL 与历史表处于同一个库；任一库失败会终止启动。
Nacos 等外部配置也应移除旧的 Flyway 连接配置及 `db/master` 位置。
迁移已合并为唯一的 `db/migration/V1__baseline.sql`，包含业务表、授权唯一索引和审计表，
不再创建已废弃的管理请求表与数据源顺序表。此次基线重建要求先由运维重置数据库及 Flyway 历史，
不能直接覆盖升级旧库；`baseline-on-migrate=false` 防止将未迁移的非空库静默标记为 V1。
新增库仍需在加入发号池前复制既有业务配置和 Token 授权，并设置正确的 remainder；迁移不会自动补齐业务数据。
数据库连接信息只用于服务端建池，不会通过管理 API 返回。

V1 中的 `test`、`test_odd` 和固定 Token 是历史示例。服务不再在启动迁移阶段自动删除或补齐数据，已有
数据库及历史发号进度保持不变；全新环境是否保留示例由部署初始化流程决定。
管理页面在请求提交期间禁用按钮，服务端依靠业务唯一约束避免重复业务或授权；不保存通用管理请求状态。
TinyID 内部 REST 模型统一位于 `pojo.form`、`pojo.query`、`pojo.vo`，不会发布到 `macula-cloud-api`。

跨数据库写入不使用分布式事务：系统会先全量预检，再按管理列表顺序写入；失败时反向补偿。若补偿仍
失败，会留下脱敏审计并要求人工修复；不得重排既有 `remainder`。

## 示例

请参考getting start

## 推荐使用方式

- macula-cloud-tinyid推荐部署到多个机房的多台机器
    - 多机房部署可用性更高，http方式访问需使用方考虑延迟问题
- 推荐使用tinyid-client来获取id，好处如下:
    - id为本地生成(调用AtomicLong.addAndGet方法)，性能大大增加
    - client对server访问变的低频，减轻了server的压力
    - 因为低频，即便client使用方和server不在一个机房，也无须担心延迟
    - 即便所有server挂掉，因为client预加载了号段，依然可以继续使用一段时间 注:
      使用tinyid-client方式，如果client机器较多频繁重启，可能会浪费较多的id，这时可以考虑使用http方式
- 推荐 db 配置两个或更多：管理端创建业务时会自动写入全部当前数据库；运行期发号仍可在某个数据库
  暂时不可用时依赖其他实例和既有缓存继续服务。

## tinyid的原理

- tinyid是基于数据库发号算法实现的，简单来说是数据库中保存了可用的id号段，tinyid会将可用号段加载到内存中，之后生成id会直接内存中产生。
- 可用号段在第一次获取id时加载，如当前号段使用达到一定量时，会异步加载下一可用号段，保证内存中始终有可用号段。
- (如可用号段1~1000被加载到内存，则获取id时，会从1开始递增获取，当使用到一定百分比时，如20%(默认)
  ，即200时，会异步加载下一可用号段到内存，假设新加载的号段是1001~2000,则此时内存中可用号段为200~
  1000,1001~2000)，当id递增到1000时，当前号段使用完毕，下一号段会替换为当前号段。依次类推。

## tinyid系统架构图

![](https://github.com/didi/tinyid/raw/master/doc/tinyid.png)

下面是一些关于这个架构图的说明:

- nextId和getNextSegmentId是macula-cloud-tinyid对外提供的两个http接口
- nextId是获取下一个id，当调用nextId时，会传入bizType，每个bizType的id数据是隔离的，生成id会使用该bizType类型生成的IdGenerator。
- getNextSegmentId是获取下一个可用号段，tinyid-client会通过此接口来获取可用号段
- IdGenerator是id生成的接口
- IdGeneratorFactory是生产具体IdGenerator的工厂，每个biz_type生成一个IdGenerator实例。通过工厂，我们可以随时在db中新增biz_type，而不用重启服务
-

CachedIdGenerator则是具体的id生成器对象，持有currentSegmentId和nextSegmentId对象，负责nextId的核心流程。nextId最终通过AtomicLong.andAndGet(
delta)
方法产生。

- SegmentIdService是生成SegmentId对象的服务，在服务端通过Db生成，在client端通过Http访问服务端生成

## 多数据库的配置

[多数据库配置](https://github.com/didi/tinyid/wiki/Tinyid-server-config)

## 其他说明

其他id生成项目推荐

- twitter snowflake
- 百度uid-generator: 这是基于snowflake方案实现的开源组件，借用未来时间、缓存等手段，qps可达600w+
- 美团leaf: 该篇文章详细的介绍了db号段和snowflake方案，近期也进行了Leaf开源

## 部署说明

TinyID 是独立应用；Gateway 只代理 `/tinyid/api/v1/admin/**` 管理接口。接入应用的发号请求不经过
Gateway，而是使用 TinyID Starter 配置的服务地址，通过集群内服务发现或专用负载均衡地址直连 TinyID，
减少发号链路依赖。TinyID 服务自身继续对白名单 `/api/v1/id/**` 开放匿名访问，并使用 `(token, biz_type)`
校验发号权限。
