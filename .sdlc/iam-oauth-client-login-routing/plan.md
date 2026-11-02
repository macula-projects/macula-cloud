# Plan: IAM OAuth Client 网页登录路由 (from spec.md 2026-10-22)

## Files that change
- SDLC：修改 `.sdlc/iam-oauth-client-login-routing/spec.md`，新增
  `.sdlc/iam-oauth-client-login-routing/plan.md` 和 `review-findings.md`，记录已接受规格、工程顺序、Plan 评审及
  实际偏差。
- System API 契约：新增
  `macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/BrowserLoginProviderType.java`；
  修改 `ExternalCodeType.java` 删除 `WECOM_WEBVIEW_CODE`；新增或修改对应的
  `BrowserLoginProviderTypeTest.java`、`ExternalCodeTypeTest.java` 固定协议值。
- System OAuth Client 管理：修改
  `macula-cloud-system/src/main/java/dev/macula/cloud/system/pojo/entity/SysOAuth2ClientConfig.java`、
  `form/OAuth2ClientForm.java`、`vo/OAuth2ClientVO.java`、
  `service/impl/OAuth2ClientConfigServiceImpl.java`，增加网页登录 allowlist 与可选直接登录提供商，服务端校验
  authorization_code、redirect URI、身份源和枚举一致性。
- System 数据库与测试：新增
  `macula-cloud-system/src/main/resources/db/migration/V10__iam_oauth_client_login_routing.sql`；修改
  `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/OAuth2ClientConfigServiceImplTest.java`、
  `integration/ExternalCredentialLoginMigrationIT.java`，覆盖 V9→V10、干净库、新字段默认值及过时企微网页
  external code 配置清理。
- Admin：修改 `macula-cloud-admin/src/api/model/system/oauth2Client.js`、
  `macula-cloud-admin/src/views/system/oauth2Client/index.vue`、
  `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`，增加网页登录提供商多选、直接登录单选及
  空集合默认拒绝提示。
- IAM Client 模型与策略：修改
  `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/entity/SysOAuth2Client.java`、
  `service/support/SysOAuth2ClientService.java`、`service/support/impl/SysOAuth2ClientServiceImpl.java`；新增
  `authentication/thirdparty/BrowserLoginClientPolicyService.java`，只从受控 SavedRequest 解析并加载 Client
  策略，统一提供登录页、自动路由和第三方入口检查。
- IAM 浏览器认证公共链：修改
  `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyLoginContext.java`、
  `AbstractThirdPartyAuthenticationFilter.java`、`controller/LoginController.java` 和
  `config/DefaultSecurityConfiguration.java`，绑定 clientId、实现企微/微信环境优先路由、直接登录、选择页及错误
  回退，并在发起和回调阶段两次校验 Client 策略。
- IAM 五个平台装配：修改 GitHub、企微、飞书、钉钉、微信目录下的
  `*AuthenticationFilter.java` 与 `*LoginFilterConfigurer.java`，把统一 Client 策略传入公共过滤器；平台 Client、
  Provider 和换码合同不改。
- IAM external code：修改
  `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomExternalCodeVerifier.java`，只保留
  APP、MINIAPP、BROWSER；修改 `WecomExternalCodeVerifierTest.java` 及 externalcode 目录下使用
  `WECOM_WEBVIEW_CODE` 作为通用样例的 converter/provider/registry/replay 测试，改用仍受支持的 code type。
- IAM 浏览器与协议测试：修改
  `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/ThirdPartyAuthenticationFlowTest.java`、
  `HttpSessionThirdPartyLoginStateRepositoryTest.java`、`ThirdPartyBindingServiceTest.java`、
  `ThirdPartyIdentityResolutionServiceTest.java`、
  `controller/ThirdPartyBindingControllerTest.java`、`handler/BrowserAuthenticationFailureHandlerTest.java`、
  GitHub/企微/飞书/钉钉/微信各自的 `*AuthenticationProviderTest.java` 以及 `config/IamProtocolTest.java`；新增
  `authentication/thirdparty/BrowserLoginClientPolicyServiceTest.java`，覆盖路由优先级、策略绕过、发起/回调
  二次校验、state/待绑定/绑定成功上下文中的 clientId 保留、错误回退和 authorization code + S256 PKCE 续转。
- 文档：修改 `macula-cloud-iam/README.md`、`macula-cloud-system/README.md`，使 Client 配置说明、接入矩阵、
  V10 升级步骤与实际行为一致。
- 不修改 Gateway、业务服务 API、第三方 JSSDK/SDK、小程序代码、provider HTTP 换码协议、Token grant 或
  现有身份绑定模型。若实现需要越出以上文件或职责，先记录计划偏差并停下确认。

## Order of work
1. 先固定共享契约：新增 `BrowserLoginProviderType`，删除 `WECOM_WEBVIEW_CODE`，更新枚举测试；把所有通用
   external code 测试样例切换到仍受支持的类型，确保删除只影响企微端内网页 external grant。
2. 新增 V10 和迁移测试。V10 为 `sys_oauth2_client` 增加 allowlist/direct 字段，默认空集合；精确删除
   `WECOM_WEBVIEW_CODE` 映射并从 CSV 中移除该 token，不改用户、绑定或其他 code type。正式运行迁移前先
   重新取得目标数据库 `flyway_schema_history`，若证据不可得则只完成源代码和隔离数据库验证，不触碰业务库。
3. 扩展 System Entity/Form/VO/Service。保存时规范化 provider 集合，要求 Client 启用 authorization_code 且
   有 redirect URI，direct 必须属于 allowlist，每种 provider 必须有唯一启用身份源；事务提交后沿用现有 Client
   缓存失效。
4. 扩展 Admin OAuth Client 表单。allowlist 使用共享目录返回的稳定值，不自由输入；direct 候选只来自已选集合，
   清空 allowlist 时同步清空 direct，明确提示“未配置仅允许本地认证”。
5. 在 IAM 实现 `BrowserLoginClientPolicyService`。它从 `HttpSessionRequestCache` 的受控 SavedRequest 提取单值
   client_id、加载 Client、解析共享枚举，并提供 allowed/direct/visible 决策；不把 Cookie、查询参数或 UA 当作
   Client 授权证明。
6. 更新 `LoginController`：受控 error 先停留登录页；其次 `wxwork + WECOM`、非企微
   `MicroMessenger + WECHAT`；再使用 direct provider；否则展示 allowlist 与 enabled/visible 的交集。环境命中但
   provider 未允许时继续 direct/选择逻辑，不猜测授权。
7. 更新公共第三方过滤器和五个平台装配。发起阶段要求策略允许并把 clientId 写入一次性 state；回调阶段消费
   state 后重新核对 SavedRequest、clientId、provider 和当前策略。无受控 authorize 请求、跨 Client、策略收紧、
   直接路径绕过均使用统一失败处理器失败关闭；现有 binding required 和 SavedRequest 恢复不变。
8. 更新 IAM/System README，说明旧 Client 默认关闭、企微/公众号自动续转、direct/选择模式、企微小程序与
   网页 OAuth 边界及 V10 运维步骤。部署顺序固定为：先停止或排空全部旧 IAM 实例，再由 System 执行并验证
   V10，精确失效 `OAUTH2_CLIENT_CACHE_KEY` 中受影响 Client（无法可靠枚举时清空该专用 Client cache，不能
   清共享 Redis），最后启动删除旧枚举的新 IAM；禁止新旧 IAM 跨 V10 混跑。
9. 只运行 Stage 3 实现反馈所需的最小测试和静态检查，记录实际偏差；完成实现后停在人工门，不自行进入完整
   Stage 4、提交、推送、部署或业务数据库迁移。

## Risks
- 当前工作区包含大量用户未提交的 IAM/System/Admin/V8/V9 修改；每个文件必须以当前内容为基线逐块合并，
  禁止从旧版本整文件覆盖。新增 V10 避免改写可能已执行的 V9。
- SavedRequest 解析若接受多值 client_id、非授权端点或只依赖 Cookie，会形成跨 Client 第三方入口绕过；策略
  服务必须验证受控路径、单值参数和实际 Client，并在回调再次核对 state。
- `wxwork` User-Agent 可能同时包含微信标记；优先级错误会把企微成员送到公众号授权。判断必须先 WECOM、后
  WECHAT，并证明 UA 只选路由、不授予身份。
- 自动/直接登录失败若再次触发自动跳转会形成循环；受控 error 页面必须停止自动路由并保留本地登录和允许的
  选择项。
- 旧 Client 的 allowlist 默认为空会有意关闭过去全局可见的第三方入口；这符合默认拒绝要求，但部署前必须盘点
  需要补配置的 Client，不能通过迁移自动扩权。
- V10 清理 `WECOM_WEBVIEW_CODE` 是配置收敛。SQL 必须只匹配完整 CSV token 和精确映射行，不能误删
  `WECOM_MINIAPP_CODE`、`WECOM_BROWSER_CODE` 或其他 provider 配置。
- 旧 IAM 实例和共享 OAuth Client 缓存可能在 V10 后继续持有已删除能力；升级必须使用停止旧 IAM、执行并验证
  V10、失效专用 Client cache、启动新 IAM 的顺序，不能滚动混跑，也不能用“数据库已更新”代替运行实例验证。
- `ThirdPartyLoginContext` 序列化结构变化会使发布时正在进行的短期 Session state 失效；允许用户重新发起，
  不尝试兼容或反序列化不完整的旧状态。
- 本期仍使用 provider type 级唯一启用身份源；不能暗示不同 OAuth Client 已可使用不同平台 App 凭据。
- Docker 状态查询本轮无响应。任何 Compose、Flyway 或真实数据库动作都必须在状态恢复并重新核对后另行执行，
  本计划不授权部署或迁移。

## Proof
- `BrowserLoginProviderTypeTest` 和 `ExternalCodeTypeTest` 固定共享协议值，证明企微网页 external type 删除而企微
  小程序、APP、browser 类型保留。
- `OAuth2ClientConfigServiceImplTest` 覆盖空 allowlist、未知值、无 authorization_code/redirect URI、身份源
  缺失/禁用/重复、direct 不属于 allowlist、合法保存回显和事务后缓存失效。
- `ExternalCredentialLoginMigrationIT` 新增 V9→V10 与干净库用例，证明字段默认值、完整 token 清理、映射删除、
  其他 external types、用户和身份绑定不变；只运行命名隔离 MySQL，不连接业务库。
- `admin-identity-security-settings.test.js` 覆盖网页登录 allowlist、direct 候选联动、清空行为、编辑回显、默认拒绝
  提示和服务端错误展示。
- `BrowserLoginClientPolicyServiceTest` 覆盖合法 SavedRequest、非授权路径、多值/缺失 client_id、伪造 Cookie、
  未知/空策略、direct、provider allowed 和策略收紧。
- `ThirdPartyAuthenticationFlowTest` 覆盖五个平台的发起/回调双重策略检查、clientId 绑定、跨 Client、重放、
  SavedRequest 错配、禁用 provider、显式路径绕过和成功恢复原授权请求。
- 五个平台 Provider、Session state、失败处理、待绑定和绑定控制器测试全部使用可信 clientId 构造 context，证明
  state 创建/消费、binding required 保存和绑定成功续转不会丢失或伪造 Client 边界。
- `IamProtocolTest` 使用真实 SecurityFilterChain 与 Session 验证企微优先于微信、微信公众号、direct、选择页、
  error 不循环、本地登录回退、authorization code + S256 PKCE，以及 GitHub/企微绑定回归。
- externalcode converter/provider/registry/replay 和 `WecomExternalCodeVerifierTest` 证明删除过时类型后现有 grant、
  防重放与企微 APP/小程序/browser verifier 行为不变。
- 捕获日志断言不出现 code、token、state、Cookie、完整 SavedRequest、subject、异常消息或堆栈。
- 升级回归先预热包含旧企微 webview 能力的 OAuth Client cache，再执行 V10/专用缓存失效，证明新 IAM 对原始
  `WECOM_WEBVIEW_CODE` 请求返回受控拒绝；同时验证旧 IAM 已排空且没有新旧版本混跑。自动化仅证明缓存和
  协议行为，实际实例排空仍作为部署证据单列。
- Stage 3 反馈命令为共享 API 定向测试、IAM 定向测试、System 定向测试、Admin 相关 Vitest 和
  `git diff --check`；Stage 4 再执行根 Maven、Admin 全量单测/构建、隔离 MySQL V10、依赖/许可证检查及真实
  企微/微信设备联调，分别报告 Mock、集成和人工证据。

## Implementation notes and deviations
- IAM 直接复用了现有 `SysOAuth2ClientService#getClientByClientId` 加载 Client，没有为本功能修改该接口和实现；
  `BrowserLoginClientPolicyService` 负责 SavedRequest 校验、策略解析与默认拒绝，职责边界与规格保持一致。
- `DefaultSecurityConfiguration` 已包含五个平台显式入口的放行与对应 Configurer 装配，因此本功能没有再复制或
  改写过滤链规则；策略校验统一落在公共第三方过滤器中，显式入口仍不能绕过 Client allowlist。
- Admin 使用 `oauth2Client.js` 中的集中常量生成网页登录提供商选项；System 没有新增仅供该表单使用的目录接口。
  服务端仍以共享 `BrowserLoginProviderType` 枚举作为最终校验边界，前端值与共享协议值由单元测试固定。
- 为使当前用户工作区中的既有改动能够通过编译和回归，顺带完成了少量相邻一致性修正：补齐 IAM
  `SysMessageProvider.endpoint` 映射、消除 `SmsAuthenticationToken` 重载构造歧义、修正
  `RedisLoginCaptchaServiceTest` 局部变量遮蔽、更新 `FeishuClientTest` 对浏览器换码 `redirect_uri` 的断言，并整理
  个别测试导入。这些修正不改变本计划的协议和数据库范围。
- 除定向测试外，已额外执行 IAM 模块全量测试、System 目标 reactor 全量测试和 Admin 生产构建；这些结果只作为
  当前实现反馈，不代表自动通过后续人工 gate。V10 隔离 MySQL 集成测试、真实设备联调、部署和业务数据库迁移
  均未执行。
