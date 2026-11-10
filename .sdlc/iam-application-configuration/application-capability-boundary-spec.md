# Spec: 应用类型与身份源能力排他适配
Status: accepted
Author: Rain

## Source intent
沿用已接受[intent.md](intent.md)。Rain本轮明确：移动客户端仅SDK/AppLink且只能选1个；小程序仅小程序能力；二者可并存IAM内置登录但IAM页不显示第三方；Web/SPA在IAM页展示第三方网页图标；新增端内应用只走端内能力不显示标准IAM页；后端仅client_credentials。

## Requirements
1. NATIVE：最多关联1个真实注册的移动SDK或AppLink来源，不允许关联任何第三方网页登录/端内JSAPI/小程序能力。可独立开启IAM内置PASSWORD/SMS托管入口，与该第三方直接登录并存；IAM页不展示第三方图标，显式伪造第三方网页发起和回调同样拒绝。
2. MINIAPP：仅选择1个平台的小程序注册来源；同一微信小程序来源可同时使用登录Code及手机号Code，不把两种Code当两个应用或要求二选一。仅展示目前已实现的微信/企微小程序，其他平台确有适配后才增加。IAM内置入口可并存，页面和服务端均禁止第三方网页登录。
3. WEB_SERVER/SPA：只关联通用第三方网页授权能力，在IAM标准登录页下方展示已关联且适合实际浏览器的图标，可关联多个网页渠道；不能关联移动SDK、小程序Code或飞书/钉钉端内JSAPI。默认展示IAM页面，不因为UA识别到平台容器就抢先自动跳转。网页登录能力与端内专属能力分别筛选，公众号/企微端内专属授权归入端内应用。
4. 新增IN_APP（端内应用）：固定1个已实现的端内来源/能力。企微端内、微信内网页直接进入该来源网页授权；飞书/钉钉端内由业务页面JSAPI取Code并走既有external_code。均不显示IAM标准登录页，不提供独立密码/短信回退。来源单选使目标容器明确，无需再增加重复的平台选择字段。
5. IN_APP使用网页授权能力时保留IAM授权码/PKCE及一次性state；JSAPI能力按固定Code Type换Token，不把“直接进入授权”强制解释为所有厂商都用302。错误容器、能力撤销或来源失效明确失败，不回退其他厂商/内置登录。已有普通IAM会话不能绕过端内来源约束；已有外部身份证明后的专用账号绑定流程保留，不能当独立登录入口。
6. SERVICE：服务端固定仅client_credentials，管理端不提供用户登录、第三方来源、OIDC、回调、PKCE、刷新令牌及无关参数；保留必要应用信息、客户端认证/凭据、Scope、有效期和接入说明。
7. 前端筛选、System保存/目录、IAM托管页/发起/回调/Code兑换/账号证明/SSO续转使用同一类型边界与来源数量限制；请求不能自报终端或借其他grant绕过。第三方应用Secret仍只在服务端，平台Code与IAM授权码/Token严格区分。原多条字段绑定及注册身份域不变。

## Non-goals
不新增未经核实的平台SDK/小程序，不改变厂商取码/换码接口，不增加平行Token接口，不自动合并账号或改写凭据，不更新镜像、业务数据库、提交或推送。

## Design
共享应用类型、能力分类及来源数量规则驱动Admin、System和IAM；保留网页登录与直接Code两条路径，只收窄各类型可用集合。标准Web不再需要增加机密客户端端内Code路径，端内场景归IN_APP。Native/Miniapp的IAM入口与其单个直接身份源独立；IN_APP按固定能力自动进入正确厂商流程，不能显示通用标准页作回退。只读接入指南按实际类型/能力给步骤。

## Data and interfaces
新增applicationType=IN_APP；复用现有browserCapabilityBindings、externalProviderBindings等引用，不新增凭据表或平台选择字段。小程序两种Code必须固定同一registrationId。接口继续使用既有Client CRUD和OAuth端点，不暴露flow/协议版本选择。
旧配置保留Client ID、凭据及用户绑定，不自动猜类型或丢弃引用；新增/保存及新认证入口执行新规则，旧不兼容关联展示待整改并拒绝继续使用。既有Web/SPA端内接入拟提供管理员显式确认的受控转换到IN_APP，保存前展示grant/登录方式差异，保留身份与Client ID；其他类型仍只读，不做批量转换。是否需要追加迁移由计划阶段核对实际字段决定。

## Flagged concerns
- NATIVE只能1个来源、Native/Miniapp可并存IAM内置入口已由Rain明确确认；小程序拟定1个注册来源（允许同来源两种Code）、IN_APP拟定1个端内来源，需在本次规格接受时确认：[non-blocking；Rain于2026-11-10明确接受]。
- IN_APP不显示标准IAM页，但飞书/钉钉JSAPI仍需业务前端集成取码，不会自动变成IAM网页跳转；端内来源限定还必须防止已有普通IAM会话绕过。此语义与覆盖范围需本次接受：[non-blocking；Rain于2026-11-10明确接受]。
- 新模型替代之前“Native/Miniapp可关联第三方网页登录、Web/SPA可选端内Code”的范围；已保存的不兼容配置将停止新发起的该渠道登录。为保留原Client ID，拟提供旧Web/SPA端内应用显式转换，作为原类型只读规则的有限例外；需本次接受，不自动执行：[non-blocking；Rain于2026-11-10明确接受]。
- 适用AGENTS.md及architecture/backend/frontend/testing规则，使用sdlc-design；未发现额外组织策略技能。真机/平台后台联调仍须接入方环境完成：[non-blocking]。

## Verification strategy
共享矩阵逐类型正反测试和数量/平台注册隔离；Native/Miniapp的IAM只显示内置且发起/回调拒绝网页登录，第三方直接入口仍可用；同一小程序双Code同源约束；Web/SPA第三方图标及默认不自动直登，桌面/手机浏览器筛选（包括已核实钉钉H5网页授权）；IN_APP网页授权/JSAPI分别成功、错误容器与来源变更拒绝、无标准登录及既有普通会话不能绕过、专用绑定证明不丢；Service仅client_credentials。覆盖显式转换/CAS/旧ID密钥绑定不变和失败无半更新，并执行全后端、前端单测/构建、浏览器及适用隔离IT，区分Mock与真机证据。


2026-11-10 Rain在完整矩阵、端内平台差异及三项flagged concerns展示后回复YES，明确接受来源数量、IAM内置入口并存、端内来源约束及保留Client ID的显式转换。上述blocking规格决策已解决；不自批后续增量计划。

应用类型排他边界的Stage3实现、实际偏差和开发检查已记录plan.md及verification.md对应2026-11-10节；实现完整性仍待Rain确认，未自批Stage4。
