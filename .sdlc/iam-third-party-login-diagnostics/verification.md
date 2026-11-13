# IAM第三方登录安全诊断验证

Status: green

用户YES已批准实现、测试、仅更新IAM及复测。Plan Round1/3 NO_FINDINGS；不自批人工门。

## 实现证据

- 修改前真实分支模拟回归：`Tests run: 24, Failures: 5, Errors: 0, Skipped: 0`。五个新增断言均因缺失安全诊断失败。
- 实现后同组24项通过；增加恶意响应/HTTP/边界用例后：`Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`。
- 钉钉数字子码含旧errmsg、结构化字段及HTTP requiredScopes；飞书业务/HTTP 99991672仍返回UNAVAILABLE且tolerable=false，缓存故障不泄露message；公共目录拒绝仍不写快照/排队。
- 注入Code/Token/Secret/用户标识、URL、换行、未知权限、非法JSON、超长数字/正文/权限数组；输出只含已知权限和有界字段，无原文或异常堆栈。

## 正式验证

- `mvn -o -pl macula-cloud-iam -am verify -Plocal`：`BUILD SUCCESS`；汇总477项，Failures 0，Errors 0，Skipped 17。
- `mvn -o verify -Plocal`：`BUILD SUCCESS`；汇总760项，Failures 0，Errors 0，Skipped 52。默认跳过为环境开关控制的隔离集成测试；本次不改数据结构、事务或缓存合同，不把跳过当作已执行。
- `npm run test:unit -- --run`：`Test Files 23 passed (23)`，`Tests 223 passed (223)`。Admin生产代码未改，浏览器使用上一轮已构建且摘要不变的dist；完整Cypress新执行结果：`All specs passed! 96 96`，9个spec、0 failing/pending/skipped。
- 精确8个Java路径Checkstyle成功；git diff --check通过。范围外217个已有文件摘要保持。未改CLAUDE.md、技能、hooks、REVIEW.md或reviewer定义，不适用相关配置eval。
- 临时证据：/private/tmp/macula-iam-review-20261111/diagnostic-*.log；未提交生成物或日志。

## 平台与交付边界

本修订只补安全诊断，不是登录修复；不变更平台权限、准入策略、组织匹配、来源版本、绑定、Code消费状态或客户端配置。新镜像尚未更新，真实平台须在更新后由用户重新发起授权，不复用旧Code。不宣称已证明缺少具体权限或已恢复登录。

Verification is green。用户已授权验证后仅更新IAM，开始定向构建；新平台回调结果待部署后取证。


## IAM定向更新与运行验证

仅构建并替换macula-cloud-iam，镜像`sha256:a412b8d33b9bcff109f946613080656a2aefc6415b40212dc7f8d410ad4973e3`，healthy；容器实际镜像ID与tag一致。启动出现Started MaculaIamApplication，ERROR为0，无临时SPRING_APPLICATION_JSON/TRACE。

运行JAR内ProviderFailureDiagnostics、DingtalkClient、FeishuDirectoryClient、ProviderDirectoryPolicyService以及共享mode enum与已验证本地产物字节一致。其余13容器ID未改变，6张相关业务表摘要未变。运行环境键无变化，无凭据轮换、Schema或平台权限写入。

HTTP冒烟：Admin首页、IAM直连/login、Admin代理/iam/login、OIDC Discovery均200，issuer与原配置相同；无Token System及Admin代理业务入口均401。完整receipt与日志在临时目录；没有提交、推送或合并。

已通知用户重新从Admin发起飞书/钉钉授权。此处仅证明诊断镜像交付与运行，真实失败根因及登录恢复仍待新回调，不复用旧Code。


## 真实回调复测（2026-11-12 14:59 Asia/Shanghai）

用户回复已重试后读取新镜像日志，确认两家均被平台权限拒绝，尚未恢复登录：

- 14:59:23：`provider=FEISHU stage=directory status=400 code=99991672 requiredScopes=[contact:contact:access_as_app, contact:contact:readonly, contact:contact:readonly_as_app]`。平台用户身份核验成功，失败在应用身份通讯录请求；安全投影只显示白名单内权限，不代表完整响应字段。
- 同次公共准入：`stage=admission status=UNAVAILABLE required=false tolerable=false reason=proof_unavailable`。现有FeishuDirectoryClient只特别识别41050，99991672仍沿用未识别错误的unavailable(false)；不是固定组织不一致或未绑定账号。此修订不改变该分类/放行语义。
- 14:59:46：`provider=DINGTALK stage=member status=200 errcode=88 subcode=60011 requiredScopes=[qyapi_get_member]`。前序OAuth用户Token和用户信息已完成，getbyunionid被拒绝，已不再停留于早先users/me 403。

下一步需分别由应用管理员为实际配置的飞书/钉钉应用开通对应API权限并完成所需发布/授权生效；飞书还应确认通讯录数据权限范围包含登录成员。没有修改平台权限、业务数据或放宽目录准入，不宣称补权限后其余绑定步骤已通过。

官方依据：飞书99991672权限排障https://open.feishu.cn/document/faq/trouble-shooting/how-to-fix-the-99991672-error；钉钉接口授权说明https://open-dingtalk.github.io/developerpedia/docs/develop/best-practices/permission-deny/。实际所需权限以上述真实应用响应为证据。


## 账号密码绑定跟进验证（2026-11-12）

Plan增量Round2/3 NO_FINDINGS。仅补原catch包装前诊断；未改变绑定判定、账号证明、failedAttempt、事务或pending消费。

- 修改前ThirdPartyBindingServiceTest：7项中2项新增诊断断言失败。
- 实现后定向测试发现1项夹具预期错误：Mockito thenThrow会重写模拟异常栈，使预设requireClient位置变为实际mock bind位置。调整集成断言为实际调用点，并在纯诊断单测单独证明denied帮助方法被跳过；未因测试改变认证逻辑。初次全仓验证同一夹具失败，没有标绿或交付。
- 修正后`mvn -o verify -Plocal`：`Tests run`模块汇总764项，Failures 0，Errors 0，Skipped 52；`BUILD SUCCESS`。允许/拒绝、次数、pending保留/提交消费及日志秘密/非法代码位置均有断言。
- 10个精确Java路径Checkstyle通过，git diff --check通过；范围外215个既有文件摘要不变。Admin源码未改，沿用本轮已完成的223单元/96浏览器证据，不当作绑定真实平台成功证据。保护配置未改，无相关eval。
- Verification is green。运行环境对比无变化，无TRACE；新IAM镜像待定向替换后再由用户复测实际拒绝分支。


绑定诊断IAM镜像已更新：`sha256:d86b14f315e1d7d5e9c049222ac4f5ce8c17f81b92452e227757e6e0efae3ec5`，healthy，启动ERROR0。运行JAR内绑定Service、诊断工具及原平台/目录类与已验证产物字节一致。其余13容器和6张业务表摘要不变，login/代理/Discovery均200，受保护入口401。已请求用户从新第三方登录开始再尝试一次绑定；尚未宣称绑定修复或得到真实拒绝位置。


## 绑定真实失败分支与隔离复现

用户新回调15:25:00记录`stage=binding_write exception=AuthenticationServiceException site=IdentityProviderConfigService.requireLoaded:138`。密码证明已经完成，配置JSON解析失败；没有绑定INSERT。

在临时独立MySQL使用当前编译类/依赖及Mybatis加解密拦截器、合成AES数据运行LockedSourceReplay：
```
REPLAY stage=outside-by-id secretJsonIsEmptyObject=true appSecretDecrypted=true
REPLAY stage=inside-by-id secretJsonIsEmptyObject=true appSecretDecrypted=true
REPLAY stage=inside-wrapper secretJsonIsEmptyObject=false appSecretDecrypted=false
REPLAY stage=locked-first secretJsonIsEmptyObject=false appSecretDecrypted=false
REPLAY stage=locked-again secretJsonIsEmptyObject=false appSecretDecrypted=false
REPLAY stage=explicit-locked secretJsonIsEmptyObject=true appSecretDecrypted=true
```
实际执行成功，自建MySQL/Redis进程已关闭；未使用业务凭据或改业务数据库。证据/tmp/macula-iam-review-20261111/binding-locked-source-replay.log。专用锁定读取的生产修复尚待精确方案批准，与已绿色交付的诊断代码分开。

隔离重复锁定补验：`REPLAY stage=explicit-locked-again secretJsonIsEmptyObject=true appSecretDecrypted=true`；同事务重复查询同样保持正确解密。Plan Round3/3 NO_FINDINGS，生产修复未实施。


## 专用锁定查询修复正式验证（2026-11-12）

用户YES接受精确修复、测试及IAM更新，Plan Round3/3 NO_FINDINGS。实际生产改动仅Mapper增加显式参数化主键FOR UPDATE、绑定Service两处调用替换，来源配置校验未变。为证明真实回归，先将新Mapper方法委托原Wrapper路径：IdentityProviderLockedReadIT的2项中1项解密断言失败。改为显式SQL后2/2通过，验证普通读/锁定读/同事务重复读取、原密文不变及非法/缺失配置拒绝。

- 原IdentitySourceBindingIT首次17项中4项因隔离Redis缺少iam-source-it-only运行标记失败，保护断言正确阻止测试访问未确认实例；未修改测试或认证代码。补齐自建Redis标记后，17/17通过，0 skipped。新IT在全新隔离数据库再次2/2通过。自建MySQL/Redis均已停止，未操作业务实例。
- `mvn -o verify -Plocal`：`BUILD SUCCESS`；模块汇总`Tests run: 766, Failures: 0, Errors: 0, Skipped: 54`。默认skip中的新2项与原17项已按上述隔离条件显式执行，不能把其余默认环境跳过当作已验证。
- 13个精确Java路径Checkstyle通过；git diff --check通过。范围外214个已有文件摘要不变。Admin源码未改，沿用同轮223单元/96浏览器结果，不宣称其证明真实绑定成功。无策略/技能/hooks变更，无相关eval。
- Verification is green。运行环境/TRACE检查不变，正在仅构建IAM；不迁移Schema、不改凭据、用户、来源或绑定，不改Boot公共Crypto组件。真实用户绑定仍需新镜像后重试。


## 专用锁定查询修复运行验证

仅IAM替换为`sha256:e2fff69f58a342035b545a8e1270eabc73fea028188599304434a7fa04a18570`，healthy；启动Started MaculaIamApplication，ERROR0，无临时TRACE。首轮Docker Hub构建前端认证EOF，重试构建成功；未改Dockerfile、依赖或配置。

运行JAR内SysIdentityProviderMapper、RegistrationIdentityBindingService及已有诊断/绑定类、共享enum与验证产物字节一致。其余13容器ID不变，6张业务表在用户复测前摘要不变，凭据/绑定没有迁移或人工改写。Admin/IAM登录及代理、Discovery均200；无Token业务入口401，issuer未变。已请求用户从新第三方登录开始复测绑定，尚未宣称真实账号成功。

临时构建/启动/运行receipt在/private/tmp/macula-iam-review-20261111/locked-source-*，不提交生成物。没有Git提交、推送或合并。


## 用户真实复测确认

2026-11-12，专用锁定查询修复镜像交付后，用户回复“可以了”，确认本次第三方登录后的账号绑定流程已恢复可用。该确认对应用户实际复测的流程；未据此扩大为所有平台、设备或登录方式均已验收。诊断与修复闭环完成，没有追加代码、数据修改、提交或远程操作。
