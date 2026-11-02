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
