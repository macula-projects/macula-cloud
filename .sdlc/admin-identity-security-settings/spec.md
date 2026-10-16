# Spec: 租户安全策略统一管理 (from intent.md 2026-10-16)
Status: accepted

## Source intent
[已接受的意图](./intent.md)：将通用安全、防御策略和密码策略整合为一个三 Tab 页面，并将当前租户的每个配置项作为独立 key/value 保存到 `sys_dict_item_tenant`。

## Requirements
1. Admin 在系统管理下只提供一个“安全策略”入口，页面包含“通用安全”“防御策略”“密码策略”三个 Tab；每个 Tab 独立处理加载、空态、校验、失败提示、保存状态和重复提交保护。
2. 通用安全必须包含 `sessionValidTime`、`verifyCodeValidTime`、`rememberMeValidTime`、`sessionMaximum`；前三项以秒为 API 与持久化单位。
3. 防御策略必须包含 `loginFailureDuration`、`loginFailureCount`、`autoUnlockTime`、`contentSecurityPolicy`；`autoUnlockTime` 以分钟为单位，CSP 支持超过 50 个字符的实际策略文本。
4. 密码策略必须包含 `passwordBiggestLength`、`passwordLeastLength`、`passwordComplexity`、`customWeakPassword`、`weakPasswordCheck`、`includeAccountCheck`、`notSameChars`、`historyPasswordCheck`、`historyPasswordCheckCount`、`illegalSequenceCheck`、`passwordValidDays`、`passwordValidWarnBeforeDays`，并校验最小长度不得大于最大长度及开关与关联数值的组合关系。
5. 每个字段在 `sys_dict_item_tenant` 中对应一条记录；固定 `type_code` 为 `system_security_policy`，`name` 使用上述字段名作为稳定 key，`value` 只保存该字段的字符串化单值，不保存整组 JSON。已知 key 以外的请求字段必须拒绝。
6. 所有读取和写入都使用认证上下文中的当前租户，不接收客户端提交的 `tenantId`；同名 key 在不同租户中互不覆盖，缺失项返回文档化默认值，保存时只更新当前 Tab 的已知 key。
7. System 提供统一读取 API 和三个分区写入 API；响应按 `basic`、`defense`、`password` 分组，写入使用结构化 JSON 对象并由 Bean Validation 与 Service 组合校验共同保护。
8. 管理 API 由现有 URL 权限体系默认拒绝匿名和未授权用户；ROOT 及被当前租户明确授予该菜单/接口权限的管理员可访问。前端菜单或按钮隐藏不能代替服务端授权和租户隔离。
9. 数据库迁移必须扩充字典项 `value` 容量，并在写入前证明 `(tenant_id, type_code, name)` 不会出现多条有效记录；发现历史重复时必须停止升级并报告，不能任选一条或静默覆盖。
10. 页面、API、迁移、默认值、key 清单、租户语义和“暂未接入运行时”的限制必须同步写入 System/Admin README，并提供后端、前端和真实 MySQL 迁移测试。

## Non-goals
- 不在本轮让 IAM、Gateway 或密码修改流程实际执行这些策略。
- 不把身份提供商、消息设置或 OAuth 客户端管理合并到安全策略页面。
- 不允许管理员通过该专用页面新增任意字典 key、修改 `type_code` 或指定其他租户。
- 不升级 Vue、Element Plus、Spring Security、MyBatis-Plus 或 Macula Boot，也不修改通用租户拦截器实现。
- 不自动删除无法确认来源的历史配置或重复字典项。

## Design
适用政策为根 `AGENTS.md`、`.agents/rules/architecture.md`、`backend-development.md`、`frontend-development.md`、`testing.md`、`dependencies-release.md`、`REVIEW.md` 和 `bands.yaml`。未发现额外的组织级品牌、合规或数据处理 Skill。设计沿用 System 的 Controller/Form/Service/Mapper 分层、MyBatis-Plus 租户上下文和 Admin 的 Vue 3/Element Plus/API 封装。

Admin 新建单一 `securityPolicy` 页面。页面首次进入并行展示三个 Tab 的框架，但由一次统一 GET 加载完整配置；各 Tab 使用自己的表单引用、校验规则和保存锁，保存一个 Tab 不覆盖另外两个 Tab。旧的通用安全和密码策略页面不再作为菜单入口。页面始终显示“配置保存不代表 IAM/Gateway 已生效”的提示。

System 新增统一 `SecurityPolicyController` 和 `SecurityPolicyService`。Controller 只接收/返回 Basic、Defense、Password 的 Form/VO；Service 维护允许的 key、默认值、类型转换、边界和组合校验，并在单个 Tab 的事务中批量 upsert。持久化复用 `SysDictItemMapper` 与 `SysDictItem`，但不通过开放的通用字典 Controller 绕过安全策略字段白名单。

每个租户共用固定命名空间 `system_security_policy`。整型使用十进制字符串，布尔值只接受 `true`/`false`，复杂度使用稳定枚举 code，普通字符串原样保存但执行长度和控制字符校验。读取时缺失 key 使用代码中的版本化默认值；格式非法、未知枚举或同一租户同一 key 多行时明确失败并提示数据修复，不能回退成看似有效的配置。

租户由经过认证的上下文和既有 MyBatis 租户机制决定。Service 不切换 `TenantContextHolder`，也不接受 `tenantId`。写入一个 Tab 时以事务保证该 Tab 全部成功或全部回滚；审计记录只包含 key 和操作结果，不记录完整 CSP、弱密码内容或其他配置值。

## Data and interfaces
- 字典命名空间：`type_code = system_security_policy`。
- key 清单：`sessionValidTime`、`verifyCodeValidTime`、`rememberMeValidTime`、`sessionMaximum`、`loginFailureDuration`、`loginFailureCount`、`autoUnlockTime`、`contentSecurityPolicy`、`passwordBiggestLength`、`passwordLeastLength`、`passwordComplexity`、`customWeakPassword`、`weakPasswordCheck`、`includeAccountCheck`、`notSameChars`、`historyPasswordCheck`、`historyPasswordCheckCount`、`illegalSequenceCheck`、`passwordValidDays`、`passwordValidWarnBeforeDays`。
- 新 API：`GET /api/v1/security-policy` 返回 `{ basic, defense, password }`；`PUT /api/v1/security-policy/basic`、`PUT /api/v1/security-policy/defense`、`PUT /api/v1/security-policy/password` 分别保存一个 Tab 并返回该分区的最新值。
- API 不包含 `tenantId`、字典记录 ID、`typeCode`、`sort`、`status` 等通用字典维护字段。写入记录固定为启用状态，排序由服务端 key 元数据确定。
- 增量迁移将 `sys_dict_item_tenant.value` 从 `varchar(50)` 扩为可容纳 CSP 和自定义弱密码的文本类型；是否新增 `(tenant_id, type_code, name)` 唯一索引取决于升级前重复数据检查和评审决定。
- 动态菜单只保留 `/system/security-policy`，组件为 `system/securityPolicy/index`；为 GET 和三个 PUT 路径建立 URL 权限。旧入口/API的处理见阻塞关注项。

## Flagged concerns
- 旧 API 与菜单兼容策略：accepted intent 未决定是直接删除 `/api/v1/security-settings`、`/api/v1/password-policy` 和旧菜单，还是保留一个发布周期的适配/跳转；这影响调用方、迁移 SQL 和测试范围，需要 Rain 决定，blocking。
- 历史独立表数据：尚未确认任何环境是否已写入 `sys_security_settings`、`sys_password_policy`；若存在则必须定义字段换算和迁移，若不存在可只停止使用旧表，需要环境负责人确认，blocking。
- `loginFailureDuration` 单位：提供的模型未标注单位，现有实现使用分钟；API、页面标签和持久化必须统一，需要 Rain 确认是否沿用分钟，blocking。
- `passwordComplexity` 枚举：当前仓库只有数值档位，未发现 `PasswordComplexityRule` 的枚举定义或稳定 code 清单；跨版本持久化不能依赖未知的 ordinal，需要 Rain 提供枚举值或批准本仓库定义，blocking。
- 字典项唯一性：当前表没有 `(tenant_id, type_code, name)` 唯一约束，且未知外部环境是否已有重复项；并发 upsert 的正确性需要升级前审计，若能清理重复则建议新增唯一索引，否则必须另行批准锁策略，blocking。
- 字典 value 长度：现有 `varchar(50)` 无法容纳 CSP 和自定义弱密码，必须增量扩列；这是向后兼容的容量放宽，但需用真实 MySQL 迁移证明，non-blocking。
- 字典类型表的 `code` 目前是全表唯一而不是租户内唯一；专用 Service 可直接使用固定 `type_code`，但通用字典页面可能不会为每个租户显示对应类型，后续若要求通用页面可编辑需单独调整模型，non-blocking。
- 运行时效果：这些值本轮不会被 IAM/Gateway 消费，页面和文档必须持续明确“暂未生效”，non-blocking。

## Verification strategy
1. System 单元测试覆盖 20 个 key 的默认值、字符串类型转换、边界、密码组合校验、未知字段拒绝、按 Tab 更新不覆盖其他分区、事务回滚、非法持久化值和重复行拒绝。
2. Web/Security 测试覆盖 ROOT、被授权租户管理员、未授权用户和匿名请求；租户测试至少使用两个 tenantId，证明同名 key 隔离且请求体不能越权指定租户。
3. 真实 MySQL 隔离测试执行现有迁移到新版本，验证 `value` 长文本往返、租户/key 查询、重复数据预检和选定的唯一性方案；若批准历史迁移，覆盖旧表有数据与空表两条路径。
4. Admin Vitest/Cypress 覆盖单页面三个 Tab、精确字段和单位、加载/空态/失败、每 Tab 保存、重复提交、保存一个 Tab 不改另两个、路由/权限及“暂未生效”提示。
5. 运行 System/API 定向 Maven 测试、Admin 单测与生产构建、相关 E2E、变更范围 lint 和 `git diff --check`；按 `REVIEW.md` 分别执行 Bugs、Security、Compliance 检查。
