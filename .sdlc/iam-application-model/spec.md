# Spec: IAM接入应用领域模型统一 (from intent.md 2026-11-12)
Status: accepted

## Source intent
[intent.md](intent.md)：用户已确认命名统一，并明确“保持单选协议，支持更多协议类型”。本规格将单纯重命名调整为协议中立的数据分层。

## Requirements
1. 一个IAM应用对应零或一份协议配置；未选协议的草稿合法。配置表以application_id唯一约束落实单选，不再维护enabled_protocols/default_protocol/protocol_mode三套重复权威状态。
2. 应用主表只保留应用标识、名称、架构类型、租户/用户目录、用途/受管标识、现有状态/版本和登录策略；OAuth2的client_id、Secret及其哈希/有效期、grant、redirect URI、Scope、PKCE、令牌设置归入协议注册配置。
3. 登录认证与协议发布分离。密码、SMS、EMAIL、浏览器/端内模式及第三方来源选择仍是应用登录策略；OAuth2、SAML2、CAS适配器消费同一受信认证结果，不重复实现账号验证和绑定。
4. 统一“应用—登录能力—身份源”关联，收敛主表的浏览器来源JSON与旧external-provider映射。每个application_id/login_capability唯一指向一个identity_provider_id；来源凭据继续只存sys_identity_provider。
5. external_code只属于OAuth2协议授予。协议配置决定允许的授予和Code类型，共用来源关联决定该能力固定哪个真实来源；SAML2/CAS不能因此获得未实现的Code换凭证能力。
6. 当前实现仍只启用OIDC/OAuth2.1。新增协议须提供独立的类型化配置、校验器、标识解析及发布适配器后才能进入可启用注册表；通用注册表不得硬编码OAuth2设置类型或静默转换未知协议。
7. 已有公开client_id、Secret/哈希、应用/来源/用户主键、有效绑定、来源版本、账户状态和当前安全校验保持；不重新生成或猜填。Token/授权/Session安全持久化类型、字段及UID不因领域重命名改变。
8. 新增Flyway迁移，不改历史迁移。先验证关联可唯一解析、协议至多一份、配置合法；有歧义/孤儿记录则在破坏性DDL之前停止。运行环境切换须另行确认，本轮不直接迁移业务库。

## Non-goals
不实现SAML2 IdP或CAS Server端点，不增加授权方式，不改变浏览器/端内二选一，不调整平台权限、账号绑定规则或凭据，不删除旧sys_user_identity_binding记录，不部署、提交或推送。

## Design
三张核心表：

| 表 | 职责与主要内容 |
|---|---|
| sys_iam_application | 应用公共元数据和登录策略；应用架构、租户/目录、现有状态/版本、hosted登录方式、webLoginMode等 |
| sys_iam_application_protocol_config | application_id唯一，protocol、schema_version、类型化configuration及受保护的协议凭据；草稿可无记录 |
| sys_iam_application_identity_provider | application_id、login_capability、identity_provider_id；统一浏览器和直接Code来源路由 |

调整前一版第三表建议：不采用sys_iam_application_external_code_source作为共用关联名；external_code授予许可留在OAuth2配置，来源路由按通用登录能力表达。

协议配置严格区分类型：OAuth2/OIDC包含客户端标识、客户端认证、授权/回调/Scope/Token策略；未来SAML2包含SP实体标识、ACS、证书及属性策略；CAS包含服务匹配与属性/退出策略。不能把三者塞进OAuth2ProtocolSettings，也不为未来协议制造空实现。这里的SAML2/CAS是IAM向业务应用发布协议，和上游第三方身份源接入不同。

协议凭据与普通JSON展示分离，Secret/哈希原样保留，不进入普通详情响应或日志。IAM发布端自身私钥由现有服务端密钥管理负责，不能与业务应用的协议注册凭据混合。

代码采用显式协议策略注册表：每种已实现协议提供Settings、Validator和发布适配器；OAuth2专属RegisteredClient投影留在protocol/oauth2，应用服务只编排公共应用与单个协议策略。具体类和文件清单由Build计划按当前调用链展开。

## Data and interfaces
- sys_oauth2_client改为sys_iam_application；sys_application_protocol_config改为sys_iam_application_protocol_config。主表旧协议字段与子表重复数据收敛为一份权威配置，不能长期双写。
- sys_oauth2_client_external_provider合并为通用关联模型，字符串client_id映射为内部application_id；浏览器来源JSON同步规范化。未能唯一确定能力/来源的历史数据必须报告，不能自动择源。
- sys_registration_identity_binding改为sys_identity_provider_user_binding，registration_id改为identity_provider_id，保持现有唯一性、大小写和绑定行内容。
- 公开OAuth2 client_id和协议字段含义不改；HTTP路径/已有字段通过边界适配保持兼容，领域Entity/Mapper/Service可统一新名称。OAuth2/OIDC共享客户端标识命名空间，迁移必须继续防止跨两类重复client_id。SAML实体标识和CAS服务匹配不强行套用OAuth2标识。
- 应用配置缓存按必要版本隔离，防止改名后读取旧类型；不借机重写授权Token/Session存储。
- 旧sys_user_identity_binding目前存在历史记录，本轮保留，不猜测跨真实来源的自动迁移关系。

## Flagged concerns
- [字段重分层与来源关联收敛]: 用户2026-11-12明确YES接受本完整规格，已解决。
- [迁移数据歧义]: 发现多协议记录、旧来源不能唯一映射或孤儿关联时须停止迁移，由工程师确认；不得删记录“修复”，blocking for affected database migration。
- [SAML2/CAS发布能力]: 本轮只建立扩展边界，未实现的协议不会显示可启用；协议引擎另立需求，non-blocking。
- [旧绑定表历史数据]: 不影响当前新绑定链，本轮保留，待独立确认归档/迁移/删除，non-blocking。
- [运行环境升级]: 新表结构需要System/IAM协同切换并考虑配置缓存，实际迁移与镜像更新须在新验证绿色后另行授权，non-blocking for code and isolated verification。
- [组织规约]: 已读取AGENTS.md、architecture/backend-development/testing/dependencies-release规则及REVIEW.md；无额外组织技能政策。保护凭据、租户和账号归属，保持Java17、显式导入、必要中文Javadoc，不生成package-info.java。

## Verification strategy
逐项验证名称和所有调用方；全新与V26升级路径都用隔离MySQL跑真实迁移，按行/字段摘要证明ID、客户端标识、凭据和绑定不变；加入歧义拒绝、唯一约束、未选协议草稿和仅单协议用例。真实Mapper/加密插件验证绑定锁定读取，回归密码/SMS/EMAIL、浏览器/端内、external_code及标准OAuth2/OIDC。证明通用注册表不依赖OAuth2类型、未知/未实现协议拒绝，Secret不出详情或日志。保持HTTP兼容，运行相关模块与全仓verify、Admin单元/build/浏览器及静态检查。Schema切换与真实平台结果单独报告，不用模拟结果替代。

规格接受记录：用户YES接受本规格及关注点，进入sdlc-build Phase A。未授权Git提交，按会话约束不执行技能建议的接受提交。

## 2026-11-12已确认的旧绑定表退役补充

用户在获知当前业务不再读写、无外键/视图/触发器依赖、存在1条历史记录，并收到“先核对归档，再新增V28删除，历史迁移不变”的具体建议后，明确要求“建议删除”。该指示替代本规格此前对sys_user_identity_binding的保留非目标，仅针对这张已退役表。当前sys_identity_provider_user_binding、应用、来源、凭据及用户保持。旧记录归档后可恢复，但不猜测来源或自动合并到当前绑定。
