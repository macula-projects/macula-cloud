# 应用 API Key 管理审查记录

## Plan — Round 1
- 未安装 codex-plan-review，按 sdlc-build 回退独立 sdlc-reviewer Plan pass；仅计划审查，不执行代码审查/测试。预算最多3轮，第二轮起只审增量、不重复问题。
- Important：plan.md 步骤6，应用删除捕获关联 Key 与并发创建未使用共同数据库并发协议，可能遗留可用 Key 缓存。
- 处理：步骤6明确创建及删除先锁定同一归属应用行，锁后重查并操作，批量按ID排序；Proof补充删除/创建交错及隔离数据库行锁验证。
- Round1候选1，修订后等待增量复核；尚未取得人工计划批准。

## Plan — Round 2
- 仅复核步骤6及对应Proof增量，不重复Round1已记录发现。
- 共同应用行锁、锁后复核、批量排序及交错测试覆盖原计划缺口，无新增发现。
- 实际共2轮，仅Plan pass；计划审查完成，等待工程师明确接受plan.md，不代表源码验证或人工门禁通过。

## Deploy — Round 1
- Standard，Bugs/Security/Compliance；基线291e80dc、HEAD edf2c7c加未提交工作区，scope fingerprint ddf925b4582e27ffc3ae4e7c58262cb5435f5833b6d7f352a403d00868e702e1。最多3轮，Nit上限5。
- Security Important：SysApplicationApiKeyServiceImpl.application信任请求租户，目标租户同名管理角色可能绕过成员关系读取/管理凭据。fresh verifier确认实际路径；已修复并于Round2独立审查/复核确认关闭。
- Bugs Important：afterCommit尚占原连接，REQUIRES_NEW再申请连接导致有限池竞争；真实Hikari单连接探针返回503，fresh verifier确认；已修复并于Round2独立审查/复核确认关闭。此前无界数据源IT不覆盖该风险，撤销绿色状态返回验证。
- Compliance未发现额外适用监管要求。Counts：posted2，suppressed1（此前已修复计划行锁问题），verifier-rejected0，Nit0。

## Deploy — Round 2
- Standard，Bugs/Security/Compliance，只审Round1后的11文件增量；HEAD edf2c7c加未提交工作区，scope fingerprint a983c16f104ddd8b48539bcab31de33f7e4fd2ea64061bf9eed93d7173984f54。
- Security：可信租户解析已在Key及应用写入中复用；真实解析器的伪造请求租户回归覆盖同名管理角色、无目标成员关系，在查询角色及凭据前拒绝。原Important关闭。
- Bugs：提交后复用连接，缓存专用Mapper强制清理查询缓存；真实Hikari单连接下创建和撤销成功，探针SUCCESS。原Important关闭。
- fresh verifier逐项确认两项候选不再发生，不调整严重性、不新增问题、不自批门禁。
- 复验：默认后端411条目无失败（410执行通过、1条件跳过已单独实测通过）、真实IT6通过、前端74单元/33E2E通过，构建及静态检查通过。
- Counts：posted0，suppressed2，verifier-rejected0，Nit0；关闭2，未解决0。代码审查共2轮，未超过3轮上限。
- 最终25个被审源码/测试/迁移文件SHA-256与本轮快照一致，漂移0；文档后续仅补结果与清理记录。

## Recurring
- 请求租户未校验即用于写入/缓存访问的类别，在本次及tenant-aware-menu-route-cache的Plan Round1中均出现。编码：REVIEW.md的Security pass明确检查“missing authorization at a trust boundary”；.agents/rules/backend-development.md:27明确禁止信任请求租户并要求校验安全上下文。实现约束由既有MenuRouteTenantContext复用及对应越权测试承载。本轮未修改审查规则或创建未经确认的eval。
- 有限池提交回调额外借连接目前只发现于本变更，已用真实Hikari单连接IT约束。

## Round2时的人工门禁记录
当时本地验证与审查完成，未解决发现0，业务源码尚未提交或推送。后续用户已明确授权本地部署，并验收通过；本轮又明确授权提交、推送和创建PR。独立审查不替代PR人工审查或合并批准。

## Deploy — Round 3
- Standard，依据REVIEW.md执行Bugs/Security/Compliance。HEAD edf2c7c加最终工作区，91个源码/配置/规则文件快照fingerprint：1baaa880049244cb8969d255d8a6993dbec122e3bd1c438241d28d65115dfd6d。
- 本轮只补审Round2之后的日期规范、Date/Instant映射、详情VO/写入Form、导出BO、部门兜底、前端当地时间/UI及Compose时区增量和直接调用链。此前独立轮次的逐文件快照当前不可得，按范围与变更记录识别增量，不声称本轮重新审查完整API Key功能。
- Bugs：未发现证据明确的兼容性、日期精度、主键wire或映射调用链错误。Security：未发现新增授权缺口、敏感字段泄漏或客户端审计字段覆盖。Compliance：未发现违反已声明的模型隔离与时间协议要求，无额外适用监管要求。
- Counts：posted0，Important0，Minor0，Nit0；suppressed3（此前已关闭问题2、证据不足候选1）。候选是显式存储时区与JVM不同时Excel按JVM展示；未承诺保留数据库墙钟值，当前部署时区一致，未列为缺陷。候选运行探针未完成，不声称已复现。
- Boot远端main已核对包含c30ff533日期能力；Cloud与Docker验证使用本地快照，未验证远端Maven快照可获取性。测试及本地运行证据详见test-report，本轮独立审查未冒称重新执行测试。
- fresh verifier确认surviving findings为空，无待逐项验证项，verifier-rejected0；未独立复跑测试或Docker。
- 未新增重复类别；Recurring既有编码仍有效。已达3轮审查上限，本轮无待修复发现，不自批合并。
