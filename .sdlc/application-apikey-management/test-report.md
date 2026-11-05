## 接口允许Date或Instant及映射支持（2026-11-05）

- 用户明确确认接口时间点允许java.util.Date或java.time.Instant；更新AGENTS、模块文档及类型约束测试，已有Date接口字段不批量替换。
- Boot DateTimeConverter新增instantToLocalDateTime(Instant)与toInstant(LocalDateTime)，复用同一storage-zone；不经过Date，Java映射保留纳秒。方法使用不同名称以兼容已有toLocalDateTime(null)调用。数据库实际精度仍由列定义决定。
- Boot目标模块clean test共15项通过；实际生成普通MapStruct和Plus Mapper均调用新增方法，配置时区和系统默认时区下均可往返，自动发现及空值验证通过。已安装到本地Maven仓库供Cloud验证，未发布远端。
- Cloud System/API/TinyID目标测试175条目，174通过、1既有迁移条件跳过，无失败/错误；本轮仅规则、文档和类型守卫测试变更，既有Date映射回归通过。Boot及Cloud相关Java Checkstyle无违规，diff检查通过。
- 沿用上一节标准ISO JSON互通与真实浏览器精度证据；没有更改前端生产逻辑、数据库或接口现有字段类型，没有部署Docker或提交推送。

## Date/Instant JSON兼容补查与修复（2026-11-05）

- 用户询问接口是否同样支持Instant。实测发现前次新增的固定SSS日期格式会将9位小数当作超大毫秒数，导致Date解析时间滚动；已移除System/TinyID的spring.jackson.date-format，保留Jackson标准ISO解析及UTC输出。
- 新回归验证Instant的9位小数输入到Date仅截断为毫秒，同一时间点带偏移输入、没有毫秒的ISO输入正常；Date输出再读取为Instant也正常。默认解析器可能兼容无偏移ISO输入并按UTC解释，不能将输出带时区等同于严格拒绝所有无时区输入；接口调用约定仍要求Z或偏移。
- System/API/TinyID目标测试175条目：174通过、1既有迁移条件跳过，无失败/错误；前端相关19项通过；真实浏览器3项通过，直接验证Instant纳秒ISO与Date毫秒ISO得到同一毫秒值；定向ESLint、Checkstyle及diff检查通过。生产前端格式化逻辑未改动。
- 公共DateTimeConverter当前显式提供Date与LocalDateTime映射，未在本轮扩展Instant实体映射或更改现有VO类型；JSON兼容不等于Java字段可无修改替换。
- 未更新Docker，未提交或推送。旧的固定SSS和严格缺省时区拒绝说明以本节修正为准。

## 接口日期时间统一修复（2026-11-05）

- 按用户审查后的明确授权，将接口9个JSON VO的13个时间字段及用户Excel导出VO改为java.util.Date；数据库Entity/BO保留LocalDateTime，IAM协议、授权Redis存储和历史数据库值不变。
- 移除剥离时区的字段JsonFormat；System/TinyID显式输出UTC ISO（yyyy-MM-dd'T'HH:mm:ss.SSSXXX），测试验证带偏移反序列化、缺少时区输入拒绝、UTC输出和空值。
- System和TinyID普通Mapper引用Boot GlobalMapperConfig；API Key和TinyID旧实体时间转换复用注入的DateTimeConverter；覆盖外部Asia/Shanghai存储时区。ApplicationBO/UserBO/TenantBO改为数据库日期时间语义，租户XML匹配日期时间类型。
- 角色/菜单/权限详情使用独立VO，原URL及业务字段名保留，主键保留原字符串JSON契约；菜单和权限写入改为业务Form，不接收审计时间。权限更新以路径ID为准。用户导出查询先返回UserExportBO，再按公共时区转换Date导出VO，Excel格式实测保留时分秒。
- 调用链补查修复部门孤立节点兜底分支的BeanUtil复制，统一DeptConverter，并添加服务分支回归。
- 管理端应用、用户、部门、日志、维护人、TinyID及API Key时间列统一调用tool.formatDateTime，按浏览器当地时区展示；纯日期查询及Excel显示格式不改成时间点协议。

验证证据：
- 全仓mvn test -Plocal通过：419条目，418执行通过，1既有迁移条件跳过，无失败/错误（/private/tmp/cloud-unified-dates-all-final-test.log）。之后部门兜底增量修复执行System/API package复验：API2项、System138条目（137通过、1既有条件跳过），新增HTTP/JSON/生成Mapper/Excel/部门分支测试全部通过。
- TinyID34项通过，包含已有Testcontainers多MySQL数据源测试6项；没有复用或改写业务数据库。本轮未重新执行需显式开关的API Key隔离MySQL/Redis IT，不将此前IT证据冒充日期修复的验证。
- 全量前端Vitest13文件93项通过；UTC时区定向18项通过；全量Cypress5文件34项通过（真实浏览器、模拟API），最终应用列表ISO当地时间新增断言后定向3项通过。
- 前端构建通过，System/API/TinyID打包通过；60个变更Java文件Checkstyle无违规，相关前端ESLint无错误，git diff --check通过。
- 本地构建使用本次安装到Maven仓库的新Boot Parent/MapStruct Starter，未发布远端。System包已包含新增自动配置类；本轮没有更新Docker容器。
- 之前两轮API Key代码审查对应旧快照，不自动覆盖本轮日期统一改动。源码未提交/推送，新范围尚未执行独立代码审查或生产升级。

## Date VO 类型调整复验（2026-11-05）

- 按用户约定将ApplicationApiKeyVO.createTime改为java.util.Date，实体LocalDateTime保持不变。映射时使用审计写入的JVM时区转Instant，再创建Date。
- 字段JsonFormat显式输出带Z的ISO字符串，避免全局日期格式丢失时区；前端当地时间格式化不变。
- API Key Service/Controller定向测试13项通过，覆盖UTC及Asia/Shanghai时区转换、JSON字符串精确值、空值和既有授权行为；3个Java文件Checkstyle和git diff --check通过。未重复运行未改动的前端测试。
- 已重建本地System镜像用于更新；不修改数据库、其他服务或全仓DTO/VO/Form。

## 创建时间当地显示复验（2026-11-05）

- System容器实际为UTC，审计通过LocalDateTime.now写入；VO补充服务端JVM偏移，避免前端把无时区时间误当当地时间。OffsetDateTime只用于响应，不修改数据库存储或网关认证。
- 列表按浏览器当地时区格式化yyyy-MM-dd HH:mm:ss，列标题注明当地；复用tool.dateFormat，空值/非法时间显示破折号，不硬编码八小时时差。
- 后端System/API -am test -Plocal通过，System131条目（130执行通过、1既有环境条件跳过）；新增测试验证UTC/Asia-Shanghai服务端偏移和空值。
- 前端相关单元测试在TZ=UTC与TZ=Asia/Shanghai各12项通过，验证同一时刻的不同偏移表示和跨日显示；定向Cypress3项通过并检查实际截图；构建、定向ESLint无错误、3个Java文件Checkstyle和git diff --check通过。
- System/Admin本地Docker重新构建；未改变数据库/JVM时区。各System实例和重启前后必须保持审计写入的时区一致，这是现有LocalDateTime存储约束。

## 紧凑图标布局复验（2026-11-05）

- 用户要求收窄列表：显示/隐藏及复制改为图标，保留 tooltip 与 aria-label；Key 列360→260px，抽屉1120→960px，备注/路径/操作列同步收紧。
- API Key 单元测试11项通过；定向 Cypress 3项通过，覆盖图标实际SVG、显示/隐藏、复制、桌面及390px单行几何断言，已检查两种截图；构建、定向ESLint无错误及git diff --check通过。
- 仅更新管理端布局与既有测试选择器，不改变服务端授权或凭据处理。

## 用户反馈界面修正复验（2026-11-05）

- 联系方式增加与 ApplicationForm.mobile 一致的前端格式校验，字段旁提示有效11位手机号；后端校验规则不变。
- 应用访问路径改为多行输入，应用和 Key 均说明 METHOD:/path、Ant 通配符、英文逗号和留空语义；补充关键逻辑注释。
- API Key 值与显示/复制、编辑/删除各自保持单行；长 Key 字段横向滚动。Cypress 对桌面和390px手机宽度执行真实几何断言，并验证复制值；已查看截图。
- 实际浏览器还发现共享 ScTableSelect 访问旧版 ElSelect.selected 的异常；改为当前 states.selected，补齐单选显示名称和 value-key，并以实际 Element Plus 挂载验证单选/多选。
- 全量前端 Vitest 12文件86项通过；全量 Cypress 5文件34项通过（API模拟，实际浏览器）；生产构建通过，定向 ESLint无错误（保留既有格式警告），git diff --check通过。
- 本地 Admin Docker 已重建并替换，健康检查通过；System与数据库无需再更新。本次未重复运行后端测试，未声称真实登录/业务数据端到端验证。
- 先前两轮代码审查结论针对界面修正前快照；本段记录新增前端改动的回归证据，不冒称旧审查覆盖新改动。

# 审查修复后的正式复验

状态：**Verification is green**。Round1独立审查及fresh verifier确认两项Important后曾撤销绿色，已修复并完成下列全量复验，Round2增量审查及fresh verifier均确认两项问题关闭，新增/未解决发现0。

- 全仓 `mvn test -Plocal`：411条目，410执行通过、1既有条件跳过，失败/错误0；该条件迁移测试已在专用空库单独执行通过。
- 真实MySQL/Redis `ApplicationApiKeyIntegrationIT`：6通过，0跳过，包含真实Hikari单连接池创建/撤销。
- 有限池探针：修复前 `BOUNDED_POOL_RESULT=503`，修复后 `BOUNDED_POOL_RESULT=SUCCESS`；不得把单连接复现泛化为所有默认池请求都会失败。
- 前端全量单元74通过、Cypress全量33通过；最终布局构建成功、Java Checkstyle通过；ESLint及diff检查通过。

实际输出：
```text
[INFO] BUILD SUCCESS
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
Test Files  11 passed (11)
Tests  74 passed (74)
All specs passed! 00:38 33 33
BOUNDED_POOL_RESULT=SUCCESS
```

租户修复复用既有MenuRouteTenantContext校验token租户/请求租户/成员关系及ROOT切换；API Key与应用写操作均使用可信租户。新增“同名角色但无目标租户成员关系”拒绝测试。缓存在afterCommit复用已提交连接，专用Mapper禁用查询缓存并强制清理缓存；直接刷新时仍开启只读事务。真实单连接池下缓存创建/撤销成功，不额外借第二个连接。

本轮原始日志：/private/tmp/cloud-apikey-all-tests-round2.log、cloud-apikey-review-fix-tests.log、cloud-apikey-integration.log、cloud-apikey-pool-probe.log、cloud-apikey-all-ui-tests-round2.log、cloud-apikey-all-e2e-round2.log、cloud-apikey-checkstyle-round2.log。只读方法使用共享已提交连接，不承诺数据库/Redis跨资源原子性。

---
以下为第一轮验证与历史记录，最新状态以上述结果为准。

# 应用 API Key 管理正式验证

状态：**Verification is green**。用户确认 accessPath 命名调整后明确要求继续，进入正式验证。本报告中的绿色仅指当前隔离测试证据，不表示部署、真实 IAM/网关联调或人工批准。

## 实际执行结果
| 范围 | 命令/方式 | 结果 |
| --- | --- | --- |
| 全仓后端 | mvn test -Plocal | 410条目，409执行通过、1既有环境条件跳过，失败/错误0 |
| 既有条件迁移测试 | SYSTEM_MIGRATION_IT=true，定向 SysUserUsernameUniqueMigrationTest | 1通过，0跳过；覆盖默认跳过项 |
| 新增真实集成 | SYSTEM_APIKEY_IT=true，定向 ApplicationApiKeyIntegrationIT | 5通过、0跳过 |
| 管理端全部单元 | npm run test:unit -- --run | 11文件、74通过 |
| 管理端全部E2E | npm run test:e2e:ci | 5文件、33通过 |
| 构建 | npm run build | 成功 |
| 静态检查 | 19个变更Java Checkstyle、定向非修复ESLint、git diff --check | 成功；最近修改的IT/布局再次检查通过 |

实际输出：
```text
[INFO] BUILD SUCCESS
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Test Files  11 passed (11)
Tests  74 passed (74)
All specs passed!  00:38  33  33
```

全仓Maven日志各模块汇总合计410，默认跳过项为SysUserUsernameUniqueMigrationTest，另一个独立空库中执行该项成功，不能把默认跳过计为默认执行通过。新增IT通过显式命令运行，不冒充默认mvn test覆盖。

## Proof 对照及真实环境
- 隔离MySQL使用本机已有官方 mysql:8.4 镜像，随机127.0.0.1端口、tmpfs数据目录；每轮新建空 macula_apikey_it_* 库；Flyway实际执行V1至V13共13个迁移成功。未使用既有业务库。
- Redis8.10.2仅监听随机127.0.0.1端口、关闭持久化；使用项目已有Redisson连接工厂、真实RedisTemplate与分布式锁。
- IT实际覆盖：多Key、API Key URL优先/空值回退、清空URL真实写NULL、缓存身份字段、撤销不影响其他Key、重建清除撤销Hash、实际菜单角色管理权限及跨应用/跨租户拒绝、事务回滚不写Redis、应用删除级联撤销、应用删除持行锁时并发创建阻塞后拒绝、大小写敏感唯一索引及已撤销Key不能重用。
- 通过真实MyBatis/Spring事务和生产审计填充器装配，不启动Nacos/IAM。初轮IT因装配使用Lettuce而失败，改用现有Redisson；随后修正手动Mapper注册顺序以启用生产审计填充器。未为让测试通过而修改产品持久化语义。
- 单元/MockMVC补充接口映射、分页/长度边界、未知身份字段拒绝、响应no-store、审计排除完整值、Redis失败安全报错、上下文恢复及HMAC回归。
- Cypress验证实际构建页面的脱敏、显示/隐藏、关闭后清除、创建、accessPath编辑与删除；使用模拟登录/API。所有未声明接口阻断，不依赖业务后端。测试首轮曾因防缓存query和常驻隐藏抽屉选择器失败，修正测试范围后全套通过。
- 已查看桌面及390x844移动截图；根据实际截图补齐抽屉内容边距，随后全量单元/E2E重新通过。截图位于管理端忽略的cypress/screenshots/application-api-key.cy.js/，不提交截图或dist。

## 日志与边界
原始日志位于/private/tmp：cloud-apikey-all-tests-final.log、cloud-apikey-integration.log、cloud-existing-migration.log、cloud-apikey-all-ui-tests.log、cloud-apikey-all-e2e.log、cloud-apikey-e2e-build.log、cloud-apikey-checkstyle-formal.log、cloud-apikey-it-checkstyle.log、cloud-apikey-eslint-formal.log。

隔离服务保留至本轮审查完成，结束前关闭Redis并删除本次专用MySQL容器及临时凭据。未变更线上/现有数据库或部署服务。

数据库与Redis跨资源原子性、额外密钥加密管理仍是已接受的边界；未宣称真实IAM/网关联调或生产升级验收。当前未修改CLAUDE/AGENTS、skill、hook、REVIEW或reviewer，无相关配置eval变化，未虚构eval套件。

---
以下为历史开发阶段记录，旧的待验证项以上述正式结果为准。

# 应用 API Key 管理实施验证记录

日期：2026-11-04。阶段：Build 开发回归，不替代实施确认后的 sdlc-test 正式验证与独立代码审查。

## 已实现
- 应用多 Key、脱敏分页、授权查看/复制、备注与独立 accessPath 编辑、撤销。
- 新增/更新/重建缓存使用 Key URL 优先、应用 URL 兜底；归属与认证租户沿用 Boot 字段协议。
- 提交后同步缓存、应用删除级联撤销、启动重建；共同应用行锁及缓存分布式锁防止删除/创建与发布竞争。
- 新表及权限迁移 V13、管理端抽屉、使用和恢复说明。

## 已执行的开发检查
- 后端 compile 成功；首次下载 Starter 遇到仓库TLS中断，重试后成功，无POM变更。
- `mvn -pl macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal` 成功：System API 2项通过；System 129条目，128执行通过、1项既有环境条件跳过；失败/错误0。
- 新HTTP测试先复现默认JSON忽略多余字段的行为，修复Form显式拒绝后通过；此前测试导入和重载匹配编译错误已修复。
- `npm run test:unit -- --run tests/unit/application-api-key.test.js tests/unit/application-auth-tenant.test.js`：2文件、21项全部通过。
- `npm run build` 成功；既有大chunk提示未作为错误处理。
- 定向非修复ESLint成功；变更Java Checkstyle修复两项后成功；git diff --check通过。
- 日志在 `/private/tmp/cloud-apikey-system-tests.log`、`cloud-apikey-ui-tests.log`、`cloud-apikey-ui-build.log`、`cloud-apikey-checkstyle.log`、`cloud-apikey-eslint.log`，不提交生成物。

## 证据边界及待正式验证
- 后端为单元/MockMVC测试；未在真实数据库执行V13，也未验证真实Redis、数据库行锁与撤销/创建竞争。不以静态SQL契约断言代替数据库迁移验证。
- 现有 SysUserUsernameUniqueMigrationTest 1项因未提供隔离数据库条件跳过，不是API Key迁移验证。
- 临时模拟API预览服务曾在本机5917启动；自动浏览器访问host不可用，改用Chrome原生通道时遇到用户操作，未完成可见布局验收。服务已关闭，未连接业务后端、未创建真实凭据。
- 现有权限表url_perm全局唯一，新增规则复用一个应用菜单授权；其他租户的网关默认鉴权配置与角色衔接需在正式集成验证中核对，Service仍独立校验当前租户菜单角色/应用归属并默认拒绝。
- 数据库和Redis跨资源原子性不在本轮保证范围，缓存失败时必须报错并恢复，旧权限可能短暂存在；README已说明。
- 尚未进行Stage5独立代码审查；本次未修改AGENTS/技能/hook，无相关eval变更。

## accessPath 命名调整复验
按用户要求，API Key 列名改为 access_path，Java/JSON/管理端属性改为 accessPath，与应用一致；缓存 permitUrls 保持原协议。相关后端定向测试、前端21项测试及构建通过，旧 permit_url/permitUrl 属性无残留，git diff --check 通过。

## 最终清理与一致性
独立审查结束后已核对专用容器名称/镜像/随机端口，关闭临时Redis8.10.2，删除本次tmpfs MySQL容器及临时凭据文件。没有操作现有业务服务。25个被审代码文件与Round2快照一致，漂移0。业务源码仍未提交，正式验证和审查记录不表示部署或人工批准。

## 2026-11-05 本地全应用镜像更新

用户明确授权更新各镜像。本地Compose共9个应用镜像构建成功，使用已验证的Boot Parent及MapStruct本地构件；仅重建应用容器，中间件及数据卷未重建。全部9个应用health为healthy，运行镜像ID与当前构建结果一致。8个Java应用均具有TZ=Asia/Shanghai和-Duser.timezone=Asia/Shanghai，未注入MACULA_TIME_STORAGE_ZONE；System/TinyID启动日志确认参数及+08:00偏移，运行JAR确认包含Date/Instant转换器。

HTTP冒烟：Admin首页200；IAM OpenID发现200；无令牌访问System及Gateway菜单路由均401（预期认证边界）。本轮构建跳过测试，功能测试证据见上文；未验证登录后的全业务操作，未转换历史DATETIME。本地镜像标签6.1.0-SNAPSHOT；未推送镜像或代码。

构建日志：`/private/tmp/cloud-timezone-all-images-build.log`；容器更新日志：`/private/tmp/cloud-timezone-all-apps-recreate.log`。
