# Plan: 身份与安全设置管理 (from spec.md 2026-10-13)
Status: accepted

## Files that change
- 数据和依赖：现有工作区已有 `macula-cloud-system/src/main/resources/db/migration/V2__admin_identity_security_settings.sql`、相关 POM 与配置改动。本轮新增 `macula-cloud-system/src/main/resources/db/migration/V3__identity_provider_json_config.sql`，仅扩容身份源凭据列并增加备注列；不编辑 V1/V2。其他依赖或部署文件只在验证确有必要且记录偏差后修改。
- System 持久化：现有工作区已包含 `pojo/entity/SysIdentityProvider.java`、其余五类设置/OAuth Entity 及对应 Mapper。本轮只调整 `SysIdentityProvider.java` 的备注映射和加密凭据列语义；必要时调整 `mapper/SysIdentityProviderMapper.java` 的查询，不修改 IAM Entity 或其他持久化模型。
- System API 模型：现有工作区已包含 `form/IdentityProviderForm.java`、`vo/IdentityProviderVO.java`、`enums/IdentityProviderType.java` 及其余设置和 OAuth 管理模型。本轮调整这三个身份源类型以支持分类、备注、对象型 `config` 与 `configuredSecrets`；视实现需要在 `service/impl/` 或 `enums/` 新增本仓库自有的六类字段模式/校验类型。新增 Java 顶层类型按版权头、类级 Javadoc、`@author Rain`、`@since` 编写，不依赖或复制 eIAM 类。
- System 业务与协议：现有工作区已包含五类 Service/Controller、`RootAdminGuard` 和消息适配。本轮修改 `service/IdentityProviderService.java`、`service/impl/IdentityProviderServiceImpl.java`、`controller/IdentityProviderController.java`、`pojo/entity/SysIdentityProvider.java`、`mapper/SysIdentityProviderMapper.java`（仅实际需要时），完成分类过滤、差异字段校验、敏感字段拆分与旧记录读取兼容；其他服务维持现有范围。
- Admin：现有工作区已包含五个页面和 API 封装。本轮修改 `macula-cloud-admin/src/views/system/identityProvider/index.vue`、`src/api/model/system/identityProvider.js`（仅参数适配时）、`src/locales/lang/zh-cn.js` 与 `en.js`；使用现有 Element Plus 组件展示社交/企业页签、六种专属字段及列表图标，不引入新前端依赖或复制参考项目素材。菜单仍沿 V2 动态路由。
- 测试与文档：修改 `macula-cloud-system/src/test/java/dev/macula/cloud/system/service/impl/IdentityProviderServiceImplTest.java`、`macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`、`macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`，并在隔离数据库测试 `macula-cloud-system/src/test/java/dev/macula/cloud/system/integration/SettingsPersistenceIT.java` 覆盖 V3 与加密列；修改 `macula-cloud-system/README.md`、`macula-cloud-admin/README.md`。其余现有测试保留，IAM 运行时不改。

## Order of work
1. 冻结现有工作树：检查 `git status --short`、现有 V2、身份源 Form/VO/Entity/Service/Controller、页面/API/测试及 README；只改本轮列出的身份源文件，保留其余功能和用户改动。先核实隔离数据库与本地 Compose 的身份源记录数量，不把本地空表推断为其他环境为空。
2. 先添加失败用例：后端覆盖六种字段模式、分类不匹配、对象型 `config`、未知或跨类型字段、Secret/私钥不回显、凭据保留、旧记录无损读取及无效记录拒绝；前端覆盖页签切换、分类查询、差异表单和请求体。测试数据只用虚构值。
3. V3 迁移与后端模型：使用现有 Flyway、MyBatis-Plus、Macula Boot `@CryptoField` 及项目已引入的 Jackson 工具，不增加依赖；在独立 V3 中增加备注并扩容加密凭据列，不触碰 V1/V2。将 `IdentityProviderForm`/VO 改为 `config` 对象，Entity 保留 `config_json` 文本与加密凭据列。为旧 `app_id`/`callback_url` 定义读取合成和同步策略，迁移不覆盖已有记录。
4. 后端业务和 API：按提供商白名单校验类别与字段，拒绝未知字段；非敏感字段写入规范 JSON，敏感字段拆出加密，更新空值保留、显式值替换；类型不可变；列表按分类过滤。读取旧记录只公开白名单字段，遇无效/可疑 JSON 明确失败并提示处理，不静默丢弃。
5. 前端页面：社交/企业页签分别查询、展示和添加，类型切换清理旧字段，按类型呈现标签与必需项；编辑只填回非敏感 `config`，凭据不回显；使用自有/现有图标和中英文文案。保留加载、空态、失败、重复提交和 ROOT 权限边界。
6. 文档与验证：补充 V3 备份/预检/回滚和新 API 契约；运行相关后端单测、System/API Maven 测试、隔离库迁移/加密往返、Admin Vitest/Cypress/构建及 `git diff --check`。按 `REVIEW.md` 做 Bugs、Security、Compliance 检查。正式测试签收属于 Stage 4；外部供应商实网投递仍需独立凭据和收件确认。

## Risks
- Boot 当前 `@CryptoField` 拦截器加密异常后可能继续写库。按已接受规格本轮不改 Boot、不承诺失败即中止；验证正常加解密路径，并在交付时明确残余风险，不把明文凭据放入应用日志。
- `sys_oauth2_client` 可空 secret 的迁移与 IAM 构造 `RegisteredClient` 的行为可能不兼容；需在开发早期用旧/新客户端样本验证。若必须改 IAM 运行时或扩大跨模块契约，先记录计划偏差并请工程师确认。
- System 与 IAM 的 Redis 缓存必须共享相同 cache name/key/序列化配置；缓存只能在事务成功提交后失效，避免旧密钥、租户或 scope 持续生效。
- SMTP AUTH 在目标 Exchange 邮箱已开放，但 TLS、证书和供应商账号/模板审核仍依赖外部环境；本地 SMTP/HTTP 模拟不能代表实网投递成功。自定义 HTTP POST 必须限制目标地址以防 SSRF，并禁止脚本/表达式。
- 新管理 API 的 ROOT 校验必须覆盖读取和测试发送，不能只靠菜单或网关；全局配置不能受当前用户租户上下文误过滤。测试发送需限制滥用与敏感回显。
- 菜单 ID、权限码、Flyway 版本与已部署数据可能冲突；只增量迁移，提供数据库备份/回滚说明，不自动删除既有客户端、菜单或配置。
- V2 已在本地 Compose 执行，不能改历史迁移；外部环境可能已有身份源记录。V3 仅扩列，读取旧 JSON 时必须验证格式和字段；无法无损兼容的记录需由环境负责人处理后再升级，绝不自动删除或清洗。
- TopIAM 把私钥/Secret 放在单一 JSON 中，本仓库按已验收规格分离密文列。支付宝 PEM 私钥加密后可能超过旧列长度，V3 必须扩容并以长私钥样本做数据库往返；不得把原文持久化到 `config_json`、日志或 API 响应。

## Proof
- System 单测：ROOT/非 ROOT/匿名访问；六种身份源的分类/字段/JSON/凭据更新与拒绝、旧记录兼容与异常拒绝；安全/密码边界、模板默认与覆盖、OAuth 租户和 PKCE/OIDC 等现有测试不退化。
- 数据库/跨服务集成：从 V1→V2→V3 和 V2→V3 升级、旧身份源行保留、长私钥密文往返且 `config_json` 无密钥；旧机密客户端与新公共客户端、IAM 缓存失效和 PKCE/OIDC 兼容维持原证明范围。区分真实隔离库验证与 mock。
- 网络适配测试：模拟 SMTP AUTH 和阿里/腾讯/自定义 HTTP 端点，覆盖成功、拒绝、超时、错误映射、临时目标与 POST 参数映射；仅凭真实测试账号和收件端确认实网投递。
- Admin Vitest/Cypress：社交/企业页签、六种字段、跨类型切换清理、对象型 `config`、凭据不回显，并保留五个入口的加载、空态、失败、保存与重复提交、租户选择、PKCE/OIDC、模板预览和临时测试目标；构建与 `git diff --check`。不把 IAM 第三方登录、运行时密码策略或细粒度权限配置误报为已实现。

## Deviations recorded during implementation
- `CLAUDE.md` 在该工作树不存在；按根 `AGENTS.md` 和 `.agents/rules/` 执行，未绕过仓库规则。
- 安全/密码配置使用单条 JSON 持久化，并分别新增 Form 与 VO；身份源和 OAuth 分页直接接收有界参数，未增加原计划的两个 Query 类型。字段仍经服务端验证，不改变 REST 路径。
- 消息设置 UI 的四个功能页签保留在一个 `messageSettings/index.vue`，未拆成四个只服务该页的子组件；发送适配器以 `*TestSender` 类实现，避免额外的空接口层。功能路径与原计划一致。
- Macula Boot 的加密自动配置只有 `macula.crypto.enable=true` 才生效；System `application.yml` 增加 AES/BASE64 和必需的 `MACULA_CRYPTO_PASSWORD` 环境占位。未修改 Boot，仍保留其加密异常处理风险。需要在环境提供 16/24/32 字符密钥后才能启动 System。
- 阿里/腾讯短信 SDK 版本未由 Boot BOM 管理，System POM 在目标模块单独固定本地可解析的 Apache-2.0 版本；未改根 POM或 API 模块。
- 自定义短信要求鉴权请求头与 `@CryptoField` 凭据，避免 Boot 拦截器对空凭据执行 `String.valueOf(null)` 加密后误认其已配置；OAuth 管理 API 不接受新 `client_secret_jwt` 配置，因本方案的单向哈希密钥无法供 HMAC 验签；`private_key_jwt` 仍保留。
- Stage 4 隔离数据库启动发现 System 原先没有 `CacheManager` Bean；已在 System POM 增加与 IAM 相同的 Macula Boot cache starter。为满足新的必需加密环境变量，增补 `deploy/docker-compose.yml` 的 System 环境映射及 `deploy/.env.example` 说明，未部署现有容器。
