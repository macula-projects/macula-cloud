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

## 接口日期时间约定

接口 VO/Form/DTO 的时间点可使用 `java.util.Date` 或 `java.time.Instant`，已有Date字段保持兼容，数据库实体和查询 BO 保留 `LocalDateTime`。JSON 使用带时区的 ISO 格式，例如 `2026-11-05T00:50:30.000Z`；浏览器提交时间点时同样携带 `Z` 或偏移，显示时按浏览器当地时区格式化。纯业务日期筛选和 Excel 显示格式保持原有语义。

System/TinyID 保留 Jackson 的标准 ISO 日期格式，仅设置 `spring.jackson.time-zone=UTC`。普通 MapStruct Converter 显式引用 Boot 的 `GlobalMapperConfig`，日期映射由自动注册的 `DateTimeConverter` 完成；`macula.time.storage-zone` 配置后优先使用外部值，未配置则使用 JVM 默认时区。该时区应与数据库中日期时间的实际写入约定一致，JSON 输出 UTC 不代表修改数据库存储时区。

角色、菜单、权限的详情接口保留现有路径和业务字段，通过独立 VO 返回审计时间，不直接返回 Entity；菜单及权限写入使用业务表单，审计字段由服务端维护。旧的仅日期或不带时区的 JSON 时间会变为完整 ISO 时间，调用方应使用日期解析器处理，不能按固定位置截取字符串。

运行本次改动需要包含 `GlobalMapperConfig`、`DateTimeConverter` 和自动配置的新版本 `macula-boot-starter-mapstruct`；仅在本地验证时，应先安装对应 Boot Parent 和 Starter。本轮不修改 IAM 协议、授权 Redis 记录或数据库表结构。

JSON时间点同样可以使用 `Instant`：Jackson原生支持带时区ISO读写。`Date`保留毫秒，`Instant`可保留纳秒，转换为Date时超出毫秒的精度会丢弃；不使用固定 `SSS` 的 `spring.jackson.date-format` 作为通用ISO解析器，避免纳秒输入被错误解析。接口调用约定仍要求时间点携带Z或偏移。公共实体映射转换器同时支持Date/Instant与LocalDateTime双向映射，两种接口类型均遵循同一存储时区配置；Instant映射不经过Date，保留纳秒。

## OAuth 授权规则

`OAuth2GrantPolicy` 提供当前授权白名单与已退役标识识别；当前仅允许标准授权码、刷新、服务凭据及 external_code 扩展。System 新写入使用 `OAuth2ProtocolConfiguration.validateWritable()` 拒绝 password/sms/email；`validateRuntime()` 只保留已知旧配置读取，历史 Flyway 的 `validate()` 合同不变。可读历史数据不代表可重新签发授权。此处 `email` grant 与标准 email Scope、用户资料字段不同。


`OAuth2HostedLoginMethod` 定义 PASSWORD、SMS、EMAIL。它们控制托管认证页面、认证 POST 和发码，不作为 Token grant；null 继承平台，空集合关闭，显式列表按管理员选择执行。
