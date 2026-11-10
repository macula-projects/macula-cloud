# Intent: IAM协议配置分区与接入自动适配
Author: Rain. Status: accepted.

## Problem
协议配置将OIDC与OAuth2.1选项、端点混在一起；用户需要理解协议细节并手工处理认证/授权组合，容易得到截图中的冲突。Admin接入固定为OIDC，通用接入与具体应用协议之间的适配边界不清晰。

## Proposed outcome
- 默认协议位于协议配置顶部，只能选择已经启用的协议；启停后保持默认值有效，无可用协议时明确提示。
- OIDC、OAuth2.1分别使用独立Tab，每个Tab内提供启用开关；Tab切换与启用、设为默认是独立操作。
- 每个协议展示自身相关配置、授权方式、Scope和端点。端点放在对应Tab底部，默认折叠；共同端点可以相同，协议专属端点不混列。
- 按应用类型、认证方式、协议能力联动选项，阻止产生无效组合；确需移除已有选择时给出明确反馈，减少重复错误堆叠。
- 接入处理与示例根据明确的应用协议配置匹配发现信息、Scope与Token要求，减少用户手工判断。

## Affected users and systems
IAM应用管理员、Web/SPA等接入开发者；Macula Cloud Admin、IAM、System及System API，涉及客户端协议配置与授权码接入。

## Constraints
- 首期限定平台已支持的OIDC、OAuth2.1，不照搬截图中的password/implicit或增加SAML/CAS。
- 自动处理不能将OIDC验证失败静默降级为纯OAuth，不根据响应是否碰巧含ID Token猜测协议；保留各流程必要安全校验。
- 保留既有应用类型、身份源、租户与权限边界，不重新引入浏览器Secret。
- 保留当前未提交修改；本轮开发完成不自动部署、提交或推送。

## Open questions
无。Rain已明确：Admin继续使用OIDC；同一应用的不同协议配置分别保存。

## Acceptance
Rain以yes接受本意图；两个Open questions仍待明确选择，不能将接受意图等同于已选择其答案。未授权Git提交或部署。

## Scope clarification
Rain明确选择“1，Admin继续用OIDC吧；2、分别保存”。此项替代先前未决范围记录。

## Future protocol clarification
Rain确认当前设计方向，并要求预先考虑CAS、SAML。当前仍只实现OIDC/OAuth2.1；未来协议需有独立配置、端点与认证处理扩展点，不展示未实现能力。Admin继续OIDC。
