# Macula Cloud Seata

## 作用

`macula-cloud-seata` 为平台提供 Seata Server，用于协调跨服务的分布式事务。

## 核心功能

- 提供 Seata 事务协调服务。
- 通过 Nacos 发布配置并注册服务。
- 使用 MySQL 保存事务会话和锁数据。
- 提供 Seata 管理控制台。

## 使用

先按[根 README](../README.md)启动 MySQL 和 Nacos，再在 IDE 中运行 `MaculaSeataApplication`。

默认端口：

| 端口 | 用途 |
| --- | --- |
| `8091` | Seata 客户端连接事务协调服务 |
| `9091` | Seata 管理控制台 |

业务服务使用 Seata Starter，并将注册中心、配置中心和事务组配置指向本服务。完整 Docker Compose 启动与连接配置见 [`deploy/README.md`](../deploy/README.md)。
