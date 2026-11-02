# Macula Cloud System API

## 作用

`macula-cloud-system-api` 提供 System 服务的跨服务 Java 契约，供 IAM 等服务通过服务发现调用 System，而不依赖 System 的业务实现。

## 核心功能

- `UserFeignClient`：读取用户登录信息和角色数据。
- `MenuFeignClient`：读取当前用户可访问的菜单路由。
- 提供系统管理相关的请求模型、响应模型和公共枚举。
- 提供可由调用方扩展的 Feign fallback 抽象。

## 使用

在调用方模块中添加依赖：

```xml
<dependency>
    <groupId>dev.macula.cloud</groupId>
    <artifactId>macula-cloud-system-api</artifactId>
    <version>${revision}</version>
</dependency>
```

启用 OpenFeign 后即可注入契约接口：

```java
private final UserFeignClient userFeignClient;
```

调用时需要保证 `macula-cloud-system` 已注册到 Nacos，并继续传递认证与租户上下文。
