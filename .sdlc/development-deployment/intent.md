# Intent: 开发与 Docker Compose 部署支持
Author: Rain. Status: accepted.

## Problem
开发人员目前无法用一套简单、清晰的 Docker Compose 入口快速运行 Macula Cloud：本地 IDE 调试所需的中间件与初始化数据缺少可重复启动能力，各个可运行模块也缺少就近、直观的容器镜像定义。此前同时引入 Kubernetes 模板和集中式通用 Dockerfile，使部署目录过于复杂，不利于理解、维护和直接使用。

## Proposed outcome
开发人员能够一条命令启动已初始化的中间件供 IDE 调试，也能够通过 Docker Compose 构建并运行工程内后端模块和管理端。每个可运行模块在自身目录中提供明确的 Dockerfile；重复的构建约定在不牺牲可读性和独立构建能力的前提下适度复用。部署材料只保留 Docker Compose 路径，不再包含 Kubernetes 部署方案。

## Affected users and systems
受影响用户包括 Macula Cloud 后端与管理端开发人员。受影响系统包括 Gateway、IAM、System、TinyID、Seata、SnailJob、RocketMQ 管理服务、Docs 服务、`macula-cloud-admin`，以及 MySQL、Redis、Nacos、RocketMQ 中间件和相关初始化数据。SnailJob 与 Seata 仍作为 Macula Cloud 应用模块，而不是 Compose 中的独立中间件产品。

## Constraints
保持 Java 17、Macula Boot 6.1、Spring Boot 4.0、Spring Cloud 2025.1 和现有 Vue 3 + Vite 技术栈兼容。Compose 必须同时满足“只启动中间件供 IDE 调试”和“容器化运行应用”的需要，数据初始化可重复且普通重启不删除数据。每个模块的 Dockerfile 应可从仓库根目录独立构建，运行阶段使用非 root 用户。不得引入 Kubernetes、Helm 或 Kustomize，不得提交真实凭据、令牌、私钥或不可公开的环境地址。

## Open questions
无。具体的 Dockerfile 复用边界和 Compose 服务分组在设计阶段确定，但不得重新引入 Kubernetes 或削弱模块独立构建能力。
