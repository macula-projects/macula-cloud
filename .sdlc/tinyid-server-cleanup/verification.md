# Verification: TinyID Server 遗留清理

日期：2026-10-09。用户已确认实施完成，本轮执行 Stage 4 验证。

## 执行结果

所有命令均在独立工作树 `macula-cloud-tinyid-cleanup` 执行；前端命令在其 `macula-cloud-admin` 目录执行。

| 命令 | 实际结果 | 退出码 |
| --- | --- | --- |
| `mvn -pl macula-cloud-tinyid,macula-cloud-gateway -am verify -Plocal` | 修正测试夹具后重跑：39 项，失败/错误/跳过均为 0，`BUILD SUCCESS` | 0 |
| `mvn verify -Plocal` | 全仓 44 项，失败/错误/跳过均为 0，`BUILD SUCCESS` | 0 |
| `npm ci` | 依赖安装完成 | 0 |
| `npm run test:unit -- --run` | `Test Files  2 passed (2)`；`Tests  10 passed (10)` | 0 |
| `npm run build` | `✓ 2241 modules transformed.`；构建完成，有大于 500 kB 的 chunk 提示 | 0 |
| `npm run test:e2e:ci` | `All specs passed!`；8 项通过（TinyID 5 项、示例 3 项），无失败或跳过 | 0 |
| `git diff --check` | 无输出 | 0 |

后端全仓关键原始输出：

```text
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 17.52 s -- in dev.macula.cloud.tinyid.service.impl.TinyIdManagementServiceIntegrationTest
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

原始日志保存在本机 `/tmp/tinyid-cleanup-{target,full,vitest,build,cypress,npm-ci}.log`，未纳入版本控制；JUnit XML 在模块 `target` 报告目录。

## 失败与修正

首轮后端验证退出码 1：`TinyIdManagementServiceImplTest` 错误调用 `TinyIdBusinessAggregateBO` 无参构造器，实际类型仅有全参构造器。改为使用现有全参构造器创建测试数据，未修改生产模型；随后目标范围及全仓验证均通过。

## Proof 对照

- Token 链路/旧应用接口：生产 Java、SQL、前端 TinyID API/页面搜索无旧 Token 类型、表名、专属 Advice 或 `/apps` 引用；Controller 用例验证旧应用删除接口不再映射。
- V1/业务持久化：真实 Testcontainers MySQL 8.4.6 双容器执行 6 项数据库测试，验证新 V1 无 Token 表、业务创建/分页/一致性、删除限制与失败补偿；审计监听另有 2 项测试通过。
- 统一异常/权限：Controller、ExceptionIT、SegmentSecurityIT 验证成功协议、业务 ResultCode、空参数、ROOT 边界、匿名/伪造/过期 JWT 拒绝；补充非发号路径匿名拒绝断言。
- 网关：4 项 GatewayIT 验证 `/tinyid/**`、StripPrefix、认证拒绝与允许、过期凭据及下游失败；未修改共享认证。
- 前端：10 项单测和 8 项 Cypress 通过，覆盖入口清理、无应用请求、业务/审计、空态、加载结束、表单及删除成功/失败状态；构建验证删除组件后引用完整。
- 配置与 eval：本次修改业务路由配置及 SQL，未修改 CLAUDE.md、技能或 hook，无适用的配置行为 eval。

## 验证边界

- Cypress 使用 `cy.intercept()` 模拟后端，不是实际 Gateway/IAM/Server 联调。
- MySQL 证据仅来自临时测试容器，没有修改真实环境数据库、执行 Flyway repair 或迁移已有库。
- V1 修改仍会影响已有环境的校验和；用户需另行安排真实环境基线处理。
- `npm ci` 报告现有依赖 `60 vulnerabilities (4 low, 19 moderate, 30 high, 7 critical)`；本次未更改依赖或 lockfile，未执行自动修复，此结果不代表依赖安全审计通过。
- 未发布、部署、推送或合并；未触碰原 Cloud 工作区的 IAM 与网关改动。

Verification is green
