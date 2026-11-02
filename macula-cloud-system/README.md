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
