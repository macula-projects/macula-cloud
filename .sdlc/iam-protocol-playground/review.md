# IAM 演示功能评审

2026-10-09。状态：人审通过。独立复审完成，I1 已关闭，当前未解决 Important 0、Nit 0。遵循根目录 REVIEW.md，独立 sdlc-reviewer 执行 Bugs、Security、Compliance 三轮（Nit 上限 5）。未创建远端 PR、未提交、推送、合并或部署。

## 人审批准记录

Rain 在独立复审结果展示后，引用本文件明确回复“通过”，批准当前 IAM 演示变更的评审结论。本记录来自用户明确批准，不是代理自批；提交、推送、合并和部署操作尚未执行，仍按用户后续指令进行。

随后 Rain 在“下一步可执行本地提交”的明确上下文中回复“下一步”，授权本地提交当前已评审变更；不包含推送、创建远端 PR、合并或部署。上方未提交状态为人审记录时的快照，实际提交以 Git 历史为准。

## 独立复审结论

- Bugs：本次局部异常处理不改变成功路径、password/sms Provider 或验证码行为，无新增问题。
- Security：`PlaygroundApiController.java:76` 局部捕获校验与 JSON 解析异常，固定 400，不记录或返回异常/被拒绝输入。`PlaygroundInputSafetyTest.java:48` 装配实际 advice，16 组输入检查日志正文、堆栈和响应均无敏感 marker；`PlaygroundHttpIntegrationTest.java:67` 导入真实全局 advice。I1 可关闭。
- Compliance：满足项目明确凭据保护要求；无新增适用要求或问题。
- 独立 reviewer 实际重跑 `mvn -pl macula-cloud-iam -am test -Plocal -Dtest=PlaygroundInputSafetyTest -Dsurefire.failIfNoSpecifiedTests=false`：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，退出 0。没有声称独立重复执行全量测试；最新全量证据仍见 verification.md。
- 保留初审其余范围结论。本次只修改评审文档，不改实现，不替人批准风险、提交或合并。

## PR 正文草稿（尚未发布）

标题：feat(iam): 新增 OAuth 接入指南与真实协议演示

上下文：[意图](intent.md) → [规格](spec.md) → [计划](plan.md)。作者 Rain。

- 新增 H5、移动端、Device、应用后端和兼容接入五类中文演示；真实 IAM PKCE/OIDC、设备授权、刷新、凭据、introspection/撤销及 password/sms 兼容演示。
- 默认关闭、显式非生产许可、生产 profile 否决；专用 opaque 客户端、会话隔离、CSRF/同源检查、敏感字段遮罩及安全异常处理。
- 不改 password/sms 原业务或 Captcha 默认 true；不改 Gateway/System/Admin 源码，不写客户端数据库，不新增依赖。
- 验证：IAM 72 项、完整后端 107 项；管理端单测 10 项/Cypress 8 项；IAM 新旧浏览器 9/8 组；打包通过。命令/输出和边界见 [verification.md](verification.md)。
- 评审遵循 REVIEW.md 的 Bugs、Security、Compliance 三轮。Important：初审 I1 已修复并经独立复审关闭；当前无未解决项。Nits：0。
- 限制：真实手机深链、生产身份源、反向代理和跨节点并发未验证；slow_down 由受控 HTTP 响应验证客户端行为，不声称 IAM 已产生该响应。
- 人审门禁：Rain 已明确回复“通过”；后续 Git 发布与部署操作另按用户指令执行。

## I1 修复提交前记录

Rain 回复“继续”后，局部异常处理新增 MVC 参数校验和 JSON 解析异常，固定 400；新增实际全局 advice 的日志捕获测试，先观察 2 项失败，修复后全部通过；HTTP fixture 同步导入该 advice。最新 IAM 72 项、全后端 107 项、管理端及两套浏览器全部重跑通过，见 [verification.md](verification.md)。此为修复过程记录；最新独立复审结论见上方，以下保留原始发现。

## 上下文与验证证据

- [已接受意图](intent.md)、[已接受规格](spec.md)、[已接受计划](plan.md)
- [验证报告](verification.md)：既有 IAM 70 项、全后端 105 项、管理端单测 10 项/Cypress 8 项、IAM 新旧浏览器 9/8 组通过；本次发现其未覆盖默认全局异常处理，不能据此放行。
- 评审范围包含实际 tracked diff 与 IAM playground 全部新增源码/页面/测试，不仅已跟踪文件。

## Important

### I1：输入校验异常将原始凭据写入日志（Security / Compliance）

位置：`macula-cloud-iam/src/main/java/dev/macula/cloud/iam/playground/PlaygroundApiController.java:50`、`:74`；`PlaygroundRequest.java:37`。

`@Valid @RequestBody` 的字段长度校验在进入 Controller 方法前抛出 `MethodArgumentNotValidException`。当前局部处理仅覆盖 `IllegalArgumentException`、`IllegalStateException`，因而该异常进入 Macula Boot 默认启用的 `ControllerExceptionAdvice`，其 `log.error(..., e)` 输出 `FieldError.rejectedValue`。DTO 的脱敏 `toString()` 不保护此路径。

独立 reviewer 使用当前解析的 `macula-boot-starter-web-6.1.0-SNAPSHOT.jar`（并以 javap 核对）及 standalone MockMvc 装配实际控制器与实际 advice，仅发送合成超长密码，不涉及真实凭据。输出摘录（marker 已缩写）：

```text
ERROR ControllerExceptionAdvice -- MethodArgumentNotValidException...
field 'password': rejected value [SYNTHETIC_REVIEW_MARKER_...]
REVIEW_STATUS=500
```

复现输入为 PASSWORD 场景、任意合成用户名、密码 `SYNTHETIC_REVIEW_MARKER_` 拼接 260 个 x。默认 `WebAutoConfiguration` 的 exception-advice 配置 matchIfMissing=true，IAM 没有关闭。既有 HTTP fixture 未导入该 advice。

影响：用户误粘贴超长密码、验证码等敏感值时写入共享日志，违反规格 R7 与后端规则的凭据日志禁令；验证阶段的无泄露证明不完整。

建议：演示控制器局部接管参数校验及请求反序列化异常，固定返回 400，不输出异常、cause、rejectedValue；装配实际全局 advice 补充日志捕获回归，断言密码、验证码、回调敏感字段不进入日志或错误响应。无需改变原 password/sms Provider 或验证码默认行为。修复后返回 sdlc-test，重新执行受影响与完整项目验证，再复审。

## 其余轮次与结论

- Bugs：未发现其他达到 Important 的证据型问题；已检查业务仓库委托、生产否决、存量授权拒绝、PKCE/OIDC、Device 确认与轮询、会话隔离。
- Compliance：I1 同时违反明确凭据保护规则，不重复计数；未发现其他适用组织或法规要求，不虚构要求。
- Nits：0。

初审遵循 sdlc-deploy 在发现问题后停止，随后依 Rain 指令修复、复验并独立复审。现已收到上方明确人审批准；发布 PR 仍需后续明确授权，本地材料不能视为 PR 已发布或代码已合并。
