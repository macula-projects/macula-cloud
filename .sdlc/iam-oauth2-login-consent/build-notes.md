# IAM 实施与开发验证记录

日期：2026-10-08。分支：fix/iam-oauth2-login-consent。

## 已实现

- null tokenType 查询七种索引；指定类型隔离，未命中返回 null；新增 ID/device/user token 索引与 DTO 字段。
- Redis v2 主记录、TTL、索引原子替换、CAS 并发更新、删除墓碑、旧 DTO 读取与固定序列化 UID；协议测试使用真实隔离 Redis。
- 构建阶段注册 password/sms Provider，保留两种兼容扩展；按用户纠正恢复公共 Provider 原有 grant 存储标记与异常映射，统一 opaque/JWT 生成器与平台 claims。
- 公共客户端 PKCE、S256 校验、授权码重放拒绝；OIDC Discovery/JWKS、ID Token、UserInfo、退出，覆盖 opaque/JWT 路径。
- 短信占位校验按用户纠正保留默认返回 true；表单 CSRF、SecurityContext 保存、重复过滤器安装修正；密码从已认证用户对象清除。

2026-10-09 修正：撤回 CaptchaServiceImpl 返回 false 的修改，恢复原有 true；恢复短信登录入口默认开启及对应测试断言，password/sms 继续作为兼容入口。下列测试与打包结果来自修正之前，本次修正的验证另行记录。
- 中文登录、授权、登录后页面；本地样式资源；移动单列、触控、密码管理器、错误恢复；显式拒绝取消当前请求且保留历史 consent。
- README 更新协议、客户端接入、密钥配置、Redis 升级回滚与验证方式。

## 已执行验证

2026-10-09 自定义 grant 专项纠正后：`mvn -B -ntp -pl macula-cloud-iam -am test -Plocal` 成功，26 tests，0 failures/errors/skipped。新增 SMS 原参数发令牌、profile scope 刷新、缺失 phone/captcha 拒绝与 password 错误凭据映射。复用真实验证码占位实现，数据库身份查询模拟；不代表真实手机号查询或短信联调。先前测试暴露 token endpoint 缺少 CaptchaAuthenticationProvider，已补齐；测试用多验证码用户服务 Bean 导致装配冲突，已改为仅模拟数据库身份源。

2026-10-09 00:28 刷新兼容修复完成：先增加直接协议测试，复现兼容 grant 携带 openid 的空指针；新增 CompatibilityRefreshTokenAuthenticationProvider 后，`mvn -B -ntp -pl macula-cloud-iam -am test -Plocal` 通过，27 tests，0 failures/errors/skipped。新增回归循环覆盖 password/sms × opaque/JWT/refresh 复用客户端：保留 openid、无 ID Token、scope 缩小、scope 扩权拒绝、跨客户端拒绝、轮换后旧令牌失效、复用与撤销；标准授权码 OIDC 刷新明确断言仍返回 ID Token。该问题已关闭，不修改原业务验证、参数或 scope 语义。`git diff --check` 通过；本轮未重新打包或运行浏览器测试，未部署或推进正式 Test gate。

2026-10-09 兼容行为恢复后：`mvn -B -ntp -pl macula-cloud-iam -am test -Plocal -Dtest=IamProtocolTest,CaptchaAuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false` 成功，14 tests，0 failures/errors/skipped；覆盖短信入口默认展示、登录过滤器和 password/PKCE/OIDC 协议回归，不代表真实短信供应商联调。

1. `mvn -B -ntp -pl macula-cloud-iam -am package -Plocal`：成功，包含测试；24 tests，0 failures，0 errors，0 skipped。Java release 17，生成 IAM 可执行 JAR。
2. IamProtocolTest：11 个测试，真实 SecurityFilterChain、Thymeleaf、隔离 Redis、演示密钥；身份与客户端数据库服务模拟。覆盖表单成功/失败/CSRF、会话保持、匿名授权回跳、PKCE、OIDC、password 兼容、UserInfo、刷新轮换、撤销、退出、拒绝授权和已有 consent。
3. MaculaOAuth2AuthorizationServiceTest：7 个测试，真实随机端口 Redis。覆盖所有 token 类型、类型隔离、未命中、过期/短 TTL、轮换/撤销/删除、并发 CAS、旧快照防复活、用户 Principal/授权请求序列化及 state 不续期。
4. MaculaRegisteredClientRepositoryTest：3 个测试，覆盖未知客户端、五分钟授权码默认值、公共客户端强制 PKCE 与机密客户端显式策略/password 保留。
5. 原 CaptchaAuthenticationFilterTest：3 个测试通过。
6. `src/test/ui/check-ui.cjs`：Chrome headless，登录/授权页分别在 320×640、390×844、844×390、1440×960 检查，共 8 组通过。无横向溢出、按钮至少 44px、无页面 JS 异常；密码显隐、401 后恢复和拒绝提交不携 scope 通过。主代理已人工查看手机登录、桌面授权截图。
7. `git diff --check`：通过。用户原有授权码五分钟修改保留。

## 验证限制与部署注意

- 这是真实框架与隔离 Redis 的开发回归，不是生产部署、真实 Gateway/MySQL/Nacos 联调或 SLO 验证。
- 手机尺寸用桌面 Chrome 模拟，未验证真实 iOS Safari、安卓系统键盘与辅助技术。
- Spring Security Jackson 2 API 废弃提示仍存在；本次保持明确模块与 allowlist 的兼容配置，不等于完成 Jackson 3 全平台迁移。
- 既有 Lombok/MapStruct 编译提示与本机 Netty DNS 原生库提示仍存在，未影响结果。
- 不允许新旧 IAM 节点混写 Redis；回滚需防止旧键恢复已失效授权。v2 单 hash slot 的容量限制见 README。
- 非本地环境须配置外部签名密钥和客户端可访问的 issuer。email 数据未在现有身份模型中提供，不伪造邮箱或已验证状态。
- 不提交 target 产物、截图、日志或临时文件；没有提交、推送或部署。

## SDLC gate

本记录仅证明 Build 阶段实施和开发验证，未自批 Build 完成，也未推进正式 Test/Review。等待用户确认实现是否符合已接受计划。
# Jackson 3 修正（2026-10-09 09:30）

用户明确回复“改”后按 sdlc-build 回到实现阶段，替换 IAM 全部运行时 JSON mapper；自定义模块迁至 `dev.macula.cloud.iam.jackson`，使用 Jackson 3 的 ValueDeserializer、SetupContext.setMixIn 和 SecurityJacksonModules。授权服务同时迁移主记录与嵌套属性 mapper；仅允许具体 IAM 类型，未放开包级类型校验。共享 annotation 包保持原名，未排除 Nacos 所需依赖。password/sms 业务及验证码默认 true 未改动。

开发回归实际结果：
- `mvn -B -ntp -pl macula-cloud-iam -am clean test -Plocal`：34 项通过，0 失败/错误/跳过；清理的仅是该 reactor 的生成 target 产物，可由构建恢复。
- 协议测试自身 mapper 同步迁移后，`mvn -B -ntp test -Plocal`：11 模块 BUILD SUCCESS，72 项通过，0 失败/错误/跳过。
- 新增旧 Jackson 2 JSON 读取测试：包含数字 Instant、Long、SysUserDetails、已认证/未认证 Captcha/Weapp token、权限与 WebAuthenticationDetails，并重新以 Jackson 3 保存读取；已有旧 JDK DTO 二进制 fixture 继续通过。测试专用 Jackson 2 writer 不进入运行时代码。
- 新增未许可多态类型拒绝测试；既有 PKCE/OIDC、password/sms、刷新、撤销、Redis TTL/CAS 回归全部通过。
- `git diff --check` 通过；生产源码无 Jackson 2 core/databind、jackson2 模块、JavaTimeModule 引用。

首次编译发现 LongDeserializer 包移动至 deser.jdk，已修正；迁移协议测试 mapper 后遗漏 JsonNode import 导致一轮 testCompile 失败，已修正并完整重跑成功。当前测试仍为开发阶段回归，不自批 Build/Test/Review gate；未重新执行浏览器/前端套件、Nacos/生产 Redis 联调，不宣称这些已验证。此前正式验证记录标为迁移前历史结果。
