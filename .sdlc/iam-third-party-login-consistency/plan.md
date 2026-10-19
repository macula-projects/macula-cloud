# Plan: IAM 第三方登录类型与路径一致性 (from spec.md 2026-10-19)

## Files that change
- SDLC：新增 `.sdlc/iam-third-party-login-consistency/plan.md`、`.sdlc/iam-third-party-login-consistency/review-findings.md`，记录工程约定与 Plan 评审。已接受的 intent/spec 保持不变；若需要扩大范围，先回到相应审批门。
- IAM 枚举：新增 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/enums/ThirdPartyProviderType.java`，仅含 `GITHUB`、`WECOM`，显式保存原 `providerType` 和 `loginPath`；新增 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/enums/ThirdPartyProviderTypeTest.java`，以字面量固定对外值。
- GitHub：修改 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubLoginFilterConfigurer.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProvider.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/github/GithubAuthenticationProviderTest.java`。
- 企业微信：修改 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationFilter.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomLoginFilterConfigurer.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProvider.java`、`macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/wecom/WecomAuthenticationProviderTest.java`。
- IAM 入口与文档：修改 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/controller/LoginController.java`、`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/config/DefaultSecurityConfiguration.java`、`macula-cloud-iam/src/main/resources/templates/login.html`、`macula-cloud-iam/README.md`。
- 安全链回归：修改 `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/config/IamProtocolTest.java`，验证实际渲染链接、匿名放行与回调。
- 不修改 System、Gateway、System API、管理端、POM、配置或数据库迁移；不引入新依赖。若发现必须修改以上文件，先记录计划偏差与影响并请工程师确认。

## Order of work
1. 核对工作区与既有 `GITHUB`、`WECOM`、`/login/github`、`/login/wecom` 的 IAM/System 调用点；先在新枚举测试及既有 Provider/Security 测试中固定外部值、另一 Provider 拒绝、匿名入口、实际模板链接和 `client_id` 参数。测试期望必须用独立字面量，避免与被测枚举同源。
2. 新建 IAM `ThirdPartyProviderType` 枚举，显式定义两组稳定字符串，并沿用相邻 Java 文件的 Apache 2.0 头、职责 Javadoc、非空 `@author Rain` 与 `@since`。不根据枚举名推导值，也不把持久化、state 或 token 的 String 字段改为枚举类型。
3. 依次替换两个 Filter、Configurer、Provider 的运行时字面量：Filter/Configurer 的匹配路径与 Provider 的类型判断、配置读取、已认证 token 类型均从枚举读取；保留现有安全链顺序、GET 回调语义和受信 issuer 构造方式。
4. 让 LoginController 的可见性判断及提供给模板的链接路径、DefaultSecurityConfiguration 的匿名 matcher 使用同一枚举；登录模板从服务端路径生成两个链接，并继续携带原 `client_id`。检查页面隐藏/禁用语义和上下文路径、URL 编码。
5. 在 IAM README 记录稳定类型/路径与枚举作为 IAM 内部单一映射，不改变开放平台注册回调说明。静态核对 System `IdentityProviderType.callbackUrl()` 仍对应原路径，不对 System 建立实现依赖。
6. Stage 3 运行定向测试及 `git diff --check` 作为实现反馈，搜索受影响目录确认运行时重复字面量已收敛且测试字面量仍可独立保护契约；记录实际偏差。完成后停在 Build 实现确认门，正式测试与环境差异由 Stage 4 记录。

## Risks
- Phase B deviation（2026-10-19）：仓库中没有 `CLAUDE.md`；按技能要求完成检查后，改用仓库实际存在的 `AGENTS.md` 和已读取的相关 `.agents/rules/*` 作为实现指令。此偏差不改变文件清单、实施顺序或验证范围。
- 登录模板改为动态路径时，Thymeleaf URL 表达式可能丢失上下文路径、编码或 `client_id`；必须看实际渲染 HTML，而非只查模板源码。
- Filter matcher、Configurer processing URL 与 Security 匿名放行若取得不同路径，会导致入口不可达或放行过宽；保留现有两条精确路径，并分别验证允许与拒绝路径。
- `providerType` 仍作为数据库、state 和 token 的字符串；若只替换部分调用点或误用枚举名，可能破坏配置/绑定查询及回调校验。测试必须固定大写 code、小写 URL 和 Provider 拒绝另一类型。
- System 目录仍独立维护六种身份源及回调 URL；本次只静态核对，未来新增源仍需跨服务契约检查。这是已接受 spec 中的非阻断关注点。
- 不增加新依赖、配置、Schema 或外部 API；回滚可恢复本次 IAM 源码与模板变更，不需要数据库数据转换。Stage 3 不声称真实 GitHub/企业微信平台往返已验证。

## Proof
- `ThirdPartyProviderTypeTest` 精确断言枚举只有两项，且 `providerType`/`loginPath` 分别为 `GITHUB`/`/login/github` 与 `WECOM`/`/login/wecom`。
- `GithubAuthenticationProviderTest`、`WecomAuthenticationProviderTest` 覆盖正确类型可认证、另一类型返回不支持、配置/绑定读取和已认证 token 仍使用原字符串；保留现有失败路径测试。
- `IamProtocolTest` 检查匿名 GET 发起和回调路由、受信 issuer 精确回调、登录页实际渲染的两条链接与 `client_id`、无有效客户端及隐藏/禁用入口行为，并回归表单、短信、小程序、OAuth/OIDC 既有断言。
- Stage 4 使用 `mvn -pl macula-cloud-iam -am test -Plocal` 做正式验证；若外部服务不足，报告具体环境缺口与未运行的测试，不把编译当测试通过。`git diff --check`、引用搜索和 System 回调实现核对证明无意外契约/Schema/依赖变化；真实第三方平台往返另列未验证范围。
