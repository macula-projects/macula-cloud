# Intent: IAM单协议接入、Scope与资源访问边界优化
Author: Rain. Status: accepted.

## Problem
一个Client同时维护多个协议和默认协议，界面重复且难以理解；令牌配置默认折叠，配置不够直观。纯OAuth2.1缺少可实际使用的基础资料Scope，空目录说明没有解决接入需求。资源服务器与普通业务应用配置混用，内置Gateway身份可被误删；同一IAM签发令牌后，缺少清晰的目标资源及Scope准入边界。

## Proposed outcome
- 一个业务Client单选一个协议，移除多协议开关、默认协议和两套配置Tab。OIDC仍可获取Access Token访问API，确需不同协议时使用不同Client。
- 统一整理五个配置分区及弹窗排版，底部操作可见；令牌格式、有效期、刷新策略、OIDC的ID Token设置直接展示，按实际适用条件显示，不默认收起。
- OAuth2.1提供可选择且服务端生效的profile基础资料Scope，字段参考当前OIDC实际提供的资料范围；签发、内省和资料读取权限一致，未授权不得读取受保护资料。纯OAuth不要求openid、不签发ID Token，不冒充OIDC UserInfo。
- 业务Scope来自明确登记的资源能力；不提供尚未实现的email/phone等资料权限，无用户的服务令牌不能伪装用户资料授权。
- 资源服务器按内省用途配置认证和访问许可，平台内置身份有明确标识及防误删/误停用保护，不要求填写无关的用户登录参数。
- 分清内省调用身份、目标Token所属Client和允许访问的资源；正常跨Client内省保留，Access Token按目标资源、Scope及现有角色/租户规则授权，允许多个Client显式共享资源。
- Admin OIDC和合法API调用可验证、可恢复；拒绝原因明确，不再仅表现为无法理解的500。

## Affected users and systems
平台管理员、接入开发者及API维护者；Macula Cloud的Admin、IAM、System、System API和Gateway，必要时涉及Macula Boot网关Starter。第三方厂商认证流程沿用现状，附件GithubClient.java不是本次单独重构目标。

## Constraints
- 本意图取代同Client多协议分别保存的交互要求，后台同样执行单协议约束；协议切换不得混用旧参数、code和refresh。
- Admin自身继续OIDC；OAuth Scope优化须落实实际资料权限，不能仅在前端增加选项或解除校验。
- 复用现有框架；ID Token、Access Token、code、refresh各自保持用途边界。CAS/SAML仅保留未来扩展规则。
- 沿用不需历史兼容的要求，允许旧Token重新登录；保留Admin/Gateway既有身份，不猜测补授资源权限。
- 保留已有未提交工作，不混入API Key/HMAC等其他在途修改。源码实施前接受设计和计划，不自动提交、推送或部署。

## Open questions
- Rain以YES确认首批覆盖Admin/System和网关内省关系；其他业务资源经明确登记后加入。当前意图无待定范围问题。

## Acceptance
Rain以YES接受本意图、单协议/OAuth profile/令牌配置直接展示要求及首批资源范围，进入设计阶段；不包含Git提交、推送或部署授权。
