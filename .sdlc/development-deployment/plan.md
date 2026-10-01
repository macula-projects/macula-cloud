# Plan: 开发与 Docker Compose 部署支持 (from spec.md 2026-10-01)

## Files that change
- SDLC 工件：`.sdlc/development-deployment/plan.md`；本计划替换已废弃的 Kubernetes 实施计划，后续实际偏差继续记录在本文件。
- 仓库入口与忽略规则：修改 `.gitignore`、根 `.dockerignore`、根 `README.md`，只保留 Compose、本地环境文件和镜像构建相关入口/忽略项。
- 删除 Kubernetes 与集中式 Dockerfile：删除空入口 `deploy/k8s.yml`、整个 `deploy/k8s/`、`deploy/docker/Dockerfile.java`；不新增任何集群部署文件。
- Compose 编排：重写 `deploy/docker-compose.yml`；更新 `deploy/.env.example`、`deploy/scripts/compose.sh`、`deploy/README.md`，增加默认中间件模式、`apps` profile、八个后端服务、Admin、构建命令和显式重置。
- 中间件初始化：修改 `deploy/init/mysql/init.sh`，只创建五个数据库/应用账号并导入 Nacos schema；保留 `deploy/init/mysql/nacos-mysql.sql`；删除 `deploy/init/mysql/seata-mysql.sql`、`deploy/init/mysql/snail-job-mysql.sql`。保留并调整 `deploy/init/nacos/init.sh`、`deploy/init/nacos/seata.properties`、`deploy/rocketmq/broker.conf`。
- 模块内 Flyway：修改 `macula-cloud-system/pom.xml`、`macula-cloud-tinyid/pom.xml`、`macula-cloud-seata/pom.xml`、`macula-cloud-snailjob/pom.xml`，使用 Spring Boot 4 Flyway starter 和父依赖管理的 Flyway 11.14.1 MySQL 支持；分别新增 `src/main/resources/db/migration/V1__baseline.sql`。System/TinyID 的 V1 由现有 docs dump 移入资源目录并删除原 `docs/*.sql`；Seata/SnailJob 的 V1 由已核对上游版本 SQL 移入各自模块。
- Flyway 与运行配置：修改 System、TinyID、Seata、SnailJob 的 `application.yml`，配置 datasource、`baseline-on-migrate`、baseline version、validate 和 migration location；Gateway、IAM、System、TinyID、SnailJob 继续支持 IDE 本机默认值、Docker Profile 和 Nacos 配置中心覆盖。
- System 编译兼容：只对 `macula-cloud-system/src/main/java/dev/macula/cloud/system/service/*.java` 和 `service/impl/*.java` 中实际导入旧 MyBatis-Plus `extension.service` 的 28 个文件执行机械 import 迁移到 3.5.17 的 `spring.service` 包，不改方法、类型层次或业务逻辑。
- TinyID 兼容验证：先在 `macula-cloud-tinyid/pom.xml` 与配置范围内验证当前 Druid 1.2.28/Spring Boot 4 装配；若确认必须升级版本或引入未管理依赖，暂停实施并修订计划，不自行扩大依赖变更。
- 实施偏差（2026-10-01）：干净编译发现 Spring Security 当前版本已将 `AbstractValidatingPasswordEncoder.encode` 设为 final；为保持既有无前缀 MD5 fallback 算法且让 System/IAM 镜像可从源码构建，增加对 `macula-cloud-system/.../PasswordEncoderConfig.java` 和 `macula-cloud-iam/.../PasswordEncoderConfig.java` 的最小适配：把匿名 `MessageDigestPasswordEncoder` 改为等价 `PasswordEncoder`，不改变默认 bcrypt、编码标识或密码数据。
- 实施偏差（2026-10-01，经用户明确要求）：修复 IAM 在 Spring Security 7 下的干净编译阻断。修改 `macula-cloud-iam/pom.xml` 显式引入已不再传递提供的 `spring-security-config`；迁移 captcha/weapp 过滤器和 configurer 的 `AntPathRequestMatcher` 到 `PathPatternRequestMatcher`，迁移授权服务器 configurer、Bearer filter 包名、lambda DSL、扩展 password grant 常量及相关 Spring Framework 7 API；同步迁移 IAM 的 5 个 MyBatis-Plus service import，并将不再可用的 Commons Logging 调用切换到已有 SLF4J；新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/captcha/CaptchaAuthenticationFilterTest.java` 验证路径/方法匹配和参数转换。仅恢复当前认证配置的等价装配，不改变端点、HTTP 方法、认证授权规则或公共契约。
- 实施偏差（2026-10-01，经用户明确要求）：参考 Macula Boot Alibaba examples，将 Gateway、IAM、System、TinyID、SnailJob 的 `bootstrap.yml` 合并进各模块 `application.yml` 并删除 5 个旧文件。基础文档只保留应用名、`docker -> local` Profile group 和两个 Nacos Data ID import；`local` 文档包含 IDE 所需的全部本地默认配置并直写本地 Nacos 连接；`docker` 文档只把 Nacos 地址覆盖为 Compose DNS。TinyID 删除不再需要的 `spring-cloud-starter-bootstrap`；Compose 固定激活 `docker` 且不再向应用传递 Nacos URL、用户名、密码或 namespace。其他共享环境只允许在对应 Profile 文档中放必要的配置中心连接参数，其余配置进入配置中心；当前未提供经批准的非本地连接参数，因此不写入真实凭据、内网地址或虚构占位值。
- 实施偏差（2026-10-01）：合并配置后的 `clean package` 暴露 Gateway 仍引用 Spring Boot 3 的 `org.springframework.boot.autoconfigure.data.redis.RedisProperties`，旧 `target` 曾掩盖该错误。将 `macula-cloud-gateway/.../RedisConfiguration.java` 机械迁移到 Macula Boot 6.1 `RedissonConfigBuilder` 已使用的 `org.springframework.boot.data.redis.autoconfigure.DataRedisProperties`，不改变双 Redis 客户端配置键或连接行为。
- 实施偏差（2026-10-01）：Gateway 容器启动进一步暴露 Spring Boot 4 Redis 自动配置与两个自定义 `DataRedisProperties` Bean 的候选冲突。将主 `spring.redis` 属性 Bean 标记为 `@Primary`，并为两个 Redisson 客户端参数增加显式 `@Qualifier`；这只消除装配歧义，继续保持主/系统双 Redis 配置键和客户端分工。
- 实施偏差（2026-10-01）：同一次干净构建确认 TinyID 不需要升级 Druid 版本，但旧 starter 的 `DruidDataSourceBuilder` 不再可用，因此改为直接创建 `DruidDataSource` 并继续由原 `spring.datasource.druid.master` 属性绑定；同时将 Spring Boot 4 的 `ServletComponentScan` import 迁移到 `org.springframework.boot.web.server.servlet.context`。不改变数据源配置键、动态路由或 Servlet 扫描范围。
- 实施偏差（2026-10-01）：SnailJob 干净构建还暴露 `ServletWebServerFactory` 的 Spring Boot 4 包迁移；将 `MaculaSnailJobApplication.java` 的 import 机械迁移到 `org.springframework.boot.web.server.servlet`，不改变启动检查或 Web Server 停止逻辑。
- 实施偏差（2026-10-01）：容器运行验证确认 Nacos 3.2 的 Console API 已从旧 `/nacos/v1/...` 迁移到独立 8080 端口的 `/v3/console/...`。初始化脚本按官方 v3 readiness、namespace 和 config API 调整，并校验统一响应体的 `data`，避免 HTTP 200 但业务数据为空时误判成功。
- 实施偏差（2026-10-01）：Flyway 11 的 MySQL 连接初始化会读取 `performance_schema.user_variables_by_thread`；MySQL 初始化仅为本地应用账号增加该表的 `SELECT` 权限，业务库权限保持不变。SnailJob 1.9.0 自带入口类仍链接 Spring Boot 3 的 `ServletWebServerFactory`；保留 1.9.0/Java 17，不扫描或启动该旧入口，改由仓库内已等价声明扫描范围、事务、REST 和 gRPC 启动检查的 `MaculaSnailJobApplication` 启动。
- 实施偏差（2026-10-01）：SnailJob 1.9.0 还传递引入 MyBatis-Plus Boot 3 starter 和 MyBatis-Spring 3；在 SnailJob 模块排除这两个旧启动依赖并显式使用父级管理的 MyBatis-Plus Boot 4 starter 3.5.17，不修改 SnailJob 版本。Seata 2.0.0 内置 Logback 配置的条件 include 在当前 Logback 上未生成任何 appender，模块内增加 Boot 4 可解析的最小控制台配置，保持日志级别和业务配置不变。
- 实施偏差（2026-10-01）：运行 V1 发现原 System dump 的 `sys_role_menu.menu_id` 定义后缺少逗号，修正该 SQL 语法后重新从空测试库迁移。SnailJob 1.9.0 的 datasource template 还以二进制名引用 MyBatis-Plus 旧包下的 `MybatisSqlSessionFactoryBean`，在 SnailJob 模块增加继承 3.5.17 新实现的单类兼容桥；其节点注册只读取 Boot 3 旧 `ServerProperties` 的 port/context-path，因此增加同二进制名的最小配置属性桥。Seata 2.0.0 的 Web Console 安全配置仍依赖已移除的 `WebSecurityConfigurerAdapter`；保留 console JAR 中 Server console 所需 DTO，但由仓库入口启动并从组件扫描中排除 `io.seata.console.*`，事务协调器核心和 `/health` 仍保留。
- 实施偏差（2026-10-01）：System/IAM 的 Redis 配置键从 Boot 3 的 `spring.redis` 迁移为 Macula Boot 6.1 `DataRedisProperties` 使用的 `spring.data.redis`；Gateway 的两套 Redis 仍由其显式 `@ConfigurationProperties("spring.redis...")` 绑定，不作无关重命名。
- 实施偏差（2026-10-01）：Spring Security 7 的 `@EnableWebSecurity` 不再替代应用配置类声明，为 IAM 的默认过滤链补回显式 `@Configuration`，使 `HttpSecurity` 基础设施先于两条 `SecurityFilterChain` 创建。Seata 扫描还会拾取依赖内的 `ServerApplication` 并再次触发无过滤扫描，进一步排除该上游入口。SnailJob 的 Jackson 2 配置显式依赖 JSR-310 模块，并为 MySQL 8 首次认证 URL 增加 `allowPublicKeyRetrieval=true`。
- 实施偏差（2026-10-01）：Seata 2.0 Server 的 `ClusterController` 仅按类型获取旧 `ServerProperties`、`ServerRunner` 仅用旧包名事件读取 Web 端口；为这两个 Boot 3 二进制名增加模块局部最小桥和 bean，避免把兼容类扩散到 Macula Boot 或其他服务。
- 实施偏差（2026-10-01）：Nacos 3.2 schema 不再预置 `nacos/nacos`，但本仓库 local Profile 显式使用该公开本地开发账号；MySQL 初始化以 `INSERT IGNORE` 补齐 bcrypt 用户和 `ROLE_ADMIN`，不覆盖已有账号。Compose 仍只监听 loopback 且保持本地开发的 auth-disabled 配置，避免把该示例账号误用为生产安全配置。
- 实施偏差（2026-10-01）：Seata 2.0 的 Nacos registry 适配器会在 Nacos 3 client 尚处于 `STARTING` 时立即注册并终止进程；单机 Compose 不依赖注册中心发现 Seata，因此与既有 `SEATA_CONFIG_TYPE=file` 一致，将 Compose 的 registry type 也显式设为 `file`。IDE/local 默认仍保留 Nacos registry 配置。
- 实施偏差（2026-10-01，经用户复核要求）：五个 Cloud 服务的 Nacos 连接统一移到 `application.yml` 公共文档；`local`、`docker`、`dev`、`stg`、`pet`、`prd` 文档只声明 Nacos 地址、namespace 和认证信息，均提供公开开发默认值并支持 `NACOS_*` 环境变量覆盖。`local` 仍独占完整本地业务配置，其他共享环境的业务配置仍由配置中心提供；Compose 不注入 Nacos 连接参数。
- 实施偏差（2026-10-01，经用户再次复核）：进一步按配置是否随环境变化重排五个 `application.yml`。`server.port` 显式改为 `${SERVER_PORT:模块默认端口}`；context-path、公共 Nacos 连接、路由/安全规则、Flyway 策略、连接池等稳定配置进入公共文档，`local` 只保留本机连接信息和调试差异，其他 Profile 只保留 Nacos 连接变量。Docs、RocketMQ、Seata 也统一改用 `SERVER_PORT`，并移除 Seata/SnailJob 重复的 HTTP 端口环境变量。
- 实施偏差（2026-10-01，经用户最终确认）：五个 Cloud 服务不再分别声明 Nacos Config/Discovery 连接值，公共文档只在 `spring.cloud.nacos` 放置一份连接值；各 Profile 的连接变量统一收敛到 `spring.config.nacos`，再由公共连接配置引用。容器验证表明 Alibaba Discovery 2025.1.0.0 不会从公共属性继承 namespace，会错误注册到 `public`，因此仅保留 `spring.cloud.nacos.discovery.namespace` 对公共 namespace 的兼容引用；System 独有的 `discovery.metadata.version` 也继续保留。
- 实施偏差（2026-10-01，经用户再次确认）：MySQL、Redis 和 IAM 连接值不再通过 Compose 环境变量注入应用。Gateway、IAM、System、TinyID、SnailJob 的 `local` 文档直接保存本机公开开发值，`docker` 文档直接覆盖 Compose DNS；Seata 的本机数据库值直接写入基础文档，并由 `docker` 文档覆盖数据库主机。共享环境仍通过 Nacos 提供环境差异。Compose 公共应用环境删除全部 `MYSQL_*`、`REDIS_*`、`IAM_*` 项，中间件初始化自身所需变量继续保留。
- 实施偏差（2026-10-01，经用户复核端口配置）：应用容器内端口保持为服务间稳定契约，由 Dockerfile 的 `APP_PORT`/第二端口构建参数设置镜像 `SERVER_PORT`、`SNAILJOB_GRPC_PORT`、`SEATA_SERVER_SERVICE_PORT` 和 `EXPOSE`，`application.yml` 提供相同 fallback。Compose 删除八个 Java 服务重复硬编码的监听端口环境变量，健康检查直接读取镜像环境；可调整的发布端口统一改名为 `*_HOST_PORT`，只改变宿主机绑定，不改变容器内端口或服务间 URL。
- 实施偏差修正（2026-10-01，经用户澄清）：上一条关于删除 Compose 监听端口变量的处理不符合用户意图，已被本条替代。Compose 必须为八个 Java 应用显式传入 `SERVER_PORT`，并为 Seata/SnailJob 显式传入第二服务端口；Dockerfile 环境变量仅作为镜像独立运行默认值。`*_HOST_PORT` 仍只控制宿主机发布端口，使应用监听端口、容器目标端口和健康检查在 Compose 中可直接核对。
- 实施偏差（2026-10-01，经用户复核 System 配置）：System 的 Flyway、Feign、Seata、SpringDoc 和完整 logging 配置从公共文档移入 `local` 文档。`docker` 通过 Profile group 继续继承这些本地开发设置；dev/stg/pet/prd 不再从仓库获得这些设置，由对应 Nacos Data ID 提供。
- 实施偏差（2026-10-01，经用户要求统一其余配置）：其余模块按 System 的分层规则同步调整。Gateway/IAM/Docs/RocketMQ 的完整 logging、IAM 的 Feign/Seata/SpringDoc、TinyID/SnailJob/Seata 的 Flyway 均移入 `local`；Seata、Docs、RocketMQ 补充 `docker -> local` Profile group，使 Compose 继续继承本地开发配置。模块专属且环境无关的路由、连接池和服务参数仍保留公共文档。
- 实施偏差修正（2026-10-01，经用户复核数据库凭据）：数据库地址和库名继续由各模块 `local`/`docker` 文档直接声明，但容器内 MySQL 应用账号和密码不再继承 YAML 中的本地默认值。Compose 从 `deploy/.env` 读取统一的 `MYSQL_APP_USER`、`MYSQL_APP_PASSWORD`，仅注入 System、IAM、TinyID、Seata、SnailJob，并由五个模块的 `docker` 文档显式引用；IDE `local` 仍保留公开开发默认账号。
- 实施偏差修正（2026-10-01，经用户进一步复核 Profile 和端口）：MySQL 应用账号和密码在五个数据库模块的 `local`、`docker` 文档中都显式配置，`local` 使用可由环境变量覆盖的公开开发默认值，`docker` 要求 Compose 注入。八个后端的 `server.port` 从公共文档移入 Profile：`local` 对齐 `.env` 的 `*_HOST_PORT`，`docker` 对齐 Compose 的 `SERVER_PORT`；SnailJob gRPC、Seata service 端口同样区分宿主机与容器变量。本机 MySQL、Nacos、Redis、IAM、RocketMQ 连接端口也统一引用 `.env` 的宿主机端口变量，Docker 继续使用固定容器端口和 DNS。配置核对时同时删除 System `ignore-urls` 中的空列表项，避免 Spring Security 7 将空字符串解释为非法路径。
- 实施偏差修正（2026-10-01，经用户澄清应用监听端口）：上一条将 `server.port` 绑定 `*_HOST_PORT` 的处理不正确，由本条替代。`server.port` 是 Spring Boot 进程监听端口，在容器内即容器端口，因此八个模块恢复公共 `${SERVER_PORT:模块默认端口}`；SnailJob gRPC 与 Seata service 同样恢复进程监听变量。`*_HOST_PORT` 仅用于 Compose `ports` 发布映射。只有 `local` 访问 MySQL、Nacos、Redis、IAM、RocketMQ 等依赖时才使用 `.env` 的宿主机端口，`docker` 使用 Compose DNS 与容器端口。
- 模块 Dockerfile：新增 `macula-cloud-gateway/Dockerfile`、`macula-cloud-iam/Dockerfile`、`macula-cloud-system/Dockerfile`、`macula-cloud-tinyid/Dockerfile`、`macula-cloud-seata/Dockerfile`、`macula-cloud-snailjob/Dockerfile`、`macula-cloud-rocketmq/Dockerfile`、`macula-cloud-docs/Dockerfile`。各文件从仓库根构建自身 Maven module，使用 Java 17 多阶段构建和非 root 运行用户；重复结构保持一致，不引入额外基础镜像链。经用户复核后，端口改为带模块默认值的 `APP_PORT` 构建参数并继续支持运行时 `SERVER_PORT` 覆盖，JVM 参数由 `JAVA_OPTS` 覆盖；Seata/SnailJob 的第二服务端口也提供独立构建参数并写入镜像元数据。
- Admin 镜像：完善 `macula-cloud-admin/Dockerfile`、`.dockerignore`、`.env.production`、`nginx.conf`，使用 `npm ci`、非 root Nginx、SPA 回退及 Gateway/IAM Compose DNS 代理。
- 实施偏差（2026-10-01）：Admin 的 Linux 镜像构建暴露 `sideM.vue` 引用 `NavMenu.vue` 与实际 `navMenu.vue` 大小写不一致，修正引用以保持大小写敏感文件系统可构建；同时将仓库自带的 Vue 默认 Cypress 示例替换为当前登录页冒烟断言，使计划中的 Admin E2E 入口对应真实应用，而不修改页面功能。
- 最小装配：保留并完善 `macula-cloud-docs/src/main/java/dev/macula/cloud/docs/MaculaDocsApplication.java`、Docs/RocketMQ 的 `src/main/resources/application.yml` 及两个 `*ApplicationTest.java`，只保证独立端口和 Spring Boot 最小装配。
- 不修改公共 REST/Feign 契约、领域实体/Mapper、数据库业务结构、认证授权规则、租户行为、依赖版本属性、`bands.yaml` 或远端环境。

## Order of work
1. 清理旧方向：删除本次产生的 K8s 目录、旧空入口和集中式参数化 Dockerfile，移除 README、`.gitignore` 中的 K8s 引用，确保后续只有一个 Compose 部署路径。
2. 建立数据库所有权：将 System、TinyID、Seata、SnailJob 的已核对 SQL 移为各模块 V1 migration；MySQL bootstrap 改为建库/建用户并只导入 Nacos 官方 schema，确认默认中间件启动不会创建 Flyway history。
3. 集成模块内 Flyway：四个模块加入父级管理的 Flyway 依赖和显式配置；空库执行 V1，已有非空库使用 baseline version 1 接管，checksum 或失败 migration 必须阻止启动。System 是共享 `macula-system` 的唯一 migration owner，IAM 不引入 Flyway。
4. 修复已确认的最小构建阻断：机械迁移 System 的 28 个 MyBatis-Plus import 并先做编译；验证 TinyID 的真实装配状态，若必须修改未获准的依赖版本则停止，而不是用旧 target 产物绕过。
5. 为八个 Java 模块创建就近 Dockerfile：统一 Maven/Java 17/非 root 结构，模块名固定，应用端口、第二服务端口和 JVM 参数提供模块默认值并允许构建期/运行期覆盖；从仓库根逐个运行对应 Maven package，检查生成的是可执行 JAR。
6. 完成 Admin 镜像：保留模块本地构建上下文，验证生产环境变量、Nginx `/api/`、`/iam/` 代理和 SPA fallback。
7. 重写 Compose：基础设施和初始化任务不设 profile；八个后端与 Admin 使用 `apps` profile。使用 YAML anchors 复用公共应用环境、日志和重启策略，并按 MySQL/Nacos/RocketMQ、System migration、IAM/Gateway/Admin 的依赖关系组织启动顺序。
8. 更新脚本与文档，并完成用户追加的 IAM Spring Security 7 最小兼容迁移及五个 Cloud 服务的单一 `application.yml` 配置结构：`compose.sh` 提供 `up`、`up-apps`、`build`、`status`、`logs`、`down`、`reset --confirm` 和显式服务参数；README 记录数据库所有权、Flyway 新增 migration 规则、IDE 顺序、Nacos 覆盖方式、Compose 全栈启动和故障诊断；IAM 保持原认证端点与安全语义并恢复干净编译。
9. 执行静态与构建证明：检查 Compose 默认/`apps` profile、Shell、Dockerfile、敏感信息和 K8s 遗留；运行四个数据库模块及最小装配模块的 Maven 测试/打包和 Admin 构建。
10. Docker daemon 可用时执行运行证明：从空 volume 启动中间件，确认只有 Nacos schema 已初始化；启动 `apps` profile，验证四个应用库的 Flyway V1/history、重复启动、基本服务状态和显式 reset。若 daemon 仍不可用，明确标记运行项未验证。

## Risks
- System V1 dump 含 `DROP TABLE` 和种子数据，只能在空库执行；`baseline-on-migrate` 可避免已有库重放，但不能证明已有 schema 与 V1 一致，接管前需备份并核对。
- System/IAM 共用数据库，IAM 先于 System migration 启动会缺表；Compose 必须建立 System 就绪依赖，IDE 文档必须明确首次启动顺序。
- Seata 自身的 datasource 装配未必会暴露 Spring Boot Flyway 所需的主 `DataSource`；若自动配置无法运行，需要在 Seata 模块内补最小 migration datasource 装配，但不得改变 Seata store 行为。
- TinyID 当前 Druid starter 与 Spring Boot 4 可能运行不兼容；本计划只允许使用当前管理版本内的最小兼容方式，版本升级属于需要重新确认的偏差。
- Spring Boot 4 将 Flyway 自动配置拆分到独立模块；四个模块必须使用 `spring-boot-starter-flyway`，并显式包含 Flyway 11 的 `flyway-mysql` database support artifact。
- 八个 Dockerfile 存在有意的少量重复；过度提炼成自定义基础镜像、生成器或符号链接会降低模块独立性，本次只通过一致模板和 Compose anchors 控制漂移。
- Compose `depends_on` 只能表达容器/健康状态，TCP 探针不等于业务健康；Gateway/IAM/System 调用链仍需运行验证。
- IAM Spring Security 7 迁移涉及安全过滤链装配；必须用干净编译和针对性过滤器测试证明 POST/path 匹配及 token 转换行为保持不变，不能仅凭 import 成功断言安全行为等价。
- 合并配置后，非 `local` Profile 不再读取仓库内数据库、缓存、路由等默认值，必须由 Nacos 的通用/环境 Data ID 完整提供；`docker` 是本地容器例外，通过 Profile group 复用 `local` 默认值并仅覆盖 Nacos DNS。验证时需检查 Maven 资源过滤结果及 `local`/`docker` 文档激活边界。
- Docker daemon 或内存不足会限制全栈证明；静态 config 和 Maven package 不能替代实际容器启动。
- 删除 K8s 目录和迁移原 docs SQL 路径可能影响已有本地引用；根/部署 README 必须明确新入口和 migration 位置。

## Proof
- 范围与静态质量：`git diff --check`；`sh -n`，可用时运行 `shellcheck`；搜索并确认没有 `deploy/k8s`、`k8s.yml`、Helm/Kustomize 引用、真实 Secret、共享内网地址、floating image tag、target/dist 或范围外 API/业务修改。
- Flyway 依赖与资源：检查四个模块的依赖树包含 Spring Boot Flyway 自动配置、父管理的 Flyway 11.14.1 core/MySQL support；打包后检查 JAR 内存在各自 `db/migration/V1__baseline.sql`，Nacos SQL只存在于部署初始化路径且无 `flyway_schema_history`。
- Flyway 行为：空库分别启动 System、TinyID、Seata、SnailJob，确认 V1 成功及关键表/history；重复启动无待执行项；对已有关键表无 history 的库验证 baseline 接管；修改 migration checksum 的临时验证必须失败且不提交修改。
- System/TinyID/IAM 兼容：System 与 IAM 完整重新编译，确认不再引用旧 `extension.service`；运行 System 目标测试；为 captcha/weapp matcher 和 converter 增加或运行针对性测试。TinyID 运行目标测试与启动冒烟，明确区分 Druid 基线兼容失败和本次 Flyway 配置失败。
- Compose 合约：运行 `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml config` 以及 `--profile apps config`，核对默认集合不包含应用、apps 集合完整、应用发布端口由 `*_HOST_PORT` 控制且容器内端口固定、端口只默认绑定 `127.0.0.1`、卷/健康检查/依赖/环境变量一致。
- 配置来源：确认 Gateway、IAM、System、TinyID、SnailJob 不再包含 `bootstrap.yml` 或 `spring-cloud-starter-bootstrap`，所有模块过滤后的 `application.yml` 能被 YAML 解析；`server.port`、`spring.cloud.nacos` 及环境无关配置位于公共文档，八个模块的 `server.port` 使用 `SERVER_PORT`，`*_HOST_PORT` 仅存在于 Compose 发布映射；各 Profile 的 Nacos 变量位于 `spring.config.nacos`，`local` 包含本机依赖连接/调试差异并使用宿主机端口，各模块的 Flyway、Feign、Seata 客户端、SpringDoc 和完整 logging 仅位于实际需要它们的模块 `local` 文档，`docker` 通过 profile group 继承 `local` 并覆盖依赖的容器 DNS/端口，基础文档包含两个 Nacos Data ID import；Compose 渲染结果显式激活 `docker`，仅向五个数据库应用传递 MySQL 账号密码，不向应用传递 MySQL 地址/库名、Redis、IAM 或 Nacos 连接环境变量。
- 模块构建：对八个模块分别执行与 Dockerfile一致的 `mvn -pl <module> -am package -DskipTests -Plocal`，检查可执行 JAR；Docker 可用时逐个构建镜像并检查 Java 17、非 root 用户、入口和端口。
- Java/前端验证：运行四个数据库模块的相关 `-am test -Plocal`，运行 Docs/RocketMQ 最小装配测试；在 Admin 执行 `npm ci` 和 `npm run build`，检查 Nginx SPA/代理配置。全量测试失败必须区分既有依赖问题、外部环境问题与本次回归。
- 运行集成：从空 Compose volume 启动默认基础设施，确认 MySQL/Redis/Nacos/RocketMQ 健康、Nacos schema/config 已初始化且四个应用库仍为空；再启用 `apps` profile，确认四个应用库由各自模块迁移、八个后端/Admin 状态及 Gateway/IAM/System 基本链路；重复启动和 `reset --confirm` 分别证明数据保留与显式重建。
