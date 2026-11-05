# Plan: 应用 API Key 管理与网关缓存联动 (from spec.md 2026-11-04)
Status: accepted

## Files that change
以下后端路径相对 macula-cloud-system/：
- src/main/resources/db/migration/V13__application_apikey.sql：新表、索引及 API Key URL 权限/应用菜单角色授权衔接。
- src/main/java/dev/macula/cloud/system/pojo/entity/SysApplicationApiKey.java（新增）。
- src/main/java/dev/macula/cloud/system/form/ApplicationApiKeyForm.java（新增）。
- src/main/java/dev/macula/cloud/system/vo/app/ApplicationApiKeyVO.java、ApplicationApiKeySecretVO.java（新增）。
- src/main/java/dev/macula/cloud/system/mapper/SysApplicationApiKeyMapper.java（新增）。
- src/main/java/dev/macula/cloud/system/service/SysApplicationApiKeyService.java、impl/SysApplicationApiKeyServiceImpl.java（新增）：分页、生成、编辑、查看、撤销和对象归属校验。
- src/main/java/dev/macula/cloud/system/service/ApplicationCredentialCacheService.java（新增）：集中组装 HMAC/API Key 缓存、事务提交后同步、启动重建及刷新锁。
- src/main/java/dev/macula/cloud/system/controller/SysApplicationApiKeyController.java（新增）。
- src/main/java/dev/macula/cloud/system/service/impl/SysApplicationServiceImpl.java：应用写入/删除与凭据缓存维护衔接。
- src/main/java/dev/macula/cloud/system/controller/SysApplicationController.java：移除重复的 Controller 刷新调用，改由业务事务统一维护。
- src/main/java/dev/macula/cloud/system/listener/ApplicationListener.java：启动时恢复应用及 API Key 缓存。
- src/test/java/dev/macula/cloud/system/service/impl/SysApplicationApiKeyServiceImplTest.java、service/ApplicationCredentialCacheServiceTest.java、controller/SysApplicationApiKeyControllerTest.java（新增）。
- src/test/java/dev/macula/cloud/system/service/impl/SysApplicationServiceImplTest.java：应用更新及删除回归。
- src/test/java/dev/macula/cloud/system/migration/ApplicationApiKeyMigrationTest.java（新增）：迁移字段/授权语义验证。
- README.md。
前端路径相对 macula-cloud-admin/：
- src/views/system/application/index.vue：新增 APIKEY 入口和抽屉挂载。
- src/views/system/application/apiKeys.vue（新增）：列表、创建成功弹窗、备注/URL 编辑、显示/隐藏、复制、删除。
- src/api/model/system/application.js：统一 API Key 请求封装。
- tests/unit/application-api-key.test.js（新增）。
- README.md。
另外维护本变更的 intent/spec/plan/review-findings/test-report；不改 Boot 源码、IAM 协议、POM 或无关功能。

## Order of work
1. 核对当前 Boot 依赖包含 APIKEY 常量；必要时构建安装已合入 Boot 的本地依赖，不发布远端。确认基表无统一逻辑删除字段，不凭空依赖不存在的字段；新表使用显式 deleted 标记，Key 唯一索引保留撤销凭据的唯一性。api_key 使用大小写敏感比较；分页限制最大100，备注及 accessPath 设置长度上限。
2. 新增表与模型。Key 至少32随机字节，使用 sk- 加无填充 URL-safe Base64，数据库唯一索引兜底。列表仅返回掩码，完整值仅在创建及 secret 接口返回；Form 不接受应用/租户/凭据覆盖。清空 accessPath 要显式写 NULL，避免 MyBatis 空值忽略导致无法恢复应用 URL 兜底。
3. 明确服务端管理授权：ROOT 沿用既有特权；其他用户必须具有当前租户应用管理菜单对应角色权限并能读取目标应用，未能确认授权则拒绝。不得只依赖“已登录”和前端菜单可见；Key 操作始终限定 appId/keyId/tenantId。V13 通过应用菜单路径定位而非硬编码菜单ID，为新接口配置 URL 权限并沿用已授予应用管理菜单的角色，secret 与创建同为敏感操作；与 Mapper 租户拦截及现有网关权限缓存一起验证。必要查询尽量放入新增 Key Mapper，不扩大通用权限系统。
4. 新增缓存服务，共用字符串字段组装；permitUrls 使用 StrUtil.isNotBlank(key.accessPath) ? key.accessPath : application.accessPath，两个字段独立存储。HMAC 字段和租户逻辑保持；API Key Hash 使用 Boot 现有常量。认证租户为空则回退应用归属租户，拒绝无应用或无归属的缓存输入。
5. 写操作使用事务，afterCommit 后同步缓存；所有刷新/撤销用同一 Redis 分布式锁串行化，获取锁后从数据库重新读取最新已提交状态，避免旧快照恢复删除数据；撤销时删除已知 Key Hash，启动重建还应清除已持久化撤销 Key 的 Hash。锁不跨线程解锁，不在持锁后等待另一个同锁的事务回调。批量/全租户刷新在 finally 恢复上下文。Redis 失败传播明确错误，DB 已提交事实保留，允许重试和重建；本轮不承诺跨资源原子性或引入 outbox。
6. 创建 Key 与应用删除采用同一数据库并发协议：在事务中先锁定当前租户下的应用行（SELECT FOR UPDATE），重新确认应用存在及权限，再创建 Key 或捕获关联集合并级联撤销；批量应用按ID排序锁定。删除提交后并发创建必须因应用不存在而拒绝，创建先提交则删除必须捕获其 Key。应用写入及删除在 Service 中触发提交后缓存维护；删除应用前在当前租户捕获完整关联集合并标记其 Key 撤销，批量删除只处理确实可访问的应用。Controller 不再额外异步刷新；ApplicationListener 启动恢复 HMAC/API Key 缓存。应用配置的既有刷新流程写 Key 缓存时仅复用步骤4，不修改 Key 的 accessPath，也不创建 URL 配置绑定。
7. 新增 Controller 及管理端抽屉，按设计接口完成操作；创建和 secret 响应 no-store，敏感请求/响应不进入审计（使用现有 AuditLog 开关）。完整 Key 不作为 URL 参数、不写日志；组件关闭清除明文与错误状态。URL 显示 METHOD:/path 示例，空值说明“未配置时写缓存使用应用可访问路径”，处理空态、加载、失败和重复提交。
8. 完成对应测试和 README；记录实际偏差。实施完成先请求用户确认，再进入 sdlc-test 正式验证，之后才进入代码审查。不提交或推送源码、不部署，除非后续明确授权。

## Risks
- 可恢复保存 Key 及 Redis key 含凭据是已接受边界；限制完整值传输和日志输出，本轮不引入独立加密密钥管理。
- DB提交后Redis失败可能暂时保留旧凭据，本轮明确报错与恢复，不把业务回滚误当作缓存撤销；必须测试恢复及竞争。
- 权限缓存未命中时网关可能按配置仅要求登录，所以 Service 的默认拒绝不可省略；迁移不能随意为所有用户授权。
- 新表和权限迁移需升级前执行，真实数据库集成验证只能在隔离环境；不可修改V1/V12或真实数据库。
- 原应用刷新依赖异步全量扫描，调整必须保留 HMAC 原有行为、租户上下文及应用删除清理，控制在列明文件范围。

## Proof
- SysApplicationApiKeyServiceImplTest：多 Key、随机格式、列表脱敏、创建/查看、编辑备注与空 URL、重复撤销、跨租户/跨应用/缺少权限拒绝，未知/未授权应用不能刷新缓存。
- ApplicationCredentialCacheServiceTest：缓存字段精确匹配网关；Key URL优先及应用兜底、新增更新同规则；提交刷新/回滚不刷新；撤销/重建竞争不复活；Redis失败报告及恢复；HMAC回归和上下文恢复。
- SysApplicationApiKeyControllerTest：路由、BeanValidation、分页边界、no-store、敏感响应/日志审计无泄漏；应用删除回归覆盖关联 Key 撤销及批量越权；用受控交错覆盖删除捕获集合与并发创建，证明没有有效孤立 Key 或遗留 Hash，并在隔离数据库中验证行锁语义。
- ApplicationApiKeyMigrationTest 与隔离数据库验证：字段、大小写敏感唯一索引、撤销状态、权限与角色衔接；没有真实数据库时明确报告未验证范围，不用字符串断言冒充迁移执行。
- application-api-key.test.js：入口、分页/空态/错误态、创建复制、显示隐藏、编辑URL、删除、重复提交和关闭清除明文；构建与实际界面验证。
- 正式阶段按规则执行 System/API -am test -Plocal，必要时全仓测试；前端相关 Vitest、npm run build、定向非修复 ESLint；git diff --check 与变更Java Checkstyle。隔离验证数据库和Redis，记录真实服务与模拟覆盖边界。

用户回复“YES”，明确接受本实施计划，开始实现。

## 实施偏差
- ApplicationListener 构造器增加缓存恢复依赖，需同步修改现有 SysTenantServiceImplTest 的启动装配测试；保留原租户/权限刷新断言，并增加凭据缓存恢复断言。仅扩展测试文件集，不改变设计范围。
- 实际权限表 url_perm 为全局唯一，V13 为已有应用菜单生成一套规则，避免多租户同路径菜单导致迁移唯一键冲突；Service 对当前租户菜单/角色与应用归属的默认拒绝保持独立，真实权限联调需验证不同租户网关默认规则。

## 实施结果
- 已完成列明后端/管理端/迁移/文档与测试文件。源码保持未提交，未推送、未部署。
- 开发回归：System/API共131测试条目（130通过、1既有条件跳过），相关前端21项通过，构建及定向静态检查通过。
- 正式数据库/Redis集成、并发协议实测和可见布局验收尚未完成，详见test-report.md；未将开发回归标记为Stage4绿色证据。
- 等待用户确认实现完成后进入sdlc-test。

- 按用户追加要求，API Key 数据库字段统一为 access_path，Java/JSON/管理端属性统一为 accessPath，与应用一致；网关缓存字段 permitUrls 保留，不改变 URL 优先级规则。V13 尚未在真实数据库执行，直接同步本次新增迁移，不改动已有迁移。

- 正式验证新增 migration/ApplicationApiKeyIntegrationIT.java，使用显式隔离 MySQL/Redis 测试实际迁移、Mapper、缓存、授权与应用删除/创建行锁交错，不改产品范围；默认不连接外部环境。

- 正式验证新增管理端 cypress/e2e/application-api-key.cy.js，按仓库既有模拟登录/API模式验证实际应用页面、显示隐藏、创建、独立URL编辑和撤销；不连接真实业务后端。

- 正式验证已完成：全仓后端410条目无失败（默认跳过的1项已在独立空库补实测），新增真实MySQL/Redis IT5项、前端单元74项、Cypress33项通过，构建/静态检查通过。已检查桌面和移动截图，进入Stage5独立审查。

- 代码审查Round1修复：Key及应用写操作复用现有MenuRouteTenantContext校验token/请求租户及成员关系；提交后缓存读取改为PROPAGATION_REQUIRED复用已提交连接，专用Mapper强制刷新查询缓存，避免REQUIRES_NEW在有限池中再次申请连接；增加伪造请求租户与单连接池回归。文件范围不变，补齐已接受的可信租户及并发证明。

- 两项代码审查Important已修复，正式全量复验后由Round2审查及fresh verifier确认关闭。最终源码/测试/迁移25文件漂移0，临时MySQL/Redis与凭据已清理，源码未提交，等待用户审阅。

- 用户追加并授权界面修正：save.vue 增加与后端一致的手机号即时校验，应用 accessPath 改为多行并说明 METHOD:/path、Ant 通配符和空值语义；API Key 抽屉凭据/按钮单行布局。扩展原有管理端测试及浏览器布局验证，完成后更新本地 Admin Docker；不改变后端授权、数据或缓存协议。

- 界面回归发现 ScTableSelect 仍访问旧版 ElSelect.selected，打开新增应用会抛异常；最小兼容修复改为当前 states.selected，并验证单选/多选名称显示，不改变查询和授权。

- 用户追加紧凑布局：API Key 显示/隐藏、复制改为图标按钮，保留 tooltip 和 aria-label；收窄抽屉与表格列，复用原复制/授权逻辑。

- 用户要求 API Key 创建时间按浏览器当地时区显示：VO 改为带服务端偏移的 OffsetDateTime，按审计写入的 JVM 时区解释存储值，前端复用日期工具换算；不硬编码UTC或东八区，不迁移数据库。验证UTC/东八区服务端偏移及浏览器跨日显示，并更新本地System/Admin。

- 按用户类型约定，ApplicationApiKeyVO.createTime 改为 java.util.Date，实体仍保留既有 LocalDateTime；映射时按审计 JVM 时区转换瞬时时间，JSON 显式输出带Z的ISO字符串，前端当地显示逻辑不变。增加Date瞬时值和JSON序列化断言。

## 用户追加的接口日期统一修复

用户明确要求统一修复前述全仓审查清单，沿用已确认的类型/时区设计。接口时间使用Date，JSON统一UTC ISO且保留时区；数据库实体/BO保留LocalDateTime，IAM协议及授权存储不变。

文件扩展：System API七个已有VO（ApplicationVO/UserVO/AuditLogVO/RolePageVO/DeptVO/MenuVO/UserExportVO），新增RoleDetailVO/MenuDetailVO/PermissionVO/PermissionForm；System七个日期相关Converter及新增PermissionConverter、角色/菜单/权限Controller、MenuForm、ApplicationBO/UserBO/TenantBO/AuditLogBO、SysTenantInfoMapper.xml、OAuth2ClientVO/OAuth2ClientConfigServiceImpl、SysApplicationApiKeyServiceImpl；TinyID两个时间VO、TinyIdManagementConverter及Service；System/TinyID application.yml和相关README。

调用链及验证扩展：API Key单元/集成测试构造器同步，TinyID服务测试构造器同步；新增System接口日期JSON/Mapper/Controller边界测试及TinyID日期映射测试，覆盖外部存储时区、ISO带偏移序列化/反序列化、空值、详情业务字段和Form不接收审计字段；管理端tool.js及所有时间列统一当地显示，相关Vitest/Cypress同步ISO样例并运行前端构建。执行目标模块及全仓单元测试、Java Checkstyle、定向ESLint、diff检查。不改数据库结构或历史数据，不自动提交或推送。

- 导出链路补齐数据库边界：SysUserMapper及XML先返回新增UserExportBO（LocalDateTime），SysUserServiceImpl通过UserConverter映射Date导出VO；避免JDBC直接把接口Date作为数据库时间解释。独立详情VO保留原BaseEntity主键的字符串JSON契约。

- 调用链补查发现SysDeptServiceImpl孤立节点兜底分支使用BeanUtil复制，改为与树形分支相同的DeptConverter，并增加外部存储时区的服务分支回归。

- Date/Instant互通补查发现固定SSS的SimpleDateFormat会误解析9位小数；保留Jackson标准ISO解析，仅配置UTC输出，增加纳秒Instant到Date只截断毫秒、不发生时间滚动的回归。JSON对无时区输入可能按UTC兼容解析，调用契约仍要求Z或偏移，不把序列化规范误写成严格反序列化校验。

- 用户明确确认接口允许Date或Instant：更新AGENTS及接口类型约束测试，不批量替换现有Date字段；Boot公共转换器新增Instant与LocalDateTime双向映射，复用同一存储时区并保留纳秒，两类生成Mapper和自动发现链路共同验证。

- 用户确认暂不配置macula.time.storage-zone，沿用JVM默认时区；Compose共享应用环境统一TZ及-Duser.timezone为Asia/Shanghai，保留已有JAVA_TOOL_OPTIONS，匹配MySQL/JDBC。同步部署说明并验证Compose渲染；本次不重启服务、不修改历史数据。
