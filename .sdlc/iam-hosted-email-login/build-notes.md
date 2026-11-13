# Build 实施记录

Status: supplement-implemented-human-confirmed。用户yes确认本轮Build完成；Stage 4实际验证已记录，未自批PR或执行远端操作。

## 邮件/SMS统一补充（2026-11-12）

- 公共端口为VerificationCodeChannelService，VerificationCodeContext只携带可信Client/用途/显示名；SmsCodeService与EmailCodeService组合共享内核，保留原Redis/HMAC/TTL/配额合同。Email不支持绑定，绑定SMS不能缺Client。
- UnavailableVerificationCodeService统一设施缺失的失败关闭。删除旧SmsService、SmsServiceImpl、UnavailableSmsService及未用便利重载；绑定isSmsAvailable仍有效，原托管开关不替代绑定证明策略。
- Filter模板统一POST、单值、trim、长度与六位验证码；Provider模板统一一次消费、账号状态、无凭据结果、details/角色与真实OTT时间。原Token的包名、字段、父类、UID和精确类型允许列表不变。
- Configurer采用固定HttpSecurity及显式构造，删除ApplicationContext查询、伪泛型、可变setter、固定Converter/参数副本与无意义身份权限Mapper。两个Configurer保持独立，不互相覆盖，Provider只在登录链注册。
- 新增双通道合同、设施缺失装配、真实Redis短信/OIDC与当前Token因子存储用例；现有绑定/MFA/托管测试按公共端口更新，旧短信服务测试迁包。这里只证明代码已编译，尚未证明用例运行通过。
- README、架构类图及配置说明以当前职责更新。补充范围外124个已有import整理文件与先前快照一致，未生成package-info.java。
- 无名单外变更。用户yes已通过本轮Build人工门；Stage 4结果以verification.md为准。端内应用新模型仍待策略语义确认，不夹带到本次已批准实现。

## 此前已确认实现


## 实现边界

- 登录新增EMAIL，Filter/Provider只注册在登录链，Session续转仍走标准授权码+S256/OIDC。新主体使用EmailCodeAuthenticationToken，不复用退役EmailAuthenticationToken标识；新旧受限序列化分别保留。
- VerificationCodeStore是安全存储端口，Redis实现共享原限流与消费Lua；VerificationCodeService组合申请、投递、激活、消费。SMS固定键/摘要合同保留，EMAIL独立email-login空间；发送失败不会激活。
- message按mail/sms/template/audit分包；MailSender/SmsSender是真实投递接口，不用共同父类强行统一不同模板协议。邮件只认服务端MAIL渠道和MAIL_LOGIN模板。SMTP提交成功不等于收件人实际收到。
- HostedUserDetailsService成为受信上下文到ClientAccountDetailsService的薄适配。托管用户名本地员工优先、显式Client扩展目录优先的旧语义保留；邮箱/手机唯一候选与最终主体ID/租户必须相同。
- Admission完整快照谓词在绑定锁内执行；缓存安全配置摆脱协议配置类型依赖，原键及加密上下文不变。
- 匿名匹配按静态资源、精确登录/绑定入口、health组织，第三方路径与Configurer同源。移除没有启用设施的H2放行，不增加/login/**或/api/**。
- 页面三种认证页签，第三方本地SVG横向单行，原显示条件和路径保留；Admin支持EMAIL保存及主动取消。
- V25只准备源码及隔离库验证，未应用业务库。合法非空显式列表追加EMAIL，NULL继承、空/不支持类型保持；迁移一次性执行，不在读取时补值。大小写严格匹配枚举，非法小写配置保持原样。
- 按用户要求删除IAM全部15个纯说明package-info.java，确认没有NullMarked/NonNullApi等运行时包注解；后续不再生成。职责说明保留类级Javadoc与架构文档。

## 实际偏差与修正

精确新增路径及理由均记录于plan.md的Implementation deviations及相邻追加项。

1. OIDC实际链路要求真实主认证时间。邮箱凭据和账号核验后添加框架OTT因子，MFA分类增加EMAIL；不在签发时补造、不声称完成额外MFA，不伪造acr/amr。Access Token权限排除框架因子，业务角色仍保留，分别有资源Scope和最小Scope断言。
2. 新邮箱主体加入生产受限授权JSON的精确类型白名单；不扩大任意前缀。
3. 真实迁移测试将保护字段按现有schema的default_protocol核验，而不是不存在的protocol列。
4. 原SMS MFA测试候选缺少ID，现补齐与真实fixture主体一致的42，保留主体一致性规则及503断言。
5. 既有并发IT的缓存断言从clear对齐当前生产invalidate；补EMAIL/PASSWORD竞争，保留409与仅一次失效。没有修改生产缓存逻辑。
6. 清理星号导入时曾漏anyBoolean；编译发现后补齐，后续全仓测试通过。
7. 前次自动审批超时未实际写入；用户确认macula-cloud可写后，已通过授权写入，没有绕过沙箱。

## 保留范围

保留用户V24应用URL/IP工作及DefaultSecurityConfiguration所在config根包；OAuth2装配仍在config/oauth2。无依赖升级、真实邮件、凭据轮换、业务库、提交、推送、部署。架构图、类图与配置说明依据当前实现更新。


## 已授权本地镜像更新（2026-11-11）

用户随后明确要求更新镜像，已构建并定向替换本机macula-cloud-dev的System、IAM、Admin，采用--no-deps --no-build --force-recreate --wait；其余11个运行容器ID保持。更新前对本地macula-system做0600临时备份，System从V24自动迁移到V25成功。没有远端部署、提交、推送或凭据轮换。

实际镜像：System 99e23968d8e0、IAM 5e91f79986b0、Admin 09647d96bd58，标签均6.1.0-SNAPSHOT；运行容器镜像ID与新构建一致，全部healthy，聚焦启动日志无应用启动失败。Admin与IAM登录页HTTP200，System无Token菜单请求HTTP401。该记录是本地镜像/运行证据，不能代替真实厂商、设备或邮件送达验证。

## 本轮Stage 4补强

绑定页可用性断言明确区分页面的绑定服务Mock与真实绑定服务的端口能力传递，新增真实服务合同用例。初次错误夹具的失败输出已保留；定向用例与最终全仓测试通过，不以删断言掩盖问题。没有修改本轮计划之外的源文件，也没有修改Token字段、Redis/HMAC、协议grant、登录开关或平台身份校验。
