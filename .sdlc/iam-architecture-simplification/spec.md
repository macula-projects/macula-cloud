# Spec: IAM 架构精简与自定义授权收敛 (from intent.md 2026-11-11)
Status: accepted

## Source intent
[intent.md](intent.md) 已由用户于 2026-11-11 以 YES 接受；源码审阅基线为 `8baee0a`。

## Requirements
1. 自定义 grant 仅保留 `urn:macula:params:oauth:grant-type:external_code`。删除 sms/email Token 扩展、password 遗留及无用途资源所有者基类；保留 external_code 正在使用的签发器。
2. 保留 authorization_code、refresh_token、client_credentials、S256 PKCE、OIDC、UserInfo、内省和撤销的现有授权规则。有效机密 Client 请求已删除 grant 时返回 `unsupported_grant_type`；无效认证或畸形请求可先按原认证/参数规则拒绝。不得产生验证码消费、平台换码或 Token 签发副作用。
3. 保留托管密码/SMS、网页第三方认证、全部已实现 external_code 能力、自动绑定和账号证明；保持现有路径、参数和错误合同。SDK 网页绑定完成后不创建 SSO、不直接发 Token；仅保留授权码 Token 作为绑定 API 的允许证明，SDK 网页密码/SMS/合格会话证明继续可用。
4. System/API/Admin 同步移除 sms/email grant 的新建、更新、目录和示例入口。历史配置保留原值并标示待整改；不得前端加载即静默删除、自动改写为其他 grant，或自动关闭合法登录能力。历史 Client 的运行与 Token 过渡策略见待决事项。
5. 五家网页登录共用认证编排和装配模板，厂商差异留在适配器。统一 Provider 仍须核验当前 Client 固定 registration、能力、来源版本和 flow，调用共同绑定策略。注册冲突在启动时失败；未知厂商/能力拒绝，不能尝试其他平台作为回退。
6. 来源/Client 版本、租户和用户目录、state/RequestCache、CSRF、防重放与消费状态、Client/IP 限流、目录准入、MFA、票据所有权及事务后完成语义保持；不得以减少代码为由删除边界校验。
7. 相关生产类移除仅供测试的目录/限流/事务空值旁路；测试显式提供替身，事务测试执行真实提交/回滚语义。保留目录请求在事务外、写入时锁内复核版本的顺序。不把必要依赖改成默认 NoOp。
8. 清理已证明无运行引用的兼容刷新 Provider、旧用户名加载服务、空 CAS/SAML 配置及相关无效测试装配；删除 Mapper/Entity 前核对 XML、扫描与调用方。补齐受影响方法 Javadoc 与安全意图注释，保留 Java 17、版权头及现有排版；代码减量报告以最终 diff 为准。

## Non-goals
不增加 grant、平台能力、MFA 因子或邮箱托管登录；不统一厂商 HTTP 合同；不删除绑定历史表、业务邮件/短信能力或标准 email Scope；不重写 Redis 授权存储、不改其格式/TTL/CAS/墓碑语义；不迁移业务库、部署、提交或推送。

## Design
- **约束来源**：Cloud 根 AGENTS.md、architecture/backend-development/frontend-development/testing 规则及 REVIEW.md；bands.yaml 仍是待批准的监控模板，本轮不据此宣称运行 SLO。没有发现额外适用于本次设计的组织品牌或合规技能，不虚构要求。
- **协议层**：保持现有安全链、标准框架 Provider 和 external_code 的 Converter → Provider → VerifierRegistry → 公共身份策略 → TokenIssuer。移除 sms/email 的装配和专属发码/图形挑战业务，仅删除已无其他调用的配套类、属性与依赖；原端点保留最小退役响应，托管和绑定发码独立保留。
- **策略与适配器**：统一 ThirdPartyAuthenticationProvider；通过唯一注册的厂商适配器构建授权 URL/核验 Code，并返回 VerifiedExternalIdentity。复用现有 Filter 的 state 模板；允许保留薄路由 Filter，提取重复 Configurer 装配。厂商 HTTP Client 和浏览器/SDK 的不同认证结果边界保持。
- **结果与持久化**：浏览器恢复受控授权请求；external_code 保持当前 Token/ID Token 合同与不建 SSO 的行为。Sdk binding ticket 沿既有网页账号证明或授权码 Token API 完成绑定。授权存储仅做必要引用调整，不在此批引入 Codec/Store 拆分。

## Data and interfaces
| 边界 | 目标行为 |
| --- | --- |
| `/oauth2/token` | 标准 grant 与 external_code 保留；sms/email/password 不再受理 |
| `/api/v1/oauth2/sms-codes`、`email-codes`、`login-challenges/captcha` | 精确路径返回 HTTP 410 及固定 `endpoint_retired` JSON，不读账号/Client、不发码、不重定向；删除业务 Controller 与失效 CORS/CSRF 配置，保留最小退役匹配 |
| 托管 `/api/v1/sms-code`、`/login/bind/sms-code` | 保留各自 CSRF、Client/用途、限流与校验边界 |
| `/login/bind/external`、现有绑定 Token API | 保留 ticket 流程；Token 证明去除 sms/email，只接受原有授权码证明，不新增 external_code 自证 |
| Client 配置 | 写入只接受受支持 grant，未知/已删除值明确拒绝；读取历史值不自动清洗，页面要求显式整改后保存；只读恢复历史授权与新配置合法性校验分离，不因此绕过当前版本/账号规则 |
| 数据库/Redis | 无 schema、历史数据或运行缓存批量改写；保留版本变更导致授权失效的原有规则 |
| 文档 | 更新 IAM/System/API/Admin 当前合同及迁移说明；第三方逐接口链接、参数与真机验证边界保留 |

## Flagged concerns
- 历史 Client 与切换顺序：未核查现网调用方，不能默认允许直接切换。建议发布前盘点并显式整改旧 Client，再统一升级相关服务；仅旧 grant 的 Client 不自动转型或自动启用新入口。需业务维护者确认；编码可推进，发布仍需盘点完成，[non-blocking]。
- 历史 Token/Refresh 处置：建议不主动撤销已有 Access Token，继续按到期、撤销和当前版本规则校验；旧 sms/email/password 授权的 Refresh 不再换发，返回 `invalid_grant`；标准授权刷新保持。该取舍须由审阅者在本规格批准时明确接受，未接受不得实现此处新行为，[blocking]。
- 接口退役兼容：原专属发码/挑战端点改为 410，不保证旧调用继续成功；退出日期和受影响应用仍由业务维护者确认，退役响应不能调用外部设施或泄漏配置，[non-blocking]。
- 范围控制：授权存储深拆分、全量兼容模型清理留待独立变更；本轮聚焦 grant、认证编排、相关生产测试旁路及已核实遗留。作者仍为待确认；未假设净减量或上线日期，[non-blocking]。

## Verification strategy
- 协议安全链测试：被移除 grant 的允许/拒绝路径、无副作用；标准授权码/PKCE、OIDC/UserInfo、刷新、client_credentials、内省、撤销；移除端点的实际响应和拒绝副作用。
- 身份回归：各厂商成功/失败和来源选择；external_code Scope/nonce、限流、防重放/消费状态、绑定与 SDK 网页证明；来源/Client 变更、跨租户、目录/MFA 拒绝；事务提交/回滚及票据所有权。
- 配置回归：System/API 保存校验与目录；Admin 旧配置回显、显式整改、标准/external_code 指南和托管开关不丢值，覆盖失败/空态与重复提交。
- 实施后按最小范围运行 IAM、System/API 测试，再扩大至 `mvn test -Plocal`；Admin 使用 Vitest `--run`、构建及目标 Cypress；执行变更 Java 的 Checkstyle 和 diff 检查。环境不具备时记录原因，不记作代码通过。
- 真实 Redis/MySQL 证明并发/事务；厂商/设备/业务库升级独立验收。当前阶段仅核对源码与文档，没有运行测试或平台联调。

Acceptance: 2026-11-11，用户以 YES 显式接受本规格及 flagged concerns。历史 Access Token 不主动撤销、旧扩展授权 Refresh 返回 invalid_grant 的决策已接受，原 blocking concern 已解除；现网调用方盘点与退出日期仍为发布前置条件。此批准不代替 plan.md 工程批准，不授权 Git 提交、推送或部署。
