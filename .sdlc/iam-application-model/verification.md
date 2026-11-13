# IAM接入应用模型验证报告

Status: verified

Verification is green

2026-11-12工程师对Phase B退出问题明确回答YES后，按sdlc-test执行正式Stage 4。本报告基于本阶段实际执行输出，不沿用Stage 3反馈代替执行。尚未执行Stage 5代码审查、Git提交/推送/合并或业务环境升级。

## 结果

| 范围 | 最终结果 |
|---|---|
| 全部后端测试，按测试类取最后一次有效运行并去重 | 800项：单元688、集成112；失败0、错误0、跳过0 |
| 管理端全部单元 | 23个文件，223项通过 |
| 管理端全部浏览器套件 | 9个套件，96/96通过；其中IAM应用配置49项，托管页5项 |
| 打包/构建 | 全仓Maven verify与管理端Vite build成功 |
| 规范及范围 | 152个精确增量Java文件Checkstyle通过，两个提取的JS文件无fix ESLint零错误/警告，git diff --check通过，131个范围外已有文件SHA256保持 |

根verify默认报告778项、67项条件跳过。随后对所有受环境变量保护的套件逐一提供专用空库、Redis与运行标记并执行；按类合并最终有效结果后为800项，全部执行，没有将跳过计为通过。计算明细含类名、计数和日志来源，保存在任务临时目录的backend-class-evidence.json。

## 执行命令与真实输出

证据目录：`/private/tmp/macula-iam-review-20261111/iam-application-model/stage4/`。生成日志、临时数据库及下载缓存不提交。

### 全仓后端

```bash
mvn -o verify -Plocal
```

最终根命令退出码0，backend-verify-final.log：

```text
[INFO] BUILD SUCCESS
[INFO] Total time:  54.226 s
```

默认执行包含IAM真实安全链、授权/Session回读、MFA、平台Token缓存、Gateway/TinyID集成及各模块单元。根命令中的条件跳过由以下矩阵补足。

### 专用MySQL/Redis矩阵

MySQL8.4与Redis均为本任务创建的回环进程，随机端口和schema；每类历史迁移使用全新匹配前缀的空库。数据库用户名为隔离root、空密码，不读取部署凭据；Redis有随机端口及本例运行标记。进程由运行器finally关闭。

当前聚合、V27与固定V19迁移：

```bash
mvn -o -pl macula-cloud-system -am verify -Plocal \
  -Dtest=NoTestsForFailsafe -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=IamApplicationModelMigrationIT,SingleProtocolResourceMigrationIT,IamApplicationModelConcurrencyIT,ApplicationProtocolConfigurationConcurrencyIT,ResourceClientConcurrencyIT,WebLoginModeConcurrencyIT,OAuth2ClientApplicationConcurrencyIT,AdminClientProvisioningIT,IamApplicationDraftMigrationIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

system-integration.log，退出码0：

```text
[INFO] Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

IAM来源锁定读取和绑定分别执行：

```bash
mvn -o -pl macula-cloud-iam -am verify -Plocal \
  -Dtest=NoTestsForFailsafe -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=IdentityProviderLockedReadIT -Dfailsafe.failIfNoSpecifiedTests=false
# 相同命令将 -Dit.test 改为 IdentitySourceBindingIT，使用预建V27的独立库及带标记的Redis。
```

两条命令均退出码0，iam-v27-source-green-it-rerun.log及iam-v27-source-original-binding-it-rerun.log：

```text
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

另外逐类显式启用并执行：SettingsPersistenceIT、IdentityBindingMappingMigrationIT、IdentityBindingMappingsMigrationIT、IdentitySourceFlowDirectoryMigrationIT、IdentitySourceCapabilitiesMigrationIT、IdentitySourceCapabilitiesConcurrencyIT、IdentityProviderMigrationIT、OAuth2ProtocolIdentitySourceMigrationIT、ExternalCredentialLoginMigrationIT、ApplicationProtocolConfigurationMigrationIT、HostedEmailLoginMigrationIT、WebLoginModeMigrationIT、SecurityPolicyMigrationIT、ApplicationApiKeyIntegrationIT、ApplicationUrlIpAllowlistMigrationIT；Surefire另外执行SysUserUsernameUniqueMigrationTest。每条命令退出码0，日志为remaining-<类名>.log。

执行模板：上述System命令以`-Dit.test=<类名>`逐一选择；SysUserUsernameUniqueMigrationTest使用`mvn -o -pl macula-cloud-system -am test -Plocal -Dtest=SysUserUsernameUniqueMigrationTest -Dsurefire.failIfNoSpecifiedTests=false`。SettingsPersistenceIT预建V27并提供本任务合成Crypto/Audit配置，历史能力并发预建其明确要求的V23。

修正后两个用例的真实输出：

```text
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

### 管理端

```bash
npm run test:unit -- --run
npm run build
```

两条命令退出码0，admin-unit.log与admin-build.log：

```text
Test Files  23 passed (23)
     Tests  223 passed (223)
✓ built in 5.47s
```

Vite保留既有大包体积提示；无构建错误。

全部浏览器套件使用当前构建及由IamProtocolTest生成的托管页预览，两个HTTP服务器只绑定本机随机端口：

```bash
CYPRESS_CACHE_FOLDER=<本任务临时缓存> npx --no-install start-server-and-test \
  'vite preview --host 127.0.0.1 --port <随机端口> --strictPort' \
  http://127.0.0.1:<随机端口> \
  'cypress run --expose IAM_PREVIEW_URL=http://127.0.0.1:<模板端口>/login.html --config baseUrl=http://127.0.0.1:<随机端口>,screenshotsFolder=<任务临时目录>,video=false'
```

仅测试子进程对127.0.0.1/localhost/::1设置NO_PROXY，使用Electron并有240秒总时限。最终退出码0，immutable-admin-cypress.log和browser-runner-final.log：

```text
✔  All specs passed!                        01:53       96       96        -        -        -
Cypress exit: 0
```

9个套件全部执行：Admin授权码4、身份/消息管理17、API Key3、统一登录入口3、IAM托管页5、身份源策略8、IAM应用配置49、安全策略2、TinyID管理5。

浏览器接口使用明确Mock，托管预览由真实模板渲染；这不等于真实第三方厂商或业务环境联调。真实安全链与持久化由后端隔离测试另行证明。

## 规格及计划证明映射

| 规格要求 / Plan Proof | 对应证据与结果 |
|---|---|
| R1 零或单协议、草稿、唯一约束 | 注册表与应用服务单元，V27迁移IT、IamApplicationDraftMigrationIT及浏览器空协议草稿/协议更换；通过 |
| R2 公共元数据与协议标识/凭据分离 | 实体/DTO契约、OAuth发布投影、SettingsPersistenceIT、凭据保持与秘密隐藏/日志捕获；通过 |
| R3 登录与协议发布分层 | 托管密码/SMS/EMAIL、浏览器/端内、账号目录、MFA、标准OAuth/OIDC安全链；全部单元/集成与5项托管页用例通过 |
| R4 唯一能力与固定来源 | 新关联唯一键、来源删除/保存竞争、真实绑定事务、来源能力CAS及浏览器关联；通过 |
| R5 external_code专属许可 | OAuth协议策略/Code许可拒绝路径、真实绑定与版本/账号归属复核、SDK来源增删浏览器用例；通过 |
| R6 独立类型化策略及未知协议拒绝 | IamApplicationProtocolRegistryTest的独立Definition和未知协议用例、只提供OIDC/OAuth2.1的目录回归；通过 |
| R7 ID/哈希/有效绑定与安全持久化保持 | 迁移逐值/摘要、大小写/命名空间、自增边界、旧非空绑定表、资源规范Client ID、既有授权/Session序列化与回读、真实Crypto锁定读；通过 |
| R8 新迁移且预检失败关闭 | V1→27、V26→27、历史版本目标，脏数据DDL前拒绝、7阶段故障/恢复状态与重试拒绝；通过 |
| HTTP兼容、资源身份与缓存 | WireAdapter/Controller、Secret/JWKS资源身份无业务grant、完整ETag/CAS及回滚/提交后两个实例失效、受管初始化幂等；通过 |
| 管理端与代码质量 | 单元223、浏览器96、构建、Checkstyle152、精确JS无fix ESLint、范围保护及diff检查；通过 |

运行源码没有旧三表/旧绑定表SQL，历史迁移保持原合同；无新增package-info.java。既有Session/Token类型、字段、UID和安全白名单未因模型改名变动；未清空授权/Session缓存。

## 失败循环及修正

- SettingsPersistenceIT仍查询旧表，实际错误为`SELECT client_secret FROM sys_oauth2_client WHERE id = ?`；改查唯一协议登记的credential_hash及中立主表数量。附加密钥没有配置时，既有Crypto修复使用JSON空对象，测试从错误的null期望调整为解析后的空Map。只修正测试适配，没有放宽运行规则。
- IdentitySourceCapabilitiesConcurrencyIT明确要求预建V23，首次运行器却提供空库；修正测试环境后通过，保留历史目标断言。
- 浏览器首次CDP回环误走代理；只为测试子进程增加本机NO_PROXY。Chrome长套件中途停顿，保存日志并停止精确识别的任务子进程；Electron有界重跑全部9套件成功。
- 修正测试后再次执行全仓verify、静态检查；最终证据不存在失败或未执行的必需套件。

## 配置、eval与交付边界

本次没有修改CLAUDE.md、技能、hook、REVIEW.md、AGENTS.md或reviewer定义，Git状态检查无这些路径改动，因此没有适用的保护配置eval，不虚构eval结果。应用协议/日志配置变更由对应运行测试覆盖。

本阶段未升级业务库、更新业务镜像、旋转凭据或提交/推送/合并。真实厂商设备及业务环境升级需要独立验收，属于规格明确的非目标。本报告可交给sdlc-deploy进行PR前审查；它不是PR批准或部署授权。

## Stage 4之后的本地运行验证

2026-11-12工程师另行授权镜像更新及切换。System/IAM/Admin已协调升级，运行库V27成功，应用/凭据/来源/用户/绑定指纹保持；4个应用、两类绑定各1条。实际登录页与飞书/钉钉发起、Discovery/JWKS、代理及匿名拒绝冒烟通过。原有数据库备份和镜像保留；其他容器未重建。此事实补充不替代上文正式测试输出，也不表示PR已批准或真实第三方回调已验收。

## V28退役增量：本轮实际验证与运行结果

工程师在上一轮具体退役建议后明确要求“建议删除”，替代此前保留旧表的非目标。仅新增V28、隔离测试与当前模型说明；历史迁移不变。

- `mvn -o -pl macula-cloud-system -am verify -Plocal`退出0，System验证成功。
- 专用随机端口MySQL/Redis，原System隔离命令增加`LegacyIdentityBindingRetirementIT`并显式设置`IAM_LEGACY_BINDING_RETIREMENT_IT=true`及本任务root回环连接：`Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`。其中新增V28为4项，V27历史测试仍固定目标27，不修改既有历史断言。
- `mvn -o verify -Plocal`退出0，`BUILD SUCCESS`。默认汇总782项、失败0/错误0/条件跳过71；本轮显式运行上述47项，未将默认跳过算作通过，也不将本轮结果改写为此前800项的重跑。前端代码未改，本轮只执行实际HTTP冒烟，不重复运行前端构建或浏览器套件。
- 新Java文件Checkstyle通过；git diff --check通过，309个精确范围外既有文件SHA256保持。未修改保护配置，没有新增适用eval。

运行归档：旧表结构和1条历史记录以0600文件保存到0700目录，独立MySQL恢复后全部字段摘要相同。归档摘要和实际行摘要在删除前再次一致，库内无外键/视图/触发器/存储过程/事件依赖。未把真实身份写入日志或仓库。

实际执行仅更新System镜像，由Flyway运行V28。版本28、sys_user_identity_binding已不存在；sys_identity_provider_user_binding仍有1条，应用/协议凭据/来源/用户/当前绑定逐值摘要保持。其他容器ID不变，System健康。Admin首页、运行配置、Discovery/JWKS、授权跳转IAM、PASSWORD/SMS/EMAIL呈现、飞书/钉钉发起和匿名拒绝的实际HTTP冒烟均通过；不代表第三方回调或真实用户登录验收。

本轮日志、归档恢复证据及部署记录位于/private/tmp/macula-iam-retire-20261112/，不提交生成物或真实归档。未执行Git提交/推送/合并。

## PR完整范围核对（2026-11-13）

本轮用户明确授权推送合并。以main的48ef77a为基线，05e125b为代码快照，332个Git变更文件均属于IAM相关模块及关联SDLC记录。全部266个实际变更Java文件Checkstyle通过；新增内容对运行凭据明文/编码及私钥模式扫描未发现匹配；git diff检查通过，工作区提交后干净。Stage5独立审查结果见review-findings.md，不将审查当成测试或人类风险批准。
