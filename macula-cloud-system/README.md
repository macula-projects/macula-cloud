# Macula Cloud System

## 作用

`macula-cloud-system` 是平台系统管理中心，为管理端、IAM 和其他服务提供统一的租户、组织、用户及权限数据。

## 核心功能

- 租户、应用、部门和用户管理。
- 角色、菜单、按钮及 URL 权限管理。
- 字典和审计日志管理。
- OAuth Client 与第三方身份提供商配置。
- 邮件、短信服务及消息模板配置。
- 租户安全策略管理。
- 为 IAM 提供用户登录信息，为管理端提供菜单路由。

## 使用

推荐通过 [Macula Cloud Admin](../macula-cloud-admin/README.md) 使用系统管理功能，也可以通过 Gateway 调用 REST API：

```text
http://127.0.0.1:9000/system/api/v1/tenants
http://127.0.0.1:9000/system/api/v1/users
http://127.0.0.1:9000/system/api/v1/roles
http://127.0.0.1:9000/system/api/v1/menus
```

请求需要携带 IAM 签发的 access token，并受当前租户和 URL 权限约束。

本地开发先按[根 README](../README.md)启动 MySQL、Redis 和 Nacos，再在 IDE 中运行 `MaculaSystemApplication`。服务默认端口为 `9081`；对外访问通常使用 Gateway 的 `9000` 端口。

完整 Docker Compose 启动方式见 [`deploy/README.md`](../deploy/README.md)。

## 第三方应用租户

应用的归属租户负责管理隔离，只有与网关按 `spring.application.name` 最长前缀匹配出的租户一致才允许 AK/SK 接入。应用管理中的“认证租户”决定 AK/SK 认证后访问哪个租户的业务数据，留空使用归属租户。仅平台 ROOT 可设置不同于应用归属租户的认证租户，服务端同时检查目标租户是否存在。

应用接口的可选字段 `authTenantId` 支持整数或整数字符串。修改时省略该字段保留原配置，显式提交 `null` 清空。应用缓存保留 `tenantId`，并增加 `authTenantId`；现有刷新流程将未配置的认证租户回退到归属租户。认证租户以字符串返回，避免管理端丢失长整型精度。

升级前先执行新增的 Flyway 数据库迁移以添加可空的 `auth_tenant_id` 列，再启动新版 System；历史记录无需回填。先升级网关再配置跨租户认证，回滚旧网关前先恢复认证租户与归属租户一致。网关未匹配到归属租户时使用 `-1` 并拒绝接入；本次 AK/SK 规则不覆盖 API Key。

租户管理维护 `macula:cloud:system:tenants` Hash，保存租户编码到 ID 的映射。System 启动时初始化；租户新增、修改、删除在事务提交成功后自动刷新，回滚不刷新，与应用缓存维护分开。空租户编码不参与匹配，忽略大小写后重复的编码会使刷新失败。网关不使用手工 `tenant-id` 配置；缓存未就绪时不放开接入。
