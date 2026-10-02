# Intent: 修复 Nacos Compose 环境变量透传
Author: Rain. Status: accepted.

## Problem
Macula Cloud 的本地完整容器模式无法把 `.env` 中的 Nacos namespace、用户名和密码正确传给 Java 应用；Compose 当前会把三项值渲染成包含默认值语法的字面量，导致容器内配置与运维输入不一致。

## Proposed outcome
使用默认环境样例或自定义 `.env` 渲染完整应用栈时，所有需要连接 Nacos 的 Java 应用都能收到预期的 namespace、用户名和密码，并且 Compose 不再报告相关占位变量缺失。

## Affected users and systems
使用 `macula-cloud/deploy` 启动本地或测试环境的开发者和运维人员；受影响系统包括 Compose 配置、Java 应用容器及其 Nacos 注册和配置中心连接。

## Constraints
保持现有变量名、默认值、Nacos 服务端鉴权策略、应用配置结构和其他部署行为不变；不提交真实凭据；修改前使用独立功能分支；以默认值和自定义覆盖值两种 Compose 渲染结果验证全部相关 Java 服务。

## Open questions
无。
