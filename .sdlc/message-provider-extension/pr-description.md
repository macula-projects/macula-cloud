# 扩展邮件服务与自定义短信渠道

平台管理员可选择企业自建 Exchange、163 免费邮箱和个人 QQ 邮箱；自定义短信可配置 POST URL 与认证 Header，沿用现有签名、场景、语言和供应商模板编号。管理端测试发送与 IAM 登录验证码使用相同固定 JSON 契约。

邮件和短信接口保留在职责包，实现进入 impl，登录编排进入 dispatch。新增 STARTTLS 强制升级、证书校验及有界 HTTP 响应/总超时。Header 不回显，空白保留，填写后整体替换；停用服务使用已保存配置，避免未完成编辑阻止关闭。

## 验证
- `mvn -o test -Plocal`：709 通过、0 失败/错误；既有数据库专项因未设置 SYSTEM_MIGRATION_IT 跳过 1 项。本次未改 schema。
- `npm run test:unit -- --run`：223/223。
- Admin 与隔离 IAM 预览完整 Cypress：101/101，无 pending。
- Admin 构建通过；变更 Java Checkstyle 0 违规；scoped ESLint 0 errors（保留格式 warnings）；diff check 通过。
- 两轮 Bugs/Security/Compliance 审查：首轮 1 项 Minor 经独立复核后修复，第二轮无新发现。

## 边界
Exchange 仅企业自建账号密码 SMTP AUTH；不包含 Microsoft 365/EWS/NTLM。短信接口需符合文档中的固定请求/响应契约。真实供应商账号、CA 和终端送达仍需单独联调；本次没有发送真实邮件或短信。

## SDLC 依据
- [Intent](intent.md)
- [Spec](spec.md)
- [Plan](plan.md)
- [Verification](verification.md)
- [Review findings](review-findings.md)

本地 Compose 镜像运行验证已完成；审查修复后只补更新 Admin，实际服务资源与新产物一致。真实供应商投递未验证。

本变更已获得提交、推送和创建 PR 的授权；合并仍待人工审阅。
