# Spec: 邮件服务扩展、自定义短信与消息包结构整理 (from intent.md 2026-11-13)
Status: accepted
Author: Rain

## Source intent
[intent.md](intent.md) 已由 Rain 明确确认，状态为 accepted。

## Requirements
1. 邮件新增 EXCHANGE、NETEASE_163、QQ 三个独立类型，保留现有供应商标识及配置语义。
2. 企业自建 Exchange 可配置 SMTP 主机、端口、账号、密码和发件地址；默认 587/STARTTLS。163 免费邮箱及 QQ 邮箱默认分别为 smtp.163.com、smtp.qq.com，465/SSL，提示使用客户端授权码。
3. System 测试发送和 IAM 实际邮件发送均支持 STARTTLS；协商失败必须终止，不回退明文，保持证书与主机名校验。
4. 短信新增 CUSTOM_HTTP，管理员配置 POST URL 和认证 Header；保留现有签名、语言、场景、模板编号及变量配置方式。
5. 自定义短信在 System 测试发送与 IAM 登录验证码发送中采用相同请求及成功判定。失败不切换渠道，不自动重发。
6. 邮件、短信抽象接口与实现分包；调用方依赖接口，新增文本提供现有中英文国际化。

## Non-goals
不支持 Microsoft 365、EWS、Graph、Exchange NTLM/Kerberos 或匿名中继；不新增短信正文模式、任意请求映射或脚本。不整理其他 IAM 包，不提交、推送、部署、修改业务库或向真实用户发送消息。

## Design
- 邮件沿用 SMTP 发送器；安全模式为 NONE、SSL、STARTTLS，新增 Exchange 默认 STARTTLS，保留既有模式兼容性。Exchange 本轮以账号密码 SMTP AUTH 为接入前提，服务端需开放相应接收连接器和发送权限。
- 每个服务在 message.mail/MailSender、message.sms/SmsSender 定义各自需要的接口，实现在相应 impl 子包；登录编排放 dispatch，模板和审计保留职责包。System 把写死的短信供应商分支改为按稳定标识选择接口实现。
- System 与 IAM 不相互依赖实现；固定短信契约记录在两模块文档，以同一组契约样例校验一致性。本轮不新建通用框架模块。
- 自定义短信由服务端读取有效渠道及模板，浏览器不得在发码请求中指定 URL、认证 Header 或模板供应商。

## Data and interfaces
- 沿用现有 /api/v1/message-settings 路径、权限及平台全局唯一渠道约束。
- MessageProviderForm 增加 authHeaders（字符串键值映射）；MessageProviderVO 仅增加 authHeadersConfigured 状态，不返回 Header 名称或值。前端明确空白保留、重新填写整体替换；切换到 CUSTOM_HTTP 时要求重新提供认证 Header。
- CUSTOM_HTTP 使用 endpoint 保存 URL，credential 保存认证 Header 的 JSON；序列化后最多 2048 字符。account 对此类型不要求，sender 继续表示短信签名。config_json 保留非敏感选项；既有供应商存储格式不变，不新增数据库列。切换供应商不得串用旧凭据，表单中的不适用字段由服务端拒绝。
- POST Content-Type 为 application/json，请求固定为：
  {"phone":"+8613800000000","signName":"示例签名","templateCode":"LOGIN_001","templateParams":{"code":"123456"},"scene":"SMS_LOGIN","languageCode":"zh-CN"}
  使用结构化序列化；templateParams 只包含当前模板允许的变量。
- 自定义服务仅在 HTTP 2xx 且响应 JSON success 为布尔 true 时视为已受理，例如 {"success":true}；其他状态、空响应、非 JSON、业务失败均视为失败。提交成功不代表终端送达。
- URL 限制 HTTP/HTTPS，不允许 user-info、fragment 或自动跳转；内网服务可用，由有权限的管理员配置可信地址。Header 禁止 CR/LF、重复名称和 Host、Content-Length、Content-Type 等传输控制字段。连接超时 5 秒，总请求超时 5 秒，响应读取上限 64 KiB，不自动重试。
- Header 值按凭据保护；沿用现有存储保护级别，不宣称数据库静态加密。日志与错误不包含 Header、验证码、完整手机号、请求体及供应商原始响应。

## Flagged concerns
- Exchange 认证范围：本轮拟支持账号密码 SMTP AUTH；若目标服务器只允许 NTLM/Kerberos 或其他机制，需要另行设计。Rain 已明确接受此范围，原 blocking 已解除。
- 自定义短信契约：POST URL 与 Header 不能说明请求体及成功语义；上述固定 JSON 请求和 success=true 判定需 Rain 接受，已有服务可按此提供适配端点；Rain 已明确接受该契约，原 blocking 已解除。
- 真实环境验收：尚未获得自建 Exchange 和短信服务的联调条件，本轮用隔离 SMTP/HTTP 服务验证协议行为，真实环境兼容性不作已验证声明，non-blocking。
- 出站信任：自定义 URL 可访问管理员配置的内网地址，部署需保持系统设置权限和出站网络边界，non-blocking。

## Verification strategy
- 后端覆盖供应商预设、保存读取、旧凭据保留与切换隔离、Header 不回显、非法配置拒绝、禁用渠道拒绝和接口装配。
- 隔离 SMTP 验证 STARTTLS 成功、服务端不支持 TLS、证书失败和旧模式回归；隔离 HTTP 验证完整请求、2xx 业务失败、超时、重定向及超大响应拒绝，两服务使用一致契约样例。
- Admin 验证新增选项、动态字段、Header 编辑与保留、中英文文案、加载/失败/成功状态和重复提交；运行相关 Vitest、npm run build 及对应 Cypress 流程。
- 先运行 System/IAM 最小相关测试，再按跨服务风险运行 mvn test -Plocal；外部依赖不足时单独记录未执行范围，不把 mock 结果当真实服务验收。

## Policy sources
已查阅仓库 AGENTS.md、.agents/rules/architecture.md、backend-development.md、frontend-development.md、testing.md、REVIEW.md、bands.yaml，以及 sdlc-design 技能。未发现额外适用的组织合规技能；bands.yaml 示例指标不作为本功能已批准 SLO。
自建 Exchange SMTP 依据：https://learn.microsoft.com/en-us/exchange/clients/pop3-and-imap4/configure-authenticated-smtp 。

## Confirmed implementation clarification
Rain 已明确批准实施计划，并允许迁移现有数据；此授权不要求强制迁移，也不表示执行实际业务库变更。
