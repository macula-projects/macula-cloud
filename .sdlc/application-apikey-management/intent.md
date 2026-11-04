# Intent: 应用 API Key 管理与网关缓存联动
Author: Rain. Status: accepted.

## Problem
Macula Boot 网关已支持 API Key 与 HMAC 认证，但 Macula Cloud 应用管理尚无 API Key 管理入口和对应缓存维护，管理员无法为应用创建、管理并配置可供网关使用的 API Key。

## Proposed outcome
- 应用列表增加 APIKEY 入口，按参考图提供应用级管理界面，支持多把 Key 的创建、脱敏列表、显示/隐藏、复制、备注维护和删除；展示创建时间，创建成功后可复制保存。
- 一个应用允许创建多把 API Key，每把 Key 可独立维护备注及允许访问的 URL（permitUrl）。
- API Key 缓存的新增与更新采用相同规则：优先使用该 Key 自己配置的 permitUrl；未设置时使用所属应用的可访问路径。两个 URL 独立配置，不合并、不取交集。
- API Key 创建、修改、删除时维护相应网关缓存；删除 Key 或应用后，旧 Key 不能继续通过网关认证，服务重启后可恢复有效缓存。

## Affected users and systems
应用管理员、应用 API Key 调用方、Macula Cloud Admin、System、System API，以及读取应用凭据缓存的 Macula Boot Gateway。

## Constraints
- 沿用已合入的网关 API Key 缓存及认证协议，不另造认证方式；保持现有 HMAC 和普通 OAuth 行为。
- 沿用应用归属租户、认证租户与管理权限边界，禁止跨应用或跨租户操作不属于当前权限范围的 Key。
- 普通列表默认脱敏；完整 Key 仅在授权创建、查看或复制操作时提供，不进入日志或审计参数明文。
- 按参考图及现有管理端风格实现；同步数据库迁移、必要权限、使用说明及测试。
- 本轮不自行扩展发布、部署或远端操作范围。

## Open questions
- 是否需要有效期、启停或轮换？当前需求及参考图未指定，暂不计入本轮确认范围。

用户在确认多 Key 及新增、更新缓存统一使用 permitUrl 优先规则后回复“YES”，接受本需求。
