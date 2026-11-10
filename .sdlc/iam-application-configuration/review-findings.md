# Review findings: IAM 应用配置模型与接入体验重构

## Plan — Round 1 — 2026-11-06
- Reviewer：独立sdlc-reviewer fallback（review_client_plan）。REVIEW.md未指定Stage 3 reviewer，未安装codex-plan-review skill。
- Budget：仅Plan，最多3轮；后续只审变化，不重复记录同类缺陷。本轮实际1轮。
- Important：`.sdlc/iam-application-configuration/plan.md:65` — 并发请求可同时读取旧NULL类型再先后写入不同值，破坏只确认一次；普通旧PUT也可能与确认交错覆盖。
- Fix：全部更新分支按读取时类型和平台构造原子WHERE，NULL用IS NULL，检查影响行数；0行返回409并回滚，不替换绑定、不发布成功缓存副作用。合并现有legacy认证/grant条件。
- Proof：增加OAuth2ClientApplicationConcurrencyIT，使用隔离MySQL、真实Mapper/Service与两个事务验证竞争确认、确认与旧PUT两种交错；不使用复刻SQL代替实际服务。
- State：修订后的计划仍为draft，尚未写新一轮源码。

## Plan — Round 2 — 2026-11-06
- Scope：仅Round 1后的原子更新和并发验证修订；不重复既有问题，未执行Code passes。
- Result：NO_FINDINGS。
- 实际总轮数：2（预算最多3），Round 1 Important已在计划中修正。
- Human gate：待Rain接受plan.md及评审结果。设计spec已accepted，但计划仍draft，未开始新一轮源码修改。

## Migration preflight evidence
- 本地只读核对 `macula-system.flyway_schema_history` 最新成功记录为V13 application apikey，其前为V12/V11/V10。
- information_schema未发现terminal_type/application_type/hosted_login_methods列。未修改数据库。
- 其他环境没有核实；本计划禁止覆盖已执行的V14，若发现需先调整为追加迁移。

## Build handoff — 2026-11-06
- Rain在计划展示后回复YES，计划已accepted，已完成本轮实现。
- 真实壳层query重建与完整快照并发问题的实现适配记录在plan.md；Build自检证据也在那里。
- 尚未运行Stage 5代码审阅，不以计划NO_FINDINGS代替源码审查；等待Build出口人类确认后进入正式Stage 4。


## Plan — 2026-11-10 身份源多能力模型修订

- Reviewer：独立sdlc_reviewer；REVIEW.md未指定Stage3 reviewer且当前无codex-plan-review，按sdlc-build回退。只Plan pass，预算最多3轮；工程师批准仍待请求。
- Round 1，1项Important（同类合并）：plan.md步骤6 — Important — ProviderDirectoryPolicyService.current、ProviderDirectoryRefreshService.run、ProviderApplicationTokenCache.requireCurrent仍按单sourceType/flow锚点复查，未纳入文件清单和实施步骤会拒绝同注册新增能力 — 补齐三个生产类及对应测试，在目录/缓存前后重查选定能力、版本、组织及用途隔离。
- 已处理：精确文件清单、步骤6、Proof增加对应改动；缓存仍保留registration/version/flow/purpose及owner隔离，不因凭据共享绕过capability启用校验。
- Round 1其他直接边界：旧Client写入兼容、迁移等价投影、固定registration及旧state安全失败未发现其他证据支持的计划缺口。
- Round 2：只复核Round 1修正和直接影响，独立审阅返回NO_FINDINGS；未修改文件、未批准人工门禁。实际共2轮，未运行第3轮。本轮尚未实施或测试源码。


## Plan — 2026-11-10 回调与多条绑定增量 Round 1

Reviewer：sdlc-reviewer（默认codex-plan-review未安装，按sdlc-build回退）；仅Plan pass，本增量预算最多3轮，实际第1轮。未修改代码、未自批门禁。

- plan.md:283 — Important — 纯external_code AppLink客户端无法在协议页登记必需回调，因为OAuthProtocolSettings仅在authorization_code下显示输入 — 已补OAuthProtocolSettings、oauthClientConfiguration、System Client启用校验及对应单测/浏览器回归；无需开启authorization_code即可登记并回显现有Client白名单，清楚区分用途。
- plan.md:293 — Minor — sourceField/targetField线格式与现有Record source/target及枚举默认序列化不一致 — 已指定共享显式严格JSON codec、稳定枚举value及真实Jackson→存储→IAM→VO往返证明，拒绝未知/重复属性，不依赖Record默认序列化。

两项均已在计划修正，待第2轮仅复核增量。


## Plan — 2026-11-10 回调与多条绑定增量 Round 2

Reviewer：sdlc-reviewer；仅复核第1轮两项修正及直接影响。结果NO_FINDINGS。纯external_code AppLink客户端可登记并保存现有Client白名单，HTTPS要求限定AppLink候选，不清除其他合法用途地址；共享集合明确稳定线格式、严格codec及System→存储→IAM→VO往返证明。本增量实际2轮，预算3轮；未执行代码Bugs/Security/Compliance评审，未批准人工计划门。


## Plan — 2026-11-10 应用类型排他边界 Round 1

Reviewer：sdlc-reviewer（REVIEW未配Stage3，默认codex-plan-review未安装，按sdlc-build回退）；仅本增量Plan pass，预算最多3轮，实际第1轮。结果NO_FINDINGS。已核对能力模式级白名单、Native/Miniapp注册数量、IN_APP请求级上下文隔离与当前来源证明、授权/同意/兑换复查、转换CAS和密钥保留受限例外。现有MaculaOAuth2AuthorizationService.save在授权Code持久化和用户Token发布前已有MfaCompletionService.checkToken接点，可承接计划中的证明复核；未修改源代码，未批准人工计划门。


## Stage 5 — Round 1 — 2026-11-10 全批IAM迭代

- Reviewed SHA：`a34d1b406fb53fb4e840ff301d8a29af0f37c1f1`；base/merge-base：`3142653c0b371d6c62a1e7a42467c20a375813f4`（origin/main）。完整PR为421文件，包含本会话尚未提交的协议/Scope、Admin授权码、身份源多能力与多字段绑定、六类业务应用登录能力及直接依赖；不包含私有deploy/.env或生成物。
- REVIEW.md存在；Standard深度；本次Code Review第1轮，预算最多3轮。旧报告中的Plan轮次不充作本次源码审阅。本轮未额外扩大审阅轮次。
- Reviewer：独立`iam_pr_reviewer`，只读执行Bugs、Security、Compliance三个pass。Bugs核对协议和当前Client视图、矩阵/数量、转换CAS/凭据保留、授权版本、绑定锁、前端事务和退出竞态、迁移/部署依赖；Security核对IN_APP证明/SSO、当前版本与凭据、Scope资源、来源缓存owner/用途/版本、PKCE/state/nonce/at_hash、管理URL权限。Compliance按声明的资料暴露、凭据、日志/缓存、绑定不改用户资料要求核对，未引入未声明监管标准。
- Result：NO_FINDINGS。Important 0、Minor 0、Nit 0、Pre-existing 0；重复抑制0；发布发现0。没有未解决的Important或Minor。
- 独立fresh-context verifier `iam_pr_verifier`仅收到空发现列表及指定diff，确认两SHA、merge-base和421文件范围存在；输入0、排除0、无逐项待复核发现。该复核不冒充重新审阅整个源码。
- 不将Mock/SAS/隔离数据库验证或本轮审阅视为真实厂商/设备验收；未由agent自批PR。Rain已明确授权“可以推送合并PR”，后续正常合并以该人类指令为依据，不使用自己的评审替代批准，不绕过分支保护。

### Recurring

已扫描`.sdlc/*/review-findings.md`的历史记录。本轮没有新增发现类别，因此没有本轮新出现于两个及以上变更的重复缺陷或待编码条目；不把既有Plan已修主题重复发布为本轮发现。
