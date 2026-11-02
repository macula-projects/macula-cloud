# Macula Cloud RocketMQ

## 作用

`macula-cloud-rocketmq` 是 Macula Cloud 的 RocketMQ 管理服务入口，用于承载平台侧的消息队列管理能力。

## 核心功能

当前模块提供独立的 Spring Boot 服务入口和 RocketMQ NameServer 连接配置，尚未对外提供主题、消费组或消息查询等管理 API。

## 使用

先按[根 README](../README.md)启动 RocketMQ NameServer 和 Broker，再在 IDE 中运行 `MaculaRocketMQApplication`。服务默认端口为 `9083`，本地默认连接 `127.0.0.1:9876`。

也可以随完整平台一起通过 Docker Compose 启动，具体方式见 [`deploy/README.md`](../deploy/README.md)。
