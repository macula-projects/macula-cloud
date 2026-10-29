# Review findings: tenant-aware-menu-route-cache

## Plan

### Round 1

- `.sdlc/tenant-aware-menu-route-cache/plan.md:19 — Important`：原计划只在缓存 key/淘汰层校验，写路径可能先按未授权 header 执行 MyBatis 写入。已改为每个菜单/角色写 Service 在首个 Mapper 调用前解析 `TrustedTenant`，SQL 与 afterCommit 淘汰复用同一捕获值，并增加越权时零写入、零缓存测试。
- `.sdlc/tenant-aware-menu-route-cache/plan.md:9 — Important`：角色 code 更新和角色删除同样改变 `listRoutes()` 结果，原计划只覆盖 `updateRoleMenus()`。已把 `saveRole()`、`deleteRoles()` 和 `updateRoleMenus()` 全部纳入提交后精确失效及测试。

### Round 2

No findings.
