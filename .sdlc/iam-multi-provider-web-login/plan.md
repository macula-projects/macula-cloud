# Plan: IAM 飞书、钉钉、微信网页认证接入 (from spec.md 2026-10-21)

Author: Rain.

## Files that change

- SDLC：新增 `.sdlc/iam-multi-provider-web-login/plan.md` 和 `.sdlc/iam-multi-provider-web-login/review-findings.md`，记录工程方案、Plan 评审及后续实际偏差；已接受的 intent/spec 不再扩大范围。
- IAM 公共认证层：修改 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/AbstractThirdPartyAuthenticationFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigService.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysIdentityProvider.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/enums/ThirdPartyProviderType.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandler.java`，支持三个新类型、受控 browser/webview 重定向、附加密钥读取及不保存原始异常的失败跳转。
- 飞书认证：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuClient.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuLoginMode.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProvider.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuLoginFilterConfigurer.java`；browser 按官方网页 OAuth 发起并换码，webview 只重定向业务前端桥接地址，回调最终提取非空 `employee_no`。
- 钉钉认证：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClient.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkLoginMode.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProvider.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkLoginFilterConfigurer.java`；browser 将个人授权身份解析到配置 `corpId` 内的成员 `userId`，webview code 直接核验为该组织成员 `userId`。
- 微信认证：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatClient.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatLoginMode.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProvider.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wechat/WechatLoginFilterConfigurer.java`；browser 使用网站应用 `snsapi_login`，webview 使用公众号 `snsapi_base`，两条路径都只接受非空 `unionid`。
- IAM 安全链与页面：修改 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`、`macula-cloud-iam/src/main/resources/templates/login.html`、`macula-cloud-iam/src/main/resources/static/iam/auth.css`，显式注册三个 Provider/Filter、精确放行九个入口、按 enabled/visible 展示自动入口与 browser/webview 辅助选择；不修改 `auth.js`，不加入 JSSDK 或桥接页面。
- IAM 测试：修改 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/IdentityProviderConfigServiceTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/enums/ThirdPartyProviderTypeTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/handler/BrowserAuthenticationFailureHandlerTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`；新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuClientTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuAuthenticationProviderTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuLoginModeTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClientTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkAuthenticationProviderTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkLoginModeTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatClientTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatAuthenticationProviderTest.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wechat/WechatLoginModeTest.java`。
- System 多密钥与目录：修改 `macula-cloud-system/src/main/java/dev/macula/cloud/system/enums/IdentityProviderType.java`、`macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysIdentityProvider.java`、`macula-cloud-system/src/main/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImpl.java`，将钉钉 `corpId`、飞书/钉钉 `webviewBridgeUri` 设为必填，新增微信公众号凭据，并把主密钥与附加密钥分别保存在不可回显的加密字段中。
- System 迁移与测试：新增 `macula-cloud-system/src/main/resources/db/migration/V8__identity_provider_additional_secrets.sql`；修改 `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`、`macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/IdentityProviderMigrationIT.java`、`macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java`，覆盖旧行升级、多密钥加密持久化、留空保留、秘密不回显及 URI 校验。
- Admin 动态表单提示：修改 `macula-cloud-admin/src/views/system/identityProvider/index.vue` 和 `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`，移除“IAM 尚未接入”旧提示并固定多个 secret 字段的动态遮蔽/留空保留契约；不增加提供商硬编码或前端 SDK。
- 文档：修改 `macula-cloud-iam/README.md` 和 `macula-cloud-system/README.md`，记录三家 browser/webview 流程、`code + state` 前端交接、绑定主体、回调、字段、V8 升级/回滚和真实平台验收条件。
- 不修改根 POM、IAM/System POM、Gateway、System API、Docker/环境配置或其他业务前端；不引入 JSSDK、提供商 Java SDK、支付宝登录代码或小程序代码。若实现证明必须越出此清单，先记录计划偏差并请工程师确认。

## Order of work

1. 先固定跨模块契约测试：扩展 Provider 枚举、登录路径、System 目录字段、公开/秘密配置边界、`webviewBridgeUri` 校验、V8 旧库升级和登录页可见性预期；所有协议字面量在测试中独立声明，避免与被测常量同源。
2. 在 System 实现通用多密钥存储。V8 为 `sys_identity_provider` 新增可空 `secret_config_json TEXT`；System 实体使用 `@CryptoField` 加密整个附加密钥 JSON。`app_secret` 继续保存目录声明的主凭据，其他 secret 字段只进附加密钥 JSON；读取 API 仅生成每个密钥键的 `configuredSecrets` 布尔值。更新时空值保留旧密钥，未知/错类型/超长键值失败关闭，公开 `config_json` 永不接收 secret。
3. 更新身份源目录与校验：飞书增加必填 `webviewBridgeUri`，钉钉将 `corpId` 和 `webviewBridgeUri` 设为必填，微信保留网站应用 `appId/appSecret` 并增加 `officialAccountAppId/officialAccountAppSecret`。`webviewBridgeUri` 仅接受规范化绝对 HTTPS URL，拒绝 user-info、fragment 和预置 `state` 查询参数；支付宝目录保留但不进入 IAM 登录枚举。
4. 扩展 IAM 只读配置模型与公共过滤器：解密并严格解析附加密钥 JSON，通过区分公开值和 secret 的受控访问器供 Client 使用；保留 issuer 固定回调、一次性 Session state 和 SavedRequest 校验。普通 browser 发起重定向官方端点；飞书/钉钉 webview 只向已保存的桥接 URI 附加 state；未知环境回到登录页选择显式入口。回调统一消费 `code + state`，不接受请求提供的回调或桥接地址。
5. 依次实现飞书、钉钉、微信的 LoginMode、Client、Filter、Provider、Configurer。沿用 `WecomClient` 的现有 `RestClient`、固定 HTTPS 端点、连接/读取超时、受控 JSON 字段和白名单日志方式，但为三家 Client 使用明确禁止自动跟随 HTTP 3xx 的请求工厂；换码或用户信息端点返回任何 3xx 都立即失败，绝不请求 `Location`。标准参数优先使用 `OAuth2ParameterNames`，平台专有参数、配置键、阶段和模式使用常量/枚举。browser/webview 使用各自官方换码链，最终分别返回 `employee_no`、目标组织 `userId`、`unionid`；缺字段、租户/组织不匹配、平台错误、HTTP/传输/解析异常全部失败关闭。飞书不新增可由请求传入的 `tenantKey`：可信租户锚点是 System 中配置的同一企业自建应用 `appId/appSecret`，普通网页和端内 code 均必须由服务端使用这组凭据在官方换码链兑换。飞书官方自建应用凭据只代表应用所属租户，错误应用/其他租户 code 无法通过该凭据兑换；IAM 不信任前端声明的租户字段，两条链路都只从成功兑换得到的用户信息读取 `employee_no`。若实现时发现官方接口不能提供这一凭据绑定保证，暂停并回到规格评审，不静默增加 `tenantKey` 或降低租户约束。
6. 将三个 Provider/Filter 显式接入 `DefaultSecurityConfiguration`，精确放行自动、browser、webview 入口并复用现有 `SavedRequestAwareAuthenticationSuccessHandler` 与 `BrowserAuthenticationFailureHandler`。失败处理器改为直接受控重定向，不再借助会把原始异常保存到 Session 的处理器；待绑定分流保持不变。
7. 更新登录控制器、模板和样式：只展示 enabled 且 visible 的新平台，主按钮走自动入口，辅助选项提供普通浏览器/端内入口；IAM 页面不加载 JSSDK、不获取端内 code。更新 Admin 的动态字段提示，但保持现有目录驱动表单和多 secret 输入逻辑。
8. 更新 IAM/System README，明确业务前端桥接页的责任和契约：从 IAM 接收并原样回传 state，通过官方 JSSDK 获取 code，禁止持久化或日志记录 code/state，使用顶层 `location.replace` 返回 `<issuer>/login/{provider}`；记录微信开放平台绑定、飞书工号生命周期、钉钉组织成员约束和 V8 数据库运维步骤。
9. Stage 3 只运行与实现反馈直接相关的定向测试、编译、管理端单测及 `git diff --check`，记录实际偏差；完成后停在 “Is the implementation complete per the plan?” 门。正式完整验证、隔离 MySQL 升级证据和真实平台/真实前端联调留给 Stage 4，不能用 Mock 或编译结果替代。

## Risks

- 飞书 browser OAuth 与端内 JSSDK code、钉钉 browser OAuth 与端内免登 code 使用不同换码链；若错误共用端点或主体字段，会造成认证失败或身份串联。Client 必须由 state 中的 LoginMode 选择固定流程，并证明两条路径归一到相同绑定主体。
- `employee_no` 可由企业管理员修改或复用。实现按已接受规格使用它作为飞书绑定主体，但缺失时必须拒绝；组织侧需保证业务员工号唯一、不复用，变更时同步维护绑定。
- `webviewBridgeUri` 是新的外部跳转边界。若允许请求覆盖、非 HTTPS、user-info、fragment 或攻击者预置 state，会形成开放重定向或 state 替换；System 保存和 IAM 使用两侧都要失败关闭。
- 飞书/钉钉前端桥接实现不在本仓库。IAM 的模拟测试只能证明 state 和 code 交接契约，不能证明 JSSDK、客户端版本、CSP 或真实设备可用；缺少业务前端实现时端内免登不能上线。
- 微信双场景需要网站应用与公众号两组独立凭据绑定同一开放平台账号，并要求两条链路都返回 `unionid`。配置不全或无 `unionid` 时必须拒绝，不能退回 `openid` 或形成两个绑定。
- 附加密钥 JSON 是新的加密持久化链。迁移、实体字段、CryptoField、System 写入和 IAM 读取任一处不一致都会导致配置不可用或秘密回显；必须同时验证旧行、密文落库、API 遮蔽和留空更新。
- 新增 V8 属于共享数据库升级。历史迁移不可修改，升级失败不得自动清洗数据；回滚需要恢复升级前数据库备份并同步回退 IAM/System。干净库通过不能替代 V7→V8 升级验证。
- 三组新 Filter 的 matcher、匿名放行和顺序若不一致，会造成入口不可达、回调被登录页截获或放行过宽；每个自动/显式路径都要在真实 SecurityFilterChain 中验证。
- 提供商响应和异常可能含 code、token、主体或自由文本。日志只能输出 provider、本地 stage、HTTP 状态、允许的数值错误码、本地失败类别和异常类名；捕获日志测试必须用恶意标记值证明不泄漏。
- Java HTTP 客户端对 GET/POST 的 3xx 默认行为可能不同。所有令牌和用户信息请求都必须显式禁用重定向；不能依靠当前方法恰好不跟随，也不能将供应商返回的 `Location` 加入白名单日志。
- 本计划不引入新 Java/JavaScript 依赖。若官方协议只能由新增 SDK 安全完成，属于方案偏差，必须暂停并重新评审依赖，而不是手写不完整签名或静默加入依赖。

## Proof

- `ThirdPartyProviderTypeTest` 固定 IAM 新增 `FEISHU`、`DINGTALK`、`WECHAT` 及 `/login/{provider}` 路径，同时证明支付宝未进入 IAM 运行时枚举。
- `ThirdPartyAuthenticationFlowTest` 覆盖 browser 官方重定向、webview 仅跳预配置桥接 URI、state 与 Session/provider/mode/SavedRequest 绑定、固定 issuer 回调，以及缺失/过期/错配/跨提供商/重放 state、恶意 SavedRequest 和 URI 参数替换失败。
- 三个平台的 `AuthenticationProviderTest` 使用 `MockRestServiceServer` 覆盖两种模式的成功换码和主体归一，并覆盖飞书缺失 `employee_no`、错误应用/其他租户 code 被配置应用凭据拒绝、钉钉非配置组织成员、微信缺失或不一致 `unionid`、平台错误、非成功 HTTP、超时、畸形响应及日志无 code/token/secret/subject/provider text/exception message/stack trace。飞书两条成功路径必须使用同一配置应用锚点并归一到同一 `employee_no`，测试不得用请求中的 `tenantKey` 冒充租户校验。
- 三个平台的 `ClientTest` 使用可观测请求工厂覆盖每一个换码/用户信息阶段：恶意 3xx `Location` 指向异域时立即失败，断言没有对 `Location` 发出后续请求，也没有在日志中输出该地址或任何授权材料。
- 三个平台的 `LoginModeTest` 固定自动识别和 browser/webview 显式路径，证明 User-Agent 只选交互模式、不作为身份依据，未知环境不猜测认证。
- `IdentityProviderConfigServiceTest` 验证主密钥与附加密钥解密读取、未知/畸形 secret JSON、禁用/重复配置、必需字段缺失和秘密不进入公开 values。
- `BrowserAuthenticationFailureHandlerTest` 验证绑定异常仍进入短生命周期绑定仓库，其他失败只重定向受控 reason，Session 不含原始 `AuthenticationException`，输出不含异常或提供商敏感标记。
- `IamProtocolTest` 在真实 SecurityFilterChain 与 Thymeleaf 中验证九个新入口的精确匿名范围、实际登录页链接、enabled/visible 行为、统一成功/失败处理和既有 GitHub、企业微信、密码、短信、OAuth/OIDC 回归；断言 IAM 静态资源及模板不包含飞书/钉钉 JSSDK。
- `IdentityProviderServiceImplTest` 验证目录字段、钉钉 `corpId`、两家 bridge URI、微信双密钥、未知字段、秘密布尔状态、留空保留和旧配置兼容；`SettingsPersistenceIT` 验证附加秘密以密文持久化且 API 不回显。
- `IdentityProviderMigrationIT` 从旧 schema/旧身份源行升级到 V8，核对 Flyway 历史、新列、旧配置保留和干净库初始化；正式执行只允许指向命名受控的全新隔离 MySQL 数据库。
- `admin-identity-security-settings.test.js` 证明 Admin 继续由 catalog 动态渲染多 secret 字段、编辑时留空保留且不回显，并移除“尚未接入”旧提示；不新增 JSSDK 或提供商硬编码。
- Stage 3 反馈命令为 `mvn -pl macula-cloud-iam -am test -Plocal`、`mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal`、`npm run test:unit -- admin-identity-security-settings.test.js`（在 `macula-cloud-admin/`）及 `git diff --check`。Stage 4 明确执行并记录根目录 `mvn test -Plocal`、`macula-cloud-admin/` 下的 `npm run test:unit` 与 `npm run build`，另行记录隔离 MySQL V8 升级、依赖树/漏洞扫描和未具备的外部条件；若基础设施或外部服务不具备，必须记录未运行命令、缺失前置条件和已完成的较小范围证据，不能标为通过。三家真实平台与飞书/钉钉业务前端桥接必须单列为人工联调，不得由 Mock 测试冒充。
