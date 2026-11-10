# Verification: IAM单协议、Scope与资源访问边界

Status: verified
Date: 2026-11-08

**Verification is green**。本报告替代早期incomplete结果。Rain已明确确认Build实现完整，本轮按sdlc-test重新执行测试、修正失败并补齐原缺失的隔离真实多服务证据；没有自批PR或执行应用部署。

## 命令、真实结果和跳过边界

| 实际命令/范围 | 最终结果 |
|---|---|
| Boot `mvn clean verify` | 单测333通过；集成50项中47通过、3项按原有条件跳过；BUILD SUCCESS |
| Cloud `mvn test -Plocal`及`mvn verify -Plocal` | 全仓BUILD SUCCESS；默认单测509项中508通过/1跳过、集成42项中10通过/32跳过；下行33项专门补跑将这些跳过项全部执行通过 |
| Cloud条件套件逐类 `mvn -pl <module> -am verify -Plocal -Dtest=<class-or-no-match> -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<class-or-no-match> -Dfailsafe.failIfNoSpecifiedTests=false` | 13类33项全部通过；每类独立空库，必要时预迁移V19；真实MySQL/Redis；包含原跳过的唯一用户名单测 |
| 新资源数据库测试 | 全仓verify显式设置IAM_RESOURCE_IT，SingleProtocolResourceMigrationIT 2项、ResourceClientConcurrencyIT 1项通过 |
| Admin `npm run test:unit -- --run` | 20文件187项通过 |
| Admin `npm run build` | 成功 |
| Admin `npm run test:e2e:ci` | 7个spec共45项通过，退出0；使用API fixtures验证管理交互 |
| 真实Admin浏览器 `cypress run --config-file /private/tmp/iam-resource-isolated/cypress.config.cjs` | 1个完整场景通过，含真实OIDC登录、资料/租户/菜单、客户端页面、1440×900和390×844布局、两类503故障及恢复；没有伪造HTTP响应 |
| 变更Java文件 `mvn -f /private/tmp/iam-resource-checkstyle-<repo>.xml checkstyle:check` | 两仓0违规 |
| 变更前端文件 `eslint <exact-files> --ignore-path .gitignore` | 退出0，0错误、1867条排版warning；未用--fix重排 |
| 两仓 `git diff --check -- <task-files>` | 通过；保留初始未提交工作 |

Boot的3个既有可选外部验收跳过项为KafkaProducerIT（@Disabled，需Kafka）、OrderServiceIT（@Disabled，需RocketMQ）、TinyIdClientIT（需TINYID_E2E和相应服务）。本任务没有新建skip，没有把这3项算通过；它们不属于本次IAM合同要求的外部条件。本次所需MySQL/Redis/浏览器/多服务条件已全部准备并执行。

### 真实输出摘录
Boot：
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.056 s -- in dev.macula.boot.starter.security.test.ResourceDependencyFailureChainTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.011 s -- in dev.macula.boot.starter.security.test.ResourceTokenAuthenticationManagerResolverTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.140 s -- in dev.macula.boot.starter.cloud.gateway.security.OAuthDependencyFailureChainTest
[INFO] BUILD SUCCESS
[INFO] Total time:  01:36 min
```
Cloud：
```
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.309 s -- in dev.macula.cloud.iam.config.IamProtocolTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.620 s -- in dev.macula.cloud.system.config.SystemResourceBoundaryTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.984 s -- in dev.macula.cloud.system.integration.ResourceClientConcurrencyIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.967 s -- in dev.macula.cloud.system.integration.SingleProtocolResourceMigrationIT
[INFO] BUILD SUCCESS
[INFO] Total time:  48.663 s
```
条件补跑关键输出：
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.467 s -- in dev.macula.cloud.system.integration.AdminClientProvisioningIT
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.046 s -- in dev.macula.cloud.system.migration.ApplicationApiKeyIntegrationIT
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.882 s -- in dev.macula.cloud.system.integration.SettingsPersistenceIT
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.598 s -- in dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceBindingIT
```
前端：
```
 Test Files  20 passed (20)
      Tests  187 passed (187)
    ✔  All specs passed!                        00:54       45       45        -        -        -
  1 passing (13s)
```

## Proof与Verification strategy逐项证明

| 计划/设计项 | 实际证据 | 结果 |
|---|---|---|
| R1/R2单协议、创建与协议/终端切换 | 保存/注册单测，Cypress确认/取消/清空/编辑锁定；真实配置版本变化后旧code/refresh/access分别400/400/401 | 通过 |
| R3/R4五分区、满宽、底部操作、令牌展开、端点折叠、Scope选择 | 45项fixture浏览器断言覆盖五区与表单状态；真实页面1440×900/390×844的按钮位置、document宽度和截图检查；扩大Electron实际窗口解决初期截图捕获裁剪 | 通过 |
| R5/R6 OAuth Profile、OIDC UserInfo与最小资料 | 原生Provider/Redis/MockMvc安全链39项；真实HTTP纯OAuth Profile和OIDC UserInfo均200，纯OAuth无ID Token；服务授权申请profile为400，服务令牌读取users/me为403；租户/aud/Scope/撤销/过期及字段白名单由相应拒绝用例覆盖 | 通过 |
| R7资源、Scope、角色、租户、code/refresh绑定 | 真实JWT与opaque签发；JWT的不足Scope在Gateway和直连System均403；profile-only和无效Token均401；真实刷新缩Scope到profile，配置更新后旧code/refresh/access拒绝；ROOT/onlyAuth及URL角色规则组件测试 | 通过 |
| V19迁移与并发 | 空库完整历史、V18到V19、保留ID/哈希、双配置DRAFT、唯一约束、幂等；真实MyBatis行锁、ETag、密钥重置冲突、受管保护和回滚 | 通过 |
| R8/R9资源用途与跨Client内省 | 原生安全链校验调用身份/目标身份分离、private_key_jwt真实JWKS、字段投影；真实资源身份不能client_credentials取Token（400）；平台认领及缓存提交边界、重复启动不增版本均通过 | 通过 |
| API Key/HMAC来源隔离 | 全仓原有回归+新签名来源测试；真实API Key及HMAC经Gateway换发签名身份到System均200，错误HMAC签名403；真实MySQL/Redis API Key套件6项通过 | 通过 |
| R10 Admin及故障恢复 | 真实浏览器登录后users/me、租户、菜单均200；错误内省凭据及停止IAM时Gateway/System均503/C0403；刷新保留同一已提交session，恢复后原页面正常；新候选会话不能提前提交 | 通过 |
| Feign边界 | 正式UserFeignClient接口+SpringMvcContract，真实HTTP POST到System，明确Bearer凭据，响应为当前用户；无匿名回退 | 通过；使用明确URL，不声称验证Nacos服务发现 |
| 延迟观察 | 本机10次请求的中位数/最大值记录在下方；Gateway和System逐请求内省 | 已记录；不是压测或生产SLA |

## 真实HTTP结果

### 登录与资料

```json
[
  {
    "case": "gateway-user",
    "status": 200,
    "expected": 200,
    "ms": 359.9
  },
  {
    "case": "gateway-menus",
    "status": 200,
    "expected": 200,
    "ms": 263.3
  },
  {
    "case": "system-direct-user",
    "status": 200,
    "expected": 200,
    "ms": 133.4
  },
  {
    "case": "oauth-profile",
    "status": 200,
    "expected": 200,
    "ms": 62.8
  },
  {
    "case": "oidc-userinfo",
    "status": 200,
    "expected": 200,
    "ms": 28.5
  },
  {
    "case": "profile-cannot-enter-gateway",
    "status": 401,
    "expected": 401,
    "ms": 90.1
  },
  {
    "case": "profile-cannot-enter-system",
    "status": 401,
    "expected": 401,
    "ms": 111.7
  },
  {
    "case": "invalid-token",
    "status": 401,
    "expected": 401,
    "ms": 78.6
  }
]
```

### 授权矩阵

```json
[
  {
    "case": "machine-jwt-issued",
    "status": 200,
    "expected": 200
  },
  {
    "case": "gateway-scope-insufficient",
    "status": 403,
    "expected": 403
  },
  {
    "case": "system-scope-insufficient",
    "status": 403,
    "expected": 403
  },
  {
    "case": "machine-cannot-request-profile",
    "status": 400,
    "expected": 400
  },
  {
    "case": "machine-opaque-issued",
    "status": 200,
    "expected": 200
  },
  {
    "case": "machine-cannot-read-current-user",
    "status": 403,
    "expected": 403
  },
  {
    "case": "resource-identity-cannot-obtain-tokens",
    "status": 400,
    "expected": 400
  },
  {
    "case": "confidential-code-issued",
    "status": 200,
    "expected": 200
  },
  {
    "case": "refresh-can-shrink-scope",
    "status": 200,
    "expected": 200
  },
  {
    "case": "old-code-rejected-after-change",
    "status": 400,
    "expected": 400
  },
  {
    "case": "old-refresh-rejected-after-change",
    "status": 400,
    "expected": 400
  },
  {
    "case": "old-access-rejected-after-change",
    "status": 401,
    "expected": 401
  }
]
```

### 应用凭据

```json
[
  {
    "case": "api-key-through-gateway-and-system",
    "status": 200,
    "expected": 200
  },
  {
    "case": "hmac-through-gateway-and-system",
    "status": 200,
    "expected": 200,
    "response": null
  },
  {
    "case": "hmac-invalid-signature",
    "status": 403,
    "expected": 403
  }
]
```

### 内省凭据错误

```json
{
  "gateway": {
    "status": 503,
    "code": "C0403"
  },
  "system": {
    "status": 503,
    "code": "C0403"
  }
}
```

### 内省凭据恢复

```json
{
  "gateway": {
    "status": 200,
    "code": "00000"
  },
  "system": {
    "status": 200,
    "code": "00000"
  }
}
```

### 停止IAM

```json
{
  "gateway": {
    "status": 503,
    "code": "C0403"
  },
  "system": {
    "status": 503,
    "code": "C0403"
  }
}
```

### 恢复IAM

```json
{
  "gateway": {
    "status": 200,
    "code": "00000"
  },
  "system": {
    "status": 200,
    "code": "00000"
  }
}
```

Feign真实输出：
```
Official UserFeignClient + SpringMvcContract over real HTTP: PASS
```
本机小样本延迟（无并发负载，不能外推生产容量）：
```json
{
  "gateway": {
    "requests": 10,
    "median_ms": 263.2,
    "max_ms": 375.6
  },
  "system": {
    "requests": 10,
    "median_ms": 122.7,
    "max_ms": 139.6
  }
}
```

## 失败循环中修复的实质问题
- 网关与Servlet默认认证失败处理器重抛AuthenticationServiceException，原来真实HTTP变成500或ERROR分派后的401；仅资源模式显式关闭重抛，真实过滤器链回归和组合故障注入均证明503。
- Admin仅Axios层保留503会话，刷新初始化仍会清除；补齐初始化和路由处理，兼容现有HTTP封装直接抛出的response.status。新候选会话及401仍失败关闭。
- IAM/Gateway复用本地演示公钥时，System原来误判合法IAM JWT为网关JWT；现在只有固定网关issuer检查保留签名来源，其余issuer独立在线内省，不直接信任解码claims。
- 旧集成fixtures仍按双协议/共享列/V18运行时假设；历史迁移用例明确target18，运行时迁至V19，单协议并发及DTO断言按新合同更新；未修改V17/V18历史迁移。
- 旧Admin初始化表单未同步单协议公开字段，幂等比对误报漂移；补齐表单，真实幂等/回滚测试通过，新Platform初始化边界不变。
- 提交后缓存清理改用保证当前缓存立即失效的Cache.invalidate；保留真实跨实例断言，并用TransactionAwareCacheDecorator防止afterCommit内再次排队clear。SettingsPersistenceIT修复后连续独立运行通过，回滚和缓存故障单测保留。

## 隔离、安全与证据边界
真实服务均由当前源码打包后以独立JVM运行，只绑定127.0.0.1随机端口；MySQL为任务专用容器，Redis为任务专用进程。每类测试使用自己前缀的全新数据库；用户、客户端、密钥和应用缓存均为合成fixture。没有读取或打印业务凭据，没有改动用户业务库或现有应用容器。初期测试准备脚本的建库标准输入、空库复用、Redis进程生命周期、密码格式及签名路径问题均修正后重跑，不将这些失败混同产品缺陷。

HMAC组合请求按既有GlobalFilter位于StripPrefix之后的真实路径签名；该协议和过滤顺序未修改。浏览器测试扩大实际窗口后查看了无裁剪桌面与移动截图。第三方厂商真实SDK、Nacos服务发现、生产部署及压力测试不属于本次验证结论。

未修改CLAUDE.md、Skill、Hook、REVIEW.md或reviewer定义，无适用配置eval；没有编造eval运行。未提交/推送、未重建现有应用Docker镜像、未迁移业务数据库。测试环境清理由cleanup.json记录；保留私有日志和无凭据的界面截图供复核。

## 条件套件清单
- OAuth2ProtocolIdentitySourceMigrationIT：1项通过。
- OAuth2ClientProtocolsDraftMigrationIT：1项通过。
- ApplicationProtocolConfigurationMigrationIT：1项通过。
- ApplicationProtocolConfigurationConcurrencyIT：1项通过。
- IdentityProviderMigrationIT：1项通过。
- SecurityPolicyMigrationIT：1项通过。
- SysUserUsernameUniqueMigrationTest：1项通过。
- ExternalCredentialLoginMigrationIT：1项通过。
- AdminClientProvisioningIT：1项通过。
- OAuth2ClientApplicationConcurrencyIT：1项通过。
- ApplicationApiKeyIntegrationIT：6项通过。
- SettingsPersistenceIT：6项通过。
- IdentitySourceBindingIT：11项通过。

## 私有日志和截图
最终日志均在`/private/tmp/iam-resource-isolated/`：`boot-full-final.log`、`cloud-full-final.log`、`cloud-verify-final.log`、`<条件类名>.log`、`admin-unit.log`、`admin-build.log`、`admin-e2e-final.log`、`combo-cypress.log`、`combo-http.log`、`combo-matrix.log`、`combo-apps.log`、`feign.log`、`style-boot.log`、`style-cloud.log`、`eslint-final.log`。脚本和配置在同一私有目录及`/private/tmp/iam-resource-*.py`；合成凭据文件仅在私有目录，未入Git。

桌面：`screenshots/combo.cy.js/real-protocol-desktop.png`；移动：`screenshots/combo.cy.js/real-protocol-mobile.png`。失败日志保留供审计，结论以上述最终成功运行和逐项真实结果为准。
