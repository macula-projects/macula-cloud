# Macula Cloud Gateway

## 作用

`macula-cloud-gateway` 是平台统一访问入口，负责将外部请求路由到后端服务，并在入口层处理认证、权限和接口保护。

## 核心功能

- 通过 Nacos 发现后端服务并进行负载均衡路由。
- 校验 access token，并执行 URL 级访问控制。
- 支持接口签名和请求、响应加解密。
- 当前提供 System 的 `/system/**` 和 TinyID 的 `/tinyid/**` 路由。

## 使用

先按[根 README](../README.md)启动基础设施以及 IAM、System 等目标服务，再在 IDE 中运行 `MaculaGatewayApplication`。本地默认端口为 `9000`。

通过 Gateway 访问后端服务：

```text
http://127.0.0.1:9000/system/api/v1/menus/routes
http://127.0.0.1:9000/tinyid/api/v1/id/nextId
```

除公开端点外，请求需要携带 IAM 签发的 access token：

```http
Authorization: Bearer <access_token>
```

完整 Docker Compose 启动方式见 [`deploy/README.md`](../deploy/README.md)。
