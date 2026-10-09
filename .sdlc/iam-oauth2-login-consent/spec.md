# Spec: IAM OAuth2 升级兼容与登录授权界面
Status: accepted

## Source intent
用户要求修复 IAM 升级兼容问题与 Redis 授权存储，补齐专业化登录和授权界面；进一步明确全 token 类型查找、PKCE、OIDC 及保留 password 兼容扩展，并于 2026-10-08 对完整 plan.md 回复“确认”。本文件整理该已接受合同，不新增需求或自批 gate。

## Requirements
1. 授权记录支持所有框架 token 类型及 state 的按类型/无类型查询、未命中、过期、撤销和轮换语义。
2. Redis 剩余 TTL、主记录与索引、旧缓存兼容、并发更新和错误传播满足 plan.md 的专项合同。
3. 标准登录支持授权码 S256 PKCE；password 与 sms 扩展及既有登录入口均为兼容保留，不要求旧客户端迁移至 PKCE/OIDC。
4. 支持 OIDC Discovery、JWKS、ID Token、UserInfo 和标准退出，涵盖 opaque/JWT access token 和 scope 信息隔离。
5. IAM 登录、同意和承接页面采用统一中文企业风格，支持移动端、键盘、错误状态与防重复提交。
6. 未接入短信服务时不模拟发送成功；按用户纠正，验证码占位实现保留原有默认返回 true 的行为，真实短信验证不在本次范围内。
7. 2026-10-09 用户明确授权将 IAM 完整 JSON 序列化链升级到 Jackson 3，保留历史 Redis DTO/JSON 可读和类型安全边界，不改兼容 grant 的业务语义。

## Non-goals
不重做 Vue Admin，不移除 password，不切换 Gateway 默认 token 格式，不新增 CAS/SAML、动态客户端注册或生产部署，不扩展退出回调数据库字段。

## Design
采用当前 Spring Security 7 的协议扩展点，沿用 Thymeleaf、Java 17 和现有身份源。具体文件、顺序、缓存与协议契约以用户已确认的 [plan.md](./plan.md) 为准。

## Data and interfaces
外部兼容 token 接口保持；Redis 引入版本化主记录和类型索引，保留历史 DTO 读取。签名资源允许外部配置。无数据库迁移。

## Flagged concerns
- 缓存升级回滚：旧节点不能与新节点同时写新授权状态；需记录迁移和回滚限制，non-blocking。
- 真实设施联调：开发阶段采用隔离验证，不把单元结果当生产联调，non-blocking。
- 未接入短信验证：保留短信登录入口默认开启、后端返回 true 的既有占位行为，不宣称已完成真实验证码校验，non-blocking。

## Verification strategy
按 plan.md Proof 覆盖存储、并发、协议成功与拒绝、序列化、会话和页面。实现阶段可执行必要回归诊断，正式 Test gate 在 Build 完成确认后推进。
