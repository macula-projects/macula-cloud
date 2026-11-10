# Spec: IAM单协议接入、Scope与资源访问边界优化 (from intent.md 2026-11-07)
Status: accepted

## Source intent
[已接受的 intent.md](intent.md)，Author: Rain。首批覆盖 Admin/System、基础资料与 Gateway 内省关系。

## Requirements
1. 一个业务 Client 只能配置 OIDC 或 OAuth2.1 中的一种；前后端和数据库共同约束，不再提供多协议开关、默认协议或双配置页。Admin 继续 OIDC。
2. 创建使用对话框，自动生成 client_id；终端类型使用单选，创建时切换清空相关配置，编辑时不可修改终端类型。协议切换需明确确认并清空协议参数，保存后旧 code/refresh 不可继续使用。
3. 保留快速开始、应用配置、协议配置、登录控制、访问授权五个分区。页面铺满可用区域，分组留白，底部操作始终可见；移动端无横向溢出。平台接入地址紧凑展示并可复制。
4. 令牌格式、有效期、刷新策略直接展开；ID Token 设置只在 OIDC 出现，refresh 设置仅在启用相应授权时出现。端点信息位于协议配置底部，默认折叠。
5. Scope 从服务器能力目录添加选择，不要求手填。OIDC 支持 openid、profile；OAuth2.1 支持平台定义的 profile 和已登记业务 Scope，不允许 openid，不签发 ID Token。未实现的 email/phone/address 不展示、不接受。
6. profile 实际授权读取当前用户的 sub、preferred_username、nickname；只有用户授权令牌可以使用。未授权、服务令牌、跨租户、失效或已撤销令牌不得读取资料。纯 OAuth 使用独立平台 API，保留 OIDC UserInfo 的协议边界。
7. Access Token 的 aud、scope 与签发时的授权记录一致；JWT、opaque 内省和刷新结果一致。资源许可、Scope、角色与租户必须同时通过，root 和 onlyAuthUrls 不能绕过资源与 Scope 检查。
8. Gateway 内省身份按资源服务器用途管理，不要求回调、用户类型、登录控制或 client_credentials 授权；客户端认证不等于获得签发令牌权限。内置身份不可通过普通应用接口删除、停用或变更用途。
9. 允许多个 Client 显式访问同一资源；内省调用方与目标令牌所属 Client 无需相同。无权内省的调用方不得获取目标令牌资料。
10. Admin 的 OIDC 登录、资料、菜单和合法 System API 可用；失效令牌、权限不足和 IAM 依赖故障有可区分的错误，不能统一变成不明 500。

## Non-goals
不实现 CAS/SAML、动态资源注册门户、任意第三方资源接入、每个业务接口的细粒度业务 Scope 重构；不修改 Github 身份源认证、API Key/HMAC、既有角色与数据权限语义。不自动提交、推送或部署；不保持旧令牌和旧多协议配置兼容。

## Design
### 规则与来源
沿用 Cloud AGENTS.md、architecture/backend-development/frontend-development/testing/dependencies-release 规则和 REVIEW.md；通用网关改动遵守 Boot AGENTS.md 及相关规则。使用 sdlc-design 技能。未发现额外适用的组织品牌、合规或数据处理技能，不声称执行额外组织审查。复用 Java 17、Spring Security、现有 Redis 授权存储和 Vue/Element Plus；跨服务模型放 System API，避免复制 Boot 通用认证能力。

### 单协议与界面
业务 Client 保存一个 protocol 和一份 settings。协议采用带简明说明的单选按钮：OIDC 用于登录并取得用户身份；OAuth2.1 用于授权 API。选择后只显示该协议的字段、Scope 和接入说明；共用端点可相同，不能为制造区别而伪造端点。

新建应用先选名称、终端类型、协议，不填写认证地址；认证方式按终端能力给出可选项及建议。private_key_jwt 继续配置 JWKS URL，平台不生成或保管客户端私钥；Secret 类方式显示适用的认证示例，Secret 仅按现有安全机制展示。外部身份源以添加选择方式显示。

编辑切换协议先提示清空及重新登录影响，取消保留原值；确认后生成该协议的干净配置。保存为事务操作，并增加授权配置版本。未保存切换不影响服务器；并发修改沿用 ETag，冲突提示重载。

### 资源与 Scope
建立受信任的资源能力目录，首批为 urn:macula:system 与 urn:macula:iam:profile。system:access 表示允许进入 System API，仍逐请求检查既有角色、URL、租户和数据权限；profile 对应基础资料资源。openid 是 OIDC 身份请求标记，不作为 System API 许可。

业务 Client 只能选择目录中明确授权给它的资源/Scope。服务器根据实际批准的 Scope 推导 aud，不采信任意请求 aud 或未经实现校验的 resource 参数。请求 Scope 必须是 Client 许可的子集；省略 Scope 时只使用明确的默认 Scope，不能自动获得目录全部能力。首批 Admin 的 openid/profile/system:access 由受信任的平台初始化声明显式绑定，其他 Client 不自动继承。

授权记录保存协议、配置版本、实际 Scope 和 aud；code/refresh 绑定该记录。刷新只能保持或缩小原授权，当前配置收紧、协议改变或授权失效后拒绝，不按新配置扩权。Access Token 的 aud 使用资源标识；OIDC ID Token 的 aud 仍为业务 client_id。JWT/opaque 共用同一套签发声明与授权计算。

### 基础资料
IAM 新增 GET /api/v1/profile，使用本地授权存储验证 Access Token、有效期、撤销、配置版本、profile Scope、资料资源 aud 和用户上下文。只按已验证的用户及租户读取资料，返回 sub、preferred_username、nickname，不接受请求指定目标用户；不复用包含角色/菜单等内容的完整 users/me 返回体。

OIDC /userinfo 继续要求 OIDC/openid；profile 字段读取复用同一份最小资料映射。服务授权禁止申请 profile。Access Token 中鉴权需要的内部声明不能因此成为资料 API 的额外返回字段。

### 资源服务器与内省
Client 用途区分 APPLICATION 与 RESOURCE_SERVER，平台归属由服务端不可伪造的 managedKey 标识。资源服务器页仅显示身份、客户端认证、允许内省的资源与状态；不参与业务应用登录/授权配置。普通页面不提供修改平台归属的入口，后端同样拒绝越权。

资源服务器继续复用框架的 Secret/JWT 客户端认证。框架 RegisteredClient 构建器不允许空授权类型，因此仅在内部适配对象中使用保留的、没有签发 Provider 的内省能力标记；不落为业务 grant，不暴露为可选项。授权端点、令牌端点和所有自定义 grant 在进入签发逻辑前拒绝 RESOURCE_SERVER 用途；直接提交保留标记也拒绝。不得用暗中增加 client_credentials 解决构建约束。

/oauth2/introspect 先认证调用者并验证其内省用途，再读取目标授权。目标 Client 用基础仓库恢复，不按调用者认证方式套用，避免公众 Admin 令牌再次被当作机密客户端拒绝。仅处理 Access Token；无效或超出调用者资源许可返回 active:false，不泄露资料；调用身份认证失败返回 invalid_client。

### 网关与 System 准入
Gateway 为受保护的 System 入口绑定所需资源 urn:macula:system 和 system:access。安全链读取的是重写前的外部请求路径，匹配规则显式包含网关前缀；不能在这里使用 StripPrefix 后路径。资源校验位于角色/onlyAuth 分支之前；匿名白名单保持明确边界，受保护 System 路径不得落入白名单。

System 同时执行 Bearer 资源及 Scope 校验，不能仅靠 Gateway 判断或相信来路 Header；直接请求 System 也必须拒绝错误 aud/Scope。复用 Boot 现有服务安全扩展，若缺少可复用能力则在 Boot 增加可覆盖、可禁用的通用校验，Cloud System 显式启用。Feign 与内部调用须声明实际凭据及权限，不能为保持调用成功添加匿名回退。

首批受影响的 Gateway Bearer 内省关闭正向授权缓存，逐请求读取有效授权，移除旧缓存命名空间的使用；避免当前按 iat 到 exp 的完整生命周期缓存导致超期或撤销后继续放行。后续缓存优化另行设计，不缓存内省故障或宽松回退。响应式链不增加阻塞 Redis/HTTP 调用。

### 错误与安全
无效/过期/撤销 Token 为 401；已成功内省的有效令牌但路由资源或 Scope 不足为 403；超出调用者内省许可、无法确认有效性时为 401，并提供可诊断错误码；IAM 不可用或网关内省凭据配置错误为服务依赖故障，外部返回 503，服务端安全日志标明类别，不输出 Secret、Token 或用户资料。OAuth 端点沿用对应协议错误，避免把内省客户端错误冒充终端用户登录过期。Admin 不因依赖故障清空会话或循环跳登录。

## Data and interfaces
- 应用管理 Form/VO/运行时 DTO 改为单 protocol/settings，移除 defaultProtocol、多配置数组及旧字段回退；协议目录仍允许未来注册新协议类型，但未实现协议不可选。
- 主表增加 purpose、managed_key、authorization_version；managed_key 唯一且不可由普通请求写入。协议子表对 application_id 唯一，保证每个业务 Client 至多一份配置；资源服务器没有业务协议行。
- 增加资源目录、Scope 定义、Client 资源/Scope 许可及资源服务器内省许可的明确模型；首批目录由受信任的平台声明维护，接口只读提供适用项。所有保存、绑定和查询保持租户边界与服务端权限检查。
- 增加最小 ProfileVO；GET /api/v1/profile 返回平台标准响应包装，接入示例说明与 OIDC /userinfo 返回格式的区别。
- 普通业务 Client 不再默认拥有内省权限；Gateway 仅获首批 System 资源的内省许可。合法跨 Client 内省不要求相同 client_id。
- 管理写入、协议切换、Secret 重置、删除、平台身份保护在同一事务锁和 ETag 约束下执行；影响有效授权的变更增加版本，使旧授权失效。
- 使用新增 Flyway 迁移，不改已执行 V17/V18。保留既有主键、client_id 和 Secret 哈希；不从应用名猜测平台身份。通过受信任 Admin/Gateway 客户端配置显式认领并校验唯一性，不匹配时停止初始化并给出诊断。
- 已有单配置转换为单协议；存在两份配置时不自动选择默认值，标记待配置并阻止签发，管理员明确选择后启用。Admin/Gateway 根据显式平台声明单独初始化，不批量给其他应用补授资源。
- 新授权使用新的存储/缓存版本，旧授权不进入新路径，允许重新登录；数据库迁移前须备份，回退采用配套数据恢复和服务版本，不承诺旧镜像直接读取新结构。
- README 同步更新协议区别、客户端认证示例、Scope/资源配置、最小资料调用、网关内省配置及迁移/恢复步骤。

## Flagged concerns
- 授权收紧和现有接入中断：新增 aud/Scope 与内省用途后，原先仅凭同 IAM Token 的调用不再自动通过；需要 Rain 接受首批显式许可、旧会话重新登录及双协议应用待配置的切换策略，blocking。
- 框架空 grant 约束：内省身份采用不可签发的内部能力标记适配，必须覆盖全部标准/自定义签发入口的拒绝测试；需要 Rain 接受该方案，否则改为独立内省认证适配设计，blocking。
- 平台身份认领：Admin/Gateway 的可信 client_id 绑定必须来自实际部署配置；无法唯一确认时不得按名称或示例 UUID 猜测。实施与部署分别核对，缺失会阻止初始化，non-blocking。
- 跨仓安全链及直接访问：涉及 Boot Gateway 与 System 服务端准入，必须协调契约与依赖版本，并验证内部 Feign 调用；不具备下游拒绝证据时不得宣称资源隔离完成，non-blocking。
- 内省延迟与可用性：首批关闭正向缓存以保证撤销和权限变更生效，将增加 IAM 请求量；需要记录组合验证的延迟及故障行为，后续缓存性能优化不在本次范围，non-blocking。
- profile 命名：纯 OAuth 中它是平台定义的基础资料能力；文档必须说明其端点和字段，不能让接入方误以为支持完整 OIDC 或未实现的 email/phone，non-blocking。

## Verification strategy
1. 单元/安全链：单协议约束、切换清空/取消、配置版本失效、Scope 子集、aud、code/refresh 绑定、JWT/opaque 一致、内省用途及伪造 managedKey；含服务令牌申请 profile 和所有内省身份取 Token 的拒绝场景。
2. 资料：OIDC UserInfo 与纯 OAuth Profile 正常读取；缺 Scope、错误 aud、跨租户、无用户、过期、撤销均拒绝；响应字段白名单验证。
3. 网关/System：跨 Client 合法调用通过；错误资源、缺 Scope、root/onlyAuth 绕过、直连 System、内省客户端失效、IAM 停止均验证；确认 API Key/HMAC 既有用例不变。
4. MySQL/Redis 集成：已有数据迁移、双配置待配置、身份保留、防误删、并发保存/切换/密钥重置、事务回滚及旧授权拒绝。测试不得修改或清理用户业务数据。
5. 管理端 Vitest、Cypress、无自动修复 ESLint、生产构建；桌面 1440×900 与移动 390×844 检查五区、对话框、底部按钮、令牌展开、Scope 添加选择、错误/loading/空态及重复提交。
6. 最小相关 Maven 测试后运行受影响跨模块验证；组合环境验证 Admin OIDC 登录、菜单、资料和实际 API。分别报告静态、单元、Mock、集成及真实运行证据，未执行的部署或性能验证明确列出。

## Acceptance
Rain 以 YES 明确接受完整设计及全部 flagged concerns，包括两项 blocking 决策；进入实施计划审核，不代表提交、推送或部署授权。

## Accepted clarification
Rain 随实施计划以 YES 接受：越出内省许可 active:false 对外 401，成功内省后路由许可不足 403，依赖故障 503；不扩大内省资料可见范围。

## Accepted amendment: OIDC third-party Code — 2026-11-08
Rain明确确认：前端直接访问Token端点保持公共客户端认证none；OIDC应用也应支持第三方SDK Code接入，不能因选择OIDC而隐藏身份源能力。第三方Code验证并绑定本地用户后，OIDC申请openid返回ID Token和Access Token；纯OAuth仍不允许openid且不签发ID Token。这是Macula扩展登录流程，不声称为标准OIDC授权码流程。Rain以OK接受上述方向。

本修订取代前文将所有直接Token扩展限定为OAuth2.1、OIDC必须授权码的互斥规则，仅放开external_code，不放开sms/email/client_credentials。允许用户型小程序选择OIDC，不强迫配置不存在的网页登录回调。一个Client仍只选一个协议；资源/Scope/租户、来源绑定、防重放、自动绑定和已有网页登录安全边界不变。具体实现计划另行审核接受；本轮不继承上一轮镜像更新或业务数据库迁移授权。
