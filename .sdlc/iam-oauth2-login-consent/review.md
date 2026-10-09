# IAM 变更评审记录

日期：2026-10-09。范围：`iam-oauth2-login-consent` 当前工作区相对 HEAD `4b50e88` 的 IAM 变更，包含暂存删除、未暂存修改及未跟踪新增文件。

## 合同与证据

- [需求](./intent.md)、[设计](./spec.md)、[计划](./plan.md)。
- [正式验证](./verification.md)：11 后端模块、69 项测试通过；IAM 34 项及打包通过；管理端单测 10 项、Cypress 8 项、IAM 桌面/移动端 8 组检查通过。
- 按根 REVIEW.md 执行 Bugs、Security、Compliance 三轮检查，Nit 上限 5。
- 依 sdlc-deploy 调用独立 sdlc-reviewer（iam_review_jackson3），主代理复核关键链路；不是用户批准，不接受组织风险。

## Important

0 项。未发现已确认的阻断问题，不表示不存在未覆盖风险。

## Nits

1 项，已处理：plan.md 旧记录仍直接写“保留显式 Jackson 2 Security 模块”，容易误读为当前方案。已标明“迁移前历史记录，已由 2026-10-09 Jackson 3 决策替代”，保留历史。仅修改文档，不改变运行时代码；修改后 git diff --check 通过。

## 各轮结果

### Bugs

未发现已确认的 Important。检查了 Redis 七类索引与主记录校验、剩余 TTL、Lua CAS、轮换/撤销、旧键墓碑；Jackson 3 双 mapper、自定义 token/Long、历史 JSON/JDK DTO；兼容刷新客户端绑定、scope 子集、原 grant、刷新复用/轮换；PKCE/OIDC、同意/拒绝和会话保存。

### Security

未发现已确认的新安全缺陷。自定义反序列化类型许可未开放整个包或 Object；UserInfo 校验本地 access token 活动状态，框架进一步检查 openid/ID Token；表单 CSRF、过滤器顺序、同源回跳及 consent state/principal 边界已核对。Gateway 继续使用 introspection，默认 opaque token/平台 claims 未切换。password/sms 业务与验证码默认 true 为用户明确保留的原有行为，不作为本次新增缺陷或已完成真实短信验证的依据。

### Compliance

未发现已确认的阻断性项目约束违反。检查中文授权信息、真实 scope、键盘/移动布局、保留及回滚说明；不虚构额外法规控制。

## 限制与下一人审门禁

生产 Redis/Nacos/Gateway、真实身份源、短信供应商及真实设备 Safari 未联调；隔离测试不等于生产 E2E。上线前必须遵守 README 的缓存切换/回滚限制，不能混写旧新 IAM 节点。

本轮只修正评审文档，未修改运行时代码，未提交、推送、创建远端 PR、合并或部署。用户尚未授权远端操作，因此 sdlc-deploy 的 PR 发布步骤暂停于人审门禁；需要用户审阅本报告并明确授权后续 Git/PR 操作。本报告不能当作 LGTM 或已发布 PR 的证据。
