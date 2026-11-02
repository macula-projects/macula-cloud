# Macula Cloud SnailJob

## 作用

`macula-cloud-snailjob` 是平台任务调度与失败重试中心，为业务服务提供统一的任务管理、执行记录和重试能力。

## 核心功能

- 定时任务和工作流任务管理。
- 分布式任务执行与调度。
- 失败任务重试及重试策略管理。
- 任务、重试实例和运行日志查询。
- 通过 gRPC 与业务客户端通信。

## 使用

先按[根 README](../README.md)启动 MySQL 和 Nacos，再在 IDE 中运行 `MaculaSnailJobApplication`。

默认入口：

| 地址或端口 | 用途 |
| --- | --- |
| <http://127.0.0.1:9086/snail-job/> | 管理控制台 |
| `17888` | 业务客户端 gRPC 连接端口 |

业务服务接入 SnailJob 客户端后，将服务端地址指向本模块，并在管理控制台创建任务或重试策略。完整 Docker Compose 启动方式见 [`deploy/README.md`](../deploy/README.md)。
