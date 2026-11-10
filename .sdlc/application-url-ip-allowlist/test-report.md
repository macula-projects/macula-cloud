# Test report: application-url-ip-allowlist
Status: green

## Scope
来源为已接受的 [spec.md](spec.md) 和 [plan.md](plan.md)。本报告核对多行 URL、允许 IP、两级独立继承、字段省略/清空、缓存生命周期及兼容升级。用户已确认实现并要求推送 PR、合并。

## Actual verification
- 当前源码提交：`98a91086760f09e71a6021213e6cd65090d56f0b`。
- `mvn test -Plocal`，exit 0，全仓 621 项，0 failure/error，1 个既有 SysUserUsernameUniqueMigrationTest 按原条件跳过。本次变更的迁移与真实缓存另以隔离 IT 验证。

```text
Tests run (module totals): 621, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
[INFO] Total time:  44.584 s
```

- `npm run test:unit -- --run`：220 passed；`npm run test:e2e:ci`：全部 8 个 spec、75/75 passed，Cypress 接口为可控 Mock。

```text
Tests  220 passed (220)
All specs passed!  01:32  75  75  -  -  -
```

- `npm run build` 已于本次实现阶段执行成功，此后管理端源码未改；Docker 模式 Admin 构建也成功，运行容器返回 HTTP 200。
- 在两个空的临时 MySQL 库及临时 Redis 中执行 `mvn -pl macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am verify -Plocal -Dtest=ApplicationFormTest,ApplicationApiKeyFormTest,ApplicationCredentialCacheServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ApplicationUrlIpAllowlistMigrationIT,ApplicationApiKeyIntegrationIT -Dfailsafe.failIfNoSpecifiedTests=false`，exit 0：Surefire 16 项，Failsafe 8/8 passed，环境和容器已清理。此后迁移/缓存源码未改，无需重复。

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- 表单与服务测试覆盖省略/null/空白、2000 字符上限、非法 IP、未知身份字段拒绝及租户权限；缓存测试和真实 IT 覆盖 URL/IP 分别继承、应用变更、覆盖/清空、重建及撤销。
- V24 实际本机升级成功；两表列与缓存字段已只读核对。此为用户单独授权的本机更新，不代表生产数据库升级验证。

## Required checks and boundaries
- 本 PR 全部变更 Java 执行 `mvn -N checkstyle:check -Dcheckstyle.includes=<精确文件列表>`：0 violations；`git diff --check` 通过。
- 未修改 CLAUDE.md、AGENTS.md、REVIEW.md、skills、hooks 或 reviewer 定义，无适用的配置 eval。
- 保持 HMAC 签名与普通 OAuth 行为。来源仍按直连对端 IP，真实代理拓扑及真实第三方调用方端到端验收未包含在本地单元/Mock 链测试中。
- Verification is green。

## Dependency correction recheck
Boot 独立审查发现并修复 IPv6 结构边界后，使用重新安装的 Commons 再次执行 mvn test -Plocal：621 项，0 failure/error，1 个既有条件跳过，exit 0。管理端与迁移/缓存源码未改变，先前 220 项单元、75 项 Cypress、8 项隔离 IT 证据仍适用。
