# Macula Cloud API

## 作用

`macula-cloud-api` 是 Macula Cloud 的跨服务 API 契约聚合模块，用于集中管理服务间共享的 Feign 接口、请求与响应模型及必要枚举。本模块不包含业务实现，也不能独立启动。

## 核心功能

- 聚合各平台服务对外提供的 Java API 契约。
- 统一服务间调用使用的 DTO、Form、Query 和 VO。
- 隔离调用方与服务端实现，避免跨模块直接依赖 Controller、Service 或 Entity。

当前包含：

- [`macula-cloud-system-api`](macula-cloud-system-api/README.md)：System 服务的用户、菜单等跨服务契约。

## 使用

需要调用平台服务的模块应依赖具体的 `*-api` 子模块，不要依赖本聚合 POM。例如：

```xml
<dependency>
    <groupId>dev.macula.cloud</groupId>
    <artifactId>macula-cloud-system-api</artifactId>
    <version>${revision}</version>
</dependency>
```

在仓库根目录构建 API 模块：

```bash
mvn -pl macula-cloud-api -am package -DskipTests -Plocal
```
