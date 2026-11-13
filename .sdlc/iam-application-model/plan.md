# Plan: IAM接入应用领域模型统一 (from spec.md 2026-11-12)

Status: accepted

用户YES已接受spec及完整Plan；当前处于Phase B实施，退出确认仍由工程师作出。只在隔离设施验证迁移，不升级业务库、不部署、不提交。使用Java17、现有Spring/MyBatis-Plus/Flyway/Jackson/Vue工具链，不引入依赖。遵循AGENTS.md及architecture/backend/testing/dependencies-release规则；无CLAUDE.md时使用这些实际规则。sdlc-design要求的接受提交因会话未授权Git提交而不执行。

## Files that change

精确路径包括重命名前/后文件；只有表述为旧协议API/历史兼容的类型才保留。历史SQL迁移、V18/V19 Java迁移和仅验证历史版本的测试SQL不修改。新增/移动类型遵循版权头与中文Javadoc，不生成package-info.java。

- `.sdlc/iam-application-model/build-notes.md`
- `.sdlc/iam-application-model/intent.md`
- `.sdlc/iam-application-model/plan.md`
- `.sdlc/iam-application-model/review-findings.md`
- `.sdlc/iam-application-model/spec.md`
- `.sdlc/iam-application-model/verification.md`
- `macula-cloud-admin/README.md`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/utils/oauth2ProtocolConfiguration.js`
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientApplication.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCreate.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientCredentials.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/IdentitySourcePicker.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`
- `macula-cloud-admin/tests/unit/application-protocol-registry.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-api/macula-cloud-system-api/README.md`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/HostedLoginMethod.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IamApplicationPurpose.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IamApplicationStatus.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IamApplicationType.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/IamWebLoginMode.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationType.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientConfigurationStatus.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientPurpose.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2HostedLoginMethod.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2WebLoginMode.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolDefinition.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistry.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/ApplicationProtocolSettings.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/IamApplicationProtocolRegistration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/IamApplicationProtocolRegistry.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ApplicationProtocolSettings.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolDefinition.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolSettings.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/IamApplicationCacheNames.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/IamApplicationLoginPolicy.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ApplicationLoginPolicy.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ClientCacheNames.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/IamApplicationTypeTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/IamWebLoginModeTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationTypeTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/OAuth2WebLoginModeTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistryTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/IamApplicationProtocolRegistryTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/security/IamApplicationLoginPolicyTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/security/OAuth2ApplicationLoginPolicyTest.java`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/docs/architecture.md`
- `macula-cloud-iam/docs/configuration.md`
- `macula-cloud-iam/docs/third-party-integration.md`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginPolicyFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/inapp/InAppAuthorizationRequestFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/inapp/InAppLoginPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/AbstractThirdPartyAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/BrowserLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ProviderFailureDiagnostics.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2PersistenceConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2ProtocolExtensionConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2TokenConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/ProtocolOAuth2Configuration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysApplicationProtocolConfigMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIamApplicationIdentityProviderMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIamApplicationMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIamApplicationProtocolConfigMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIdentityProviderMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIdentityProviderUserBindingMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysOAuth2ClientExternalProviderMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysOAuth2ClientMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysRegistrationIdentityBindingMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysApplicationProtocolConfig.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIamApplication.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIamApplicationIdentityProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIamApplicationProtocolConfig.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProviderUserBinding.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2ClientExternalProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysRegistrationIdentityBinding.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ApplicationProtocolResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolValidator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolCheckingTokenGenerator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ResourceAuthorizationPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ResourceTokenIntrospectionAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/binding/ExternalIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistrationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistrationServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2LoginApplicationResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/ClientAuthenticationExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2CorsConfigurationSource.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2UserGrantTokenIssuer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/ClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/IamApplicationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/IamApplicationServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/IamApplicationSnapshot.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/application/LoginApplicationResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/client/SysOAuth2ClientService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/client/impl/SysOAuth2ClientServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/IdentityProviderUserBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/resources/application.yml`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginPolicyFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/inapp/InAppAuthorizationRequestFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/inapp/InAppLoginPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/BrowserLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ProviderFailureDiagnosticsTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/verification/VerificationCodeAuthenticationContractTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamLoggingConfigurationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/MfaProtocolIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedEmailLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedSmsLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/InAppLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ApplicationProtocolResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ResourceAuthorizationIntegrationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/binding/ExternalIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/OAuth2ClientRegistrationServiceImplTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2CorsConfigurationSourceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/application/IamApplicationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/application/IamApplicationTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/client/impl/SysOAuth2ClientServiceImplTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentityProviderLockedReadIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentityProviderUserBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-system/README.md`
- `macula-cloud-system/src/main/java/db/migration/V27__iam_application_model.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/IamApplicationController.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/controller/OAuth2ClientConfigController.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IamApplicationDraftForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/IamApplicationForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientDraftForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/form/OAuth2ClientForm.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysApplicationProtocolConfigMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysIamApplicationIdentityProviderMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysIamApplicationMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysIamApplicationProtocolConfigMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysOAuth2ClientConfigMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysOAuth2ClientExternalProviderMapper.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysApplicationProtocolConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIamApplication.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIamApplicationIdentityProvider.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIamApplicationProtocolConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientExternalProvider.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/IamApplicationCatalogService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/IamApplicationService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2ClientConfigService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/OAuth2ClientConfigurationCatalogService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationPolicyValidator.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationProtocolService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IamApplicationWireAdapter.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientCredentialService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2IssuerMetadataServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningService.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/IamApplicationVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientConfigurationCatalogVO.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/vo/OAuth2ClientVO.java`
- `macula-cloud-system/src/main/resources/application.yml`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/config/SystemLoggingConfigurationTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/AdminSettingsAuthorizationTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/IamApplicationControllerTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/InterfaceDateTimeContractTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/controller/OAuth2ClientConfigControllerTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/AdminClientProvisioningIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ApplicationProtocolConfigurationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IamApplicationDraftMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IamApplicationModelConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IamApplicationModelMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IdentitySourceCapabilitiesConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientApplicationConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/OAuth2ClientProtocolsDraftMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/ResourceClientConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SingleProtocolResourceMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/WebLoginModeConcurrencyIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/WebLoginModeMigrationIT.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/AdminClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationProtocolServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IamApplicationWireAdapterTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/PlatformOAuthClientProvisioningServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ProtocolConfigurationTestFixtures.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/vo/SettingsModelJsonTest.java`

- `macula-cloud-system/src/main/java/dev/macula/cloud/system/config/protocol/IamApplicationProtocolConfiguration.java`

- `macula-cloud-system/src/main/java/dev/macula/cloud/system/mapper/SysIdentityProviderMapper.java`

## Order of work

1. 固定基线和兼容边界。保留所有已有未提交工作；根据本清单记录增量before及范围外SHA256。先新增/更新契约测试，保护HTTP路径/JSON键、client_id、凭据编码原值、现有Cookie/Session及授权序列化字段/UID/白名单。迁移和新读取可用前不替换运行容器。Plan review后交工程师确认，获得Plan批准才实施后续步骤。

2. 建立中立模型与单协议策略。应用类型/用途/状态/网页登录模式/托管登录方式改中立枚举名，序列化值不变。核心新增ApplicationProtocolDefinition<T>和显式IamApplicationProtocolRegistry，仅注册OAuth2/OIDC已实现策略；Definition负责有界解析、校验、凭据需求和协议元数据，通用Registry不出现instanceof OAuth2Settings。OAuth2专属授予、Scope及客户端认证规则留在OAuth2ProtocolDefinition/OAuth2ProtocolConfiguration。

   保留ApplicationProtocolConfiguration、OAuth2ProtocolSettings和ApplicationProtocolRegistry的v1协议契约作为HTTP/历史V18引用的薄边界，SCHEMA_VERSION=1及历史行为不变；不在旧Settings追加默认序列化字段。新存储使用版本2的OAuth2ApplicationProtocolSettings，组合旧OAuth2设置与externalCodeTypes，避免历史迁移因共享类型变更而改变输出。新IamApplicationProtocolRegistration承载单个协议登记的非秘密数据，Secret仅存受保护持久化字段，不进入普通DTO。

3. 新表结构与迁移V27。以自包含Java Flyway迁移通过JDBC和固定V26格式映射，不调用会随未来协议扩展变化的运行注册表；不改V1—V26，不新增密钥或重编码凭据。

   - sys_iam_application：保留id、purpose、managed_key、authorization_version、resource_permissions、application_type、web_login_mode、miniapp_platform、hosted_login_methods、user_type、tenant_id、configuration_status、审计字段；增加application_code/application_name（从旧client_id/client_name原值迁移）、application_code_issued_at（原client_id_issued_at逐值保留）和可空direct_login_capability。不保留OAuth2 grant/redirect/Scope/token/Secret列，不保留protocol_mode/enabled_protocols/default_protocol和浏览器来源JSON/允许厂商CSV的持久化副本。
   - sys_iam_application_protocol_config：保留协议行id及审计信息，application_id UNIQUE，protocol、schema_version、enabled、configuration；新增protocol_identifier、identifier_issued_at、credential_hash、credential_expires_at。现有公开client_id及其issuedAt从旧主表原值迁入；credential_hash保存原编码字符串，不再encode。configuration只包含类型化非秘密策略，credential_hash不进入普通JSON响应/日志。草稿可无记录；迁移时允许下述schema_version=0的不可执行未分配槽保留旧凭据。
   - 标识唯一性：protocol_identifier采用有界二进制比较支持未来协议；另用适用于OIDC/OAUTH2_1及schema_version=0未分配旧槽的生成列oauth2_identifier（VARCHAR100、沿用旧utf8mb4_unicode_ci）及唯一索引，保留OAuth家族跨两类型的原有唯一/查询语义；生成列不作为第二个写入权威。未来CAS service匹配保留在其Settings，不当成OAuth2 client_id。protocol_identifier的具体长度及索引字节预算在迁移测试中验证（建议VARCHAR512）。
   - sys_iam_application_identity_provider：application_id、login_capability、identity_provider_id，UNIQUE(application_id,login_capability)；沿用已有login capability稳定值。非空browser_capability_bindings为当前权威，旧provider绑定只能在当前模式/来源能唯一投影时转换；external-provider的client_id经原主表唯一解析到id，code_type按冻结映射转capability；校验来源类型/凭据类型/能力/流，拒绝冲突、孤儿或未知映射，不按厂商择任意来源。source凭据与版本不变。外部Code许可进入OAuth2配置，关联只管固定来源。
   - sys_identity_provider_user_binding：由新注册绑定表改名并将registration_id改为identity_provider_id；保持id、user_id、user_type、identity_kind/value、二进制比较、唯一约束和全部行不变。旧sys_user_identity_binding及其1条历史记录不动。

   在DDL前扫描全部原始数据并校验：最多一份协议、活动协议与状态一致、32KiB/已知字段/结构版本、凭据可无损归位、来源和所有外键映射唯一。未选协议草稿不自动补OIDC：已知V26格式的旧凭据/策略按下述不可执行槽无损保留；未知字段、无法无损归位的策略或来源歧义则报有界错误码并停止，不能丢弃配置。若无协议且无残留协议凭据，应用code及application_code_issued_at仍原值保留；旧VO的clientId/clientIdIssuedAt从中立字段投影，后续选择OAuth2时协议identifier及identifier_issued_at继续使用原值。

   MySQL DDL非事务：使用版本化staging表构建新结构、复制并校验语义投影的数量/摘要，验证通过后原子RENAME发布四张新表；原表只在新表完整验证后清理，不长期双写。发布前失败保留原表；发布后/清理阶段失败进入明确的恢复状态并停止，不自动repair、不猜测已完成。日志只含阶段/有界问题码/数量，不输出凭据、身份值或SQL参数；SQL异常对外只保留受控阶段、SQLState/数字码，不透传可能带行值的message/cause。测试注入阶段失败并验证无业务行丢失。真实升级需外部备份和匹配System/IAM版本协同切换，不承诺滚动兼容旧镜像。

3a. RESOURCE_SERVER与普通应用的协议语义不同，明确作为现有OAuth2管理调用身份迁移，不按“无协议脏数据”拒绝，也不开放业务登录。其原purpose、resource_permissions、client_id、issuedAt、认证方式、Secret/哈希或JWKS认证参数完整保留。

   内部为purpose=RESOURCE_SERVER建立唯一protocol=OAUTH2_1的管理认证登记（同一协议配置表，不新增表或可选协议类型），configuration中仅保留当前管理认证字段，credential_hash/有效期按原值归位；该内部登记不是“为未选协议应用猜填默认协议”。旧HTTP/页面继续protocol=null、settings采用现有资源身份视图，不显示业务协议卡片。OAuth2ProtocolDefinition必须按purpose校验：管理身份不允许authorization_code/client_credentials/external_code/refresh或业务Scope/回调，RegisteredClient投影仅用于当前许可的内省等管理认证，普通Token发行、托管登录及SSO一律保持拒绝；不因内部OAUTH2_1标识扩大权利。

   迁移专门覆盖有Secret和无Secret/JWKS两种既有资源身份，内部登记前后管理认证等价、普通授权仍拒绝；受管Gateway来源也纳入检查。普通未选协议应用的可解释旧凭据由3b保留；仅不可解释或冲突的策略才触发歧义停止。配置查找/Secret重置按purpose保持原接口和授权；RegisteredClient保留现有OAuth2ResourcePolicy.INTROSPECTION_CAPABILITY内部哨兵及EFFECTIVE_PROTOCOL=RESOURCE_SERVER，它们不成为可请求的业务grant；内部表的OAUTH2_1登记不得替换这些运行边界。不使用普通SERVICE应用默认值生成client_credentials。今后SAML/CAS的管理身份另行实现，不推导现在就支持。

3b. 历史基线及未选协议草稿可有既有OAuth2凭据，不能让全新V1→V27必然失败，也不能猜填协议或丢弃旧值。应用code/签发时间先按上文保留；只有存在旧凭据或需保留的V26协议影子数据时，新增唯一的未分配登记槽：protocol=NULL、schema_version=0、enabled=false，protocol_identifier/issuedAt与credential_hash/有效期保存原值，configuration保存严格白名单的V26非秘密影子快照。普通草稿没有这些旧数据则仍可完全没有协议行。

   schema_version=0不是新协议、不能进入可启用目录、不能创建RegisteredClient或通过登录/Token/管理认证。通用读取遇到该槽只投影“未选协议”，不调用运行Definition解析或把快照当可执行Settings；HTTP的protocol/settings维持null，clientId/签发时间及HAS_SECRET保持原视图。未来用户明确选择OAuth2后，才用已实现Definition校验并替换为v2配置，按现有规则保留/清理Secret；不能自动激活快照或放宽DRAFT启用规则。保留快照只是迁移保真，不是第二份运行权威。新类型对象必须拒绝enabled且protocol为空。

   保留旧OAuth2标识唯一性时，生成oauth2_identifier也覆盖protocol=NULL/schema_version=0的旧槽，避免已分配与未分配旧标识冲突。普通公共应用code继续有原大小写语义的唯一约束。对未分配槽加入凭据/时间/策略保真、不能认证/启用、后续明确选择协议成功与失败回滚的用例。

3c. 历史测试与当前运行时测试分开。WebLoginModeMigrationIT固定V25→V26，SQL旧表名不动，只调整改名枚举导入。SingleProtocolResourceMigrationIT的“全部历史”实际断言V19：把未指定target的路径固定为19，保留旧DDL/数据断言，V1→当前完整迁移由新IamApplicationModelMigrationIT覆盖。OAuth2ClientProtocolsDraftMigrationIT既测V15→V16又调用当前服务：重命名IamApplicationDraftMigrationIT；先固定到V16验证原历史不推断协议的断言，再独立升级V27并用新表和新服务验证同一草稿合同，不让旧SQL运行在已改名的表上。其余已固定版本的历史迁移SQL保持不变。

   V27须枚举V26真实列并显式处理每列；未列入当前实体的旧列（如introspection_resources）也不能无审计丢弃。只有证明与当前权威策略等价/为空才移除影子数据，否则保存为不可执行迁移快照或报冲突停止；不能扩大资源权限。新迁移不调用会变化的v1/v2运行注册表。

4. System聚合写入。实体/Mapper只映射上述物理模型。IamApplicationServiceImpl负责事务编排，IamApplicationPolicyValidator负责公共登录/模式/用途规则，IamApplicationProtocolService负责单协议及Definition分派；OAuth2ClientCredentialService负责现有OAuth2 Secret编码/重置与固定协议能力检查，不能让未来SAML/CAS误走Secret重置。主应用行锁和ETag/CAS覆盖协议、凭据、来源关系。创建/保存/删除/受管Admin和Gateway配置同步使用新模型；IdentityProviderService检查新关系表引用，不能继续扫旧JSON。

   IamApplicationWireAdapter集中处理旧HTTP Form/VO：路径、JSON键和错误状态保持，旧clientName/clientId/settings、enabledProtocols/defaultProtocol等只作为边界投影，不落为重复列；有冲突输入拒绝，不靠任意优先级吞掉。credential_hash字段从实体toString和普通DTO序列化中排除；普通详情永不返回credential_hash；HAS_SECRET仍按现有布尔提示。仅保留现有显式Secret创建/旋转接口的一次性明文返回和UI清理行为，不把它扩散为普通详情字段。受管初始化重试不轮换凭据、不重写用户配置。

5. IAM读取与协议发布分离。新增中立IamApplication实体、Snapshot及Service，按内部id在RR事务中读取单协议和来源关系；登录组件只使用公共应用策略。ClientAccountDetailsService补内部application_id的账号加载入口，旧clientId入口仅为现有OAuth2上下文兼容适配，不让未来协议借用OAuth2 client_id；UserAuthInfoService的租户/用户类型合同不变。LoginApplicationResolver为公共登录端口，OAuth2LoginApplicationResolver负责当前受控SavedRequest/client_id解析到application_id及原协议校验，不能信任请求自报id。未来SAML/CAS有完整实现后提供自己的解析/发布适配器，本次不加空Bean或可用枚举。

   旧SysOAuth2Client移为protocol/oauth2/client/OAuth2ClientRegistration，成为无TableName的协议投影；原ClientService移为OAuth2ClientRegistrationService，只在OAuth2层组合凭据、Settings和来源投影，RegisteredClientRepository继续使用标准框架类型。公共绑定服务改IdentityProviderUserBindingService，通过中立应用读/锁定表和具体身份源进行原验证；来源selectByIdForUpdate保持刚修复的显式查询。签名校验、state/nonce/PKCE、版本、MFA、限流、重放、目录准入和绑定证明均不放宽。

   ThirdPartyLoginContext/VerifiedExternalIdentity、Session/Token及授权存储的既有clientId/registrationId字段、类名、UID、白名单不因数据库改名变更；当前OAuth流程通过服务器适配器映射到内部应用id，不能把SAML entityId塞进旧OAuth clientId。RegisteredClient.id与client_id均保留原值，不能把RegisteredClient.id改成内部application_id而破坏已有OAuth2Authorization.registeredClientId关联；EFFECTIVE_PROTOCOL等既有协议元数据键/值也保持。配置缓存和RegisteredClient/Origin投影采用新配置版本命名空间（例如v7-iam-application），两端提交后失效一致，旧授权Token缓存不清空或重写。

6. Admin保留现有路由/菜单component路径和HTTP契约。协议目录仍只显示OAuth2/OIDC；单选更换与现有确认行为、未选协议草稿、创建后固定浏览器/端内模式均不变。通用applicationProtocolRegistry只调度已实现的协议策略，把OAuth2默认值/归一化移到oauth2ProtocolConfiguration.js；UI不显示Secret/内部凭据。补充契约回归而非顺手改变页面流程。文档按IAM模型介绍，说明三表职责、旧表处置和升级恢复，不写操作流水账。

6a. 凭据日志保护随新Mapper生效。IAM application.yml目前StdOutImpl会直接输出SQL参数/行，切换为Slf4jImpl并将iam.mapper包设INFO；System同步将system.mapper包设INFO，替代只覆盖旧SysOAuth2ClientConfigMapper类名的配置。只调整日志配置，不修改端口、连接、密码或其他运行参数。已有安全分类日志仍按WARN输出。新增IamLoggingConfigurationTest并更新SystemLoggingConfigurationTest，验证各profile默认值没有StdOut/凭据Mapper DEBUG，再在真实读取/写入测试捕获日志确认合成凭据未输出；显式一次性Secret响应仍只在授权接口返回。

7. 正式验证后交付代码与证据。先相关API/System/IAM单测，再真实V26→V27及全新升级、新Mapper/加密绑定/并发/缓存/Admin初始化集成，最后全仓verify、Admin完整单元/build/Cypress、Checkstyle/无fix ESLint/diff及范围外摘要。记录真实失败、默认skip及未做的平台验证。无授权不升级运行库、不部署、不提交/推送。

## Risks

- 原型已包含大量未提交认证/邮件/绑定/导入整理；只按增量基线实施，不回滚或覆盖已有内容。
- 历史V18直接引用v1 Registry/Settings，不能通过修改公共常量或加默认序列化字段隐式改变历史迁移；专门重跑target18/19/26用例，旧历史SQL名字保持。
- MySQL DDL自动提交，跨版本切换需维护窗口与外部备份；迁移失败不允许仅repair后假装完成。迁移stage只能操作本版本拥有的对象，不能删除未知已存在的新模型表。
- 已知未分配旧凭据按3b无损保留且永不激活；来源映射歧义、无法解释的旧策略或多份配置是升级前置失败；本轮只交付预检，不自行决定如何清洗真实数据。旧绑定表保留。
- 新凭据列仍是原PasswordEncoder编码结果，不能进行JSON普通投影或新增明文Secret日志；不因改名轮换任何值。
- 中立应用对象和OAuth2协议投影不能反向混用；需要保留的v1类型只用于历史/HTTP边界，不得重新成为运行持久化模型。
- 所有对外协议、Admin路由及Token/Session格式保持；没有实现的SAML/CAS不得进入可启用目录。实际平台/业务库不在本轮授权内。

## Proof

- API：协议注册表使用独立测试Definition证明分派不依赖OAuth2类型；未知/未实现协议拒绝，真实产品目录仅两种，单协议/空草稿、DTO/标识/Secret隐藏和v1兼容契约有断言。
- 迁移：新空库完整V1→27、V26→27、历史目标版本保持；包括RESOURCE_SERVER管理身份内部迁移/无业务协议视图/凭据保持且业务授予拒绝，以及空协议草稿代码与签发时间保持、未分配旧凭据/影子快照保真且认证拒绝；覆盖主键/编码凭据/绑定摘要、完整路由等价、OIDC/OAuth2.1跨类型client_id唯一及原排序/大小写约束、无协议草稿、冲突/孤儿/超限/多协议/残留凭据拒绝，以及发布前后故障恢复状态；不打印原数据。
- 后端：真实MySQL+Crypto绑定锁定读回归、源码命名/Mapper映射、允许与拒绝路径、来源/Client版本漂移、并发ETag/CAS单胜者、失败/回滚无缓存驱逐、两个实例提交后可见失效、受管Admin/Gateway初始化不改密钥。
- 安全持久化：保留授权与Session序列化类型/字段/UID/白名单；使用既有持久化回读/旧授权Token路径回归证明改实体名未影响，配置缓存版本隔离有效。
- 浏览器：Admin单协议、空协议创建、两登录模式只读、EMAIL/SMS/第三方来源选择及移除、保存失败/409、Token/Secret清理、手机/桌面尺寸；完整测试输出写verification，不用模拟冒充真实平台。
- 代码质量：Checkstyle、无fix ESLint、git diff --check、无旧表运行读写（历史迁移/历史测试除外）、无全限定类名散落与无package-info；测试结束前范围外SHA256必须保持。保护配置未改，无相关配置eval。

Plan Round1修订：已处理资源身份管理认证登记和空协议草稿签发时间两条发现；内部RESOURCE_SERVER适配不改变公开协议选择/授权能力。工程师尚未批准Plan。

自查细化：已补齐历史测试的当前类引用/未固定目标版本问题，以及新空库基线中未分配旧凭据的保真方案；这些细化仍不激活任何未选协议。待最终增量Plan审查及工程师批准。

## Plan审查与清单闭包记录

独立Plan Round1/3的资源身份凭据归属和草稿签发时间两项发现已解决；Round2/3和Round3/3增量均NO_FINDINGS，重复抑制均0。未执行代码review，未批准人工门。

末次审查后按全部重命名符号做只读引用闭包检查，补齐SettingsModelJsonTest、InterfaceDateTimeContractTest、绑定诊断工具/测试及两处绑定测试等确切调用文件，并列入现有公共账号端口和中立测试夹具；这仅补齐已审架构的编译/契约调用清单，不新增行为或第四轮审查。当前完整文件清单见Files，各重命名前后路径均显式列出。等待工程师批准后才写源码。

## 工程师批准

2026-11-12：工程师以明确的“YES”批准完整Plan及审查结果，开始Phase B。此前待批准的文字为审查历史；不扩大到运行库升级、镜像或Git操作。

## 实施顺序调整

在持久化集成前提前落实步骤6a的Mapper日志保护：现有IAM StdOutImpl绕开日志级别，实际迁移/Mapper验证前先消除秘密输出风险。只改已列明的日志配置和测试，不改连接或认证规则，不影响步骤3—6的依赖顺序。真实Mapper捕获证据仍待集成验证，不以配置测试替代。

步骤3后先实现两端新表实体/Mapper及IAM中立读取端口，以便迁移后的实际Mapper回归和非秘密快照验证；这是步骤4/5的公共读取基础，尚未切换现有HTTP写入或OAuth发布调用链。System写入切换、旧类型移除及绑定链调整仍按原合同完成，不以新增读模型宣称整体切换可部署。迁移额外保留已消耗的AUTO_INCREMENT边界，避免重用历史删除行标识，不改变已有主键。

实施清单补充：新增System config/protocol/IamApplicationProtocolConfiguration.java作为协议注册表Bean的装配点。原清单遗漏依赖注入入口，不能将OAuth实现选择写进公共应用服务或在@Service里混入@Bean；此补充不改变策略/协议目录或授权行为。

实施补充：为完成应用关联与身份源删除并发不产生孤儿记录的既定证明，System SysIdentityProviderMapper新增显式FOR UPDATE读取，保存按来源ID排序锁定，删除先锁来源再当前读关联。原文件未有范围外改动，已另存原文基线；不修改身份源凭据或绑定合同。

实现发现并细化：已有创建接口允许空的RESOURCE_SERVER草稿，它没有认证方式或凭据。此类草稿仍按Requirements 1允许无协议行；只有已配置的资源管理身份生成内部OAUTH2_1登记，不能因空草稿猜造认证策略。V27对应预检与新建API一致，启用仍拒绝缺失认证/凭据，增加回归。

## 托管登录调用链范围补充

实现中发现Context由OAuth投影改为中立快照时，直接消费它的账号适配与邮箱发码控制器必须同步调整访问器。补充精确路径如下，行为与HTTP键不变；验证沿用托管账号/邮箱登录回归。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/userdetails/HostedUserDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/EmailCodeController.java`

## 管理端注册表清单补充

步骤6已经明确要求通用注册表调度专用策略，但精确清单漏列现有调度文件。补充 `macula-cloud-admin/src/utils/applicationProtocolRegistry.js`；保留此前修改的原文快照，仅提取其OAuth默认值与归一化实现，沿用既有API，不改变页面交互。

## Phase B退出确认

2026-11-12：工程师对实施退出问题明确答复YES，进入sdlc-test。此确认不包含业务库迁移、部署或Git操作。Stage 4允许在既定范围内修正测试适配并重新验证，不自批后续PR。

## 2026-11-12工程师确认的V28退役补充

确认来源：上一轮已明确建议核对并归档历史1条记录后新增V28删除旧表、保留历史迁移，工程师回复“建议删除”。按此范围实施既有方案的退役补充，不扩展到当前身份绑定、凭据、其他表或Git操作。

精确增量：
- `macula-cloud-system/src/main/resources/db/migration/V28__retire_legacy_identity_binding.sql`：只DROP已退役旧表，允许表已被归档移走；不改V1—V27。
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/LegacyIdentityBindingRetirementIT.java`：隔离MySQL证明全新库、V27非空升级、旧表已不存在及重复运行；当前绑定/应用/协议/来源/用户逐值保持。V27历史迁移测试继续固定27。
- `macula-cloud-system/README.md`、`macula-cloud-iam/README.md`、`macula-cloud-iam/docs/architecture.md`、`macula-cloud-iam/docs/configuration.md`：说明当前唯一绑定表、旧表退役、外部归档与恢复要求。
- 本目录spec/plan/build-notes/verification同步确认与证据。

顺序：核对引用和数据库依赖 → 记录确认及范围 → 新增最小迁移和真实集成测试 → 相关及全仓verify/规范 → 私有目录归档运行库旧表并验证恢复 → 以已验证V28更新本地System并核对当前数据与入口。归档内容不进入仓库，不打印身份明文，不修改密码或缓存。

风险：MySQL DROP不能靠事务回滚；外部备份先于删除并演练恢复，业务库执行前再次核对引用和记录指纹。其他部署在自动执行V28前也必须备份存量记录；不清洗、合并或猜测真实来源。未验证或归档不匹配时停止。

Proof补充：4类V28隔离升级用例；原V27回归固定历史目标；全仓verify；归档恢复的记录指纹相同；运行库V28旧表不存在、当前数据指纹保持、System健康及登录入口不变。
