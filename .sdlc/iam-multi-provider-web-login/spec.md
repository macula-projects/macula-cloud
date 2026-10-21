# Spec: IAM 多平台网页认证接入

Author: Rain.
Status: accepted.
Source intent: [intent.md](./intent.md)

## Requirements

1. IAM 必须新增飞书、钉钉、微信三种第三方认证，并继续使用现有浏览器会话、SavedRequest、统一成功处理器和统一失败处理器完成认证闭环，不新增 JSON 登录响应。
2. 三个平台必须同时支持以下入口：
   - 普通浏览器授权登录；
   - 对应平台客户端内网页免登；
   - 自动识别入口 `/login/{provider}`；
   - 可显式选择且可排障的 `/login/{provider}/browser` 与 `/login/{provider}/webview` 入口。
3. 客户端环境识别只用于选择交互模式，不作为身份或授权依据。无法可靠识别时必须展示安全、可理解的入口选择，不得猜测后直接认证。
4. 三个平台必须把两种登录模式归一为同一种外部绑定主体：
   - 飞书：企业自建应用所属租户内非空的 `employee_no`；必须校验响应属于该应用限定的租户，不接受其他租户或缺少工号的用户；
   - 钉钉：配置组织内的成员 `userId`；普通浏览器授权得到的 `unionId` 必须再按 `corpId` 解析为组织成员，非该组织成员拒绝登录；
   - 微信：同一微信开放平台账号下的 `unionid`；任一路径缺少 `unionid` 都必须失败关闭。
5. 外部主体必须复用现有 `sys_user_identity_binding` 绑定到全局用户名。未绑定身份进入现有受控绑定流程；不得静默创建本地用户，也不得仅凭昵称、手机号、邮箱等可变资料自动匹配。
6. 所有授权发起必须创建与 HTTP Session、提供商、登录模式和受控 SavedRequest 目标绑定的一次性 `state`；回调必须校验并消费 `state`，拒绝缺失、错误、过期、跨提供商或重放的状态。
7. 飞书、钉钉端内 JSSDK 集成属于业务前端协议适配，不进入 IAM 静态资源或模板。IAM 必须先创建一次性 `state` 并跳转到预配置的前端桥接地址；前端取得授权码后，以顶层浏览器跳转把 `code` 与原 `state` 传回固定 IAM GET 回调。授权码只能由 IAM 服务端交换，不得写入日志、页面正文、本地存储或持久化。
8. 回调地址必须由 `AuthorizationServerSettings.issuer` 和固定登录路径生成，不接受请求参数或身份源配置覆盖；提供商 API 端点必须是代码内固定 HTTPS 地址，防止开放重定向和 SSRF。
9. 登录页只展示已启用且可见的提供商。启用但配置不完整的身份源必须保存失败或登录失败关闭，不得在运行时降级为弱认证。
10. 登录失败必须经 `BrowserAuthenticationFailureHandler` 返回登录页并显示中文受控原因；不得向页面、Session 或日志暴露提供商原始错误文本、响应体、凭据、授权码、访问令牌、刷新令牌、外部主体或异常消息/堆栈。非绑定失败不得把原始 `AuthenticationException` 保存到 Session。
11. 提供商、登录模式、回调参数、配置键、协议阶段和失败类别应使用常量或枚举；参数名称与 OAuth 2.0 标准一致时优先使用 Spring Security 的 `OAuth2ParameterNames`，只有 `auth_code`、`appid` 等提供商专有名称才定义本地常量，不直接散落字符串字面量。
12. 身份源管理必须支持本功能所需的配置字段、密钥遮蔽、必填校验和回调提示。所有密钥字段只允许写入和“已配置”状态查询，不允许 API 回显原值。
13. 新增、实质修改的顶层 Java 类型必须包含 Apache License 2.0 版权头、职责明确的类级 Javadoc、非空 `@author Rain` 和 `@since 6.1.0`。
14. IAM README、身份源配置说明和部署配置必须说明三个平台的应用类型、回调地址、权限、绑定关系与端内入口配置；不得提交真实凭据、令牌、私钥或环境地址。

## Non-goals

- 不支持飞书、钉钉或微信小程序登录。
- 本期不接入支付宝认证；现有支付宝身份源目录保持原状，不进入 IAM 登录链路。
- 不支持原生移动 App SDK 登录、桌面原生 SDK 登录或支付能力。
- IAM 不托管飞书或钉钉 JSSDK、端内桥接页面及其前端构建依赖；这些属于接入 IAM 的业务前端。
- 不新增自动注册、基于用户资料猜测账号、跨租户账号合并或解绑管理能力。
- 不改造现有 GitHub、企业微信、账号密码或短信认证的业务语义；仅允许为公共安全闭环做兼容性重构。
- 不在本期建设通用社交登录 Starter；实现仍属于可部署的 Macula Cloud IAM 服务。
- 不把提供商访问令牌或刷新令牌保存为长期凭据，也不把本次登录扩展成提供商 API 代调用授权。

## Design

### 总体结构

沿用现有 `AbstractThirdPartyAuthenticationFilter`、`ThirdPartyAuthenticationToken`、一次性登录上下文、身份绑定解析、统一成功/失败处理器和显式 Filter 安装方式。分别新增 `feishu`、`dingtalk`、`wechat` 包；每个包只负责本提供商的模式识别、授权请求、授权码交换、响应校验和绑定主体提取，公共层只负责状态、SavedRequest、认证结果和绑定。

公共过滤器扩展为两类重定向发起结果：

- Provider Redirect：普通浏览器以及微信端内网页直接 302 到官方授权端点；
- Frontend Bridge Redirect：飞书、钉钉端内网页由 IAM 创建登录上下文和 `state`，再 302 到身份源预配置的业务前端桥接地址。业务前端调用官方 JSSDK 获取一次性授权码，随后用 `location.replace` 顶层跳转到固定 IAM 回调并携带 `code` 与原 `state`。

自动入口按 User-Agent 选择模式，但显式入口优先。选择出的模式写入 `ThirdPartyLoginContext` 并受一次性 `state` 保护，回调阶段不得重新按 User-Agent 决定协议。

### 提供商协议

| 提供商 | 普通浏览器 | 客户端内网页 | 绑定主体 | 关键校验 |
| --- | --- | --- | --- | --- |
| 飞书 | 飞书网页应用 OAuth/扫码授权 | `tt.requestAuthCode` | `employee_no` | 使用企业自建应用限定租户，服务端换码并取得用户信息，`employee_no` 非空 |
| 钉钉 | 钉钉账号 OAuth/扫码授权 | `dd.requestAuthCode` | 目标组织 `userId` | `corpId` 必填；将网页 OAuth 的 `unionId` 解析为该组织成员，非成员拒绝 |
| 微信 | 微信开放平台网站应用 `snsapi_login` | 公众号网页授权 `snsapi_base` | `unionid` | 网站应用与公众号绑定同一开放平台；两条路径都必须返回 `unionid` |

协议实现以提供商官方文档为准：

- 飞书：[网页应用类型](https://open.feishu.cn/document/home/app-types-introduction/robots-web-applications-and-mini-programs)、[端内网页免登](https://open.feishu.cn/document/client-docs/h5/development-guide/step-3)、[网页扫码登录](https://open.feishu.cn/document/qr-code-scanning-login-for-web-app/introduction)、[获取登录用户信息](https://open.feishu.cn/document/server-docs/authentication-management/login-state-management/get)；
- 钉钉：[获取微应用免登授权码](https://open.dingtalk.com/tools/explorer/jsapi?id=11723)；
- 微信：[网站应用微信登录](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)、[公众号网页授权](https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/Wechat_webpage_authorization.html)；

### 浏览器页面和失败闭环

登录页在“其他登录方式”中按配置展示三个平台。自动入口是主按钮；普通浏览器和端内入口作为辅助选择或环境识别失败时的回退，不把全部技术路径同时作为默认主视觉。

飞书、钉钉桥接页由业务前端维护，不由 IAM 渲染。IAM 只向预配置且经过严格校验的 HTTPS `webviewBridgeUri` 附加一次性 `state`；不得从请求参数接收桥接地址。业务前端自行持有公开 App ID、钉钉 `corpId` 和官方 JSSDK，必须设置 `Cache-Control: no-store`、`Referrer-Policy: no-referrer`，不得使用浏览器存储保存 code/state，不得展示授权结果或提供商任意错误文本，并在取得 code 后用 `location.replace` 返回固定 IAM 回调。

统一失败处理器直接重定向到 `/login?error={reason}`；登录控制器只把枚举白名单映射为中文文案。绑定所需的待处理主体继续使用现有短生命周期 Session 仓库，其他失败不保存异常对象。

### 安全和实现约束

- 服务端 API 客户端统一设置连接与读取超时，只允许固定 HTTPS 主机；不自动跟随到非预期主机。
- 日志只允许 `provider`、本地 `stage`、HTTP 状态、提供商数值错误码（若无敏感含义）、本地失败类别和异常类名；禁止记录 URI 查询串、请求/响应体、代码、令牌、主体、原始异常消息和堆栈。
- 业务前端负责飞书与钉钉 JSSDK 的版本、许可证、CSP 和供应链治理；IAM README 只定义 code/state 交接契约，不引入或复制这些前端依赖。
- API 响应解析采用最小 DTO 或受控 JSON 字段读取，缺少关键字段、非成功状态或不一致身份均失败关闭。
- 既有 GitHub、企业微信流程必须继续使用相同公共成功/失败语义，并通过回归测试证明未受影响。

## Data and interfaces

### HTTP 接口

新增以下浏览器认证入口，均由 IAM Security Filter Chain 处理，不作为 JSON REST API：

- `/login/feishu`、`/login/feishu/browser`、`/login/feishu/webview`
- `/login/dingtalk`、`/login/dingtalk/browser`、`/login/dingtalk/webview`
- `/login/wechat`、`/login/wechat/browser`、`/login/wechat/webview`

`/login/{provider}` 同时是普通浏览器 OAuth 和端内前端桥接的 GET 回调路径。回调只接受对应协议的授权码、`state` 与错误参数；飞书、钉钉业务前端必须用顶层 `location.replace` 返回该固定路径，不得通过跨域 Ajax 交换 IAM Session。

### 身份源配置

| 提供商 | 必填公开字段 | 必填密钥字段 | 说明 |
| --- | --- | --- | --- |
| 飞书 | `appId`、`webviewBridgeUri` | `appSecret` | 同一企业自建应用覆盖网页授权和端内免登；只接受应用所属租户且有 `employee_no` 的用户 |
| 钉钉 | `appKey`、`corpId`、`webviewBridgeUri` | `appSecret` | `corpId` 从可选改为必填，用于限定业务员所在组织 |
| 微信 | `appId`、`officialAccountAppId` | `appSecret`、`officialAccountAppSecret` | 既有 `appId/appSecret` 继续表示网站应用，新增公众号凭据 |

现有 `sys_identity_provider.app_secret` 继续保存主密钥。为支持微信第二个密钥，由 System 服务拥有的 Flyway 迁移新增可空的附加密钥 JSON 列；System 只接收目录中声明为 secret 的键，写入时保留未提交的既有值，读取时只在 `configuredSecrets` 返回布尔状态。公开 `config_json` 不得包含任何密钥。IAM 读取后通过区分公开值与密钥值的受控配置对象提供给对应客户端。

已有尚未接线的微信配置保持兼容：`appId/appSecret` 无需改名，但在补齐公众号 App ID、App Secret 并满足开放平台绑定前不得启用双场景登录。钉钉已有配置在补齐 `corpId` 前不得启用。

`webviewBridgeUri` 必须是绝对 HTTPS URL，禁止 user-info 和 fragment；保存时规范化，运行时只使用已保存值，并在附加 `state` 前拒绝已有同名查询参数。它是业务前端协议入口，不是 OAuth 回调地址；提供商和业务前端最终都只能返回由 IAM issuer 生成的 `/login/{provider}` 回调。

`sys_user_identity_binding` 不改表结构，`provider_type` 新增运行时可用值 `FEISHU`、`DINGTALK`、`WECHAT`，`provider_subject` 保存上表规定的绑定主体。授权码和令牌不落库。

### 代码契约

- `ThirdPartyProviderType` 增加三个平台及固定登录路径。
- 各平台使用 `LoginMode` 枚举表达 `BROWSER`、`WEBVIEW`、`UNKNOWN`，公共上下文只保存枚举的稳定名称。
- 公共过滤器提供受控的官方授权重定向、前端桥接重定向和回调参数适配点，不在公共层写提供商条件分支。
- 每个平台的 AuthenticationProvider 仅接受本平台 token，调用本平台 Client 获取绑定主体，再调用 `ThirdPartyIdentityResolutionService`；不复制绑定逻辑。
- Filter Configurer 必须复用 Spring Security 官方配置行为，用 `http.authenticationProvider(provider)` 注册 Provider，并显式放置 Filter；不得复制框架内部实现。
- System 身份源目录、表单校验、VO 遮蔽和 IAM 运行时读取同步支持多密钥字段。

## Flagged concerns

- **Blocking — 微信开放平台前置条件：** 必须具备已审核的网站应用、已认证公众号，并将两者绑定到同一微信开放平台账号；两种授权响应都必须实际返回同一用户的 `unionid`。任一条件不满足时无法安全实现“普通浏览器 + 微信内网页”为同一账号，不能用 `openid` 拼接或双绑定降级。
- **Blocking — 真实平台配置与权限：** 三个平台都需要在各自后台配置生产/测试回调域名、应用可见范围和所需权限，且需要可用的测试账号。仓库不能提供这些外部条件；缺失时不阻塞代码和模拟测试，但阻塞对应平台的端到端验收与上线。
- **Blocking — 业务前端桥接契约：** 飞书、钉钉对应业务前端必须实现受控 `webviewBridgeUri`，使用官方 JSSDK 获取 code，并按本文约定原样回传 IAM 生成的 `state`。缺少该前端实现不阻塞 IAM 模拟测试，但阻塞对应平台端内免登验收与上线；责任人为各业务前端维护方。
- **Non-blocking — 数据迁移：** 微信附加密钥需要 System 拥有的共享数据库 Flyway 迁移。全新数据库验证不能替代升级数据库验证，部署前必须核对 `flyway_schema_history` 并验证旧配置可读。
- **Non-blocking — 飞书工号生命周期：** `employee_no` 由目标租户管理，可能被修改或在离职后复用；本期按评审决定将其作为绑定主体。组织侧必须保证业务员工号非空且不复用，变更工号时需同步维护现有身份绑定。
- **Non-blocking — 首次绑定体验：** 未预配绑定的业务员首次通过平台身份登录时仍需完成现有本地账号验证；后续才是平台免登。本期不通过自动注册缩短该步骤。
- **Non-blocking — 提供商沙箱差异：** 提供商沙箱、测试企业或审核态可能不完整支持 PC 扫码、端内静默授权或 `unionid`；模拟测试与真实设备验收结果必须分开报告。

## Verification strategy

### 自动化测试

1. 公共过滤器测试：三个平台的自动/显式模式、官方授权与前端桥接重定向、固定 GET 回调、一次性 `state`、过期/错配/重放、SavedRequest 同源与路径校验、禁用/缺失配置、未知环境选择页。
2. 各平台 Client 契约测试：用本地 Mock HTTP Server 覆盖成功换码、绑定主体提取、非成功 HTTP、提供商错误码、缺字段、畸形 JSON、超时和固定主机；不得访问真实外网。
3. 身份一致性测试：同一模拟用户的 browser/webview 两条路径必须得到同一主体；飞书租户不匹配或缺少 `employee_no`、钉钉非目标组织成员、微信任一路径无 `unionid`、主体不一致均失败关闭。
4. 身份绑定测试：已绑定用户成功；未绑定进入待绑定流程；取消、过期、重复绑定、错误本地凭据失败；不得静默注册或直接匹配资料。
5. 失败与日志测试：每个失败类别显示受控中文原因；捕获日志与 Session，断言不含 app secret、私钥、授权码、token、主体、提供商消息、异常消息或堆栈。
6. System 配置测试：目录字段、必填校验、多密钥新增/保留/替换、未知字段拒绝、秘密不回显、旧微信配置兼容、钉钉 `corpId` 必填、飞书/钉钉 `webviewBridgeUri` HTTPS 与规范化校验、同类型仅一个启用配置。
7. 数据迁移测试：在旧版本 schema 和已有身份源记录上执行升级，验证 Flyway 历史、公开配置与密钥状态；再验证干净数据库初始化。
8. MVC/页面测试：仅展示启用且可见的平台；自动入口和显式回退链接正确；IAM 不包含飞书/钉钉 JSSDK 或桥接页；失败文案可访问。
9. Security Filter Chain 回归：GitHub、企业微信、账号密码、短信和三个新平台都使用统一成功/失败处理器；匿名只能访问明确 permit 的登录资源，其他请求仍默认认证。
10. 依赖验证：运行 Maven 依赖树和仓库采用的漏洞扫描，确认 IAM 未新增飞书/钉钉前端 SDK 或其他非必要提供商依赖。

后端实现完成后按仓库规则执行目标模块及依赖测试，至少覆盖 `macula-cloud-system`、`macula-cloud-iam` 及必要的 API 模块，并显式使用 `-Plocal`。前端管理端如仅消费动态目录则执行相关单测和构建；若修改页面逻辑，再执行对应 lint（前后检查 diff）和端到端测试。

### 真实平台验收

在独立测试应用、测试组织和真实设备上按平台执行六条主路径：三个平台各一条普通浏览器登录和一条客户端内网页免登。每条路径验证首次绑定、再次免登、取消授权、错误/过期回调、退出后重新登录和原始业务 SavedRequest 续转。

真实验收还必须确认：飞书两条路径取得相同 `employee_no` 且只接受企业自建应用所属租户；钉钉桌面端/移动端支持范围并只接受配置组织成员；飞书/钉钉业务前端桥接页不保存 code/state 且能安全返回 IAM；微信两条路径得到相同 `unionid`；所有平台后台回调域名和权限配置正确。自动化模拟通过不得替代该验收，真实凭据和结果证据不得提交到仓库。
