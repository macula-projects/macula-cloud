# Spec: 简化身份源配置与可选择的账号绑定映射 (from intent.md 2026-11-09)
Status: accepted

## Source intent
[Rain以YES接受的需求](intent.md)：隐藏技术选择，明确Code场景，自动获取企业属性，并允许选择账号匹配字段。

## Requirements
1. 新建/编辑身份源不显示Flow、接口版本或通讯录策略。平台凭据、必要企业标识及权限说明保留；不清空已保存的内部合同。
2. 新来源使用适用场景下官方推荐且已核实兼容的接口。已有来源不静默升级Flow或放宽准入；未核实能力明确不可用，不能以“最新”为由启用。
3. 企业微信同一自建应用共享一个网页来源的凭据；浏览器扫码、端内网页授权分别取得Code，模式绑定服务端一次性state。JSAPI/SDK凭证继续按独立能力核验，不混入网页回调。
4. 微信小程序同一来源支持登录Code和手机号Code。业务前端分别提交WECHAT_MINIAPP_LOGIN_CODE、WECHAT_MINIAPP_PHONE_CODE；后端检查Client、来源、能力并分别调用平台接口。不存在自动猜测或失败后换接口。
5. 企业来源认证后自动补充成员ID、业务工号、手机号、邮箱。成员ID与工号分开；字段值和授权可信程度由服务端适配器给出，不采信前端提交的资料。
6. 自动取资料不等于每次四项必返。明确非成员/停用仍拒绝；暂时故障、权限不足及字段为空分别处理。缺少补充资料不得放宽已有准入，也不凭空否定已经核实且满足原准入的身份；缺少所选匹配属性不得自动绑定。
7. 建议本轮每个来源最多启用一组“源字段→IAM目标字段”；界面显示字段、权限前提及对应关系。不提供任意列名、表达式或自动多字段兜底。
8. 只有可信属性、唯一匹配且满足当前Client用户目录/租户限制的有效账号可自动绑定。未匹配、重复或冲突走已有账号证明/手动绑定，不创建账号、不覆盖资料/权限、不自动改绑。
9. 已有稳定外部身份绑定优先；修改映射只影响尚未绑定身份，保存递增来源版本，使在途旧state/票据/自动匹配快照拒绝继续写入。
10. 兼容三种旧绑定规则及现有来源/身份键。版本、组织、凭据域、Code防重放、绑定唯一约束和事务重查保持有效。

## Non-goals
不增加IAM工号字段，不实现多条规则优先级、任意数据库字段映射、组织全量同步或账号资料回写；不新增平行登录/发Token接口；不合并微信网站应用与公众号凭据；不放开未核实SDK能力。不修改Boot/Gateway，不提交、推送、部署或操作业务库。

## Design
### 策略与职责
遵循Cloud AGENTS.md、architecture.md、backend-development.md、frontend-development.md、REVIEW.md；bands.yaml为示例，不能作为生产SLO证据。未发现额外适用的组织安全/合规技能。
System维护来源目录与受控映射，System API保存共享字段枚举/兼容规则，Admin按目录渲染；IAM各平台适配器负责换码、组织验证与属性来源，公共绑定层只消费核验结果。复用现有目录、缓存、票据与事务设施，不新增认证框架。

### 场景与接口
下表是当前源码的接入路径，不冒充已完成最新官方权限核实；Plan阶段须补齐每项HTTP方法、参数、权限、身份字段和真实可用范围。

| 场景 | Code获取 | 服务端固定处理 |
| --- | --- | --- |
| 企微浏览器 | login.work.weixin.qq.com/wwlogin/sso/login | GET qyapi.weixin.qq.com/cgi-bin/gettoken；GET /cgi-bin/auth/getuserinfo |
| 企微端内网页授权 | open.weixin.qq.com/connect/oauth2/authorize，snsapi_base | 同应用Token与GET /cgi-bin/auth/getuserinfo；发起模式保存在state |
| 微信小程序登录 | wx.login | GET api.weixin.qq.com/sns/jscode2session；返回OpenID身份，session_key不下发 |
| 微信小程序手机号 | getPhoneNumber授权回调 | 应用Token与POST api.weixin.qq.com/wxa/business/getuserphonenumber；得到手机号证明，不推断OpenID |
| 企微成员资料 | 已核验成员ID | GET qyapi.weixin.qq.com/cgi-bin/user/get；敏感字段所需授权/补充接口待官方补证 |
| 钉钉成员资料 | 已核验成员ID | POST oapi.dingtalk.com/topapi/v2/user/get；字段/权限待补证 |
| 飞书成员资料 | 已核验OpenID | GET open.feishu.cn/open-apis/contact/v3/users/{open_id}，user_id_type=open_id；使用来源企业Token |

官方入口：[企微扫码](https://developer.work.weixin.qq.com/document/path/98152)、[企微网页授权](https://developer.work.weixin.qq.com/document/path/91022)、[微信登录](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html)、[微信手机号](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html)、[钉钉资料](https://open.dingtalk.com/document/development/query-user-details)、[飞书资料](https://open.feishu.cn/document/server-docs/contact-v3/user/get)。本轮正文不可读取或为空；既有证据保留于[证据记录](../iam-unified-auth-provider-completion/provider-contract-evidence.md)。

### 属性和匹配
归一化字段为member_id、employee_no、mobile、email；同时携带来源、组织、身份种类、来源版本和可信依据。UserID不默认充当employee_no，个人UnionID不充当企业成员证明；旧资料中的job_number等字段只有核实语义后才能映射。

| 源字段 | 可选IAM目标 | 条件 |
| --- | --- | --- |
| 成员ID、业务工号 | username | 由目标企业身份/目录权威返回 |
| 手机号 | mobile或username | 平台手机号证明，或已核实可信企业目录属性 |
| 邮箱 | email或username | 已核实可信企业目录属性；不接受普通社交显示邮箱 |

候选查询仅使用所选目标字段，复用UserIdentifierNormalizer：username去首尾空白、手机号保留区号且不自行补区号、邮箱沿用已有小写规则。源值先校验自身类型，再按目标语义比较；不得调用跨用户名/手机号/邮箱的通用兜底解析器。唯一性检查与锁内重查使用一致语义，历史非规范值及规范化冲突不自动修复、不靠排序取第一人。
已有绑定仍按registration+identityKind+identityValue解析；手机号Code只产生手机号证明，不产生普通登录Code的OpenID关联。资料获取在事务外；绑定事务内重查Client权限、当前来源版本、候选唯一性、有效账号及绑定冲突。

### 自动资料获取与兼容
新企业来源默认获取适用资料；调用失败不尝试其他Flow或企业凭据。只有当前组织、来源版本匹配且未过期的既有缓存可复用。仅取得部分字段时显示能力/权限说明，不显示通讯录模式单选。
历史REQUIRED等更强成员准入约束继续执行并只读保留；不能直接全部替换成OPTIONAL。组织标识来自固定来源配置或经核实的企业身份材料，不从请求corpId/tenantKey猜填；飞书等需要固定组织时保留业务可读输入。
未核实的字段不宣称可用于自动绑定；可选择的映射来自服务端能力目录。人工手动绑定证明不能伪装成已取得企业敏感资料。资料只进入必要核验上下文及现有受控缓存，不新增全量员工资料存储，不落明文敏感日志。

## Data and interfaces
- 复用/api/v1/identity-providers及/catalog；新增bindingSourceField、bindingTargetField、受控字段组合和可用性/权限原因。autoBindEnabled保留，Flow/目录策略不再由新Admin编辑，内部仍校验。
- 新增来源表可空的binding_source_field、binding_target_field；在实施时最高迁移之后追加，不重写V20或历史迁移，不更改用户表。
- 旧MEMBER_ID_TO_USERNAME、EMPLOYEE_NO_TO_USERNAME、VERIFIED_MOBILE_TO_MOBILE分别确定转换为member_id→username、employee_no→username、mobile→mobile；未知规则显式待整改，不猜测。绑定表、来源ID及身份键不变。
- bindingRule保留过渡兼容。新旧字段同时提供必须等价；旧客户端不理解新映射时不得静默清除。更新沿用版本冲突保护，缺省新字段与显式清除区分；下线旧字段前需扫描全部调用方并记录兼容/回滚策略。
- 切换映射、新增资料要求或改变内部合同递增来源版本。已有绑定不重算；不得改ID/Secret、跨来源复用Code或扩大Client来源allowlist。

## Flagged concerns
- 单组映射范围：建议先支持一组，不含多规则优先级；需Rain确认，blocking（设计范围）。
- 平台字段/授权证据：企微、微信正文读取失败，飞书/钉钉入口为空；当前目录适配器均未提供完整邮箱属性。不能据Mock承诺完整四字段或“最新接口”；实施者须补官方证据和权限合同，blocking（相关字段/能力启用与验收，不妨碍草稿审阅）。
- 企业邮箱可信度：企业目录返回邮箱不自动证明个人邮箱控制权；仅在固定组织权威和批准的Client账号目录内匹配，个人/外部邮箱不自动兜底。该信任前提需Rain接受，blocking。
- 历史强准入：隐藏策略但保留旧更强规则；新来源资料补充失败不自动降低组织准入。需Rain接受此兼容差异，blocking。
- 规范化与回滚：历史重复/非规范字段不自动改写；新字段迁移和旧客户端写入必须证明无静默映射丢失；旧服务不理解新映射时不可直接回滚接管写入，blocking（发布前证明）。
- 真实平台验证：静态、单元、Mock、隔离Redis/MySQL分别报告；无真实凭据时不能宣称厂商/设备联调完成，non-blocking（本地实现），真实支持声明仍需对应证据。

## Verification strategy
1. API合同：目录无Flow/策略表单选项；非法映射、未知/重复参数、来源能力不匹配及新旧规则不等价在服务端拒绝；旧客户端更新不丢新配置。
2. 平台合同：逐接口精确验证URL、HTTP方法、字段与Token用途；覆盖成员ID/工号区分、邮箱缺失、权限不足、非成员/停用、超时/5xx、资料与身份不一致。
3. 身份与绑定：六种允许映射、所选属性缺失、重复/规范化冲突、跨Client/租户/目录、已有绑定不改绑、并发唯一性和锁内配置变化；保持已有账号证明路径。
4. 浏览器与小程序：同一企微来源两种发起和state回调，模式篡改/重放拒绝；两种微信Code显式分流、错误类型拒绝、无跨来源/跨Client复用、手机号不生成OpenID绑定。
5. 迁移：隔离MySQL旧规则逐条升级，ID/Secret/绑定/历史强准入不变；未知规则显式保留待整改。真实Redis验证来源版本与缓存隔离，不操作业务库。
6. Admin桌面/手机验证单组映射选择、条件权限说明、无Flow/策略、保存回显及账号证明提示；目标Maven、Vitest、无修复ESLint、构建、Cypress和git diff --check。此阶段仅设计，不声称测试已执行。

## Acceptance
Rain以YES接受本设计和全部flagged concerns，包括单组映射、企业邮箱的组织权威信任前提、历史强准入保留；外部接口/权限证据及发布前证明仍须实际完成，不因接受而豁免。不包含源码实施、Git或部署授权。

## 实施计划澄清
六种映射为全局白名单，仍按既有来源/能力限制：MOBILE_PROOF始终必须与本地mobile核对，因此仅支持mobile→mobile；mobile→username适用于另有稳定企业身份和可信目录手机号的来源。该约束沿用Requirements 4/8/10，不放宽既有手机号证明。CONFIGURATION类来源（支付宝仅凭据管理）继续不要求登录Flow。

## 2026-11-09 用户明确缩小企微范围
官方user/get正文确认新应用敏感资料需要本人授权。用户选择“暂保留成员 ID 绑定，敏感字段后续处理”。因此本轮企微仅提供member_id→username，不新增snsapi_privateinfo、user_ticket或授权弹窗，也不把userid冒充工号。此为用户明确暂缓，不能记为敏感资料已经实现；其他平台及映射实施继续。
