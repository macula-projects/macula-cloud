# Intent: IAM OAuth2 升级兼容与登录授权界面

Status: accepted

## 来源与补录说明

本文件补录本会话中用户已确认的需求，不新增范围、不代替人审或 Git 提交。用户此前要求执行 IAM 升级修正，在补充 Redis、PKCE、OIDC 和兼容 password/sms 要求后回复“确认”；随后明确要求 Jackson 3 迁移并回复“改”。本轮在明确询问“实现完成、补齐 intent、进入正式测试”后，用户回复“继续”，授权继续该步骤。不包含提交、推送或部署。

## 问题与目标

升级 Spring Security/Authorization Server 后，修复 IAM 协议扩展、授权持久化及序列化兼容，并提供专业化、支持移动端的登录和授权界面。

## 范围

- findByToken 的 null 类型查询覆盖所有 token 类型及 state；修复 Redis TTL、索引、并发更新、撤销及历史数据读取。
- 支持授权码 S256 PKCE 和 OIDC；保留 password/sms 扩展的既有业务语义、参数及异常映射。
- IAM JSON 链迁至 Jackson 3，验证历史 DTO/JSON 可读及反序列化类型安全。
- 完善登录态、授权同意/拒绝和中文企业风格页面，覆盖桌面与移动端。

## 不做的事项

不重做 Vue Admin，不切换默认 token 格式，不删除兼容 grant；验证码默认 true 占位行为按用户明确要求保留，不新增短信供应商或手机号身份查询。不新增 CAS/SAML、动态注册、数据库退出回调字段，不部署生产。

## 验收与风险

以已确认 spec.md Requirements、plan.md Proof 为验收合同。运行隔离 Redis 协议/存储测试、全项目测试和桌面/移动浏览器回归；区分模拟身份源、模拟 API 与真实设施联调。缓存升级禁止旧新节点混写，回滚要求见 IAM README。未完成的生产联调不能声明通过。
