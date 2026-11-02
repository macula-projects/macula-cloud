# Intent: 租户隔离的菜单路由缓存
Author: Rain. Status: accepted.

## Problem
System 的菜单路由查询虽然受租户数据隔离约束，但 `listRoutes()` 把结果写入固定的 `system::routes` 缓存键。
任一租户首次加载菜单后，其他租户会直接复用该租户的路由结果。当前 Macula Cloud 租户先写入缓存，导致
Macula Samples 租户登录后看到 Macula Cloud 菜单，形成跨租户数据泄露和错误导航。

## Proposed outcome
每个租户只读取和更新自己的菜单路由缓存；Macula Cloud 与 Macula Samples 无论访问顺序如何，都返回各自
租户的菜单。菜单、角色菜单关系和可见性发生变化后，只失效受影响租户的路由缓存，下一次读取重新加载该租户
的权威数据。

## Affected users and systems
受影响的是所有通过 Macula Cloud System 获取菜单的租户、Macula Cloud Admin、Macula Samples Admin、
System 菜单与角色管理、共享 Redis 缓存和依赖 System Starter 的业务管理端。

## Constraints
必须以经过 Gateway AK/SK 或用户认证建立的租户上下文作为缓存隔离依据，不能信任请求参数指定租户；租户上下文
缺失时不得回退到共享缓存；不得通过关闭租户校验或返回全量菜单解决。修复需要兼容现有 API 响应和菜单数据，
并安全清理或淘汰旧的无租户 `system::routes` 缓存键。

## Open questions
无。
