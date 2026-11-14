# Plan: IAM内省用户类型准入 (from spec.md 2026-11-14)

Status: accepted

工程师已确认用户类型规则，并最新明确“应该是在iam里面去处理”。本计划据此收敛到Cloud，撤销之前扩展到Boot的缓存方案；不再要求用户逐请求内省或变更Gateway缓存。

## Files that change

- `macula-cloud-admin/README.md`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ResourceServerSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/tests/unit/resource-client-settings.test.js`
- `macula-cloud-api/macula-cloud-system-api/README.md`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/IamApplicationCacheNames.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ResourcePolicy.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/OAuth2ResourcePolicyTest.java`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIamApplication.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ResourceTokenIntrospectionAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/impl/OAuth2ClientRegistrationServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/impl/DefaultAccessTokenClaimsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/impl/DefaultClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/IamApplicationSnapshot.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/impl/IamApplicationServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/SysUserDetails.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/InAppLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ResourceAuthorizationIntegrationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ResourceTokenIntrospectionAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/impl/DefaultAccessTokenClaimsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/application/IamApplicationTestFixtures.java`
- `macula-cloud-system/README.md`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IamApplicationForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIamApplication.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationPolicyValidator.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationWireAdapter.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IamApplicationVO.java`
- `macula-cloud-system/src/main/resources/db/migration/V29__resource_server_allowed_user_types.sql`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ResourceUserTypePolicyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationServiceImplTest.java`

本目录另有intent/spec/plan/review-findings/build-notes/verification及pr-description.md（PR说明草稿），均只记录本变更。

## Order of work
1. 逐文件比对本次Boot SHA记录，恢复9个跟踪文件、删除3个本次新增文件与本次Boot SDLC文档；保留其他工作。确认Boot git status --short为空，并将必要本地Maven工件恢复干净基线。
2. System V29新增allowed_user_types，默认[]，Form/VO/实体/聚合版本及Admin保存多选/清空；非资源服务器携带或未知代码拒绝，不猜填历史许可。
3. IAM可信目录认证后给主体记录userType，签发时与业务Client核对；保留原UID、类型名和白名单，旧用户Token/主体缺失类型不补齐。
4. IAM内省Provider读取当前RESOURCE_SERVER allowedUserTypes，用户类型缺失/非法/不允许只返回active:false；机器根据OAuth2Authorization原始client_credentials跳过类型检查。原认证、有效期、撤销、配置版本规则保持。
5. Cloud API自持稳定userType声明常量，移除对撤销Boot新增常量的依赖。保留既有Client/Origin配置缓存失效，不改变Gateway或其他调用方Token缓存。
6. 用干净Boot依赖重跑Cloud API/IAM/System测试，保留未改UI和Schema的既有浏览器/MySQL证据，执行变更Java Checkstyle及diff检查。

## Risks
- 实际内省请求在IAM执行校验；调用方原缓存命中时是否再次内省属于现有接入行为。本次不宣称改变该行为或强制即时刷新。
- V29、System/IAM/Admin需配套升级；旧资源服务器默认[]不接受用户Token，由维护者明确配置。机器没有用户类型准入开关。
- 主体类型只来自可信目录，不从请求或当前配置猜补，原Java序列化UID显式保持。
- 不操作业务数据库、真实凭据、Docker服务或其他容器；不提交/推送/部署。

## Proof
- Boot工作区为空；Cloud无JWT_USER_TYPE_KEY等撤销Boot新增符号依赖，干净Boot工件本地恢复成功。
- Cloud API/IAM/System测试涵盖允许/拒绝/缺失类型、多选/清空、机器原grant、伪造grant拒绝、HTTP策略变化、持久化及UID。
- 既有V29隔离MySQL两例、Admin224个单测/构建/52个浏览器用例仍适用，相关源码未改变。
- 两仓diff检查及Cloud变更Java Checkstyle；最新全仓结果如遇Docker环境问题据实报告，不用撤回方案的Boot测试充当当前证据。

## 范围纠偏记录
2026-11-14：工程师最新纠偏优先于此前跨仓YES方案。撤回Boot前逐文件核对本次SHA，无范围外修改；仅撤回自身改动。原方案与旧验证记录仅保留本机临时留档，不作为当前合同。

## 本轮交付授权
工程师明确要求“推送提交，更新镜像”。据此授权本变更创建分支、提交、推送、创建PR，并定向构建/重建本地System、IAM、Admin。System负责V29新增列迁移；不擅自给现有资源服务器猜填允许类型、不轮换凭据、不改Boot、不重建其他服务；未授权合并PR。此前不提交/部署的表述记录的是当时边界，已由本次明确指令更新。
