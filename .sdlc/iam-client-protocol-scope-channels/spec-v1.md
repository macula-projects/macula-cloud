# Spec: IAM协议、Scope与身份源接入方式整理 (from intent.md 2026-11-06)
Status: accepted

## Source intent
[用户明确提出的协议、Scope、平台地址、继承说明和身份源方式整理](intent.md)，承接已接受的应用配置重构。

## Requirements
1. 协议按接入目的区分“OIDC：用户登录”和“OAuth2.1：API授权”，明确OIDC建立在OAuth授权之上，共用Client身份和底层授权设置。OIDC模式要求openid；OAuth2.1模式不隐式补openid，不签发OIDC ID Token，不展示无用途的ID Token配置。授权码使用S256 PKCE；password/旧组合放历史兼容区，external_code按明确的扩展能力展示，不冒充标准授权码。历史Client不自动切换协议。
2. Scope改为服务端目录驱动的复选/多选，应用页不允许手输或创建任意值。OIDC先提供已实现的openid（必选）和profile（用户名、昵称）；email、phone、address、offline_access不列为可用能力。业务API scope来自平台统一登记的资源服务声明，包含标识、名称、说明和适用场景；本轮用受控System配置提供目录，不新建完整资源权限产品。未知历史scope显示兼容项并原样保留，可明确移除，不据此成为其他应用的可选权限；服务端阻止新增未登记值。不得把角色、URL权限或示例值当成实际API scope。
3. 应用配置的平台端点使用紧凑的“名称＋可换行文本/链接＋复制”行，取消输入框外观。只展示已验证元数据字段，保留失败与重试；平台JWKS只读，客户端JWKS输入仍位于private_key_jwt配置。
4. 登录页策略改为“使用平台默认 / 为此应用单独设置”。默认表示跟随IAM全局密码/短信开关；只影响托管页新认证，不继承外部身份源、grant或访问权限。单独设置显示密码/SMS；全局关闭仍优先。没有可信实时能力数据时不伪造当前有效状态，说明默认的来源和限制，保留null/空集合语义与服务端拒绝。
5. 调整身份源类型本身，而非仅增加接入方式标签。新建身份源先选择厂商，再选择具体来源类型，例如微信网站应用、微信公众号、微信移动应用SDK、微信小程序；企业微信扫码/网页登录、企业微信SDK、小程序；飞书网页登录、AppLink、端内网页免登；钉钉网页登录、SDK、端内网页免登；GitHub OAuth与阿里云号码认证维持其真实类型。每种类型只显示并校验对应凭据、回调和接入说明，不再要求微信小程序填写网站/公众号凭据。类型决定允许的协议、场景和凭证能力。应用按类型关联具体已配置身份源，内部映射browser provider、external_code、code_type及固定registration，移除原始*_CODE复选清单。微信小程序身份登录/手机号授权属于同一小程序源的不同授权能力，仍由应用明确选择，不能自动同时开启。
6. Web/SPA提供网页登录和兼容端内方式；Native提供系统浏览器登录及已实现SDK；小程序仅提供对应平台小程序方式；Service不显示用户登录渠道。服务端目录包含名称、场景、结果、所需配置与不可用原因；前后端共同校验。关联与取消只作用于本Client，不改写全局身份源。旧绑定/未知项不静默丢失，读取、异步目录、分区切换和保存均保留完整草稿。

## Non-goals
本轮不扩展email/phone/address声明映射或离线授权，不重写角色/URL权限体系，不自动创建业务scope，不迁移用户身份域，不支持同平台多个浏览器registration，不托管私钥，不修改已有Client或自动重置凭据。第三方真实设备验证与Mock证据分开。

## Design
System提供统一的协议/Scope/接入能力目录及写入校验；IAM执行与所选模式一致的真实授权行为；Admin按业务标签组织选择与紧凑展示，沿用满幅四分区和单一草稿。身份源按具体来源类型配置凭据和声明可用方式，应用只负责关联哪一个来源及启用其适用能力。厂商分组与具体身份源类型分开建模，不能继续用单个WECHAT/FEISHU枚举承担所有来源类型。

## Data and interfaces
在已部署V14之后追加V15，增加nullable protocol_mode并同步System Form/VO/Entity与IAM映射。null表示历史兼容；新建明确模式。旧PUT省略新字段保留，不回填；显式切换展示差异并由服务端校验scope/grant组合，保持原有完整快照CAS和缓存提交边界。身份源增加显式来源类型并保留厂商归属、registration标识；已有未分类来源作为历史兼容类型保留，不猜测其类型、不自动拆分凭据。目录接口新增协议/Scope及具体来源类型元数据；保留原browserLoginProviderTypes、externalCodeTypes和externalProviderBindings存储及SDK线协议，不要求接入应用更改code参数。业务Scope由System受控配置集中登记，应用配置页仅选择。

## Flagged concerns
- 协议必须在服务端生效：目前无条件补openid；只换前端标签会继续允许纯OAuth Client使用OIDC。需新字段与真实签发测试，non-blocking。
- 业务Scope没有现成统一目录：必须由资源服务负责人明确登记，不能从历史示例推断；目录未配置时新服务/外部凭证应用应提示先登记适用范围，不能回退为手填或伪造权限。本规格选择受控集中配置作为本轮目录来源，已由Rain接受，non-blocking。
- 标准Scope文案不等于资料输出：本轮只提供已实现openid/profile，其余待独立需求补齐，non-blocking。
- 来源类型拆分涉及凭据加载、浏览器回调/state、SDK固定绑定与账号解析整个链路，不能只改管理端枚举。旧来源/账号绑定保留旧兼容解析；新来源必须绑定确定的registration及明确身份域，不允许不同企业或独立来源因同厂商/同subject串号，也不自动跨来源合并账号。仅在明确共享身份域并有测试证明时复用已有绑定，non-blocking。
- 不改变已发布V14；旧Client保持兼容模式与未知scope/绑定，显式转换才验证新模式，不撤销已签发令牌，non-blocking。

## Verification strategy
覆盖OIDC与OAuth2.1真实授权、ID Token/UserInfo边界、S256、非法组合与历史兼容；Scope目录、服务端拒绝未登记值、旧scope无损、业务范围为空时失败提示；不同具体身份源类型的独立凭据表单与校验、按类型的凭据加载/回调/SDK映射、旧来源无损、跨registration/企业同subject拒绝串号及固定绑定；继承/单独配置与全局关闭优先；紧凑地址复制、元数据失败、满幅/390px布局及分区保存。执行相关后端回归、完整Admin Vitest/build、目标Cypress、V14到V15隔离升级与Checkstyle/ESLint。保持已有真实Mapper并发与所有认证拒绝路径。

## Governing policy
根AGENTS.md、REVIEW.md、architecture.md、backend-development.md、frontend-development.md、testing.md、dependencies-release.md；未发现额外组织策略。规范与源码依据见[research.md](research.md)。

2026-11-06：Rain在来源类型修订及设计出口问题后回复“对”，明确接受本规格与flagged concerns。该确认不代表后续实施计划已获批准。
