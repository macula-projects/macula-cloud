# Plan: IAM 架构精简与自定义授权收敛 (from spec.md 2026-11-11)

Status: accepted。Plan review: completed（2 rounds，最终 NO_FINDINGS；工程师已批准）。来源：已接受的 [spec.md](spec.md)。2026-11-11 用户以 YES 明确批准，进入 Phase B；未授权提交、推送或部署。
采用现有 Java 17、Spring Security/SAS 扩展接口、JUnit/Mockito、Vue/Vitest/Cypress；不新增或升级库。文档作者仍待确认，不因此改变已接受范围。
未找到项目 CLAUDE.md，实施遵循 Cloud AGENTS.md 及相关规则。REVIEW.md 未指定 Stage 3 reviewer；可用技能/工具中未发现 codex-plan-review，按 sdlc-build 使用 sdlc-reviewer 子代理执行 Plan pass；最多三轮，逐轮记录 review-findings.md。

## Files that change

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/SysUserDetails.java`

- `macula-cloud-iam/docs/architecture.md`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/authentication/DefaultSecurityConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/authentication/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/JwtConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2PersistenceConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2ProtocolExtensionConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/OAuth2TokenConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/ProtocolOAuth2Configuration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/oauth2/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2CorsConfigurationSource.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/oauth2/JwtConfigurationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2CorsConfigurationSourceTest.java`

- `macula-cloud-iam/docs/architecture.svg`
- `macula-cloud-iam/docs/architecture.png`
- `macula-cloud-iam/docs/configuration.md`
- `.sdlc/iam-architecture-simplification/compatibility-notes.md`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/directory/IdentityDirectoryService.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/SmsServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/userdetails/HostedUserDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/userdetails/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ProfileController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ResourceTokenIntrospectionAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/binding/ExternalIdentityBindingController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/binding/ExternalIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2EndpointUtils.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2ExtensionErrorCodes.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalBindingRequiredException.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationConsentService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/persistence/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/profile/ProfileController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/profile/ProfileService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/AccessTokenClaimsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/DefaultAccessTokenClaimsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/token/claims/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/ClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/ClientAwareAccountResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/SysRoleService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/SysUserService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/UserAuthInfoService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/impl/SysRoleServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/impl/SysUserServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/account/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/client/SysOAuth2ClientService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/client/impl/SysOAuth2ClientServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/client/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/IdentityBindingMatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/IdentityProviderConfigService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationConsentService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/profile/ProfileService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysOAuth2ClientService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysRoleService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/SysUserService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/UserAuthInfoService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SmsServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysRoleServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/support/impl/SysUserServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/ClientAwareAccountResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/OAuthClientUserDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/SysUserDetailsServiceImpl.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/userdetails/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/utils/OAuth2EndpointUtils.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/hosted/HostedLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ProfileControllerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/binding/ExternalIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2TokenOriginFilterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaOAuth2AuthorizationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/persistence/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/profile/ProfileControllerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/ClientAwareAccountResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/account/DefaultClientAccountDetailsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/client/impl/SysOAuth2ClientServiceImplTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentityBindingMatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentityProviderConfigServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentitySourceBindingPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/userdetails/SmsServiceImplTest.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/aliyun/AliyunMobileExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/inapp/InAppLoginPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/HttpSessionThirdPartyLoginStateRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityBindingMatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/PendingThirdPartyBindingRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderApplicationTokenCache.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderCacheCipher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryRefreshService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryResult.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderIntegrationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderMemberSnapshotStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/README.md`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginStateRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/VerifiedExternalIdentity.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ExternalIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/IdentityBindingMatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/IdentitySourceBindingPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/AbstractThirdPartyAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/BrowserLoginClientPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyIdentityResolutionService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyLoginStateRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/browser/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderApplicationTokenCache.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderCacheCipher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/cache/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryRefreshService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryResult.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderMemberSnapshotStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/IdentityProviderConfigService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ProviderIntegrationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ThirdPartyProviderAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ThirdPartyProviderAdapterRegistry.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifier.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/MfaExtensionConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ExternalIdentityBindingController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ThirdPartyBindingController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandler.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeRateLimiter.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkExternalCodeVerifierTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkLoginModeTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuExternalCodeVerifierTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuLoginModeTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/inapp/InAppLoginPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/mfa/MfaCompletionServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityBindingMatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderApplicationTokenCacheIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ProviderDirectoryRefreshServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ExternalIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/IdentityBindingMatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/IdentitySourceBindingPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/RegistrationIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/BrowserLoginClientPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyAuthenticationFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/browser/ThirdPartyIdentityResolutionServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/cache/ProviderApplicationTokenCacheIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryRefreshServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/provider/IdentityProviderConfigServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ThirdPartyProviderAdapterRegistryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatLoginModeTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifierTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeAccountResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/ExternalCodeRateLimiterTest.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/README.md`

以下均为仓库根目录下的完整相对路径，每行一个；不使用目录通配授权。标为修改的文件仅在实际引用需要时调整，不机械修改无关代码。


**生产源码删除**

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/password/OAuth2ResourceOwnerPasswordAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/password/OAuth2ResourceOwnerPasswordAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/password/OAuth2ResourceOwnerPasswordAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/password/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/sms/OAuth2ResourceOwnerSmsAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/sms/OAuth2ResourceOwnerSmsAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/sms/OAuth2ResourceOwnerSmsAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/sms/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/email/OAuth2ResourceOwnerEmailAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/email/OAuth2ResourceOwnerEmailAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/email/OAuth2ResourceOwnerEmailAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/email/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2ResourceOwnerBaseAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2ResourceOwnerBaseAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/OAuth2ResourceOwnerBaseAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/base/package-info.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/CompatibilityRefreshTokenAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/email/EmailService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/challenge/LoginCaptchaProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/challenge/LoginCaptchaService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/challenge/RedisLoginCaptchaService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/EmailVerificationCodeStore.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/EmailVerificationProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/message/ProtocolLoginCodeDispatcher.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/ProtocolVerificationCodeController.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/form/ProtocolVerificationCodeForm.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/utils/ScopeException.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyUserDetailsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysUserIdentityBindingMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysUserIdentityBinding.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolCasConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolSaml2Configuration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2LoginGrantTypes.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatLoginFilterConfigurer.java`

**生产源码新增**

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2GrantPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/RetiredLoginEndpointFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/jackson/LegacyEmailPrincipalReader.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyProviderAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyProviderAdapterRegistry.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginFilterConfigurer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubProviderAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomProviderAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuProviderAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkProviderAdapter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatProviderAdapter.java`

**生产源码修改**

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/protocol/OAuth2ProtocolConfiguration.java`
- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationType.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java`
- `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolOAuth2Configuration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSource.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ApplicationProtocolResolver.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepository.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicy.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolCheckingTokenGenerator.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolBoundAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationToken.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingPolicyService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationFilter.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/utils/OAuth2ErrorCodesExpand.java`
- `macula-cloud-iam/src/main/resources/application.yml`

**测试删除、迁移与新增**

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/email/OAuth2ResourceOwnerEmailAuthenticationConverterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/challenge/RedisLoginCaptchaServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/EmailVerificationCodeStoreTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/message/ProtocolLoginCodeDispatcherTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyUserDetailsServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubProviderAdapterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomProviderAdapterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuProviderAdapterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkProviderAdapterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatProviderAdapterTest.java`
原五家 AuthenticationProviderTest 迁移为对应 ProviderAdapterTest，保留字段、失败与日志脱敏断言。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyProviderAdapterRegistryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginFilterConfigurerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/BindingTestTransactions.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/RetiredLoginEndpointFilterTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/security/OAuth2GrantPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/jackson/LegacyEmailPrincipalReaderTest.java`
- `macula-cloud-iam/src/test/resources/authorization/legacy-email-jackson2-attributes.json`
- `macula-cloud-iam/src/test/resources/authorization/legacy-email-jackson3-attributes.json`

**现有测试修改**

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/ProtocolLoginCorsConfigurationSourceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/MfaProtocolIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/HostedLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/InAppLoginFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/controller/ThirdPartyBindingControllerTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ExternalIdentityBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingPolicyServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyBindingServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyIdentityResolutionServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentitySourceBindingIT.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ApplicationProtocolResolverTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/OAuth2ClientProtocolPolicyTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolAwareRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolBoundAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ProtocolTestFixtures.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/ResourceAuthorizationIntegrationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationConverterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/client/PublicTokenClientAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationConverterTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/grant/externalcode/OAuth2ExternalCodeAuthenticationProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaOAuth2AuthorizationServiceTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/oauth2/MaculaRegisteredClientRepositoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/support/impl/SysOAuth2ClientServiceImplTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/userdetails/ClientAwareAccountResolverTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/protocol/ApplicationProtocolRegistryTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/OAuth2ClientApplicationTypeTest.java`
- `macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/security/OAuth2ApplicationLoginPolicyTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ApplicationProtocolConfigurationServiceTest.java`
- `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/ProtocolConfigurationTestFixtures.java`

**管理端与文档**

- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`
- `macula-cloud-admin/src/utils/applicationProtocolRegistry.js`
- `macula-cloud-admin/src/api/model/system/oauth2Client.js`
- `macula-cloud-admin/src/views/system/oauth2Client/components/OAuthProtocolSettings.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientGuide.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientProtocol.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/components/ClientLoginControl.vue`
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`
- `macula-cloud-admin/tests/unit/application-protocol-registry.test.js`
- `macula-cloud-admin/tests/unit/oauth-client-details.test.js`
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`
- `macula-cloud-admin/README.md`
- `macula-cloud-iam/README.md`
- `macula-cloud-iam/docs/third-party-integration.md`
- `macula-cloud-system/README.md`
- `macula-cloud-api/macula-cloud-system-api/README.md`
- `.sdlc/iam-architecture-simplification/assessment.md`
- `.sdlc/iam-architecture-simplification/plan.md`
- `.sdlc/iam-architecture-simplification/review-findings.md`
- `.sdlc/iam-architecture-simplification/build-notes.md`
- `.sdlc/iam-architecture-simplification/verification.md`
- `.sdlc/iam-architecture-simplification/architecture-target.svg`
- `.sdlc/iam-architecture-simplification/architecture-target.png`

删除旧 Mapper/Entity 前复核 Java/XML/装配；不删历史表/迁移。保留原路径 OAuth2UserGrantTokenIssuer、业务邮件/短信发送类与 spring-boot-starter-mail。历史迁移与厂商/目录/缓存测试纳入执行，不修改已发布 SQL。

**2026-11-11 用户追加的分层与配置精简范围**

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/SigningKeyProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/SigningKeyProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/KeyStoreSigningKeyProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/IamJwtDecoderFactory.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/jwt/PolicyClientAssertionDecoderFactory.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/jwt/keys/KeyStoreSigningKeyProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/jwt/IamJwtDecoderFactoryTest.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/ResourceAuthorizationPolicy.java`

- `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/security/OAuth2ResourcePolicy.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/JwtConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/OAuth2ConfigurerUtils.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/OAuth2TokenConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/OAuth2PersistenceConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/OAuth2ProtocolExtensionConfiguration.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/security/signing/SigningKeyProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/security/signing/KeyStoreSigningKeyProvider.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/security/signing/SigningKeyProperties.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/security/jwt/IamJwtDecoderFactory.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/security/jwt/PolicyClientAssertionDecoderFactory.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/token/AccessTokenClaimsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/token/DefaultAccessTokenClaimsService.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/CustomOidcTokenCustomizer.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/AuthorizationServerExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/ClientAuthenticationExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/AuthorizationEndpointExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/configuration/TokenEndpointExtension.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/protocol/oauth2/endpoint/OAuth2ProtocolAuthenticationEntryPoint.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/sms/UnavailableSmsService.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/JwtConfigurationTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/security/signing/KeyStoreSigningKeyProviderTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/security/jwt/IamJwtDecoderFactoryTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/protocol/oauth2/CustomOAuth2TokenCustomizerTest.java`

## Order of work

1. **锁定基线**：保留 accepted intent/spec 和干净源码基线；检查新改动、CLAUDE.md 是否出现、环境与工具版本。项目没有额外 reviewer 配置，不改 REVIEW.md。完成本计划的 Plan 审阅并等待工程师批准。
2. **先解开 external_code 继承依赖**：Converter 直接实现 AuthenticationConverter；Token 直接继承 OAuth2AuthorizationGrantAuthenticationToken，持有不可变 Scope 与专用 Code 字段。将仍需的参数提取放进唯一 Converter，保留单值、白名单、长度、nonce/上游 PKCE、Scope、已认证 Client、连接 IP 和不创建 Session 的约束。此变更会使 external_code 命中现有 ProtocolBoundAuthenticationProvider，补安全链证明，不绕过该包装器。
3. **收敛 grant 与兼容读取**：OAuth2GrantPolicy 定义支持集合和退役集合。保留历史 validate() 的迁移合同；validateRuntime() 仅为已知历史配置读取保留 sms/email，不放行未知值或其他不合法字段。新增严格写入校验入口用于 System 两条保存链；IAM 发新结果只接受支持集合。历史记录原 grant 保留，Access Token 仍执行过期/撤销/当前版本规则。删除 EmailAuthenticationToken 前，以修改前 Jackson 2/3 实际序列化器及合成用户构造旧授权 attributes 夹具，不把猜测 JSON 当证据。LegacyEmailPrincipalReader 只在授权 attributes 的 Principal 键识别固定 EmailAuthenticationToken type-id；按夹具的明确结构将已认证、凭据为空的旧载体转为现有支持的 UsernamePasswordAuthenticationToken 形状，再交给既有安全 ObjectMapper 恢复嵌套 principal/authorities/details。非该键、未知类型、未认证或携带凭据的记录不转换并失败关闭；不新增包级/任意类白名单、不放宽已有多态 validator、不做字符串全局替换。除内部主体载体类型外保留 subject、userId、authorities、details、Scope、grant 与 Token 元数据；不重签、不写回缓存。MaculaOAuth2AuthorizationService 仅新增 attributes 的有界兼容读取调用，metadata 仍走原 parseMap，CAS/格式/TTL 不变。若实际旧格式无法按此方式无损恢复，停止删除该类型并记录偏差，不丢字段或以新授权替代。框架刷新 Provider 调用前，ProtocolBoundAuthenticationProvider 在已认证 Client 内检查原授权，旧 sms/email/password 的 Refresh 返回 invalid_grant；不扩展 none 的刷新权限，不消费/改写该 Refresh。
4. **退出旧业务与路由**：先安装 RetiredLoginEndpointFilter，再删除三个旧 grant 族、无用途基类、验证码专属业务和注册。三个退役路径精确匹配 contextPath 后的路径（不扩成通配），包含 OPTIONS 在内固定返回 410/endpoint_retired JSON、无重定向；在对应链的 CorsFilter/CsrfFilter/认证处理之前终止，不读账号、Client、Session 或凭据，不成为默认全局 Servlet Filter。只放入 HttpSecurity 一次。其他端点继续原安全链与 CORS，删除它们对旧 Controller 常量和 X-OAuth-Client-Id 的专属依赖。
5. **绑定与测试依赖完整化**：ExternalIdentityBindingService 的 Token 证明只允许 authorization_code；保留 /login/bind/external 的密码/SMS/合格 Session 证明及 SDK 不建 SSO 的分支。IdentitySourceBindingPolicyService、ThirdPartyBindingService、ExternalIdentityBindingService 与 external_code Provider 仅保留完整生产构造器，必要依赖不可 null。测试使用 Mock 目录/限流和 BindingTestTransactions 的事务同步，不通过 null 避开路径；真实绑定 IT 使用真实事务管理器。保留票据提交后消费、失败计数、owner 释放和锁内当前读。
6. **收敛第三方编排**：五个厂商 Provider 转成薄 Adapter（稳定厂商键、授权 URL 构建、核验身份）；Registry 启动拒绝重复键，统一 Provider 执行 requireEnabledById → forCapability → Adapter.verify → 公共身份策略 → authenticated Token。未知厂商和能力直接拒绝，既有注册顺序不成为回退策略。Generic Configurer 装配薄路由 Filter，统一 Provider 在托管链只注册一次；五个 Filter 保留原 GET matcher/loginMode，URL 与会话续转合同不变。只移动编排，不改厂商 HTTP 请求、字段或敏感授权。
7. **精简遗留**：确认生产引用后删除旧加载服务、兼容刷新 Provider、空 CAS/SAML、旧 Java 映射及无效测试装配；不删表、不修改迁移、不移除仍被 LoginMailSender 使用的邮件依赖。OAuth2ErrorCodesExpand 只保留仍被 binding_required 使用的内容。新增/修改具名类型和方法补职责、参数/结果、异常与约束注释，不做无关格式化。
8. **同步管理端**：支持目录只列标准 grant 与 external_code；删除旧 grant 示例与依赖分支。旧值保留在草稿中、显示待整改及显式移除操作，阻止未整改保存或示例复制；模型 hydrate/协议规范化不得静默过滤旧值。保留公共/机密认证、固定来源、多能力、托管按钮与 Client CAS。对用户显式改变协议/应用类型引发的变更继续沿原确认流程，不在渲染/加载 watcher 中转换配置。
9. **验证与交付材料**：按 Proof 执行实际验证，保存非敏感证据与失败环境前提；改写当前使用说明，保留第三方逐接口资料，补退役和整改步骤，目标架构图按最终代码更新。逐项映射 spec R1—R8、报告真实净减量。记录偏差，Phase B 完成后请求实施确认，不自动进入正式测试签核、PR 或部署。

## Risks

- 规格已于 2026-11-11 获接受：不主动撤销旧 Access Token；旧扩展授权 Refresh 拒绝。历史 Client 盘点、退出日期与分批升级仍未确认，属于发布前置条件。本地实现不读取或修改业务数据库来假设已整改。
- 旧邮箱主体序列化是兼容边界：只有固定旧类型和真实夹具证明的形状可转换，未知身份/字段不得被误提升为受信主体。必须覆盖完整旧授权的恢复、内省、到期、撤销、版本变化与旧 Refresh 拒绝；不能只测序列化往返。
- 读取历史配置需要保留已知退役标识，但它们不得进入新的 grant 执行路径。不能简单缩小 validateRuntime() 白名单导致合法旧 Token 整批失效，也不能放宽整个解析器。System 新写入必须严格拒绝；已有多协议、版本、字段校验继续执行。历史 Flyway validate() 合同不变。
- Token 改为框架原生父类后会增加协议包装校验；必须证明正常 external_code 与 nonce/ID Token 路径仍通过，陈旧 Client 被拒绝。
- 退役过滤器若注册两次、安装过晚或路径过宽，会改变其他安全边界；通过装配和完整链测试排除。410 不提供旧跨域业务功能，也不回显 Origin/Client/请求内容。
- 去除 null 测试旁路可能暴露原来未执行的目录、票据、事务分支；不能削减断言、吞异常或默认 NoOp 来保持测试通过。
- 五个适配器保留真实协议差异，不能只按 providerType 忽略 capability/registration/version，不能改变 state、SSO 与 SDK 边界。
- 回滚先停止新配置写入；本轮无数据变更，但已整改 Client 的版本失效按原规则生效，旧 Refresh 拒绝不承诺跨版本自动恢复。发布/回滚需另外批准。
- 当前不授权任何 Git 提交；不修改或禁用 artifact gate。若后续实际写入被已提交计划条件拦截，记录具体阻塞后再请求必要方向，不自行提交或换路径绕过。
- 列出的文件是实施边界，不是必须逐个改动的配额。发现额外必要文件先记录精确路径与原因，若改变公共合同或范围先请求工程方向。

## Proof

| 规格 | 必须证明的覆盖 |
| --- | --- |
| R1/R2 | IamProtocolTest 与 public-client 测试证明 sms/email/password 拒绝且无验证码/HTTP/签发副作用；external_code Converter 的重复/未知/越界参数、不可变 Scope、连接 IP 保留；标准授权、PKCE/OIDC、服务身份、刷新、内省及撤销通过 |
| R3 | ExternalIdentityBindingServiceTest + ThirdPartyBindingControllerTest + IdentitySourceBindingIT 覆盖授权码证明、拒绝旧 grant/跨 Client、SDK ticket 网页密码/SMS/Session 及无 Token/SSO；LOGIN 与 IDENTITY_BINDING 短信用途不混用 |
| R4 | API/System 严格写校验、历史只读解析；使用真实旧 JSON 夹具（Jackson 2/3 合成数据）证明旧 email 主体恢复与内省/撤销，恶意 type-id/未认证/带凭据/错误位置拒绝；历史旧 grant Access Token 的活跃/到期/撤销/Client 版本变化与 invalid_grant Refresh，含混合授权配置；Admin 保留原值、显式移除、阻止保存/复制、服务端直接提交拒绝 |
| R5/R6 | 新 Provider/Registry/Adapter 测试覆盖全部五家、重复注册、未知键、停用来源、能力/版本错配；既有厂商 HTTP/日志测试保留。IamProtocolTest、MfaProtocolIT、第三方流程/目录/Replay/限流测试覆盖发布拒绝、防重放、SSO 与 state/多标签边界 |
| R7/R8 | 单元测试明确事务提交/回滚的回调及顺序，真实 MySQL/Redis IT 证明绑定并发/owner；不依赖 null 构造器；构建/装配证明移除类无残留引用；diff 与文档/图核对不包含 schema 或无关功能改动 |

执行顺序与命令（均为计划，尚未运行）：

1. `mvn -pl macula-cloud-iam -am test -Plocal`。
2. `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal`，通过后对跨服务改动运行 `mvn test -Plocal`。不以更换依赖版本掩盖缺失本地 Boot 构件。
3. `mvn -pl macula-cloud-iam -am verify -Plocal -Dit.test=MfaProtocolIT,ProviderApplicationTokenCacheIT,IdentitySourceBindingIT -Dfailsafe.failIfNoSpecifiedTests=false`。Provider 缓存 IT 使用隔离 Redis 进程；IdentitySourceBindingIT 仅在提供隔离 IAM_SOURCE_IT_URL/USER/PASSWORD/REDIS_PORT/RUN_ID 并设置 IAM_SOURCE_BINDING_IT=true 后执行。禁止指向业务库，不输出密码。环境缺失必须标为未验证，不能声称真实事务/并发绿色。
4. Admin：`npm run test:unit -- --run`、`npm run build`；使用现有 start-server-and-test 包装启动 preview，并以 `cypress run --e2e --spec cypress/e2e/oauth-client-configuration.cy.js,cypress/e2e/identity-provider-policy.cy.js` 执行目标回归，覆盖桌面及 390px。只在依赖缺失且需要时 npm ci，不改 lockfile。
5. 检查 Cloud `.config/checkstyle.xml` 与 header 的实际插件/现有脚本入口，对新增/修改 Java 执行对应检查；执行 `git diff --check`、移除符号引用检查、变更文件与本计划名单核对。资源配置移除后运行 IAM 目标 package；构建产物不提交。
6. 真实厂商/真机、运行 SLO、业务库升级和部署不在本地绿色证据中。verification.md 分列静态、单元/Mock、安全链、真实 Redis/MySQL、未执行平台验证，并记录实际命令、结果与跳过原因。

## Implementation deviations

- 2026-11-11：补充精确文件 macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js。实际回归发现该相邻测试仍期望旧 sms grant 可用；仅更新为已批准的退役拒绝合同，不扩展产品范围。
- 公共装配器内部采用单个组合Configurer委托各入口的框架原生生命周期，避免HttpSecurity按Configurer类型覆盖前面入口；已纳入完整安全链回归。
- 单独的ThirdPartyLoginFilterConfigurerTest由IamProtocolTest的全部原有厂商路径及代理/SSO/state回归替代，避免只影射装配实现；注册冲突仍由独立RegistryTest证明。

- 2026-11-11 用户明确指出接口抽象、层次与配置臃肿，并点名JwtConfiguration：本轮按该方向补充分层。SigningKeyProvider是密钥来源端口，KeyStore实现保留原local/docker回退与外部JKS合同；配置使用原键的类型化属性。JwtDecoder继续框架接口/RS256与原验证链。AccessTokenClaimsService共用于JWT/不透明Token，Customizer只适配框架上下文。AuthorizationServerExtension由ClientAuthentication/AuthorizationEndpoint/TokenEndpoint三个实现组成，主配置只装配安全链；持久化/Token Bean分别归属小配置。删除无引用OAuth2ConfigurerUtils，移出匿名UnavailableSmsService。不增加新协议、密钥来源或加密/授权算法，不改业务数据及缓存格式。
- 证明：新增密钥加载、本地/生产配置边界与替换接口的配置测试，JWT真实签验回归；原Oidc/opaque/私钥断言/绑定/协议安全链完整回归。若发现需改变密钥轮换或可信来源合同，先停止对应变化，不自行决定。

- 分层依赖细化：AccessTokenClaimsService 实现只调用 System API 的资源领域规则；共享 permissions 设置标识归到 OAuth2ResourcePolicy，避免应用服务反向依赖 protocol 适配层。值不变，原 protocol 常量保留兼容别名。

- 用户再次明确：登录认证配置与协议配置保持独立。JWT/密钥适配放在 protocol/oauth2/jwt，而不是泛化security包；DefaultSecurityConfiguration/登录Configurer只装配登录Provider与登录Filter。协议grant Provider仅在协议扩展内部创建和注册，不暴露为全局AuthenticationProvider Bean；登录Provider也只在登录链装配。JwtConfiguration仅提供协议侧JWK/JwtDecoder，不注册登录过滤器或认证方式。保持所有现有端点和可信身份合同。

- 文档收尾补记：删除 grant/README.md；该旧示例只介绍被移除的 ResourceOwner/password 继承扩展，保留会误导新增同类 grant。external_code 当前合同统一由 IAM README 维护。无运行行为变化。

- 2026-11-11 用户继续指出 authentication/thirdparty 类过多，要求核对接口/Abstract 与子包：按职责迁移 22 个实现/契约到 browser/provider/binding/directory/cache，并同步精确引用与对应测试。已有 Adapter、DirectoryAdapter、StateRepository 与 Filter 模板足够，未机械增加空接口或横跨登录/协议的大基类。保留 7 个共享/序列化类型及含内部序列化状态的仓库原全限定名，避免破坏 Session/授权兼容；新增包说明解释该约束。仅包与导入变动，不改变绑定/目录/缓存算法。验证增加清理旧编译类后的目标回归、Checkstyle 与原真实基础设施集成测试。

- 2026-11-11 用户要求独立判断service与协议职责：以实际依赖区分归属。OAuth2存储、Claims端口/实现、Profile、外部Token绑定和协议工具/错误码移至protocol；Sms实现与请求型UserDetails适配归authentication；跨链账号、Client、来源与绑定规则按业务能力归service.account/client/identity。拆分原SysUserDetailsServiceImpl的显式Client加载为ClientAccountDetailsService及默认实现，保留原目录优先/回退规则及可信快照复核；Hosted适配保留原请求路由与密码升级行为。SysUserDetails因历史Token/Session类型名保留兼容包。错误码常量接口改final类，不把常量容器伪装成扩展点；删除无调用端点工具方法。没有新协议、端点或安全信任合同；增加共享账号快照拒绝与无请求加载的行为测试，并重跑完整安全链。

- 依赖方向核对发现公共身份服务仍依赖具体ProviderDirectoryPolicyService，补提炼IdentityDirectoryService及其Admission准入投影。公共应用服务只依赖端口，原目录实现保留平台调用、加密快照与刷新算法；登录绑定和协议绑定均消费同一端口，避免形式搬包后仍反向依赖登录实现。Admission不持久化，没有旧Session类型迁移。

- 用户指出IAM README是流水账：整体改写为模块介绍、职责架构、接入方式、启动配置、扩展与验证入口。当前详细配置合同归docs/configuration.md，迁移历史与本次兼容处置归SDLC compatibility-notes.md；架构图在模块docs提供稳定入口。删除README中的日期增量、测试成绩和实施过程，不删实际能力合同。仅文档/图调整。

- 用户继续要求config分类：协议专属装配统一归config.oauth2，登录安全链归config.authentication，跨链MFA及PasswordEncoder配置留根包。ProtocolLoginCorsConfigurationSource属于协议行为而非Bean装配，移至protocol.oauth2.endpoint并命名OAuth2CorsConfigurationSource；同步测试与引用，不改变Bean名、链顺序、端点或CORS规则。补包说明，clean IAM verify验证旧类无残留。

- 用户要求整理类图及整体逻辑：新增面向维护者的docs/architecture.md，按实际类型/调用核对装配、网页登录、external_code、公共身份规则与Token/存储五组类图，并说明绑定、发布和兼容边界；README增加入口。仅文档，无运行代码变化。

- 用户后续明确要求少用全限定类名、使用import：本轮只整理IAM service下8个已有实现/端口的类型引用与显式导入，不改变字符串、注释、序列化标识、注解含义或业务逻辑；保留已有未提交工作，写入前校验源文件摘要。仅本地修改，未授权自动提交推送。

- import整理完成后，用户明确要求“提交合并”；沿用前文对公开仓库macula-projects/macula-cloud的明确YES授权，仅提交这8个服务文件及工程证据，不部署或删除分支。
