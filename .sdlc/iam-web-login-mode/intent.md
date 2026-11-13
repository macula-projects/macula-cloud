# Intent: Web/SPA 浏览器或端内登录二选一
Author: 待确认. Status: accepted.

## Problem
独立 IN_APP 应用类型混合了应用架构和登录环境；上一版共存方案增加了用户不需要的分流和切换复杂度。

## Proposed outcome
先选择标准 Web 或 SPA，再选择浏览器或端内登录模式，二选一。创建应用成功后登录模式固定，草稿亦不再切换。每个 Client 仅启用所选模式；不提供同一 Client 的浏览器/端内共存、自动分流或普通登录回退。

## Affected users and systems
应用管理员、业务接入开发者、登录用户；涉及 System API、System、Admin、IAM，核对 Gateway 令牌消费兼容性。

## Constraints
浏览器模式沿用普通网页登录；端内模式沿用固定平台能力和来源证明。保留 Web 服务端机密认证与 SPA 公共客户端/PKCE 边界，不将环境识别作为身份。
移除独立 IN_APP 应用类型及转换入口；存量 IN_APP 不兼容、不转换、不迁移，不删除业务数据。Native/小程序/Service 能力及仅保留 external_code 的自定义grant边界不变。
保留正常应用的 ID、凭据、账号绑定及现有 import/邮件/SMS成果；不新增 package-info.java。不提交、推送、合并、部署或执行业务库变更。

## Open questions
当前功能范围由用户明确为二选一。Author 仍待确认，不推断个人署名；不阻塞范围设计。

接受与修订记录：用户先明确接受 Web/SPA 上配置登录环境、存量 IN_APP 无需兼容；在设计审阅中进一步明确“标准web或者SPA的浏览器或者端内是二选一”。本需求据此修正，原共存目标作废；当前实现仍需通过修订后的规格与工程计划门。

最新接受修订：用户明确要求选择后固定，已回复YES接受创建时选择、创建后只读及服务端不可变校验；既有应用按当前有效模式保留，不转换绑定或凭据。
