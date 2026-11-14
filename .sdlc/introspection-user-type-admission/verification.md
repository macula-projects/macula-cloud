# IAM用户类型准入验证

## 当前结论

Verification is green（当前计划内本地验证）。Docker已恢复，本轮Cloud全仓构建成功，之前TinyID环境阻塞已消除。类型判断仅在IAM，System/API/Admin只提供配置支持；Boot工作区干净，未修改Gateway缓存。

## Cloud全仓

```bash
mvn -o test -Plocal
```

```text
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 448, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 203, Failures: 0, Errors: 0, Skipped: 1
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

退出0，11个reactor项目全部SUCCESS；718个测试中717通过、0失败/错误、1既有环境条件跳过。SysUserUsernameUniqueMigrationTest由SYSTEM_MIGRATION_IT开关控制，本次未开启；不把该跳过视为真实数据库验证。TinyID既有专属MySQL/Testcontainers测试本轮实际通过。

原始日志：/private/tmp/user-types-iam-only-all-tests.log。

## 相关模块复验

在撤回全部Boot变更并从干净基线恢复必要本地依赖后执行：

```bash
mvn -o -pl macula-cloud-api/macula-cloud-system-api,macula-cloud-iam,macula-cloud-system -am test -Plocal
```

682个测试中681通过、1同上条件跳过，0失败/错误；BUILD SUCCESS，退出0。覆盖用户允许/拒绝/空集合/缺失/非法类型、机器原grant分类、伪造grant不能绕过、实际HTTP内省策略变化、可信主体目录与Client一致、授权持久化和原UID保留，以及配置多选/清空、CAS、提交/回滚缓存边界。

原始日志：/private/tmp/user-types-iam-only-test.log。

## V29真实隔离MySQL

使用本次测试独占的一次性MySQL容器和随机数据库，显式设置RESOURCE_USER_TYPE_IT=true及仅回环随机端口连接，执行：

```bash
mvn -o -pl macula-cloud-system -am verify -Plocal -Dit.test=ResourceUserTypePolicyIT -Dfailsafe.failIfNoSpecifiedTests=false
```

2个IT全部通过（0失败/错误/跳过），BUILD SUCCESS：新空库和V28→V29，旧元数据/凭据/绑定逐值保持，新列默认[]，真实Mapper读取多选和清空正确。测试自行删除随机库、停止自建容器，未操作业务库。Schema及这些相关源码在纠偏后未改变，证据仍适用。

原始日志：/private/tmp/user-types-mysql-verify.log。

## Admin

`npm run test:unit -- --run`：224个Vitest通过；`npm run build`：退出0；本地构建配Mock API执行相关Cypress：52/52通过，包含1440/390宽度多选、清空、失败保留和布局断言。相关前端源码未改变，证据仍适用。

```bash
CYPRESS_CACHE_FOLDER=/private/tmp/macula-cypress-cache npm exec -- start-server-and-test 'vite preview --port 4173' http://localhost:4173 'cypress run --e2e --browser electron --spec cypress/e2e/oauth-client-configuration.cy.js'
```

原始日志：/private/tmp/user-types-admin-{unit,build,cypress}.log。Cypress不是生产后端或真实第三方登录验收。

## 规范、源码范围及依赖

Cloud变更Java 28个文件Checkstyle通过，git diff --check通过。前端变更文件无fix ESLint：0 errors、726条排版warnings，退出0，不扩大重排。未改AGENTS/REVIEW/技能/hook/reviewer配置，无适用配置eval。

Boot git status --short为空；必要本地Maven工件已从干净源码重新安装（恢复构建跳过测试，不作为测试通过证据）。本地commons JAR没有撤销的JWT_USER_TYPE_KEY、Gateway JAR没有RedisIntrospectionCache，Cloud没有这些依赖。撤回方案的Boot/Redis测试不作为当前证据。

当前源码审查快照：base d56bf4c51549f36b6707ea62f396b2de07366a5d + source SHA256 e8f32a319253d0eff35764c768c355ea4fed81d2121240fd4e84a97c28de649b；未提交源码，不虚构commit。快照清单位于本机/private/tmp/user-types-iam-review-snapshot.json。

## 边界

本次只保证实际接收到的/oauth2/introspect请求按当前allowedUserTypes校验用户Token；机器按持久化原grant跳过此项，不改变调用方已有缓存。未执行所有其他Failsafe IT、其他前端Cypress spec、真实第三方设备联调或生产部署；未改业务库、真实凭据。没有提交、推送或部署，本地green不构成人工PR批准。

## 已授权的镜像交付
本轮用户已授权提交推送及更新镜像；代码已推送PR #38，本地System/IAM/Admin已重建健康。V29及仅platform-gateway允许employee的明确选择已落实，凭据及其他31个容器保持；详细运行证据见[deployment.md](deployment.md)。先前“未提交或部署”的描述为当时验证边界，已由本轮明确授权更新。
