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

升级前先执行新增的 Flyway 数据库迁移以添加可空的 `auth_tenant_id` 列，再启动新版 System；历史记录无需回填。先升级网关再配置跨租户认证，回滚旧网关前先恢复认证租户与归属租户一致。网关未匹配到归属租户时使用 `-1` 并拒绝接入；API Key 使用相同的归属租户及认证租户缓存字段。

租户管理维护 `macula:cloud:system:tenants` Hash，保存租户编码到 ID 的映射。System 启动时初始化；租户新增、修改、删除在事务提交成功后自动刷新，回滚不刷新，与应用缓存维护分开。空租户编码不参与匹配，忽略大小写后重复的编码会使刷新失败。网关不使用手工 `tenant-id` 配置；缓存未就绪时不放开接入。

## 应用 API Key

应用管理支持一个应用创建多把 API Key，在操作栏点击 **APIKEY** 管理。列表默认脱敏，可按需显示、复制、编辑备注/允许访问的 URL，或删除。创建成功弹窗可以复制完整 Key；关闭页面会清除前端保存的明文状态。

Key 的 `accessPath` 与应用 `accessPath` 独立保存。新增、更新、重建 API Key 缓存统一优先使用非空 `accessPath`，否则使用应用的可访问路径；不合并、不取交集。例如 `GET:/service/api/**,POST:/service/api/orders`。两处均为空时沿用网关既有的不限制应用 URL 行为。

接口位于 `/api/v1/app/{appId}/api-keys`：GET 分页、POST 创建；`/{keyId}` 支持 PUT 配置和 DELETE 撤销；`/{keyId}/secret` 支持授权查看。分页大小为1至100。接口校验当前租户应用管理权限及应用/Key归属，完整凭据仅由创建/查看响应提供，设置 `Cache-Control: no-store` 且不记录响应审计。

先执行 Flyway `V13__application_apikey.sql`，再启动新版 System。数据库保留撤销记录用于恢复时清理缓存，不重用已撤销 Key。凭据以可恢复形式保存以支持再次显示，数据库与 Redis 访问必须受控，不能将完整值写入日志。

缓存使用 Boot `APIKEY_CACHE_PREFIX + Key` 的 Redis Hash，包含 `applicationCode`、`applicationId`、`tenantId`、`authTenantId`、`permitUrls`。应用和 Key 的写操作提交成功后同步缓存；撤销删除认证 Hash，旧 Key 无法依赖 JWT 缓存继续访问。System 启动重建应用和 Key 缓存，原 `refreshApplicationCache()` 运维入口也重建两类凭据。新增/撤销与应用删除使用共同应用行锁，缓存发布使用分布式锁并重读已提交状态。

数据库与 Redis 没有跨资源原子事务。若接口报告“凭据缓存同步失败”，数据库可能已经提交；先刷新管理列表确认结果，再重试或调用既有刷新入口（也可在受控维护时重启 System 重建）。缓存失败时旧权限可能暂时有效，不能将接口报错视为已成功撤销。本轮不提供有效期、启停或自动轮换。

回滚时恢复旧版 System 需同时停止新增管理接口；新表可保留，不能自动删除或回填历史凭据。真正撤销仍须清理对应网关认证缓存，回滚前应核对调用方与缓存状态。

API Key 列表的 `createTime` 使用 `java.util.Date` 表达时间点，JSON 返回带 `Z` 的 ISO 时间，管理端按浏览器当地时区显示。实体审计字段按既有数据库时间约定写入；接口映射使用公共存储时区，不改变数据库历史时间值。

## 接口日期时间约定

接口时间点允许使用 `java.util.Date` 或 `java.time.Instant`，现有接口保留Date，JSON保留Jackson标准ISO格式，输出UTC，例如 `2026-11-05T00:50:30.000Z`。数据库实体及查询BO保留 `LocalDateTime`，普通Mapper引用Boot的 `GlobalMapperConfig`，API Key也复用公共 `DateTimeConverter`。`macula.time.storage-zone` 可选，未配置使用JVM默认时区；该值应与数据实际写入约定一致，JSON输出UTC不改变数据库存储时区。

角色、菜单、权限详情保留原URL和业务字段，通过独立VO返回；菜单和权限写入采用业务表单，审计字段由服务端维护。用户导出先查询BO，再转换为Date导出VO，保留Excel的人类可读格式。旧的无时区或仅日期响应已改为完整ISO时间，调用方应使用日期解析器处理。

运行需要包含公共转换器及自动配置的新版本 `macula-boot-starter-mapstruct`，本地验证前先安装对应Boot Parent和Starter。不修改IAM协议、Redis授权记录或数据库表结构。

JSON时间点同样可以使用 `Instant`：Jackson原生支持带时区ISO读写。`Date`保留毫秒，`Instant`可保留纳秒，转换为Date时超出毫秒的精度会丢弃；不使用固定 `SSS` 的 `spring.jackson.date-format` 作为通用ISO解析器，避免纳秒输入被错误解析。接口调用约定仍要求时间点携带Z或偏移。公共实体映射转换器同时支持Date/Instant与LocalDateTime双向映射，两种接口类型均遵循同一存储时区配置；Instant映射不经过Date，保留纳秒。
