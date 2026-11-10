# Intent: IAM统一认证补齐及MFA接入预留
Author: Rain. Status: accepted.

## Problem
当前统一认证两条链路已存在，但核对发现钉钉请求字段、飞书flow与关联材料、防重放失败处理、第三方应用Token缓存、通讯录/组织准入及接入示例仍有缺口。现有Mock测试通过不能证明真实平台合同正确。未来加入MFA时还缺少明确衔接边界，可能需要重复修改各认证入口，或在二次验证完成前就建立完整登录态、签发令牌。

## Proposed outcome
- 按[核对报告](../../macula-cloud-iam/docs/third-party-integration.md)补齐六项缺口，统一管理配置、实际校验和客户端示例；分清已实现、待核实与真实平台验收。
- 保留标准浏览器授权和现有external_code直接登录，共用账号解析、绑定和准入，不强迫正常SDK/小程序登录打开IAM网页。
- 预留MFA接入点，使未来二次认证能够接入浏览器及直接登录链路；未接入时保持现有行为，需要二次认证时不能提前建立完整登录态或签发令牌。账号绑定不作为MFA完成证明，不伪造认证强度声明。
- 提供可验证的允许、拒绝、并发和失败场景，避免同一厂商不同应用、来源或flow串用。

## Affected users and systems
接入开发者、平台管理员及登录用户；涉及现有IAM、System、System API与Admin。核对Gateway/System资源和内省契约，保持既有边界；保留GitHub及阿里云号码认证能力。

## Constraints
- 复用已接受的[具体来源与统一绑定计划](../iam-client-protocol-scope-channels/plan.accepted-unified-binding-20261106.md)、[资源边界计划](../iam-resource-access-boundaries/plan.md)和[OIDC扩展计划](../iam-resource-access-boundaries/oidc-external-code-plan.md)，不重建IAM、不覆盖已有工作，本意图批准不替代设计和实施计划批准。
- 保留OIDC external_code及openid/nonce边界；不恢复password grant、不默认向公共Client开放refresh、不改变Access Token格式、aud或租户权限；不任意放宽参数白名单或新增平行登录Token接口。
- MFA本轮仅预留扩展，不实现认证因子、注册/恢复、挑战接口或配置页面，不改变已有SSO/refresh策略；未来接入须明确这些路径的二次认证要求，不能形成旁路。
- 平台接口以核实的官方合同为准；保留所有参考链接和已有材料。未读到正文、未获得真实平台证据时明确标注，不以猜测或Mock代替验收。
- 必要数据库变化采用追加迁移，不修改已执行迁移、不猜填历史组织或身份数据；不提交、推送、部署或操作业务库。
- 新增认证行为必须经过设计及实施计划确认，不自行批准SDLC门禁。

## Open questions
Rain以YES明确接受完整意图及MFA仅预留扩展、默认不启用的边界。无待定范围问题；真实因子和交互流程不属于本轮。该确认仅进入设计，不包含源码实施、Git提交或部署批准。
