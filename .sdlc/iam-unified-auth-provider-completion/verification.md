# 统一认证补齐验证记录

日期：2026-11-09。状态：Phase B实施中，尚未完成Test签收。原工作区已存在大量未提交改动；以下结果针对当前工作树，不代表远端已交付。没有提交、推送、部署或业务数据库升级。

## 已执行

| 验证 | 命令/范围 | 实际结果与边界 |
| --- | --- | --- |
| 全仓后端 | `mvn test -Plocal` | 546项，0失败/错误，1条件跳过（System既有用户名迁移环境门禁）；含Gateway 8、IAM 330、System 172、TinyID 34、RocketMQ 1、Docs 1。TinyID测试自行创建并清理Testcontainers，未操作业务容器 |
| 后续新增防重放测试 | `mvn -pl macula-cloud-iam -am ... -Dtest=ExternalCodeReplayStoreTest` | 5项通过，包括新加2项：阿里云31分钟下限/允许延长、Redis claim故障闭合；不把这些追加项算作上面全仓已执行数量 |
| MFA原生安全链 | `mvn -pl macula-cloud-iam -am verify -Plocal ... -Dit.test=MfaProtocolIT#...` | 10项基础矩阵通过，另1项晚阶段Token拒绝通过；采用真实Spring Security过滤链、签名器和授权持久化，账号/上游HTTP为测试素材 |
| 应用Token缓存 | `ProviderApplicationTokenCacheIT`显式Failsafe | 3项通过，专用Redis测试并发、加密信封和旧owner迟到发布/返回拒绝；同次初轮MFA晚阶段断言错误已独立修正重跑，不能称那次整体命令通过 |
| 后台目录恢复 | `ProviderDirectoryRefreshServiceTest` | 3项通过，实际独立Redis，两服务实例验证任务去重/容量、来源版本取消、失租后恢复及迟到结果不能覆盖 |
| 目录/消费边界 | `ProviderDirectoryPolicyServiceTest,OAuth2ExternalCodeAuthenticationProviderTest` | 通过；实时资料白名单可供绑定、缓存写失败不推翻实时成员证明、BACKGROUND旧资料不自动匹配、后续解析异常不释放已验证Code |
| 限流 | `ExternalCodeRateLimiterTest` | 3项通过，专用Redis原子并发、跨Client共享IP额度、Redis失败闭合；Converter另覆盖包装请求/伪造转发头 |
| V20迁移 | `IdentitySourceFlowDirectoryMigrationIT` | 一次性MySQL 8.4.6，V19→V20真实Flyway 1项通过，保留原来源/Secret/绑定及V14—V19校验和；未猜填组织/flow |
| 绑定事务 | `IdentitySourceBindingIT` | 同一轮一次性MySQL及专用Redis 13项通过；真实Mapper/事务、并发归属、回滚票据、来源版本围栏及REQUIRED准入快照锁内检查 |
| Admin单元 | `npm run test:unit -- --run` | 21文件196项通过 |
| Admin生产构建 | `npm run build` | 通过；构建输出仅本地生成，不提交 |
| Admin浏览器 | 本地preview 4287，Cypress 3个spec | 33/33通过，含1920/390px；API为cy.intercept Mock，不代表IAM/System或平台联调 |
| ESLint | 对目标JS/Vue运行无`--fix`的eslint | 0错误，951格式警告；保留既有混合工作区，未用自动修复重排用户改动 |
| Java风格 | 仓库配置，按本轮增量基线生成88个Java文件列表 | Checkstyle 0违规；后续新增测试已复核，final-checkstyle2日志0违规 |
| 工作区 | `git diff --check`及增量基线 | 通过，未清理/回滚用户改动；参考URL集合保留，未提交临时日志/生成物 |

## MFA矩阵说明

基础10项：密码拒绝、opaque/JWT授权码交换拒绝、已有SSO授权拒绝、Refresh拒绝但撤销仍可用、托管短信拒绝、五个平台浏览器回调（单测试循环覆盖）、绑定会话拒绝且SDK绑定不建SSO、Consent拒绝、SDK直接grant拒绝（两Token格式）、client_credentials不触发用户MFA。

第11项在内存可能已经生成Access Token后，让下一次检查拒绝，断言响应不含Token且存储无Access/Refresh半份授权。默认SPI没有因子实现，不据此声明完成真实多因素认证。

## 已发现但尚未实施的标准OIDC问题

`MfaProtocolIT.browserSourceProofSurvivesAuthorizationWithoutLeakingClaims`正向测试失败：自定义第三方主认证没有Spring Security 7所需FactorGrantedAuthority时间元数据，标准ID Token生成抛出`authenticationTime cannot be null`。密码标准OIDC与external_code OIDC已有正向回归通过，不能据此遮盖第三方/SMS/绑定会话的标准OIDC缺口。

这不是把预期结果改成拒绝就可解决的测试。计划原明确“不增加auth_time”，补充设计在plan.md单独标draft并已请求工程师选择；尚未收到该补充选择，未更改认证时间或反序列化语义。因此MfaProtocolIT全类以及全量verify目前不能标通过；上面的11项均为明确选择的不依赖补充设计场景。不能把全仓test通过等同全量verify通过。

## 官方/真实平台边界

钉钉与飞书固定HTTP合同由官方SDK及可读官方文档补证；请求体测试仍是HTTP Mock。飞书AppLink与requestAccess完整版本兼容性未核实，保留禁用配置及官方入口，不把禁用视作完成。新版租户client_credentials已找到官方页面，当前实现仍固定采用官方SDK提供的internal租户Token合同，不做失败后盲试或混用用户Token。

微信、企微、飞书、钉钉真实企业/应用安装、Code、设备回调、权限范围与字段返回：未执行。企业微信敏感user_ticket资料、未建模小程序及旧钉钉SNS不宣称新增支持。

## 日志索引（本地临时，不提交）

- `/private/tmp/iam-unified-final-reactor.log`
- `/private/tmp/iam-unified-mfa-matrix3.log`、`iam-unified-final-late-mfa.log`
- `/private/tmp/iam-unified-final-cache-mfa.log`（缓存通过，初轮晚阶段MFA断言失败；已另行重跑）
- `/private/tmp/iam-unified-recovery-tests2.log`、`iam-unified-rate-tests.log`
- `/private/tmp/iam-unified-mysql-migration.log`、`iam-unified-mysql-binding.log`
- `/private/tmp/iam-unified-admin-final-unit.log`、`iam-unified-admin-build2.log`、`iam-unified-cypress.log`、`iam-unified-admin-lint.log`
- `/private/tmp/iam-unified-final-checkstyle.log`

一次性MySQL与Redis已由runner finally停止并清理，仅操作记录的本次容器ID及进程。

## 收尾工作区核对

本次私有增量基线内103个文件实际变化（88个Java）；不把Git相对HEAD的全部用户改动归因本次。第三方文档原URL集合没有减少，git diff --check通过。当前仍等待标准OIDC主认证时间补充设计的人类选择，不自行宣布完成门禁。

## 后续用户授权的镜像更新（2026-11-09）

原“未部署／未业务库升级”描述上面验证阶段。用户随后明确要求恢复Boot并更新镜像：资源边界实现已迁入Cloud，恢复后的Boot原版参与构建且工作区干净。System/IAM/Gateway/Admin现已本地更新、healthy，System已从更新前备份后执行追加V20。其他10个服务未替换。具体来源与验证见资源边界plan.md的“仅Cloud实现及本地镜像更新结果”；真实平台和auth_time待确认项没有因此完成。

## 用户后续要求撤销额外安全装配（2026-11-09）

最新状态优先于此前“仅Cloud实现并已更新镜像”的记录：用户已要求移除CloudGatewayConfiguration等替代配置，源码已恢复原Starter自动装配、内省/缓存、角色/租户授权及内部JWT转发；已删除替代实现和配套专用测试，不再有Cloud资源拦截、System二次内省和固定503分类。内省资源清单此前也已撤销。Boot始终保持干净。

Cloud clean test（508项，1既有条件跳过）、原Starter相关153项测试和应用打包通过，运行JAR无替代类残留；镜像尚未更新，旧容器不代表当前源码。IAM第三方来源/MFA补齐与auth_time待确认项未被本次撤销扩展处理。详细备份与精确范围见资源边界plan.md最新撤销章节。

## 原Starter与最新认证界面已本地更新（2026-11-09）

用户再次明确要求更新镜像，System/IAM/Gateway/Admin已替换为当前源码版本，原Starter装配与按类型显示凭据界面已进入运行镜像。四容器healthy/零重启；镜像/JAR及HTTP入口已核对；旧内省资源选项不在前端运行包内，其他10个服务未替换，Boot干净。修正了System平台初始化所需Gateway ID的Compose传递，不恢复被撤销的资源策略。见资源边界plan.md最新本地更新章节。auth_time待确认与真实平台联调状态不因更新改变。
