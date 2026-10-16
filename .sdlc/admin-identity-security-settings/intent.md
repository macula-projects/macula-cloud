# Intent: 租户安全策略统一管理
Author: Rain. Status: accepted.

## Problem
系统管理中的通用安全、防御策略和密码策略目前分散在不同页面与保存模型中，管理员无法在一个入口内连续查看和维护完整的租户安全策略。现有独立 JSON 配置记录也不符合按配置项逐个维护和审计的要求。

## Proposed outcome
管理员通过一个管理页面的“通用安全”“防御策略”“密码策略”三个页签查看和维护当前租户的安全配置。每个配置项独立保存，保存后重新进入页面能够读取相同值；不同租户之间的配置互不泄漏。页面清楚提示尚未接入 IAM 或 Gateway 运行时的策略仅完成配置管理，不误导管理员认为已即时生效。

## Affected users and systems
- 使用 Macula Cloud Admin 管理租户安全配置的 ROOT、租户管理员及后续被授权的安全管理员。
- `macula-cloud-admin` 的系统管理菜单、路由、API 封装与统一安全策略页面。
- `macula-cloud-system` 的安全策略管理 API、服务端校验、租户边界和字典数据访问。
- 现有字典类型、字典项管理及 `sys_dict_item_tenant` 数据。

## Constraints
- 三类策略必须整合为一个页面，并以三个 Tab 呈现。
- 配置必须保存到 `sys_dict_item_tenant`；每个配置项一条记录，一个稳定 key 对应一个 value，不以单条 JSON 保存整组设置。
- “通用安全”包含 `sessionValidTime`、`verifyCodeValidTime`、`rememberMeValidTime`、`sessionMaximum`，三个有效期字段使用秒。
- “防御策略”包含 `loginFailureDuration`、`loginFailureCount`、`autoUnlockTime`、`contentSecurityPolicy`；自动解锁时间使用分钟。
- “密码策略”包含 `passwordBiggestLength`、`passwordLeastLength`、`passwordComplexity`、`customWeakPassword`、`weakPasswordCheck`、`includeAccountCheck`、`notSameChars`、`historyPasswordCheck`、`historyPasswordCheckCount`、`illegalSequenceCheck`、`passwordValidDays`、`passwordValidWarnBeforeDays`。
- 配置按租户隔离，服务端不得信任前端传入的租户身份或跨租户读写。
- 保持 Java 17、Vue 3、Element Plus、现有 System 分层和动态菜单机制兼容；不引入新的前端或持久化框架。
- 本次聚焦配置管理；除非已有运行时明确消费这些配置，否则不扩展 IAM、Gateway 的认证执行逻辑。
- 保留与本需求无关的身份提供商、消息设置和 OAuth 客户端管理能力，不把它们并入本页面。

## Open questions
- 现有 `/api/v1/security-settings`、`/api/v1/password-policy` 及两个旧菜单入口是否需要保留兼容/跳转，还是直接由统一接口和单一菜单替换。
- 独立安全/密码配置表是否已在任何环境写入数据；如果存在，需要明确迁移到字典项的升级策略。
