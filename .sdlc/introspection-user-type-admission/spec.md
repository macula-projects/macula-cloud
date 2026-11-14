# Spec: 内省用户类型准入
Author: Rain. Status: accepted.

## Requirements
1. 资源服务器拥有独立多选 allowedUserTypes（member、employee、odc、supplier）；不复用业务应用的单值 userType。空集合拒绝所有用户 Token；机器 Token 不受集合限制。非资源服务器不能配置此集合，未知类型拒绝。
2. 签发用户 Token 时记录来自已认证账号目录的可信 userType；不是请求参数。签发时核对主体目录与可信业务 Client 目录一致，跨目录或缺失拒绝。主体类只添加安全元数据，不改类型名/UID/反序列化白名单。旧主体不猜填、不自动升级。
3. 内省使用已认证 RESOURCE_SERVER 的当前服务端策略；先保留既有认证、用途、有效期、撤销和配置版本检查。根据 OAuth2Authorization 保存的原始 grant 判断机器 client_credentials，不依据缺失 userType 或请求参数。用户 Token 缺失/非法/不允许 userType 统一 active:false，不泄露资料；通过时可返回可信 userType。机器 Token 保持既有规则，任意集合（含空）都不触发用户类型拒绝。
4. 不补旧 Token、不猜填旧资源服务器配置；数据库只添加空集合默认的新列，不改变已有身份、凭据、绑定、授权或历史 SQL。旧用户 Token 缺失类型直接失活。
5. 类型准入只在IAM内省Provider执行。System及Admin仅承担新增允许集合的保存和界面，不修改Macula Boot或Gateway缓存策略。调用方已有缓存行为不属于本次实现合同。

## Non-goals
不新增机器准入开关，不修改租户/角色/Scope/aud策略，不移除既有许可或认证，不重构身份源，不做业务库升级、部署、提交、推送。

## Verification strategy
后端新增用户允许/拒绝/缺失、机器空集合允许、伪造grant不得绕过、配置修改/回滚、JSON往返及真实隔离MySQL验证；管理端测试多选保存/清空/失败回显。Cloud相关及全仓测试、Admin单元/build/相关Cypress、变更Java Checkstyle/无fix ESLint/diff。记录条件跳过与未验证的真实环境。

## Approval
工程师先明确旧的不用过渡、机器Token不考虑用户类型配置，再对逐项收敛规则回复 OK。技术实现及跨仓缓存增量由 plan.md 单独确认，不将本次需求接受当作尚未展示的 Plan 接受。

## 范围纠偏
工程师最新明确要求在IAM处理，已撤销全部Boot扩展；此前缓存方案不再适用。

## 本轮交付授权
工程师明确要求“推送提交，更新镜像”。据此授权本变更创建分支、提交、推送、创建PR，并定向构建/重建本地System、IAM、Admin。System负责V29新增列迁移；不擅自给现有资源服务器猜填允许类型、不轮换凭据、不改Boot、不重建其他服务；未授权合并PR。此前不提交/部署的表述记录的是当时边界，已由本次明确指令更新。
