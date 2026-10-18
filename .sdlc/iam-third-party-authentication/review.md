# Review: IAM 企业微信与 GitHub 第三方身份认证

日期：2026-10-18。当前分支：`hifox-agent/huang_xiao_shi/7d552e4f-57e8-4b0e-8217-9e34535d4a75`。

## TOCTOU 修复后独立复审（当前结论）

用户确认最新 Stage 4 绿灯后，独立 `sdlc_reviewer` 对当前实际工作树重新执行 `REVIEW.md` 规定的 Bugs、Security、Compliance 三个 pass。结果：**Important 0，Nit 0。** 本轮只读，未修改代码、提交、推送或创建 PR。

- Bugs：无证据支持的发现。
- Security：无证据支持的发现。state 的 tenantId/userType 由 `ThirdPartyUserDetailsService` 传入账号路由；`SysUserDetailsServiceImpl` 仅读取一次 client，核对后使用同一对象查询账号，前轮 TOCTOU 已消除。
- Compliance：无证据支持的发现；最小 scope、数据边界、凭据处理与访问控制约束均有对应实现。
- Nits：无。

重点复核同时确认：SavedRequest 受控相对目标绑定 state；state 在会话临界区内一次消费；非 `EMPLOYEE` 不回退系统用户；固定第三方 HTTPS 端点与可信 issuer 回调；code/token/secret 不持久化或写日志；V6 的失败、清理、Flyway repair 和重跑路径成立；绑定表不包含 tenantId、userId、userType 或 token。

最新 Stage 4 证据为全仓 153 项、真实 MySQL 8.4 迁移恢复、IAM/System 打包、8/8 UI 视口及 `git diff --check` 全绿。MAC-6 是顶层任务，仓库规则要求最终只推送 `feat/MAC-6`、不创建 PR；当前尚未获得推送授权，因此本阶段把评审结论记录到任务时间线并停在人类批准门。不得把本记录视为自我批准、合并或部署授权。

### 人类批准与交付授权

2026-10-18，任务负责人在对话中选择“2”，即明确 **LGTM，并授权推送 `feat/MAC-6`**。该批准允许按 MAC-6 顶层任务规则提交并推送当前已验证、已复审的变更；不授权创建 PR、合并、部署或跳过任何远端保护。

## 第二轮独立复审（历史记录）

修复后的 Stage 4 已有新鲜绿灯后，用户确认重新进入 `sdlc-deploy`。独立 `sdlc_reviewer` 按项目 `REVIEW.md` 对实际工作树重新执行 Bugs、Security、Compliance 三个 pass；本轮只读，未修改代码、提交、推送或创建 PR。

当前结论：**Important 1，Nit 0，阻断交付。** 上轮五条 Important 均确认修复完整，但发现一条新的安全竞态：

1. **Important / Security — OAuth client 校验与用户路由之间存在 TOCTOU。** `ThirdPartyUserDetailsService.java:40-45` 首次读取 client，并将 tenantId/userType 与 state 快照比较；随后只把 clientId 传给 `SysUserDetailsServiceImpl.loadUserByClient`，该方法在 `SysUserDetailsServiceImpl.java:99-117` 再次读取 client，并直接使用第二次读取的 tenantId/userType 装载账号。若管理员在两次读取间修改 client，首次校验可通过，最终却可能按新租户或新用户类型建立认证，绕过 state 快照边界。应让回调只读取一次 client，核对后把已验证的 tenantId/userType 传给账号路由；或让路由方法接收并核对期望快照，并确保最终查询使用相同值。

复审 pass 结果：

- Bugs：无其他证据充分的发现。
- Security：除上述 TOCTOU 外，无其他证据充分的发现。
- Compliance：无证据支持的发现；最小 scope、token/secret 不持久化及固定第三方端点符合已声明约束。
- Nits：无。

### 第二轮 Important 修复状态（待重新正式验证与复审）

用户已同意返回 Stage 3。实现已改为把 state 绑定的 expected tenantId/userType 传入
`OAuthClientUserDetailsService`，由 `SysUserDetailsServiceImpl` 仅读取一次 client，核对期望值后用同一
对象的 tenantId/userType 查询账号；`ThirdPartyUserDetailsService` 不再预先读取 client。定向 5 项与
IAM 全量 71 项测试已通过，并断言路由变化在访问用户源前失败且 client 只读取一次。本节仅记录 Build
反馈，不关闭当前 Important；仍须重新执行 Stage 4 与独立 Stage 5。

MAC-6 是顶层任务，项目交付规则要求最终只推送 `feat/MAC-6`、不创建 PR；当前也未获得推送授权。当前修复在重新正式验证与独立复审前仍按 Important 阻断交付。

## 首轮独立评审（历史记录）

## 范围与依据

依照项目 `REVIEW.md`，由独立 `sdlc_reviewer` 只读检查当前实际 diff 与本功能未跟踪文件，分别执行 Bugs、Security、Compliance。Important 不受数量限制，Nit 上限为 5。`AGENTS.md` 与 `.agent_context/` 是用户/HiFox 运行时已有内容，不属于本功能评审范围。

- [Intent](intent.md)
- [Spec](spec.md)
- [Plan](plan.md)
- [验证证据](verification.md)

## 审查结论

Important：5。Nit：0。当前结果阻断交付，尚未创建 PR、推送、合并或部署。

### Important

1. **Security — state 未绑定原始 SavedRequest。** `DefaultSecurityConfiguration.java:114` 使用共享会话 RequestCache 的 `SavedRequestAwareAuthenticationSuccessHandler`，但 `ThirdPartyLoginContext.java:30` 只快照 provider/client/tenant/userType。外部认证期间另一个标签页可覆盖 SavedRequest，回调随后以 client A 的身份恢复 client B 的授权请求，形成跨 client/tenant 错配。应把原始授权请求的服务端不可变快照或标识绑定到 state，回调只恢复该请求，并复核其 client 与快照一致。
2. **Security — 显式 client 用户路由会回退系统用户。** `SysUserDetailsServiceImpl.java:105-107` 在 `UserAuthInfoService` 缺失或按指定用户类型查询为空时，无条件按同租户 username 查询系统用户。这允许 `MEMBER`、`ODC`、`SUPPLIER` 等 client 以同名系统账号登录，绕过“OAuth client 决定用户体系”的核心边界。第三方显式 client 路由应对非系统用户类型失败关闭；若某一类型允许系统用户，须显式限定。
3. **Bugs — V6 迁移失败后不可直接恢复重跑。** `V6__iam_third_party_authentication.sql:2` 先创建绑定表，`:17` 才增加启用唯一键。MySQL DDL 非事务；若历史重复启用记录导致 `ALTER` 失败，绑定表已保留，清理数据并 repair/rerun 后会因表已存在再次失败。应先执行可能因历史数据失败的唯一约束 DDL，再创建绑定表，或提供并测试明确的幂等恢复路径。
4. **Security — state 消费不是原子的。** `HttpSessionThirdPartyLoginStateRepository.java:64-65` 分两步读取和删除；同一会话的并发回调可同时读取同一 state 并继续交换 code，不满足一次性消费约束。应在会话级同步完成读取、校验与删除，或使用原子 compare-and-remove 存储，并增加并发测试。
5. **Compliance — GitHub scope 超过登录识别所需最小权限。** `GithubClient.java:60` 请求 `read:user`，但本功能只需稳定数值 `id`；无 scope 的 OAuth token 已足以读取公开 profile 的 `/user.id`。当前授权扩大了 profile 数据访问权限，违反 accepted spec 的最小权限/数据最小化要求。应移除 `scope=read:user` 并更新测试。

### Nits

无。

## 其余检查结果

- Bugs：除 V6 恢复路径外，未发现其他证据充分的生产缺陷。
- Security：固定 HTTPS 端点、可信 issuer 回调、`enabled`/`visible` 分离、第三方 token 不持久化与数据库双唯一键未发现其他证据充分的问题。
- Compliance：除 GitHub 过宽 scope 外，未发现本任务违反已声明审计、访问控制或数据保留要求的证据；不推定额外法规认证。

## 验证证据与交接

Stage 4 记录的最终全仓 147 项测试、隔离 MySQL 迁移、目标模块打包、8 组 UI 视口与 `git diff --check` 均通过。独立 reviewer 未修改文件、未复跑测试。本次只新增评审记录，生产代码验证证据仍是评审前状态，但上述 Important 说明测试覆盖不足，不能据此交付。

MAC-6 是顶层任务，项目规则要求最终只推送 `feat/MAC-6` 且不得创建 PR；同时当前 5 条 Important 阻断交付。因此本阶段不创建 PR、不推送，等待人工决定是否返回 Build 修复，修复后必须重新执行完整 `sdlc-test`，再重新评审。

## 修复状态（待重新正式验证与复审）

用户已明确要求解决全部五条 Important，Stage 3 重新打开。当前实现已完成以下返工，但本节不构成 Stage 4 绿灯或 Stage 5 关闭结论：

1. state 增加受控 OAuth 授权相对目标快照，专用成功处理器移除共享 SavedRequest 后只恢复该目标；发起时拒绝 SavedRequest client 与当前 client 不一致。
2. 显式 client 路由仅允许 `EMPLOYEE` 回退现有 System 用户源，其他用户类型空结果失败关闭。
3. V6 改为先执行可能被历史重复数据阻断的唯一约束，再创建绑定表；真实 MySQL 测试覆盖失败无绑定表、清理、`repair`、重跑成功。
4. session state 的读取与删除置于同一会话同步临界区，并增加并发双回调只能成功一次的测试。
5. GitHub 授权请求移除 `read:user`，不申请额外 scope。

Stage 3 反馈：IAM 71 项、System 47 项、全仓 11 模块共 153 项测试通过；真实 MySQL 迁移恢复测试与 IAM/System 打包通过；`git diff --check` 通过。下一步仍须由用户确认 Build 完整后，重新执行 `sdlc-test` 并重新调用独立 reviewer。
