# 租户安全策略

System 管理端通过 `/system/security-policy` 提供一个“安全策略”页面，包含“通用安全”、
“防御策略”和“密码策略”三个 Tab。配置当前只负责保存，尚未由 IAM、Gateway 或密码修改流程执行。

## 数据模型

每个配置项在 `sys_dict_item_tenant` 中占一行：

- `type_code` 固定为 `system_security_policy`。
- `name` 保存稳定配置键。
- `value` 保存单个字符串值，不保存整组 JSON。
- `(tenant_id, type_code, name)` 唯一；同一个 key 可以在不同租户分别配置。

配置键为：`sessionValidTime`、`verifyCodeValidTime`、`rememberMeValidTime`、`sessionMaximum`、
`loginFailureDuration`、`loginFailureCount`、`autoUnlockTime`、`contentSecurityPolicy`、
`passwordBiggestLength`、`passwordLeastLength`、`passwordComplexity`、`customWeakPassword`、
`weakPasswordCheck`、`includeAccountCheck`、`notSameChars`、`historyPasswordCheck`、
`historyPasswordCheckCount`、`illegalSequenceCheck`、`passwordValidDays`、
`passwordValidWarnBeforeDays`。

会话、验证码和记住我有效时间以秒保存；登录失败持续时间和自动解锁时间以分钟保存；
密码复杂度使用稳定 code 0–5。V4 迁移将 `value` 扩为 `TEXT`，增加唯一索引，并在发现历史重复组合时
停止升级。升级前必须备份并执行：

```sql
SELECT tenant_id, type_code, name, COUNT(*)
FROM sys_dict_item_tenant
GROUP BY tenant_id, type_code, name
HAVING COUNT(*) > 1;
```

## API

- `GET /api/v1/security-policy`：读取当前认证租户的全部配置。
- `PUT /api/v1/security-policy/basic`：保存通用安全。
- `PUT /api/v1/security-policy/defense`：保存防御策略。
- `PUT /api/v1/security-policy/password`：保存密码策略。

请求不接受 `tenantId`、`typeCode` 或任意未知字段。旧 `/api/v1/security-settings` 和
`/api/v1/password-policy` 已删除；V4 只删除并替换平台租户（`tenant_id=1`）的旧菜单，其他租户的
菜单和授权关系保持不变。三个写接口使用平台操作日志记录固定键组与成功/失败结果，请求和响应正文均不
进入审计，避免泄露 CSP、弱密码清单或其他配置值。旧专用配置表不自动迁移或删除；如果生产环境
已向旧表写入数据，应停止升级并单独设计迁移。
