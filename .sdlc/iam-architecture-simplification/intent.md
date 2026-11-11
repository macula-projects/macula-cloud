# Intent: IAM 架构精简与自定义授权收敛
Author: 待确认. Status: accepted.

## Problem
IAM 的历史授权扩展、重复认证编排和兼容代码增加理解与维护成本；已停用的 password grant 仍保留源码，sms/email 直接 Token 扩展扩大了需要维护的协议面。用户希望基于真实代码获得架构图和冗余分析，并让保留功能的实现更简洁、注释清楚、设计一致。

## Proposed outcome
交付与当前源码一致的架构图和可追溯的冗余审阅；仅保留 external_code 这一自定义 grant，移除其他自定义 grant 及失去用途的遗留代码。保留现有标准授权、托管页密码/SMS、第三方网页及 SDK/小程序/端内 Code、身份绑定和安全边界。配置、管理端和文档与实际可用能力一致；保留流程经适当测试证明行为一致。

## Affected users and systems
IAM 维护者、接入应用开发者、应用管理员，以及 IAM、System、System API、Admin 和依赖 Token 契约的 Gateway/资源服务。使用 sms/email 直接 Token grant 的应用需要整改。

## Constraints
- 保留 external_code、authorization_code、refresh_token、client_credentials 的既有合同；不自行启用其他标准 grant。
- 删除 sms/email 的直接 Token 扩展与已停用 password grant 源码，不删除托管密码/SMS、账号证明和绑定发码。
- 保持来源、能力、版本、用户目录、租户、防重放、目录准入与 MFA 发布边界；保留未受影响功能。
- Java 17 兼容，使用适度设计模式和解释约束的中文注释；避免无行为抽象、整体重写和格式化噪声。
- 未经批准不修改业务数据、执行迁移、更新镜像或进行远端 Git 操作；按仓库规则在 accepted plan 后修改源码。

## Open questions
- 文档作者是否登记为 Rain？
- 是否存在仍使用 sms/email 直接 Token grant 的接入方？退出日期及对已签发 Token/Refresh 的过渡要求尚未确认。
- 对仅配置 sms/email 的历史 Client，应采用先整改后退出，还是本轮直接禁用并要求重新配置？不能静默改成其他 grant。
- 未给定量化代码减量或上线期限；静态审阅发现的 1,054 行删除候选不是净减量验收承诺。

Acceptance: 2026-11-11，用户以 YES 显式确认意图；未将此批准解释为未决问题的答案、Git 提交或源码实施批准。
