# Intent: 内省用户类型准入
Author: Rain. Status: accepted.

## Problem
资源服务器当前无法限定接受哪些账号类型的用户 Token，内省没有对此拒绝。

## Proposed outcome
资源服务器可配置允许的用户类型；用户 Token 类型不在集合或缺失时返回 active:false；机器 Token 跳过此项校验，既有认证与有效性规则继续生效。

## Affected users and systems
Macula Cloud IAM及配置所需的System、System API、Admin。

## Constraints
工程师明确不做旧 Token/旧配置过渡；机器 Token 不增加用户类型配置。空集合不接受任何用户类型。本次不改变调用方已有缓存策略，不修改Boot。不推送、不提交、不部署、不修改运行库或真实凭据。

## Open questions
无未决需求问题。业务规则由工程师OK确认，当前范围以用户最新“IAM里面处理”的纠偏为准。

## 本轮交付授权
工程师明确要求“推送提交，更新镜像”。据此授权本变更创建分支、提交、推送、创建PR，并定向构建/重建本地System、IAM、Admin。System负责V29新增列迁移；不擅自给现有资源服务器猜填允许类型、不轮换凭据、不改Boot、不重建其他服务；未授权合并PR。此前不提交/部署的表述记录的是当时边界，已由本次明确指令更新。
