# Intent: 开发与 Kubernetes 部署支持
Author: Rain. Status: accepted.

## Problem
开发人员目前无法快速获得 Macula Cloud 本地调试所需、可重复运行的基础设施和初始化数据，导致工程内后端模块及管理端难以直接通过 IDE 和本机开发服务器启动、联调与排障。现有部署目录没有可用内容，也无法为 Kubernetes 环境提供一致、可复用的部署入口。

## Proposed outcome
开发人员能够快速启动独立的开发基础设施并自动完成必要的数据与配置初始化，随后可在 IDE 中运行工程内全部后端模块，并在本机运行管理端完成联调。运维或开发人员能够使用原生 Kubernetes YAML 部署对应的平台组件，并通过清晰的中文说明完成配置、启动、验证和清理。

## Affected users and systems
受影响用户包括 Macula Cloud 后端开发人员、管理端开发人员以及负责开发或 Kubernetes 环境的运维人员。受影响系统包括仓库内全部可运行后端模块、`macula-cloud-admin` 管理端、开发阶段所依赖的中间件、初始化数据与配置，以及 `deploy/` 下的 Docker Compose 和 Kubernetes 部署材料。SnailJob 作为 Macula Cloud 服务处理，不作为独立中间件处理。

## Constraints
保持 Java 17、Macula Boot 6.1、Spring Boot 4.0、Spring Cloud 2025.1 及现有 Vue 3 + Vite 技术栈兼容。开发模式下，中间件与初始化过程需要可独立、快速、可重复地运行，工程内后端模块通过 IDE 启动，管理端在本机启动。Kubernetes 部署使用原生 YAML，不引入 Helm 或 Kustomize。部署材料不得包含真实凭据、令牌、私钥或不可公开的环境地址；示例配置必须明确其本地或演示用途。

## Open questions
需要在设计阶段确认中间件的最终边界，尤其是 MySQL、Redis、Nacos、RocketMQ 和 Seata 分别由独立基础设施容器提供，还是由仓库内对应 Cloud 模块承担。还需要确认 Kubernetes 部署面向开发验证环境还是同时要求满足共享及生产环境基线，以及集群入口、持久化存储类、镜像仓库和 Secret 注入方式。
