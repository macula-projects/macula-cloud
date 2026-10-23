# Review findings: IAM 外部凭证登录

## Plan

- 评审者：`REVIEW.md` 未指定 Stage 3 评审者；按 `sdlc-build` 回退到 `sdlc-reviewer` 代理。
- 范围：只执行 Plan pass，最多三轮；Round 1 全量检查，后续只检查上一轮修订。

### Round 1

- Important — OAuth Client 只有 code type allowlist，没有固定绑定具体第三方应用配置，无法阻止不同 Client 共享全局 provider registration。已增加 `sys_oauth2_client_external_provider` 映射、System/Admin 管理、`ClientSettings` 快照、运行时按 registration ID 读取与隔离测试。
- Important — 验证码接口要求“登记 Origin”，但原计划没有持久化来源、精确比较及 CORS preflight 方案。已增加 `allowedOrigins`，限定规范化 origin 精确集合，并要求 OPTIONS/POST 使用同一动态 Client-Origin 解析与跨 Client 拒绝测试。
- Important — 现有 System 校验强制所有 `none` Client 含 `authorization_code`，会拒绝只使用 `external_code` 的 App/小程序 Client。已明确公共 Client 可以只启用端侧用户 grant；只有含 `authorization_code` 时才要求 redirect URI，同时始终要求 `requireProofKey=true`、拒绝 `client_credentials`。
- Important — V9 对存量空白 mobile/email 的处理和证明矛盾。已明确迁移只把语义空白安全转为 `NULL`，再检测非空重复/非规范值；非空业务数据只输出只读诊断并停止，不自动修改。
- Important — Redis ticket 与数据库事务不能天然原子。已固定 `READY -> CLAIMED(ownerNonce, lease) -> CONSUMED` 状态机、数据库唯一键幂等、回滚 compare-and-release、`afterCommit` finalize 及提交后崩溃的幂等恢复语义。
- Compliance：除以上 Client 隔离、敏感数据和审计边界外，没有额外适用要求遗漏。

### Round 2

- Important — CORS 的 Client 提示没有明确绑定 POST body 的 `client_id`，可能用 Client A 的 Origin 驱动 Client B 的请求。已收敛为单值 `X-OAuth-Client-Id`，禁止 query 提示，并要求三个端点在任何业务处理前校验 header 与 body 完全相等。
- Important — Admin 只配置 code type，无法提供后端新增的 Origin 和 provider registration 必填值。已增加非敏感 registration/capability 目录、每个 code type 的 registration 选择器、allowed Origin 编辑及完整映射测试。
- Minor — ticket 状态机未落实失败次数。已在 READY/CLAIMED 状态保留 `failedAttempts`，调用方校验失败原子计次并在上限后终止，明确基础设施瞬时失败不计数。
- Minor — V9 证明未覆盖非规范存量值。已增加 email 大小写/空格、手机号非规范值的升级失败和去标识诊断输出测试。
- Compliance：本轮修订没有引入其他适用要求。

### Round 3

- No findings。Round 2 的四项修订均已闭环且相互一致：Client header/body 绑定、Admin registration/Origin 配置、ticket 失败计数和 V9 非规范存量数据证明均通过复审。

## Engineer feedback after Plan pass

- 端侧账号密码登录必须走 `/oauth2/authorize`，以 authorization_code + S256 PKCE 返回端侧；`external_code` 只处理端侧已经取得的第三方凭证。计划已移除把 password grant 作为新端侧直连能力的表述，并允许同 Client 的 authorization_code 用户 Token 作为绑定账号归属证明；现有 password grant 仅保持兼容。
- 企业微信成员字段按官方响应名使用 `userid`（小写 `i`）；钉钉字段保持 `userId`。规格、计划和证明项已分别固定，避免两个平台字段名混用。
- 浏览器第三方认证还包括已实现的 GitHub。GitHub 保持 `/login/github` 发起、IAM Session 回调与现有绑定闭环，不增加 `ExternalCodeType`；`external_code` 的浏览器枚举只描述调用方已经先取得第三方 code 再提交 Token Endpoint 的场景。
- APP 与微信小程序都同时支持两条通道：端侧先取得第三方 code 后使用 `external_code` 一步登录，或进入 `/oauth2/authorize` 由 IAM 完成认证并通过 authorization_code + S256 PKCE 返回。同一公共 Client 可同时启用两个 grant；实现补充微信小程序 Client 的共存测试，Admin 选择外部类型时保留已有 `authorization_code`。

## Implementation review

### Dependency evidence

- 阿里云号码认证按官方服务端合同使用 `GetMobile`，固定端点为 `dypnsapi.aliyuncs.com`；官方文档建议 Java SDK `dypnsapi20170525` 2.0.0 及以上，IAM 使用 2.0.0。
- 本地 Maven POM 静态检查到该 SDK 为 Apache License 2.0，直接声明的 Tea 传递依赖包括 `tea-util:0.2.23`、`tea-openapi:0.3.10`、`endpoint-util:0.0.8`、`tea:1.3.1`、`openapiutil:0.2.2`。
- Stage 3 未获授权运行 Maven 或联网依赖扫描，因此完整解析树、许可证聚合与漏洞结果尚未验证；这些仍是 Stage 4 阻塞项，真实平台联调完成前不得标记上线可用。

### Bugs pass

- Important — binding ticket 在 claim 后读取到畸形数据或 Redis 读取异常时，原实现可能无条件删除已被其他 owner 恢复占用的 key。已改为仅通过 owner nonce 条件脚本消费或释放，并补充 malformed/owner 边界的静态审查。
- Important — 外部身份绑定指向已删除本地账号时会被当成“未绑定”再次签发 ticket。已改为失败关闭，防止产生永远无法满足唯一键约束的绑定流程。
- Minor — access token 已保存后若 token generator 没有 claims，签发器会空指针失败并留下孤儿授权。已对无 claims 情况返回空附加参数。
- 本轮未发现其他有证据支持的 Bugs finding；Maven、Redis 并发和隔离 MySQL 运行证据留待 Stage 4。

### Security pass

- Important — 短信和邮件的手机号、邮箱及 IP 限流键原先使用无密钥 SHA-256，低熵标识可被字典恢复。已改为带服务端密钥和用途域的 HMAC-SHA256，并增加 Redis 调用参数不含原值的测试。
- Important — 阿里云 SDK 抛出的所有异常原先都会释放 external code 防重放占用，明确 4xx 也可反复重试。已改为仅网络异常和明确 HTTP 5xx 进入可重试基础设施路径，4xx/业务失败按无效凭证消费。
- Important — 验证码和 CAPTCHA 原先只有 Client 与目标/IP 的组合限流，没有独立 Client 总量窗口，分布式来源可绕过单 IP、单目标限制。已为 CAPTCHA、短信和邮件分别增加 Client 总量计数，并保留目标与 IP 维度。
- Minor — CAPTCHA 限流维度原先先用冒号拼接再摘要，存在字段边界歧义。已改为用途域加 NUL 分隔的 HMAC 元组，并补充限流 key 不含 Client、Origin、IP 原值的测试。
- Minor — 公开表单缺少若干输入长度上限。已限制 ticket、Client ID、challenge、answer 和 target，降低无意义大输入进入协议链的风险。
- 本轮未发现其他有证据支持的 Security finding；依赖漏洞扫描和真实第三方平台行为仍未验证。

### Compliance pass

- 适用边界为认证标识、凭据和第三方响应的最小披露。当前日志静态检查只发现 provider、stage、HTTP 状态、数值错误码、本地类别、目标 HMAC 和异常类名；未发现 code、Token、ticket、subject、手机号、邮箱、第三方正文、异常消息或堆栈输出。
- registration 目录只返回 ID、名称、provider 类型和非敏感能力集合；秘密仍只以是否已配置的元数据呈现。
- 除上述数据最小化、访问控制和审计约束外，本轮没有其他项目声明的适用 Compliance requirement。

### Static feedback

- `git diff --check` 通过。
- Admin 两个变更 JavaScript 文件执行 `node --check` 通过。
- 变更及新增 Java 文件版权头静态检查通过；未发现非 static 星号导入，static 星号导入符合当前 Checkstyle 配置。
- 未运行 Maven、npm 测试或构建；不得把上述静态反馈表述为编译或测试通过。
