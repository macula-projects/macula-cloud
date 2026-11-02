# Intent: IAM Client 级 Token CORS
Author: Rain. Status: accepted.

## Problem
多个浏览器系统需要直接调用统一 IAM 的 `/oauth2/token`，但当前 Token CORS 只依赖部署级全局 Origin 白名单。不同系统无法都通过 Admin 或本系统的同源代理访问 IAM；全局白名单又不能把请求来源约束到实际发起 Token 请求的 OAuth Client，导致正常接入容易被 403 拒绝，也难以维持清晰的客户端安全边界。

## Proposed outcome
每个浏览器 OAuth Client 都能独立声明允许直接访问 IAM Token 端点的精确 Origin。来自已获当前 Client 授权 Origin 的浏览器请求可以完成 Token 流程；来源未获授权或 Client 未配置 Origin 时，IAM 在凭据校验和令牌签发前拒绝跨域请求。该行为覆盖浏览器发起的所有 Token grant，不要求接入系统部署 IAM 反向代理。

## Affected users and systems
受影响者包括接入 Macula Cloud IAM 的浏览器应用开发者、OAuth Client 管理员和 IAM 运维人员。受影响系统包括 IAM `/oauth2/token`、System 中的 OAuth Client 配置及其管理端页面，以及所有直接从浏览器调用 Token 端点的接入系统；非浏览器的服务端到服务端调用不受 CORS 限制。

## Constraints
- 覆盖浏览器调用 `/oauth2/token` 的所有 grant，包括 password、sms、email、external_code、refresh_token 及服务端已支持的其他 Token grant。
- OAuth Client 未配置允许 Origin 时必须拒绝浏览器跨域请求，不回退到部署级 `IAM_TOKEN_CORS_ALLOWED_ORIGINS`。
- 不要求接入系统提供 `/iam` 同源代理；浏览器可以直接访问 IAM 的公开 Token 端点。
- Origin 必须按协议、主机和端口精确匹配，不允许 `*` 或模糊放行。
- CORS 只约束浏览器访问，不能改变 OAuth Client 认证、grant 校验、用户认证、Token 内容或服务端调用语义。
- 现有 Client 在正式切换前需要补齐允许 Origin；迁移过程不得静默扩大来源权限。

## Open questions
无；具体请求绑定方式、配置读取位置、迁移步骤和兼容测试由后续设计阶段确定。
