# Spec: IAM 托管邮箱验证码登录与认证规则收敛 (from intent.md 2026-11-11)
Status: accepted

## Source intent
[已接受的intent.md](intent.md)：邮箱认证与SMS一致；存量非空登录列表追加EMAIL、全部关闭保持；第三方图标横向单行；消息及公共规则收敛；登录白名单精简。

## Requirements
1. 登录页支持邮箱和验证码，Client登录控制增加EMAIL。页面、发码和认证执行相同策略；直接POST不能绕过关闭状态。邮箱登录成功使用现有Session、SavedRequest和授权码流程，不恢复任何退役grant。
2. 用户原有email/mobile字段及唯一查询能力复用，不新增用户字段、自动注册、更新邮箱或合并账号。邮箱规范化、用户类型与当前Client目录约束和SMS一致；歧义、停用及越权账号拒绝。暂不扩展SMS尚不支持的外部用户目录邮箱查询能力。
3. 全局邮箱开关默认开启，行为对齐SMS；具备托管登录能力的存量Client：NULL继承包含EMAIL的默认方式，合法非空显式列表追加EMAIL，空字符串/空集合保持关闭。管理员之后取消EMAIL必须持续生效。SERVICE、IN_APP及资源服务器不因此增加托管入口。
4. 邮箱验证码使用短期一次性摘要、发送间隔、Client/IP限流、尝试次数和原子消费，默认数值与SMS一致。验证码按通道、用途和Client隔离；短信和邮箱、不同Client及LOGIN/绑定用途不可互相消费。邮件发送成功提交后才激活验证码；故障不降级为认证成功。
5. 第三方入口改为横向居中的单行平台图标，显示集合、授权路径和场景约束保持；桌面及320/390px视口无页面横向溢出，每个图标可键盘访问并有可访问名称、焦点和名称提示。
6. 消息组件分清验证码安全、投递、模板及审计职责。平台发送实现通过有类型的通道契约调用；共同验证码算法复用，邮件正文与短信供应商模板合同分别保留。保留既有SMS摘要、Redis键及用途隔离。
7. 公共账号加载保留现有目录优先级但消除重复实现，托管适配器不自行承担目录加载算法；准入快照校验保留唯一完整规则；公共缓存不直接依赖external_code配置类。旧身份/Token序列化与缓存合同保持。
8. 主安全配置通过少量具名matcher表达静态资源、登录/绑定入口和健康检查；第三方过滤器与匿名匹配共用路径描述。不引入/login/**、/api/**等放宽规则，保持CSRF及Client策略先于副作用。只保留实际存在的公开端点。
9. 文档准确区分邮箱字段、邮箱认证、email grant与OIDC email Scope；当前未开放的Scope不宣称可用。README保持模块介绍，不再追加实现流水。

## Non-goals
不新增email/sms/password Token grant，不新增邮箱绑定证明方式、真实MFA因子、通用通知平台、外部身份接口或第三方来源能力；不移动现有序列化模型；不旋转密钥、改旧迁移校验和、执行现网数据操作、提交推送或部署。不顺带恢复已退役协议发码端点。

## Design
### 约束依据
已读取并应用Cloud AGENTS.md、REVIEW.md及.agents/rules/architecture.md、backend-development.md、frontend-development.md、testing.md：Java17、既有框架和样式、敏感认证默认拒绝、跨模块契约检查、必要注释、保留用户改动和分层验证。没有发现专门的组织品牌或额外合规技能，不声称已应用未发现的规范。执行sdlc-design；新增认证不走低风险快路径。

### 登录与公共账号
邮箱认证Filter/Provider归authentication，与SMS相同方式仅注册在登录链。新增邮件验证码主体使用区别于退役EmailAuthenticationToken的类型名，并补安全序列化适配，避免撞入旧Principal兼容读取。DefaultSecurityConfiguration只装配组件，保留当前config根目录位置。

ClientAccountDetailsService保留按可信Client快照加载用户名的用例，并明确托管标识解析用例；HTTP请求上下文由登录层转换为显式参数。托管员工路径保留本地目录优先，原显式Client路径保留扩展目录优先及员工回退；不以去重为由改变优先级。账号标识解析与最终主体必须一致，目录漂移/主体不一致拒绝。旧无Client门户兼容单独具名表达，不新增header/cookie作为可信路由。

EMAIL加入OAuth2HostedLoginMethod；全局键为spring.security.oauth2.server.login.email.enabled，默认true。原enabled(method)的二元分支改为明确枚举处理，避免EMAIL错误套用SMS开关。HostedLoginPolicyFilter明确映射每个入口和认证方式，登录页模型同步。

### 验证码与消息
authentication/verification承载通道/用途/Client作用域、验证码生命周期和存储契约；Redis实现复用现有Lua原子消费与限流算法。SMS保留原键、HMAC用途及绑定短信语义，EMAIL使用独立命名空间，不迁移既有验证码。

message下按mail、sms、template、audit组织投递实现。短信发送接口由Aliyun/Tencent实现，通过受控注册选择，重复/未知供应商拒绝；邮件发送接口由现有SMTP实现。保留MAIL_LOGIN/SMS_LOGIN配置及密文凭据。模板读取、HTML转义和审计按职责复用，不使用无类型Map充当所有通道的统一消息。

发码只在受控Client及允许方式内进行；与SMS相同，对格式合法的输入执行受限发送，不在发码响应中区分账号存在性。认证阶段才按当前Client解析唯一合法账号。日志仅保留目标摘要及安全错误分类。

### 公共规则整改
IdentityDirectoryService保留真实使用的准入/资料补充/绑定后操作。将快照完整有效性谓词集中到准入投影或明确的纯校验职责，删除无调用的较弱requireCurrent版本；事务内继续检查registration、identityKind/value、来源版本、flow、组织及有效期。

ProviderCacheCipher通过公共安全配置或装配适配获得现有摘要配置，保持原环境变量、派生上下文及密文格式；本轮不轮换密钥。配置文档移除暗示email Scope已开放的描述，保留邮箱属性与映射说明。

### 匿名访问与第三方入口
增加职责明确的登录RequestMatcher构造器，将资源路径、登录/绑定端点分组；健康端点单独保留。第三方路径来自同一个显式入口描述，供Configurer及匿名matcher共同使用，排除空路径和UNKNOWN；不自动将所有枚举值变成匿名入口。已有静态资源及回调HTTP方法匹配保持，新增邮箱发码只接受POST。经确认当前无H2依赖/启用配置，移除/h2-console/**放行。

主配置目标表达：
```java
.authorizeHttpRequests(authorize -> authorize
    .requestMatchers(LoginRequestMatchers.staticResources()).permitAll()
    .requestMatchers(LoginRequestMatchers.loginEndpoints(thirdPartyLogin)).permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .anyRequest().authenticated())
```
这是设计表达，不是已写入源码的实现。真正的认证、发码和绑定仍在原过滤器/服务验证，permitAll只允许匿名到达入口。

图标使用可识别且具有可用许可的本地平台资源，不加载第三方CDN。flex单行，320/390px下五个入口仍可操作，保留aria-label、可见焦点与悬停提示。新邮箱表单保持加载、错误、成功、倒计时与重复提交控制，敏感输入不持久化到浏览器存储。

## Data and interfaces
- 新增POST /login/email：表单email、code，沿用登录CSRF和成功/失败续转。
- 新增POST /api/v1/email-code：表单email，沿用SMS发码交互和响应形状；无可信Client策略或EMAIL关闭时，在发码前拒绝。原/api/v1/oauth2/email-codes继续410。
- hostedLoginMethods原有NULL继承、空列表全关语义不变，新增EMAIL值，System校验、Admin复选框、默认值及指南同步。
- 邮箱验证码默认5分钟、发送间隔60秒、每Client/间隔200次、IP/间隔20次、最多5次尝试，与当前SMS默认一致，可独立配置。SMS现有数值与配置键不变。
- 准备一次性追加Flyway迁移，当前V24已有其他功能，预期使用下一可用版本V25：仅对合法、具备托管能力的非空显式集合追加EMAIL；NULL、空、非法配置、SERVICE/IN_APP/RESOURCE_SERVER保持。幂等防重复，不改Client ID、Secret、协议、来源、用户绑定或authorizationVersion，不主动撤销既有Token。configurationVersion依赖完整快照，新值自然触发旧表单409。
- 不能在每次读取时给非空集合补EMAIL，否则管理员无法关闭。迁移仅在独立新建测试库验证；业务环境的执行与协调升级单独审批，当前不连接业务库。新增后旧版本枚举无法解释EMAIL，回滚需配套恢复显式配置，不能只回退IAM镜像。

## Flagged concerns
- 存量认证面扩大：用户已明确要求非空显式列表也开放EMAIL，明确全关仍关闭；新增邮箱认证受全局开关、渠道和账号校验限制，non-blocking。实际业务环境邮件渠道及账号邮箱质量仍由运维核验，不伪造邮箱或自动整改。
- 一次性Client配置迁移：源码设计包含数据更新，必须由本次规格/计划明确批准；现网执行与回滚准备不在本地授权内，non-blocking for local design/build，业务上线前由负责人确认，未批准不得执行。
- 现有V24及其他未提交改动：保留用户工作；实现前再核对迁移版本，版本冲突只追加下一编号，non-blocking。
- 目录优先级差异：有意保留现有两个用例的优先级，返还主体与唯一候选不一致时拒绝；不能通过强制统一成一个fallback顺序获得代码简短，non-blocking。
- 图标许可及SMTP环境：核对本地图标出处，缺失时补有可用许可的SVG；本地测试使用假SMTP/捕获邮件，不向真实用户发送，真实邮箱投递另验，non-blocking。
- 作者署名待确认：不推断作者；不影响认证设计，non-blocking。
- SDLC要求接受后提交与用户不提交约束：遵循用户边界，只维护本地accepted工件，不提交或推送，non-blocking。

## Verification strategy
- API/System：EMAIL可保存与取消，NULL/空/显式集合及不支持托管的应用类型保持边界；新旧版本和并发保存拒绝；一次性迁移覆盖合法集合、全关、已包含EMAIL、非法配置及迁移后主动关闭不会重开。
- IAM：邮箱认证成功及授权码+S256/OIDC续转；错误、过期、已消费、跨通道/Client/用途验证码拒绝；邮箱重复/停用/越权拒绝；EMAIL关闭不能发码或认证；SMTP失败不激活；CSRF缺失拒绝；旧email grant和旧协议发码端点仍拒绝。
- 抽象与兼容：SMS及绑定短信原键/摘要夹具、事务与强准入谓词；两种目录优先级及主体一致性；旧邮箱Principal、新邮箱主体、Session/Redis往返；公共缓存旧密文可读，不通过改密钥取得测试成功。
- 安全链：匿名路径逐项对照旧清单及真实第三方描述；近似路径、空路径、管理API不被放行；资源、错误页依赖、health、全部回调和绑定入口继续有效；H2不存在时不再匿名开放；CSRF/Client策略顺序不变。
- UI：邮箱页签及发码倒计时/错误/重复提交；Client复选框保存后生效；五家图标正确、键盘可达、原链接与可用条件保持；桌面、320px和390px实际浏览器检查单行和溢出。
- 执行顺序：目标API/System/IAM测试与隔离Redis/SMTP；独立MySQL迁移/绑定；跨模块全仓测试；Admin Vitest、构建及目标Cypress；源码Checkstyle与git diff --check。没有外部环境时明确未执行范围，不将Mock视为真实投递或厂商联调。

规格接受记录：2026-11-11 用户以“OK”明确批准范围及Flagged concerns；进入本地工程计划阶段。未授权提交、推送或业务库操作。
