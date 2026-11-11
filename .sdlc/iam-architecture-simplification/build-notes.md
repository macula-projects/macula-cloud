# IAM 架构精简实施记录

日期：2026-11-11。状态：本地实现完成，等待工程师确认 Build 出口；未自行批准阶段门。工程合同：[plan.md](plan.md)。

## 实现结果

- 自定义 grant 仅保留 external_code；移除 sms/email 直换 Token 与 password 遗留、专属发码/挑战组件和无用继承。托管密码/SMS、第三方网页与 SDK 登录、绑定证明继续保留。
- grant 支持规则收敛至 System API 的 OAuth2GrantPolicy；System 写入严格拒绝退役值，历史读取保留原配置。Admin 标明退役值并要求显式移除，指南不再生成旧请求。
- 三条退役发码/挑战路径全部方法固定返回 410 endpoint_retired；不进入登录跳转或发码逻辑。
- 历史 Access Token 按原到期/撤销/版本合同处理，退役 grant 的 Refresh 返回 invalid_grant。旧 email Principal 仅在指定授权属性位置进行固定类型适配，保留原安全 JSON 读取器；没有开放任意反序列化或改写缓存。
- 五家第三方网页 Provider/Configurer 编排收敛为统一 Provider、Adapter 接口、注册表与组合 Configurer；保留各平台 Client、路由、参数、state、来源和能力合同。
- 必需的目录、限流、来源与事务依赖不再允许 null 测试旁路；测试使用明确 Mock 与真实事务回调生命周期。

## 根据工程师反馈细化分层

用户先指出接口抽象、层次及配置臃肿，再明确登录认证配置与协议配置独立。最终 JwtConfiguration 只含 3 个基础设施 Bean；密钥接口和实现归 protocol/oauth2/jwt，主协议配置缩至约 112 行，并拆出 Token、Persistence 和 Extension 装配。AccessTokenClaimsService 负责共同声明算法；AuthorizationServerExtension 用三个具体实现组合端点。

登录和协议 Provider 分别在链内创建，不作为全局 AuthenticationProvider Bean 暴露。安全链回归明确检查协议链无第三方登录 Filter、登录链无 Token 端点 Filter，以及无跨链全局 Provider Bean。

## 实际偏差与处置

完整记录见 plan.md 的 Implementation deviations：相邻 Admin 测试改为退役拒绝；组合 Configurer 避免 HttpSecurity 同类型覆盖；以实际安全链所有平台路由测试替代影射实现的 Configurer 测试；补充分层接口、JWT 配置与真实签验测试；共享 permissions 标识归领域 API；删除只描述旧 password 扩展的 grant README。

MFA IT 更新其合成平台能力与 Code 类型配置，使夹具走到原定的 MFA 拒绝边界；保留拒绝断言，没有放宽生产安全策略。真实数据库验证只创建独立本地 MySQL/Redis 实例，V23 迁移测试和绑定测试通过后关闭进程。没有连接业务库。

## 规模与交付

IAM 生产 Java 规模以最终 verification.md 为准；最终新增包说明用于维护职责边界，不以目录移动伪装代码删除。架构图提供 PNG 和可编辑 SVG；基线图保留重构前状态，目标图反映最终实施。

未提交、推送、部署或更换凭据；未修改 schema、Flyway SQL、业务用户、绑定、凭据或配置快照。真实厂商与设备联调待独立执行。

## 后续架构反馈的落实

- thirdparty 实现按browser/provider/binding/directory/cache拆子包；保留7个共享/序列化类型或含内部序列化记录的仓库原全限定名。
- 按实际依赖重新判断service：协议存储、Claims、Profile、外部Token绑定与协议工具归protocol；登录请求适配及短信认证实现归authentication；公共账号、Client、来源与绑定规则归service对应能力包。
- 原UserDetails实现拆为HostedUserDetailsService与ClientAccountDetailsService默认实现。保留用户名与显式Client路径各自原目录优先级；新的账号测试证明快照漂移拒绝、无Servlet上下文加载和停用用户拒绝。
- 公共身份规则只依赖IdentityDirectoryService；原ProviderDirectoryPolicyService实现准入端口，其快照与后台刷新不进入公共服务。Admission投影随接口定义，算法及TTL约束保持。
- 常量容器改final类，删除无调用的OAuth2端点包装方法，避免常量接口和多余抽象。
- 一轮安全链测试发现隔离Test App缺少新账号实现装配，已补显式Import；合成UserAuthInfo补齐既有必需dataScope。修正后369个IAM测试通过，随后执行clean全仓与真实基础设施回归。

## README归位

原README累积了日期增量、迁移版本和本轮实施流水，已整体重写为136行的模块介绍：能力、依赖、架构、接入、端点、运行和验证。产品详细合同归docs/configuration.md，历史模型与退役兼容处置归compatibility-notes.md；不再把当前约定埋在多段互相替代的说明中。架构图提供模块docs稳定入口，源码静态审阅仍保留原基线图。

## 配置目录归类

协议装配统一归config.oauth2，登录安全链归config.authentication，跨链MFA与PasswordEncoder留config根包。协议CORS实现归protocol.oauth2.endpoint.OAuth2CorsConfigurationSource；名称不再混入Login。原Bean名、框架扫描范围、链顺序与端点保持。同步测试包及引用，clean verify通过369个IAM测试和16个MFA/缓存IT。
