# Plan: 接口与实现包结构整理 (from spec.md 2026-11-13)

Status: accepted

工程师已于 2026-11-13 对上一轮完整映射、同步引用与兼容性检查范围回复 OK；本文件记录该已获确认的具体方案，不以代理自批替代人工确认。当前仅包结构整理，不改变方法体。

## Files that change

移动前后路径：
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/IamApplicationServiceImpl.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/impl/IamApplicationServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsService.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/impl/DefaultClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistrationServiceImpl.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/impl/OAuth2ClientRegistrationServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/RedisVerificationCodeStore.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/redis/RedisVerificationCodeStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/UnavailableVerificationCodeService.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/verification/impl/UnavailableVerificationCodeService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/ClientAuthenticationExtension.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/impl/ClientAuthenticationExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/AuthorizationEndpointExtension.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/impl/AuthorizationEndpointExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/TokenEndpointExtension.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/impl/TokenEndpointExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/DefaultAccessTokenClaimsService.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/impl/DefaultAccessTokenClaimsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/KeyStoreSigningKeyProvider.java` → `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/keystore/KeyStoreSigningKeyProvider.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolDefinition.java` → `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/oauth2/OAuth2ProtocolDefinition.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolSettings.java` → `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/oauth2/OAuth2ProtocolSettings.java`
- `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/crypto/CryptoLocaleServiceImpl.java` → `macula-cloud-gateway/src/main/java/dev/macula/cloud/gateway/crypto/impl/CryptoLocaleServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/ApplicationCredentialCacheService.java` → `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/cache/ApplicationCredentialCacheService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/MenuRouteCacheService.java` → `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/cache/MenuRouteCacheService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/MenuRouteTenantContext.java` → `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/cache/MenuRouteTenantContext.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsServiceTest.java` → `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/impl/DefaultClientAccountDetailsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistrationServiceImplTest.java` → `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/impl/OAuth2ClientRegistrationServiceImplTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/RedisVerificationCodeStoreTest.java` → `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/redis/RedisVerificationCodeStoreTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/DefaultAccessTokenClaimsServiceTest.java` → `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/impl/DefaultAccessTokenClaimsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/KeyStoreSigningKeyProviderTest.java` → `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/keystore/KeyStoreSigningKeyProviderTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/ApplicationCredentialCacheServiceTest.java` → `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/cache/ApplicationCredentialCacheServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/MenuRouteCacheServiceTest.java` → `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/cache/MenuRouteCacheServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/MenuRouteTenantContextTest.java` → `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/cache/MenuRouteTenantContextTest.java`

引用闭包及文档（如仅注释提及且无需改变，不产生无效改动）：
- `macula-cloud-api/macula-cloud-system-api/README.md`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistry.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolSettings.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ApplicationProtocolSettings.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistryTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/IamApplicationProtocolRegistryTest.java`
- `macula-cloud-gateway/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ProviderFailureDiagnostics.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/JwtConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2ProtocolExtensionConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2TokenConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeChannelServicesTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/oauth2/JwtConfigurationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/application/IamApplicationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/application/IamApplicationTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingIT.java`
- `macula-cloud-system/README.md`
- `macula-cloud-system/src/main/java/db/migration/V18__project_legacy_application_protocols.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/protocol/IamApplicationProtocolConfiguration.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IamApplicationForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/listener/ApplicationListener.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationWireAdapter.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientCredentialService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/SysApplicationApiKeyServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/SysApplicationServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/SysMenuServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/SysRoleServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IamApplicationVO.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/AdminClientProvisioningIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ApplicationProtocolConfigurationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ApplicationProtocolConfigurationMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IamApplicationDraftMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IamApplicationModelConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IamApplicationModelMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ResourceClientConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/WebLoginModeConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/migration/ApplicationApiKeyIntegrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationWireAdapterTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ProtocolConfigurationTestFixtures.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysApplicationApiKeyServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysApplicationServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysMenuServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysRoleServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/SysTenantServiceImplTest.java`

## Order of work
1. 核对原工作区、相关 POM/README、类型依赖、装配和序列化合同；记录已确认范围并独立审查 Plan。
2. 移动批准的生产类型和对应实现测试；更新 package/import，补齐原同包类型引用，不修改任何方法体。保留接口所在职责包；不将普通模型机械移走。
3. 更新调用方、显式 Bean 配置、现有测试和四个模块 README 中的包约定与 Java 源码兼容说明。
4. 运行相关模块及全仓单元测试、适用 Checkstyle、旧引用与同包实现扫描、git diff --check；记录执行结果和实际偏差。

## Risks
- 旧同包隐式依赖及测试夹具需显式 import，包可见成员不能靠扩大可见性掩盖；发现问题先评估并记录。
- System API 的两个公开 Java 类型变更 FQCN，仓库外调用方需调整 import 并重新编译；JSON 使用名称 oauth2 不随 Java 类路径变化。
- 只改历史 Java 迁移的编译引用，不改迁移逻辑或 SQL；历史迁移校验和策略需核对。
- 不移动序列化 Token/Principal/Session，保持 Bean 简单类名、装配与事务边界；干净构建避免旧 class 遮蔽问题。

## Proof
- API/System/IAM/Gateway clean test -Plocal，随后 mvn test -Plocal；现有协议 JSON、授权持久化、Bean 装配、可信租户和缓存单测提供行为回归，不为纯移动新增镜像实现测试。
- 记录实际测试成功/失败/跳过数，不把未运行的外部 MySQL/Redis IT 视为通过；不更新运行环境。
- 比较移动前后 Java 去除 package/import 后的文本，确认所有方法体、注释及注解保持；仅文档和必要引用变化。
- 检查旧 FQCN、混放接口与实现、Java 编译、Checkstyle 和 diff 空白错误。

## 2026-11-13 推送与合并授权

工程师在实现及验证交付后先回复 OK，随后明确要求“推送合并”。据此允许本变更创建分支、提交、推送、创建 PR 并在审查和平台检查通过后合并 main；此前不提交/推送的约束仅记录当时授权边界，已由本次明确指令更新。不包含部署、发布镜像或数据库操作。
