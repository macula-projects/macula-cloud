# Review findings: tenant-aware-menu-route-cache

## Plan

### Round 1

- `.sdlc/tenant-aware-menu-route-cache/plan.md:19 — Important`：原计划只在缓存 key/淘汰层校验，写路径可能先按未授权 header 执行 MyBatis 写入。已改为每个菜单/角色写 Service 在首个 Mapper 调用前解析 `TrustedTenant`，SQL 与 afterCommit 淘汰复用同一捕获值，并增加越权时零写入、零缓存测试。
- `.sdlc/tenant-aware-menu-route-cache/plan.md:9 — Important`：角色 code 更新和角色删除同样改变 `listRoutes()` 结果，原计划只覆盖 `updateRoleMenus()`。已把 `saveRole()`、`deleteRoles()` 和 `updateRoleMenus()` 全部纳入提交后精确失效及测试。

### Round 2

No findings.

### Round 3 (Docker evidence amendment)

- `.sdlc/tenant-aware-menu-route-cache/plan.md:67 — Important`：修订后新增 IAM token claim 生成，原 Stage 4 命令仍只覆盖 System reactor。已把 `macula-cloud-iam` 纳入正式 reactor，并明确覆盖 token customizer、IAM 协议和 System 租户缓存测试后再执行 Docker 冒烟。

## Review

### Round 1

- Stage: Review
- Reviewed SHA: `f17bb40411e9205d591598bb3dd0fcd8052e5bd5` + scoped worktree diff
- Scope: `.sdlc/tenant-aware-menu-route-cache/plan.md` 声明的完整变更
- Passes: Bugs, Security, Compliance
- Posted: 1
- Suppressed as already posted: 0
- Rejected by verifier: 0

#### Important

- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/MenuRouteTenantContext.java:87 — Security — Important`：普通用户跨租户校验把认证用户名绑定到 `SysUserMapper.selectOne`；`application.yml` 对 `dev.macula.cloud` 开启 DEBUG，真实 MyBatis 参数日志会输出用户名，违反 spec 第 9 条拒绝日志最小化要求。独立 verifier 已确认该运行条件真实；需关闭 `SysUserMapper` 与 `SysTenantUserMapper` 的参数级 DEBUG，并增加配置测试后返回 Stage 4 全量验证。

### Round 2

- Stage: Review
- Reviewed SHA: `f17bb40411e9205d591598bb3dd0fcd8052e5bd5` + Round 1 后的日志修复 worktree diff
- Scope: `application.yml` 敏感 Mapper 日志降级、`SystemLoggingConfigurationTest` 及对应计划记录
- Passes: Bugs, Security, Compliance
- Findings: No findings.
- Posted: 0
- Suppressed as already posted: 0
- Rejected by verifier: 0

## Recurring

本轮未发现同类 finding 已在两个或以上变更中出现。
