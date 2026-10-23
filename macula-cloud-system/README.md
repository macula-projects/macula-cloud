# Macula Cloud System 管理中心

本模块基于[youlai](https://gitee.com/youlaitech/youlai-mall) 代码定制整理。

统一的租户、应用、用户、权限等管理

## 数据库初始化

Flyway 迁移统一为 `db/migration/V1__baseline.sql`，包含原有基础数据及“系统管理”下的
“ID管理”菜单、ROOT 菜单关联和 GET/POST/PUT/DELETE 管理权限。
本次合并要求由运维先清理数据库及 Flyway 历史，再执行初始化；不支持直接沿用旧版迁移历史升级。
System 数据库同时供 IAM 使用，清理后相关账号、权限及认证配置需重新初始化。

后续 `V2__admin_identity_security_settings.sql` 为增量迁移：新增平台身份提供商、消息服务和模板覆盖配置
（其中历史全局安全/密码表和菜单已由 V4 的租户安全策略替代），并将 `sys_oauth2_client.client_secret` 调整为可空以支持
公共 PKCE 客户端。V2 不清理旧客户端，也不修改已经执行的 V1；已按 V1 初始化的数据库可直接
升级。升级前请备份数据库；回滚需要恢复升级前备份，不能直接删除 V2 新表（可能包含新配置）。

`V3__identity_provider_json_config.sql` 只为身份源增加 `remark`，并把 `app_secret` 扩为 `TEXT`
以容纳支付宝私钥的加密结果；不会重写或删除已有行。升级前请完整备份 `macula-system`
数据库及 Flyway 历史，并预检 `sys_identity_provider` 的 `provider_type`、`config_json`
（例如用 `JSON_VALID(config_json)` 找出非空且非法的 JSON）和 `app_id` 是否与 JSON 标识一致。
新接口只接受下列六种类型及各自字段；旧行若含未知字段、无效 JSON 或标识冲突，读取会返回冲突错误，
需先由数据负责人核对并修复，不能自动清洗。回滚须先停写 System/IAM，再恢复数据库备份及匹配的
应用版本；不要手工删除 V3 的 Flyway 记录或直接缩回 `app_secret` 列，以免截断已保存的密文。

## 身份提供商、消息与 OAuth 客户端管理

- 新增 `/api/v1/identity-providers`、`/api/v1/message-settings`、`/api/v1/oauth2-clients` 管理 API。
  Controller 不再硬编码 `ROOT`
  角色检查；访问授权由现有网关 URL 权限配置控制。V2 只为 `ROOT` 角色建立默认关联，后续可在
  系统权限管理中调整。直接访问 System 服务虽仍需资源服务器认证，但不会经过网关的 URL 权限检查；
  应以容器网络、防火墙或入口代理限制 System 直连，不能将其端口暴露给不可信客户端。
- 身份提供商管理 GitHub、支付宝、微信、企业微信、飞书、钉钉及阿里云号码认证配置；IAM 不接入支付宝登录。
- 身份源列表可用 `category=social|enterprise` 筛选。创建/更新请求中的 `config` 必须是 JSON 对象：
  GitHub 使用 `clientId/clientSecret`，支付宝使用 `appId/appPrivateKey/alipayPublicKey`，微信使用网站
  应用 `appId/appSecret` 及公众号 `officialAccountAppId/officialAccountAppSecret`，企业微信使用
  `corpId/agentId/appSecret`，飞书使用 `appId/appSecret`，钉钉使用
  `appKey/appSecret/corpId`。请求另带 `providerType`、相符的 `category`、名称、备注、
  启用/显示开关。服务端拒绝未知或跨类型字段；响应 `config` 只含非敏感字段，
  `configuredSecrets` 只标明凭据是否已配置。更新时省略或留空凭据表示保留原值，不能通过本接口清空凭据。
  `config_json` 存规范非敏感 JSON，`app_secret` 由 `@CryptoField` 保存密文；旧 `app_id` 继续同步以兼容已有行。
  `GET /api/v1/identity-providers/catalog` 返回分类和六种类型的稳定 code、显示名称、
  字段标签、必填/敏感/多行标记、创建应用入口及每个字段的获取位置；这些元数据与服务端字段校验
  共用枚举定义，不含配置值或凭据。链接指向第三方官方开放平台，平台菜单调整时需核对提示。
  支付宝应用私钥由管理员本地生成，只上传对应应用公钥；钉钉的 Client ID/Client Secret
  对应本接口沿用的 `appKey`/`appSecret` 字段。
  已保存的 `provider_type` 大写 code 和列表 `category` 小写 code 保持不变。
  身份源不提供可配置的回调地址；目录及列表/详情 VO 的只读 `callbackUrl` 为
  `/login/{providerType 小写}`（Admin 展示为 `IAM_URL` + 该路径），与现有 `/login/sms`
  命名约定一致，由 IAM `authentication` 包中各提供商的 LoginFilter 按相同路径处理。Admin 使用其 `IAM_URL`
  配置的对外基址显示完整 URL，部署时须核对该基址。历史 `callback_url` 列和值保留，但
  `SysIdentityProvider` 不再映射该列，管理 API 不读取或写入旧值。接入应用的 OAuth
  `redirectUris` 是另一项配置，不受此变更影响。
- V5 将消息渠道收敛为每个渠道唯一的平台全局配置；发现同渠道历史重复行时迁移直接失败，
  由运维显式清理后再执行，不自动选择或删除。邮件只支持自定义 SMTP、阿里云企业邮、腾讯云
  企业邮和网易企业邮；企业邮 SMTP 地址、端口和 SSL 模式由服务端固定。短信只支持阿里云和
  腾讯云，七牛云、自定义短信和 Exchange 独立类型不再接受。
- 消息模板初始值定义在 `MessageTemplateType` 枚举。邮件固定八个场景，可选择内置默认模板或
  保存发件人显示名、主题和 HTML 覆盖内容；预览只替换场景白名单变量，并经过服务端 HTML
  允许列表清洗。短信固定九个场景，按供应商及 `zh-CN`/`en-US` 保存审核模板编码。测试发送
  只使用当前已启用的全局供应商，30 秒内限制重复提交，响应表示供应商受理而非最终送达。
  供应商账户、审核模板和网络由部署环境提供。
- 内置邮件模板使用统一的品牌邮件结构，公共变量为 `client_name`、`user_email`、`now_time`；
  验证码场景增加 `code`，登录验证增加 `expire_time`，密码到期提醒增加 `expire_days`。
  管理员重置成功和欢迎邮件不会在正文中发送或展示明文密码。
- 身份源和消息供应商凭据使用 Macula Boot 现有 `@CryptoField` 存储；读取接口只暴露“已配置”
  状态，更新时留空保留旧值。业务代码不记录明文凭据。当前 Boot 加密拦截器在加密失败时可能
  捕获异常后继续写库，本轮按已确认范围不修改 Boot，也不承诺失败必定阻止写库。部署时应确保
  `MACULA_CRYPTO_PASSWORD` 由安全环境注入（AES 密钥必须为 16/24/32 个字符），否则 System
  无法启动；还应监控加密错误。后续要消除此风险需在 Boot 独立修复。
- 消息发送审计使用独立的 `MACULA_MESSAGE_AUDIT_HMAC_KEY` 对邮箱或手机号生成 HMAC-SHA-256
  摘要。密钥至少 32 个字符，不得复用字段加密密钥；轮换会切断新旧日志间的目标关联，应结合
  审计保留策略执行。日志只记录调用方、固定场景、提供商、结果和 HMAC 摘要。
- OAuth 客户端管理的是 IAM 共用的 `sys_oauth2_client` 表，创建时必须选择有效租户及用户类型。
  机密客户端密钥由 System `PasswordEncoder` 单向哈希保存；公共客户端使用 `none` 且无密钥，
  强制启用 PKCE。表单支持 OIDC `openid` 和 ID Token 签名设置。客户端变更在事务提交后清理
  IAM 共享缓存，但不会撤销已签发的授权或令牌。仅 `client_credentials` 的应用无需回调 URI；
  `client_secret_jwt` 不开放新配置，因为现有单向哈希密钥不能用于 HMAC 验签。上线前应针对已有客户端做兼容回归。
- V6 新增 `sys_user_identity_binding`，只维护第三方 provider subject 到 username 的全局绑定，不含
  tenant ID、user ID、user type 或 token。`(provider_type, provider_subject)` 与
  `(provider_type, username)` 均唯一；当前仅由受控运维流程预置。V6 还通过生成列唯一键约束每种
  `provider_type` 最多一条 `enabled=1` 配置，禁用配置可并存。升级前须执行
  `SELECT provider_type, COUNT(*) FROM sys_identity_provider WHERE enabled=1 GROUP BY provider_type HAVING COUNT(*)>1`；
  若有结果，迁移会在创建绑定表前按设计停止，必须备份后人工选择保留项，不会自动禁用或删除。
  清理重复项后按 Flyway 标准流程执行 `repair` 再重跑迁移；隔离 MySQL 测试覆盖该恢复路径。回滚需
  恢复升级前数据库备份并同时回退 IAM/System 应用，不能只删除 Flyway 历史或索引。

- V7 为 `sys_user.username` 增加全局唯一索引，供第三方身份绑定与登录按用户名唯一解析账号。
  升级前备份数据库并预检：
  `SELECT username, COUNT(*) FROM sys_user WHERE username IS NOT NULL GROUP BY username HAVING COUNT(*) > 1`。
  有结果时先由账号数据负责人处理，再运行迁移；唯一索引会拒绝重复值，不会自动选取、合并或删除账号。
  迁移失败时检查表结构和 `flyway_schema_history`，修复数据后按 Flyway 标准流程 `repair` 并重跑；
  不得修改已执行的 V7 文件。回滚需停写 IAM/System，恢复迁移前备份和匹配的应用版本，不能只删除
  唯一索引或 Flyway 历史。

- V8 为 `sys_identity_provider` 增加可空 `secret_config_json`，用于保存微信等身份源的附加密钥。
  `app_secret` 继续保存目录的主密钥；附加密钥 JSON 整体使用 `@CryptoField` 加密，公开 `config_json`
  不得包含任何密钥，查询接口只在 `configuredSecrets` 返回每个密钥是否已配置。升级保留既有行并将新列
  置空；回滚需恢复 V8 前数据库备份并同步回退 IAM/System，不能只删除列或 Flyway 历史。

- V9 增加 OAuth Client 的 `external_code_types`、`allowed_origins` 以及
  `sys_oauth2_client_external_provider` 固定映射。未配置的旧 Client 默认禁止所有第三方 code。
  V9 只把 mobile/email 的语义空白改为 NULL；任何非空重复或非规范历史值都会使迁移明确停止，
  不自动改写、合并或挑选账号。确认数据后才会为非空 mobile/email 建立全局唯一索引。
  System 用户新增、修改和导入统一执行手机号规范化、邮箱 trim+小写、空白转 NULL 和服务层冲突预检。
  部署前必须在隔离 MySQL 分别验证历史 V8→V9 升级与干净库初始化，不能用干净库成功代替升级证据。

- V10 为 OAuth Client 增加 `browser_login_provider_types` 和 `direct_login_provider_type`。允许列表为空时
  IAM 只提供本地认证；直接登录提供商必须属于允许列表。配置网页登录提供商的 Client 必须启用
  `authorization_code` 并登记回调 URI，每个平台还必须恰好存在一个启用的身份源。V10 同时精确删除已经
  废弃的 `WECOM_WEBVIEW_CODE` 映射和 Client CSV token，保留企微 APP、小程序及普通浏览器类型。
  升级时应先停止或排空全部旧 IAM 实例，由 System 执行并验证 V10，再失效专用 OAuth Client 缓存，最后
  启动新 IAM；禁止新旧 IAM 跨 V10 混跑，也不要通过清空共享 Redis 代替精确缓存失效。

- OAuth Client 的每个 `ExternalCodeType` 必须选择一个启用、provider 类型相符且具备对应配置能力的
  身份源 registration。微信移动应用、小程序和网站应用使用各自的 App ID/secret；飞书浏览器 code
  使用固定外部 redirect URI；阿里云号码认证使用独立 AccessKey。管理接口只回显非敏感目录和
  `configuredSecrets`，不返回秘密值。sms/email grant 只允许公共 Client，并要求非空精确 Origin。

隔离数据库集成测试 `SettingsPersistenceIT` 默认不运行；显式设置 `SYSTEM_SETTINGS_IT=true` 后，
须将 `SPRING_DATASOURCE_URL` 指向名称以 `macula_settings_it_` 开头、已升级至 V8 的隔离测试库，
并提供测试用 `MACULA_CRYPTO_PASSWORD`、MySQL 凭据与独立 Redis database。此测试禁用自动
Flyway，不会自行执行迁移。测试会核对库名，
只清理自己创建的行与缓存项，不可指向业务数据库。

Flyway 增量验证 `IdentityProviderMigrationIT` 默认禁用；仅将 `SYSTEM_MIGRATION_IT=true`、
`SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 指向**全新空的** `macula_settings_it_*` 隔离库后运行。
测试先应用 V1、V2，插入虚构旧行，再应用 V3/V4，核对 `flyway_schema_history`、列类型和旧行；
测试会在首轮成功升级与约束验证后清理该专用测试库中的对象，再构造历史重复启用数据证明 V6
安全停止、未留下绑定表，并验证清理重复项后 `repair` 与重跑成功；不会删除数据库本身，执行者须
核实库名后单独清理。若切换分支后 `target/classes` 有旧迁移残留，
先对目标模块执行 Maven `clean`，不要修改已执行的 Flyway 源文件。
## 租户安全策略

`V4__tenant_security_policy_dict.sql` 将 `sys_dict_item_tenant.value` 扩为 `TEXT`，并在全表
重复项预检通过后建立 `(tenant_id, type_code, name)` 唯一索引。发现重复项时迁移会停止，升级前
必须备份并执行 `GROUP BY tenant_id, type_code, name HAVING COUNT(*) > 1` 预检，不能自动合并数据。
迁移只替换平台租户（`tenant_id=1`）的旧安全菜单和权限，不触碰其他租户的菜单关系。

安全策略固定使用 `type_code=system_security_policy`，20 个字段名分别作为 `name`，字段的字符串化
单值作为 `value`；整数使用十进制字符串，布尔值使用 `true`/`false`。统一读取接口为
`GET /api/v1/security-policy`，分区保存接口为 `PUT /api/v1/security-policy/basic`、
`PUT /api/v1/security-policy/defense`、`PUT /api/v1/security-policy/password`。请求不接受
`tenantId`，只访问认证上下文中的当前租户。旧 `/api/v1/security-settings` 和
`/api/v1/password-policy` 接口已删除。写操作审计只记录固定键组和结果，不记录 CSP、弱密码清单
或其他配置值。这些配置当前仅保存，尚未接入 IAM/Gateway 运行时。

详细字段、默认值、单位和升级说明见 `../docs/security-policy.md`。

## 关于DTO的说明

在使用MVC三层架构时，我们通常将项目分为mapper(dao)、service、controller。

```
    controller      # 展示层，依赖service
        vo          # 展示层的输入输出对象，简化dto，适配不同展示层。如果展示层不需要定制
                    # 可以直接使用service层的dto
            req     # 展示层接口请求类
            res     # 展示层返回响应类
            
    service         # 服务层，依赖mapper
        dto         # 接口的输入输出对象，接口方法中只允许出现dto类
        
    mapper          # DAO层，不可以依赖上层
        entity      # 表映射实体
        bo          # 多表关联业务实体（主要用在sql查询）
    
    converter       # pojo类转换
                    # 建议按照主实体类聚合转换方法    
```

- mapper层定义DAO接口和SQL，包含entity（映射数据表）和bo（主要用于查询和多表关联）
- service层依赖mapper层，将dto转为entity或则将entity或者bo转为dto。不可以在service接口出现entity或者bo
- controller层依赖service，将vo转为dto或者dto转为vo返回。如果vo与dto没有差别，可以直接使用dto

### 认证、鉴权记录

- 在oauth2服务的UserDetailService的loadUserByName方法中读取用户信息，包括基本信息、角色等信息

- 我的菜单和按钮权限，前端有一个directive，调用/api/v1/users/me 接口，按钮权限。system-starter需要中转这个接口

- URL权限通过定时任务后台缓存，在网关层获取URL权限信息，并检查URL访问权限
    - 网关需要依赖system缓存URL角色关系的redis，可以考虑每个租户独立的redis
    - **TODO** 监控数据表，触发更新
- 通过AK/SK鉴权
    - 网关支持AK/SK鉴权，基于KONG的HMAC AUTH协议
    - **TODO** 需要把应用的sk，允许的url path缓存到redis供网关读取

- macula-boot-start-system模块只需要给输出管理界面的应用依赖，比如xxx-admin，其他模块不需要依赖

- 所以对于每个网关来说，需要通过redis获取URL角色对应关系、应用的配置（建议每个租户的redis可以隔离，system同步各个租户的redis）

### 租户、应用

创建应用，设置应用的租户ID。具体某个应用的后端界面获取菜单时提供应用ID，获取该租户的菜单信息。（后面搞一个模版，默认就包含系统管理功能，租户隔离）
需要租户隔离的包括：应用、菜单、字典
