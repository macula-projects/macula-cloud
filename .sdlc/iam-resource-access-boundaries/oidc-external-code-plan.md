# Plan: OIDC应用的第三方Code登录 (from spec.md accepted amendment 2026-11-08)
Status: accepted

沿用iam-resource-access-boundaries已经接受的单协议、资源授权和平台身份设计。Rain已OK确认本次功能方向；Rain随后以YES明确接受本补充实施计划，开始Phase B；不包含部署、数据库迁移或Git操作授权。

## Files that change
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistryTest.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningService.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOidcTokenCustomizer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuerTest.java`（新增测试）
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-admin/src/utils/applicationProtocolRegistry.js`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/IdentitySourcePicker.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/tests/unit/application-protocol-registry.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-iam/README.md`
- `macula-cloud-system/README.md`
- `macula-cloud-admin/README.md`

- 协作工件：本目录spec.md已确认修订、本文件、review-findings.md；不修改Boot、数据库迁移、部署配置或真实凭据。

## Order of work
1. 备份当前工作树及精确文件基线。先读目标POM、README、邻近实现和现有测试；保留之前所有未提交内容。
2. System API当前运行时校验允许OIDC的external_code，与authorization_code可单选或同时启用。OIDC仍要求openid，但不再强制authorization_code；无授权码不要求redirect URI。历史validate()及V17/V18/V19迁移不变，只调整validateRuntime()。SERVICE仍不是用户登录客户端；MINIAPP可选择OIDC且只关联其平台的小程序能力。
3. System保存校验同步：具体SDK/小程序能力允许绑定OIDC或OAuth2.1，均要求none和匹配的可用registration；保留ETag、授权版本递增、提交后立即失效及managedKey保护。范围仅external_code；不放开OIDC的sms/email/client_credentials。网页身份源仍依赖authorization_code。受管Admin继续保留authorization_code以维持现有管理端登录，但可显式增配external_code；平台认领器接受这一有效组合，不再要求grant字符串只有authorization_code，不清除SDK绑定，仍完整验证当前none/OIDC/来源许可。
4. IAM客户端投影保留OIDC的真实CODE_TYPES/PROVIDER_BINDINGS，不再清空；统一协议策略允许OIDC+external_code+openid、OAuth2.1+external_code且无openid。真实RegistrationIdentityBindingService也从唯一有效协议读取external_code许可，不能继续硬编码OAUTH2_1；已绑定读取、自动绑定与绑定完成后的来源/Client当前配置重校验都走同一规则。只能使用唯一已启用配置，草稿、RESOURCE_SERVER、未授权grant/Scope/来源、机密客户端均拒绝。授权端点原生PKCE不变，不把SDK Code描述为IAM authorization_code。
5. external_code转换器支持可选nonce：提供时必须单值、非空、最多256字符；缺省不生成或声称已校验nonce。沿用重复参数、Code长度、请求参数白名单和来源核验。nonce只关联扩展请求/响应，不能代替厂商Code真实性、AppID/来源绑定或防重放；指南要求从可信SDK获取Code，不从URL自动接收外部Code。
6. 通用用户grant签发器仅为“external_code + OIDC effective protocol + openid”额外签发ID Token，复用现有JwtGenerator、签名配置与CustomOidcTokenCustomizer。OIDC请求缺openid拒绝；OAuth2.1带openid拒绝且绝不返回id_token。签发前必须有经过核验、匹配Client可信租户的本地稳定userId；缺失时失败关闭，不回退到不稳定的用户名sub。ID Token持久化到同一授权，在响应中增加id_token；iss为配置issuer，aud为client_id，sub复用tenantId:userId，profile资料按Scope输出；存在nonce则由受控请求写入ID Token并与授权关联。JWT/opaque Access Token及其资源aud/Scope不变；不得把Access Token的资源aud或角色声明复制成ID Token。
7. ID Token生成失败不得保存/返回半份成功授权；保持当前配置版本快照与授权保存的竞态检查。没有授权码时也可形成完整principal授权记录供UserInfo读取。公共客户端不签发refresh token，不建立浏览器SSO Session；不伪造厂商认证时间或acr/amr保证，不接受max_age等未实现请求。
8. 前端取消OIDC排斥SDK/小程序能力和投影清空。添加身份源仍自动启用external_code，移除最后一个SDK能力自动关闭；已存在网页登录绑定时不能移除其授权码依赖。纯SDK的OIDC不自动塞回authorization_code，不索要网页登录回调；SPA默认仍为授权码，MINIAPP选择OIDC保留SDK默认。
9. 将该能力明确命名为“第三方Code登录”，OIDC/OAuth2.1都直接显示其状态和引导入口，不收在默认折叠扩展里。入口定位到登录控制添加具体SDK身份源；sms/email仍按原OAuth扩展区域显示。快速开始根据实际grant生成scope/code_type/code和可选nonce示例，并说明ID Token的签名、iss/aud/exp和请求nonce匹配校验。
10. 跑下面Proof。仅同步文档与本次源代码，不提交/推送，不更新现有镜像或迁移业务库。

## Risks
- 这是Macula扩展登录，不是OIDC标准授权码流程；只复用ID Token与UserInfo语义，不声称现成OIDC客户端可直接使用SDK Code，也不将ID Token视为API凭据。
- 公共客户端无Secret，因此必须继续验证具体来源、厂商Code、客户端许可、可信租户、本地账号绑定和全局防重放。签发ID Token不能绕过自动绑定/绑定票据规则；未绑定时继续binding_required，不能提前签发任意Token。
- nonce是可选响应关联值，不能凭回显就宣称SDK Code与平台nonce获得上游绑定；没有nonce时接入示例不能假称完成nonce校验。不得据此放松原授权码流程的state/nonce/PKCE。
- JwtGenerator对扩展grant的ID Token行为需由实际测试确认。不得为了让它生成而伪装成authorization_code；必要调整应保持真实external_code grant和审核记录。新增未列文件先记录偏差，改变合同则再确认。
- UI normalize/watch不能在编辑OIDC时清掉原有SDK绑定，也不能在纯SDK应用恢复授权码依赖。协议切换仍按原确认语义清空，不混用配置。
- 不修改已有Flyway；没有新结构迁移，也不自动给已有Client添加SDK权限或身份源。

## Proof
- API/System：OIDC仅external_code、openid/profile、none、具体来源可保存；小程序OIDC可选；无授权码不要求回调；缺openid/无来源/不适用来源/机密认证/服务用途/未授权grant拒绝。历史V17/V18合同回归保留，原ETag/版本/缓存用例不回退；受管Admin仍可授权码登录，额外SDK许可重启认领后不丢失。
- Converter/Provider：有/无nonce、重复/空/超长nonce、重复Code、未许可CodeType、错误registration、厂商核验失败、绑定未完成、基础设施错误与重放逐项断言。OAuth2.1仍无ID Token，OIDC请求openid才走身份签发。
- Issuer/JWT：实际验签ID Token的iss/sub/aud/iat/exp、匹配nonce、profile白名单；JWT和opaque Access Token都保持资源aud及Scope。ID Token生成失败/版本变更均不保存半份成功授权；其他扩展grant响应不新增ID Token。
- 原生安全链：IamProtocolTest使用真实本地Redis、真实token endpoint和签名器，第三方远端验证器明确Mock；证明OIDC external_code生成ID Token并能访问UserInfo，OAuth2.1无ID Token，SDK无浏览器SSO Session；配置收紧后Access/UserInfo拒绝。不能将Mock厂商称作真实厂商验收。新增OIDC的IdentitySourceBindingIT使用专用V19 MySQL与Redis、真实RegistrationIdentityBindingService/IdentitySourceBindingPolicyService/ExternalCodeAccountResolver，覆盖已绑定、自动绑定、binding_required及完成后重新取Code；不能Mock整个账号resolver掩盖OIDC排斥。厂商远端核验可明确Mock，来源当前配置变化仍必须重新拒绝。
- 前端：Vitest验证协议归一化/投影不丢SDK绑定，OIDC SDK-only和小程序OIDC、关联/移除自动联动、grant依赖及nonce示例；Cypress验证OIDC添加SDK不用切OAuth、直接可见入口、无多余回调要求与桌面/移动布局；默认Admin授权码登录不变。
- 执行最小API/System/IAM Maven测试后跑Cloud全仓mvn test -Plocal及适用verify；Vitest、Cypress、生产构建、无修复ESLint、变更Java Checkstyle及git diff --check。引用实际输出与跳过原因，不用静态检查冒充运行验证。无数据库结构变更不新增迁移，不操作当前业务库；如需真实数据库测试，仅使用专用临时环境。

## Plan review
REVIEW.md未指定Stage 3 reviewer，默认codex-plan-review未安装，按sdlc-build回退sdlc-reviewer子代理，仅Plan pass，最多三轮。round 1发现1项Important：漏列真实绑定服务中的OIDC排斥，已补文件与真实账号链证明；round 2仅审修订及连带影响，NO_FINDINGS；实际2轮。审核详情见review-findings.md，实施计划已由Rain以YES接受。

## Implementation deviations
- 补充测试fixture文件authentication/thirdparty/IdentitySourceTestFixtures.java：旧SDK素材默认使用basic，与实际none合同不一致；修正为真实公共客户端，增加协议参数以验证同一真实绑定服务的OIDC场景。保留依赖该素材的其他测试，不更改其业务职责。


## Implementation evidence — 2026-11-08

实施已完成，等待工程师确认Phase B出口；以下为构建期间实际运行证据，不代表Stage 4人工验收或部署批准。

- 实现：OIDC/none的external_code可以独立保存，MINIAPP可选OIDC；具体来源仍由真实绑定服务与当前配置复核。SDK来源添加/移除联动授权；删除最后来源保留空授权草稿，不自动变成授权码登录。Admin自身保留授权码；初始化重读SDK许可及目录中的可用来源，不替换或清除已有绑定。
- 签发：仅OIDC+external_code+openid新增ID Token，复用真实签名器；稳定本地用户及可信租户缺失时拒绝。ID Token与Access Token在同一授权保存；生成失败不保存部分成功。请求nonce从实际扩展Token类型读取，验签测试覆盖提供/省略nonce，不伪造auth_time/acr/amr或创建SSO Session。
- `mvn test -Plocal`：Cloud全仓516项，0失败/0错误/1环境条件跳过（SysUserUsernameUniqueMigrationTest）。随后新增Provider用例及最终精确小程序素材通过定向8项：`IamProtocolTest#oidcSdkIssuesSignedIdentityAndUsableUserInfoWithoutBrowserSession,OAuth2UserGrantTokenIssuerTest,OAuth2ExternalCodeAuthenticationProviderTest`。日志：`/private/tmp/iam-oidc-code-all-tests.log`、`/private/tmp/iam-oidc-code-final-native.log`。
- 原生端点：真实本地Redis、真实Token端点、签名器和验签器；厂商远端核验与账号resolver明确Mock。验证JWT/opaque Access Token资源aud不变、ID Token的iss/sub/client aud/时间/nonce/profile白名单、UserInfo成功、ID Token不可用作API凭据、版本提升后拒绝、OAuth2.1无ID Token、无浏览器Session。Mock厂商不代表真实设备或厂商验收。
- `IdentitySourceBindingIT`：12项全部通过。专用临时MySQL 8.4容器运行完整V19结构，专用Redis有运行标记；真实Mapper、RegistrationIdentityBindingService、IdentitySourceBindingPolicyService和ExternalCodeAccountResolver覆盖OIDC待绑定票据、手动绑定后重新解析、自动绑定及来源许可撤销。用户详情范围服务明确Mock，未访问业务数据库。日志：`/private/tmp/iam-oidc-code-it/binding.log`。
- Admin Vitest：20文件、191项全部通过；Cypress配置页16项全部通过，含1280/390像素OIDC SDK来源添加/保存/移除及空授权草稿。生产构建通过，现有chunk体积提示保留；实际检查移动端截图，无新增横向溢出。日志：`/private/tmp/iam-oidc-code-front-tests.log`、`/private/tmp/iam-oidc-code-cypress.log`、`/private/tmp/iam-oidc-code-front-build.log`。
- 20个本次变更Java文件Checkstyle零违规；本次前端文件无修复ESLint通过；`git diff --check`通过。日志：`/private/tmp/iam-oidc-code-checkstyle.log`、`/private/tmp/iam-oidc-code-eslint.log`。
- 实施中发现并修正：OIDC移除最后SDK来源时未关授权、SDK请求类型与原生授权Token不同导致nonce漏写、normalize自动恢复authorization_code。对应拒绝、签名、配置及页面用例已覆盖。
- 没有新增/修改Flyway迁移，没有操作当前业务库，没有更新应用镜像，没有Git提交或推送。仅测试临时容器，测试后删除。
