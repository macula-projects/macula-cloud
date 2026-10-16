# Plan: 租户安全策略统一配置 (from spec.md 2026-10-16)

## Files that change
- 数据库迁移：新增 `macula-cloud-system/src/main/resources/db/migration/V4__tenant_security_policy_dict.sql`。将 `sys_dict_item_tenant.value` 扩为 `TEXT NOT NULL`，在预检无重复数据后增加 `(tenant_id, type_code, name)` 唯一索引；删除旧通用安全、密码策略菜单及其角色/权限关联，创建统一 `/system/security-policy` 菜单和三个保存权限。已存在的 V1/V2/V3 不回写；旧专用配置表不迁移、不删除，仅停止使用。
- 后端删除：删除 `controller/SecuritySettingsController.java`、`controller/PasswordPolicyController.java`，`form/SecuritySettingsForm.java`、`form/PasswordPolicyForm.java`，`mapper/SysSecuritySettingsMapper.java`、`mapper/SysPasswordPolicyMapper.java`，`pojo/entity/SysSecuritySettings.java`、`pojo/entity/SysPasswordPolicy.java`，`service/SecuritySettingsService.java`、`service/PasswordPolicyService.java`，`service/impl/SecuritySettingsServiceImpl.java`、`service/impl/PasswordPolicyServiceImpl.java`，`vo/SecuritySettingsVO.java`、`vo/PasswordPolicyVO.java`，以及对应旧单元测试。旧 `/api/v1/security-settings`、`/api/v1/password-policy` 不保留兼容入口。
- 后端新增：新增 `controller/SecurityPolicyController.java`、`service/SecurityPolicyService.java`、`service/impl/SecurityPolicyServiceImpl.java`；新增 `form/SecurityBasicConfigForm.java`、`SecurityDefensePolicyConfigForm.java`、`PasswordPolicyConfigForm.java`；新增 `vo/SecurityBasicConfigResult.java`、`SecurityDefensePolicyConfigResult.java`、`PasswordPolicyConfigResult.java`、聚合返回 `SecurityPolicyResult.java`；新增稳定值为 0–5 的 `enums/PasswordComplexityRule.java`。复用现有 `SysDictItem`/`SysDictItemMapper`，不新增依赖和专用配置表。新增顶层 Java 类型均带 Apache 2.0 头、职责 Javadoc、非空 `@author` 与 `@since`。
- 后端测试：新增 `service/impl/SecurityPolicyServiceImplTest.java`，更新 `controller/AdminSettingsAuthorizationTest.java`；新增或改造 `integration/SecurityPolicyPersistenceIT.java`，覆盖迁移、唯一约束、长值和租户隔离。测试只操作显式隔离的测试库。
- Admin 删除：删除 `src/api/model/system/securitySettings.js`、`src/api/model/system/passwordPolicy.js`、`src/views/system/securitySettings/index.vue`、`src/views/system/passwordPolicy/index.vue`。
- Admin 新增及修改：新增 `src/api/model/system/securityPolicy.js` 与 `src/views/system/securityPolicy/index.vue`，同页提供“通用安全 / 防御策略 / 密码策略”三个页签；修改 `src/locales/lang/zh-cn.js`、`src/locales/lang/en.js`、`tests/unit/admin-identity-security-settings.test.js`、`cypress/e2e/admin-identity-security-settings.cy.js`。动态路由由 V4 菜单提供，不新增静态路由或前端依赖。
- 文档：修改 `macula-cloud-system/README.md` 和 `macula-cloud-admin/README.md`，记录新 API、20 个字典键、单位、复杂度代码、权限、迁移预检及回滚边界。当前工作树中身份源、消息配置、OAuth 等无关实现保持不动，除共享测试或菜单断言的必要适配外不扩大范围；用户已有 `AGENTS.md` 与 `.agent_context/` 不修改。

## Order of work
1. 冻结并核对当前脏工作树，确认只处理安全策略相关文件；先添加失败测试，固定 20 个键、默认值、校验、三组 API、租户隔离、权限和三页签交互。
2. 增加 V4 迁移：先用 SQL 检查 `(tenant_id, type_code, name)` 重复项并在存在重复时中止，再扩容 `value`、建立唯一索引；按依赖顺序清理旧菜单的角色菜单、角色权限、租户权限关联和菜单记录，再插入统一菜单与权限。迁移不读取或搬运旧专用表数据，因为这些表尚未作为已发布数据源使用。
3. 实现后端模型与服务：固定 `type_code=system_security_policy`，以 `name` 保存配置键、`value` 保存单个标量字符串；从认证/租户上下文解析当前租户，API 请求体不接受 `tenantId`。查询时一次取出当前租户全部键并按默认值组装三个结果；三个页签分别在事务内校验并 upsert 自己的键，不修改其他页签。
4. 实现 `GET /api/v1/security-policy`、`PUT /api/v1/security-policy/basic`、`PUT /api/v1/security-policy/defense`、`PUT /api/v1/security-policy/password`，接入现有 URL 权限体系。通用安全按秒处理；`loginFailureDuration`、`autoUnlockTime` 按分钟处理；密码复杂度只接受 0–5 的稳定代码。布尔值规范化为 `true`/`false`，整数规范化为十进制字符串，字符串原样保存但执行长度和内容校验。
5. 删除旧后端 API、服务、Mapper、Entity 和旧测试，确保源码及调用方不再引用专用安全/密码策略表；保留历史数据库表以避免破坏性迁移。日志和异常不得输出自定义弱密码内容或完整内容安全策略。
6. 实现 Admin 统一页面和 API 封装：三个页签独立加载状态、表单校验和保存按钮，保存成功只刷新当前页签；覆盖空态、失败重试和重复提交保护。删除旧两个页面与 API，并同步中英文文案、菜单和权限断言。
7. 更新 README 与测试。Stage 3 先执行相关编译、单测、Admin 单测/构建和 `git diff --check` 作为实现反馈；完成后交给 Stage 4 按隔离 MySQL、完整 Maven、Vitest/Cypress 与 `REVIEW.md` 正式验证，不将环境依赖失败误报为代码失败。

## Risks
- 唯一索引会在历史重复键存在时失败，这是预期的安全停止行为；升级前必须备份并运行重复项预检，由环境负责人决定保留哪一条，代码不自动删除或合并数据。
- 旧 API 和旧菜单直接移除是已确认的破坏性变更；尚未同步的外部调用方会失败。README 明确新路径和权限，不额外提供双写或兼容代理。
- `value` 扩为 `TEXT` 后，MySQL 索引不包含该列，不影响唯一键；长 CSP 和弱密码清单需用数据库往返证明不截断，同时禁止在日志、审计描述和响应错误中泄露具体内容。
- 租户隔离依赖现有租户插件与认证上下文。服务不得接受、切换或信任客户端 tenantId；ROOT 与租户管理员都必须按当前上下文读写，授权仍由菜单 URL 权限控制。
- 同一页签多键写入必须处于同一事务；唯一约束冲突、校验失败或数据库异常时整组回滚，避免半保存。并发 upsert 要用唯一键兜底并验证异常映射。
- 旧专用表本轮不删且不迁移，回滚代码仍可读取旧表，但新字典值不会自动反写旧表。该边界是基于旧功能尚未发布数据的确认；若部署前发现生产数据，必须停止并另做迁移设计。
- 当前工作树含同一较大需求的其他未提交实现；删除和改名时必须只匹配安全策略文件，不能覆盖身份源、消息、OAuth、部署配置或用户文件。

## Proof
- 后端单测：20 个键逐一覆盖；缺失键返回默认值；三页签只修改各自键；整数边界、布尔值、CSP/弱密码字符串、0–5 复杂度及非法输入；同页签原子回滚；旧 API/类型无引用。
- 授权测试：匿名、无权限、具备对应权限的请求结果；请求体 tenantId 或其他未知字段不能越权；三个 PUT 权限相互独立，GET 遵循统一页面读取权限。
- 数据库集成：在隔离 MySQL 从 V1 迁移至 V4，验证 `value` 长文本往返、唯一索引、重复预检失败、两个租户同名键可并存且互不可见、同租户同键不可重复；验证旧菜单和关系清除、统一菜单及权限存在，旧专用表未被破坏。
- Admin Vitest/Cypress：统一入口、三个页签、字段和单位、复杂度 0–5 显示与请求、分别保存、加载/失败/重复提交保护、菜单和权限；确认旧路径不再作为入口。执行 `npm run test:unit`、目标 Cypress 和 `npm run build`。
- 工程检查：执行 `mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal`（外部依赖按测试规则区分）、`git diff --check`，并按 `REVIEW.md` 完成 Bugs、Security、Compliance 检查；Stage 4 留存真实命令、退出码和环境限制。
