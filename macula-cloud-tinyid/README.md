# Macula Cloud TinyID

## 作用

`macula-cloud-tinyid` 是基于数据库号段的分布式 ID 服务，为不同业务类型提供高吞吐、趋势递增的唯一 ID。

## 核心功能

- 按 `bizType` 分配单个或批量 ID。
- 向客户端分配号段，支持客户端本地缓存后发号。
- 管理发号业务及其步长、余数等规则。
- 查询数据源状态和发号审计记录。

## 使用

先在管理端创建发号业务并设置唯一的 `bizType`，再通过 Gateway 调用发号接口：

```http
GET /tinyid/api/v1/id/nextId?bizType=order&batchSize=10
Authorization: Bearer <access_token>
```

常用接口：

| 路径 | 用途 |
| --- | --- |
| `/tinyid/api/v1/id/nextId` | 批量获取 ID |
| `/tinyid/api/v1/id/nextIdSimple` | 以逗号分隔文本返回 ID |
| `/tinyid/api/v1/id/nextSegmentId` | 获取完整号段对象 |
| `/tinyid/api/v1/id/nextSegmentIdSimple` | 获取文本格式号段 |
| `/tinyid/api/v1/admin/businesses` | 管理发号业务 |
| `/tinyid/api/v1/admin/audit-logs` | 查询审计记录 |

本地开发先按[根 README](../README.md)启动 MySQL、Nacos 和 Gateway，再在 IDE 中运行 `MaculaTinyIdApplication`。服务默认端口为 `9082`；经 Gateway 访问时使用 `9000` 端口。

完整 Docker Compose 启动方式见 [`deploy/README.md`](../deploy/README.md)。

## 接口日期时间约定

管理接口的业务及审计VO使用 `java.util.Date`，JSON输出带时区的UTC ISO时间，例如 `2026-11-05T00:50:30.000Z`。数据库Entity/BO保留既有类型，管理Converter引用Boot的 `GlobalMapperConfig`；旧实体Date和BO日期时间之间也使用同一公共转换器，避免双向转换时区不一致。

`macula.time.storage-zone` 配置后优先使用指定时区，未配置则使用JVM默认值，须与数据库实际写入约定保持一致。管理端将ISO时间转换为浏览器当地时间。运行需要提供公共转换器和自动配置的新版本MapStruct Starter。

JSON时间点同样可以使用 `Instant`：Jackson原生支持带时区ISO读写。`Date`保留毫秒，`Instant`可保留纳秒，转换为Date时超出毫秒的精度会丢弃；不使用固定 `SSS` 的 `spring.jackson.date-format` 作为通用ISO解析器，避免纳秒输入被错误解析。接口调用约定仍要求时间点携带Z或偏移。公共实体映射转换器同时支持Date/Instant与LocalDateTime双向映射，两种接口类型均遵循同一存储时区配置；Instant映射不经过Date，保留纳秒。
