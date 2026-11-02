# Plan: 租户隔离的菜单路由缓存 (from spec.md 2026-10-29)

Accepted by Rain on 2026-10-29.
Docker evidence amendment re-accepted by Rain on 2026-11-02.
Stage 5 logging amendment re-accepted by Rain on 2026-11-02.

## Files that change
- `.sdlc/tenant-aware-menu-route-cache/plan.md`
- `.sdlc/tenant-aware-menu-route-cache/review-findings.md`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/MenuRouteTenantContext.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/MenuRouteCacheService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/SysMenuService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/SysMenuServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/SysRoleServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/SysMenuController.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/MenuRouteTenantContextTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/MenuRouteCacheServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysMenuServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysRoleServiceImplTest.java`
- `macula-cloud-system/README.md`
- `macula-cloud-system/src/main/resources/application.yml`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/config/SystemLoggingConfigurationTest.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizer.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizerTest.java`

上述文件分别承载可信租户解析、版本化缓存键、菜单与角色写路径的提交后精确失效、Controller
固定键移除、对应测试、发布说明，以及实施合同和实际偏差记录。

## Order of work
1. 实现 `MenuRouteTenantContext`。优先使用 JWT `tenantId` claim；请求上下文与 claim 相同时直接接受。二者不同时，只有带用户角色的主体可进入租户切换校验：ROOT 校验目标租户存在，非 ROOT 校验当前用户名对应用户的 `sys_tenant_user` 关系；无显式关系时只兼容默认租户 1。无租户、应用主体覆盖 claim、未知用户或越权租户统一返回 403。成功时返回内部 `TrustedTenant` 捕获值，解析过程不记录主体或凭据。
2. 实现 `MenuRouteCacheService`，只接受 `TrustedTenant` 并产生 `routes:v2:{tenantId}` key，从 Spring `CacheManager` 获取 `system` cache。事务活跃时注册 `afterCommit` 精确淘汰；无事务时仅在数据库操作成功返回后立即淘汰。缓存缺失属于服务配置错误，明确失败而非静默跳过。
3. 将 `SysMenuServiceImpl.listRoutes()` 的 `@Cacheable` key 改为可信租户版本化 key。每个菜单写 Service 入口都必须在首个 Mapper 调用前解析并捕获 `TrustedTenant`；菜单保存、权限更新、删除和可见性更新成功后把同一捕获值交给缓存服务，保证 SQL 租户与失效租户一致；`cleanCache()` 同样先解析当前可信租户。
4. 移除 `SysMenuController` 的三个固定 `@CacheEvict`，避免事务外误清共享 key。在 `SysRoleServiceImpl.saveRole()`、`deleteRoles()` 和 `updateRoleMenus()` 的首个 Mapper 调用前解析 `TrustedTenant`，全部写入成功后使用同一捕获值注册精确淘汰。`updateRoleStatus()` 当前不参与 `listRoutes()` SQL 结果，不扩大本次范围。
5. 添加租户解析、缓存事务语义和菜单/角色写路径测试。Spring Cache 代理用内存 CacheManager 依次执行租户 1、12、1 和反向顺序，证明每个租户只装载一次自己的路由且缓存 key 不交叉；越权请求必须在零 Mapper 写入、零缓存操作的状态下失败。
6. 更新 System README，记录新旧 key、24 小时旧键 TTL、排空旧 System 实例以及可选精确删除旧 `macula:cache:system::routes` 的发布步骤。
7. Build 阶段运行新增目标单测和 `git diff --check`；完整 IAM + System reactor、Docker 双租户验证和旧键清理验证交给 Stage 4。
8. Docker 真实验证发现 Cloud Admin 的 opaque access token 未携带 `tenantId`，管理端只有在附带
   `tenantId: 1` 请求头时才能读取菜单。补充 IAM access-token customizer，把已认证
   `SysUserDetails.tenantId` 写入 opaque/JWT 共用的 access claims，并增加独立单测；不向 ID Token
   添加该 claim。System 的租户拒绝改用平台 `ResourceAccessDenied`，确保既有 403 契约不被通用异常
   Advice 映射为 500。随后同时重建 IAM 与 System，再执行无请求头的租户 1 token claim、租户 12
   AK/SK claim、租户 1 token claim 顺序验证。
9. Stage 5 Security review 发现普通用户跨租户校验会在 MyBatis DEBUG 参数日志中输出认证用户名。
   在现有敏感 Mapper 日志降级配置旁，将 `SysUserMapper` 与 `SysTenantUserMapper` 的日志级别固定为
   INFO，并增加 YAML 配置测试，确保菜单拒绝链路不会输出用户名或成员查询参数；修复后重新执行完整
   Stage 4 验证和 Review 复审。

## Actual deviations
- Stage 4 Docker 冒烟显示原计划假设的用户 access-token `tenantId` claim 实际未生成：无
  `tenantId` 请求头时返回 500，带 `tenantId: 1` 时返回 200。该偏差扩展文件集到 IAM token
  customizer 及其测试，并在 System 既有租户解析文件内把拒绝异常对齐为平台 403 类型；REST、数据库、
  菜单响应和前端均不变化。
- Stage 5 Review Round 1 确认 `dev.macula.cloud: debug` 会使租户校验 Mapper 输出认证用户名。
  文件集扩展到 System `application.yml` 和日志配置测试；只收窄两个安全校验 Mapper 的日志级别，
  不改变全局 SQL 日志策略或查询行为。

## Risks
- 当前请求租户上下文可由 header 建立，不能直接作为可信 key。计划要求在任何缓存读取前完成 claim/ROOT/成员关系校验；应用主体没有用户角色时不得覆盖 JWT claim。
- 非 ROOT 无显式 `sys_tenant_user` 关系时，现有租户选项逻辑会回退默认租户 1；本实现保持该兼容行为，不能扩展为任意租户。
- `MenuRouteTenantContext` 不能依赖会反向依赖 `SysMenuService` 的高层 Service，否则会形成循环依赖；成员校验使用现有 Mapper 的只读查询，并保留 Service 层业务编排边界。
- Spring Cache 切面在方法执行前求值 key；key 解析本身必须完成授权校验，并确保该租户与 MyBatis 实际使用的 `TenantContextHolder`/JWT claim 一致。
- 固定 key 的 Controller 淘汰移除后，所有菜单和角色菜单写路径必须显式覆盖；漏掉可见性或角色关系会导致最长 24 小时陈旧菜单。
- 事务内提前淘汰可能让并发请求把旧数据库结果重新写回缓存；因此所有事务写路径必须在 `afterCommit` 淘汰，回滚不得淘汰。
- 新旧 System 混跑时旧实例仍会返回共享缓存，属于已接受的阻塞发布关注点；代码不会尝试通过清空 Redis 掩盖混跑。
- 当前工作区有大量其他 OAuth/IAM 未提交改动；实现只触碰计划列出的文件，不格式化、回滚或提交其他改动。
- access claim 只能来自认证后的 `SysUserDetails`，不得从请求参数、请求头或 OAuth Client 默认租户复制；
  否则会把未授权租户固化进令牌。JWT 和 opaque access token 必须复用同一 claim 构造逻辑，ID Token
  继续保持最小披露。
- Mapper 日志降级必须精确限定到 `SysUserMapper` 与 `SysTenantUserMapper`，避免关闭整个 System 的
  DEBUG 诊断；配置测试防止后续父 logger 调整重新暴露认证用户名或成员查询参数。

## Proof
- `MenuRouteTenantContextTest` 证明：claim-only 的 AK/SK 租户成功；应用请求头覆盖 claim 被拒；用户选择 token 租户成功；ROOT 只能切换到存在租户；非 ROOT 只能切换到成员租户；无成员用户只允许默认租户 1；未知用户、越权和无租户均返回 403。
- `MenuRouteCacheServiceTest` 证明 key 精确为 `routes:v2:1`、`routes:v2:12`；服务只接受解析器返回的 `TrustedTenant`；事务提交前不淘汰、提交后只淘汰当前租户、回滚不淘汰；没有 `system` cache 时明确失败。
- `SysMenuServiceImplTest` 在 Spring Cache 代理下按 1→12→1 与 12→1→12 调用 `listRoutes()`，断言 Mapper 对每租户只执行一次且结果不串租户；同时覆盖新增、修改、删除、可见性和显式 clean 的当前租户淘汰，并断言越权 header 在零 Mapper 写入、零缓存操作时被拒绝。
- `SysRoleServiceImplTest` 证明角色保存（含 code 变更）、删除、菜单与权限更新成功后注册淘汰，任一写步骤抛错时事务不会产生提交后淘汰；越权 header 在首个 Mapper 调用前被拒绝。
- `CustomOAuth2TokenCustomizerTest` 证明已认证用户的 opaque/JWT 共用 access claims 包含真实
  `tenantId`，非用户主体不伪造该 claim，并保持现有 client、权限与用户资料 claim；System 租户解析
  测试同时断言拒绝类型为平台 403 异常。
- `SystemLoggingConfigurationTest` 读取实际 `application.yml`，断言 `SysUserMapper` 与
  `SysTenantUserMapper` 的 logger 均为 INFO，并保留现有 OAuth Client 敏感 Mapper 的 INFO 保护。
- Build 最小验证执行新增测试类和 `git diff --check`。Stage 4 执行
  `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-iam,macula-cloud-system -am test -Plocal`，
  并明确覆盖 `CustomOAuth2TokenCustomizerTest`、IAM 协议测试和四个 System 租户缓存测试；随后在 Docker
  中使用 Cloud 租户 1、Samples AK/SK 租户 12、再租户 1 进行真实菜单与 Redis key 冒烟。报告只记录
  租户、菜单归属和 key，不记录 token、AK/SK 或用户名。
